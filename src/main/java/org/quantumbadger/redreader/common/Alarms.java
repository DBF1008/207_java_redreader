/*******************************************************************************
 * This file is part of RedReader.
 *
 * RedReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RedReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RedReader.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package org.quantumbadger.redreader.common;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import org.quantumbadger.redreader.receivers.NewMessageChecker;
import org.quantumbadger.redreader.receivers.RegularCachePruner;

import java.util.HashMap;
import java.util.Map;

public class Alarms {

	private static final String TAG = "Alarms";

	private static final Map<Alarm, AlarmManager> alarmMap = new HashMap<>();
	private static final Map<Alarm, PendingIntent> intentMap = new HashMap<>();

	/*
		An enum to represent an alarm that may be created.
		If you wish to add an alarm, just add it at the top of the enum with the 3 arguments,
		and then call startAlarm() on it.
	 */

	public enum Alarm {
		MESSAGE_CHECKER(AlarmManager.INTERVAL_HALF_HOUR, NewMessageChecker.class, true),
		CACHE_PRUNER(AlarmManager.INTERVAL_HOUR, RegularCachePruner.class, true);

		private final long interval;
		private final Class<? extends BroadcastReceiver> alarmClass;
		private final boolean startOnBoot;

		Alarm(
				final long interval,
				final Class<? extends BroadcastReceiver> alarmClass,
				final boolean startOnBoot) {
			this.interval = interval;
			this.alarmClass = alarmClass;
			this.startOnBoot = startOnBoot;
		}

		private long interval() {
			return interval;
		}

		private Class<? extends BroadcastReceiver> alarmClass() {
			return alarmClass;
		}

		private boolean startOnBoot() {
			return startOnBoot;
		}
	}

	/**
	 * Starts the specified alarm
	 */

	public static void startAlarm(final Alarm alarm, final Context context) {
		if(!alarmMap.containsKey(alarm)) {
			final Intent alarmIntent = new Intent(context, alarm.alarmClass());

			int flags = 0;

			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				flags |= PendingIntent.FLAG_IMMUTABLE;
			}

			@SuppressLint("UnspecifiedImmutableFlag")
			final PendingIntent pendingIntent = PendingIntent.getBroadcast(
					context,
					0,
					alarmIntent,
					flags);

			final AlarmManager alarmManager
					= (AlarmManager)(context.getSystemService(Context.ALARM_SERVICE));
			alarmManager.setInexactRepeating(
					AlarmManager.RTC,
					System.currentTimeMillis(),
					alarm.interval(),
					pendingIntent);

			alarmMap.put(alarm, alarmManager);
			intentMap.put(alarm, pendingIntent);

			Log.i(TAG, "Started alarm: " + alarm.name());
		}
	}

	/**
	 * Stops the specified alarm
	 *
	 * @param alarm alarm to stop
	 */

	public static void stopAlarm(final Alarm alarm) {
		if(alarmMap.containsKey(alarm)) {
			alarmMap.get(alarm).cancel(intentMap.get(alarm));
			alarmMap.remove(alarm);
			intentMap.remove(alarm);
			Log.i(TAG, "Stopped alarm: " + alarm.name());
		}
	}

	/**
	 * Returns whether the specified alarm is currently running.
	 *
	 * @param alarm the alarm to check
	 * @return true if the alarm is currently scheduled
	 */
	public static boolean isAlarmRunning(final Alarm alarm) {
		return alarmMap.containsKey(alarm);
	}

	/**
	 * Determines whether a given alarm <em>should</em> be running based on current
	 * preference values. This is the single source of truth for the mapping between
	 * preference state and alarm side-effects.
	 *
	 * <p>Currently:
	 * <ul>
	 *   <li>{@code MESSAGE_CHECKER} runs only when
	 *       {@link PrefsUtility#pref_behaviour_notifications()} is {@code true}.</li>
	 *   <li>All other alarms that have {@code startOnBoot} set run unconditionally.</li>
	 * </ul>
	 *
	 * @param alarm the alarm to evaluate
	 * @return true if the alarm should be running
	 */
	static boolean shouldAlarmRun(final Alarm alarm) {
		return shouldAlarmRun(alarm, PrefsUtility.pref_behaviour_notifications());
	}

	/**
	 * Pure (side-effect free) overload that decides whether an alarm should run
	 * given explicit preference values. Package-private for testing.
	 *
	 * @param alarm the alarm to evaluate
	 * @param notificationsEnabled whether the user has notifications enabled
	 * @return true if the alarm should be running
	 */
	static boolean shouldAlarmRun(final Alarm alarm, final boolean notificationsEnabled) {
		if(!alarm.startOnBoot()) {
			return false;
		}

		switch(alarm) {
			case MESSAGE_CHECKER:
				return notificationsEnabled;
			default:
				return true;
		}
	}

	/**
	 * Clears all tracked alarm state. Package-private; intended for use by unit tests only.
	 */
	static void resetForTesting() {
		alarmMap.clear();
		intentMap.clear();
	}

	/**
	 * Reconciles the actual alarm state with the desired state derived from current
	 * preference values. Alarms that should be running but aren't will be started;
	 * alarms that are running but shouldn't be will be stopped.
	 *
	 * <p>This is the single entry-point for syncing preference-driven side-effects.
	 * It must be called:
	 * <ul>
	 *   <li>On application startup ({@code RedReader.onCreate()})</li>
	 *   <li>On device boot ({@code BootReceiver})</li>
	 *   <li>After any preference change that may affect alarm state
	 *       (e.g. notification toggle in settings)</li>
	 *   <li>After a preference backup restore</li>
	 * </ul>
	 *
	 * @param context application or activity context
	 */
	public static void reconcile(final Context context) {
		for(final Alarm alarm : Alarm.values()) {
			final boolean shouldRun = shouldAlarmRun(alarm);
			final boolean isRunning = isAlarmRunning(alarm);

			if(shouldRun && !isRunning) {
				Log.i(TAG, "Reconcile: starting alarm " + alarm.name());
				startAlarm(alarm, context);
			} else if(!shouldRun && isRunning) {
				Log.i(TAG, "Reconcile: stopping alarm " + alarm.name());
				stopAlarm(alarm);
			}
		}
	}

	/**
	 * Starts all alarms that are supposed to start at device boot.
	 *
	 * @param context
	 * @deprecated Use {@link #reconcile(Context)} instead, which respects preference state.
	 */
	@Deprecated
	public static void onBoot(final Context context) {
		reconcile(context);
	}
}
