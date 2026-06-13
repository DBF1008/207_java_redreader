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

import androidx.annotation.NonNull;

import org.quantumbadger.redreader.receivers.NewMessageChecker;
import org.quantumbadger.redreader.receivers.RegularCachePruner;

import java.util.HashSet;
import java.util.Set;

public class Alarms {

	private static final Object LOCK = new Object();

	// Alarms that this process has scheduled. Used to avoid pointlessly rescheduling (and thereby
	// resetting the trigger time of) an alarm that is already running.
	private static final Set<Alarm> sScheduledAlarms = new HashSet<>();

	/*
		An enum to represent an alarm that may be created.

		To add an alarm, add it here with its interval and receiver, then add a case to
		shouldBeScheduled() describing when it should run. reconcile() takes care of starting and
		stopping it to match the preferences.
	 */

	public enum Alarm {
		MESSAGE_CHECKER(AlarmManager.INTERVAL_HALF_HOUR, NewMessageChecker.class),
		CACHE_PRUNER(AlarmManager.INTERVAL_HOUR, RegularCachePruner.class);

		private final long interval;
		private final Class<? extends BroadcastReceiver> alarmClass;

		Alarm(
				final long interval,
				final Class<? extends BroadcastReceiver> alarmClass) {
			this.interval = interval;
			this.alarmClass = alarmClass;
		}

		private long interval() {
			return interval;
		}

		private Class<? extends BroadcastReceiver> alarmClass() {
			return alarmClass;
		}
	}

	/**
	 * Snapshot of the preference values that determine which background alarms should run.
	 *
	 * <p>Capturing the relevant preferences in a plain value object keeps the scheduling decision
	 * ({@link #shouldBeScheduled}) pure and unit-testable, separate from the Android-specific work
	 * of actually (de)registering the alarms.
	 */
	public static final class AlarmPrefs {

		private final boolean notificationsEnabled;

		public AlarmPrefs(final boolean notificationsEnabled) {
			this.notificationsEnabled = notificationsEnabled;
		}

		public boolean notificationsEnabled() {
			return notificationsEnabled;
		}

		@NonNull
		public static AlarmPrefs current() {
			return new AlarmPrefs(PrefsUtility.pref_behaviour_notifications());
		}
	}

	/**
	 * Pure decision: whether the given alarm should be scheduled for the given preferences.
	 *
	 * <p>The preferences are the single source of truth; {@link #reconcile} applies this decision
	 * to the actual {@link AlarmManager} state.
	 */
	public static boolean shouldBeScheduled(
			@NonNull final Alarm alarm,
			@NonNull final AlarmPrefs prefs) {

		switch(alarm) {
			case MESSAGE_CHECKER:
				// The message checker only does anything useful (and only posts notifications)
				// when notifications are enabled, so there is no reason to keep it running
				// otherwise.
				return prefs.notificationsEnabled();

			case CACHE_PRUNER:
				return true;

			default:
				throw new RuntimeException("Unhandled alarm: " + alarm);
		}
	}

	private static PendingIntent buildPendingIntent(
			final Alarm alarm,
			final Context context) {

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

		return pendingIntent;
	}

	/**
	 * Starts the specified alarm, unless this process has already started it.
	 */

	private static void startAlarm(final Alarm alarm, final Context context) {
		synchronized(LOCK) {

			if(sScheduledAlarms.contains(alarm)) {
				return;
			}

			final AlarmManager alarmManager
					= (AlarmManager)(context.getSystemService(Context.ALARM_SERVICE));

			alarmManager.setInexactRepeating(
					AlarmManager.RTC,
					System.currentTimeMillis(),
					alarm.interval(),
					buildPendingIntent(alarm, context));

			sScheduledAlarms.add(alarm);
		}
	}

	/**
	 * Stops the specified alarm.
	 *
	 * <p>The cancellation uses a freshly reconstructed {@link PendingIntent} rather than a cached
	 * one, so that an alarm registered by a previous process (PendingIntents are canonical) is
	 * also cancelled. This keeps {@link #reconcile} authoritative regardless of in-process state.
	 *
	 * @param alarm alarm to stop
	 */

	private static void stopAlarm(final Alarm alarm, final Context context) {
		synchronized(LOCK) {

			final AlarmManager alarmManager
					= (AlarmManager)(context.getSystemService(Context.ALARM_SERVICE));

			alarmManager.cancel(buildPendingIntent(alarm, context));

			sScheduledAlarms.remove(alarm);
		}
	}

	/**
	 * Reconciles every alarm's scheduling state with the current preferences, so that the
	 * preferences remain the single source of truth for these background side effects.
	 *
	 * <p>This is idempotent and safe to call from any thread. Call it on app startup, on device
	 * boot, after changing a preference that affects an alarm, and after restoring a preferences
	 * backup.
	 *
	 * @param context context used to access {@link AlarmManager}
	 */

	public static void reconcile(final Context context) {

		final AlarmPrefs prefs = AlarmPrefs.current();

		for(final Alarm alarm : Alarm.values()) {
			if(shouldBeScheduled(alarm, prefs)) {
				startAlarm(alarm, context);
			} else {
				stopAlarm(alarm, context);
			}
		}
	}
}
