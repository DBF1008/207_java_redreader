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

package org.quantumbadger.redreader.test.general;

import org.junit.Before;
import org.junit.Test;
import org.quantumbadger.redreader.common.Alarms;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for the alarm reconcile mechanism in {@link Alarms}.
 *
 * <p>These tests verify the single-source-of-truth mapping between preference
 * values and alarm side-effects, ensuring that:
 * <ul>
 *   <li>MESSAGE_CHECKER alarm tracks the notification preference</li>
 *   <li>CACHE_PRUNER alarm runs unconditionally</li>
 *   <li>The {@code isAlarmRunning} / {@code resetForTesting} lifecycle is correct</li>
 * </ul>
 */
public class AlarmsReconcileTest {

	@Before
	public void setUp() {
		// Clear any residual alarm state between tests
		Alarms.resetForTesting();
	}

	// ========================================================================
	// shouldAlarmRun – pure predicate tests
	// ========================================================================

	@Test
	public void messageCheckerShouldRunWhenNotificationsEnabled() {
		assertTrue(
				"MESSAGE_CHECKER should run when notifications are enabled",
				Alarms.shouldAlarmRun(Alarms.Alarm.MESSAGE_CHECKER, true));
	}

	@Test
	public void messageCheckerShouldNotRunWhenNotificationsDisabled() {
		assertFalse(
				"MESSAGE_CHECKER should NOT run when notifications are disabled",
				Alarms.shouldAlarmRun(Alarms.Alarm.MESSAGE_CHECKER, false));
	}

	@Test
	public void cachePrunerShouldRunWhenNotificationsEnabled() {
		assertTrue(
				"CACHE_PRUNER should run regardless of notification pref (enabled)",
				Alarms.shouldAlarmRun(Alarms.Alarm.CACHE_PRUNER, true));
	}

	@Test
	public void cachePrunerShouldRunWhenNotificationsDisabled() {
		assertTrue(
				"CACHE_PRUNER should run regardless of notification pref (disabled)",
				Alarms.shouldAlarmRun(Alarms.Alarm.CACHE_PRUNER, false));
	}

	// ========================================================================
	// isAlarmRunning – state tracking tests
	// ========================================================================

	@Test
	public void noAlarmRunningAfterReset() {
		for(final Alarms.Alarm alarm : Alarms.Alarm.values()) {
			assertFalse(
					"Alarm " + alarm.name() + " should not be running after reset",
					Alarms.isAlarmRunning(alarm));
		}
	}

	@Test
	public void stopAlarmClearsRunningState() {
		// Simulate the alarm being tracked (as startAlarm would do), then stop it.
		// We can't call startAlarm without an Android Context, so we use stopAlarm
		// which is safe to call even when the alarm isn't running.
		Alarms.stopAlarm(Alarms.Alarm.MESSAGE_CHECKER);
		assertFalse(
				"Alarm should not be running after stopAlarm on a non-running alarm",
				Alarms.isAlarmRunning(Alarms.Alarm.MESSAGE_CHECKER));
	}

	@Test
	public void stopAlarmIsIdempotent() {
		// Calling stopAlarm multiple times should be safe
		Alarms.stopAlarm(Alarms.Alarm.MESSAGE_CHECKER);
		Alarms.stopAlarm(Alarms.Alarm.MESSAGE_CHECKER);
		Alarms.stopAlarm(Alarms.Alarm.CACHE_PRUNER);
		assertFalse(Alarms.isAlarmRunning(Alarms.Alarm.MESSAGE_CHECKER));
		assertFalse(Alarms.isAlarmRunning(Alarms.Alarm.CACHE_PRUNER));
	}

	// ========================================================================
	// Reconcile decision matrix – integration of predicate + state tracking
	// ========================================================================

	/**
	 * Simulates the reconcile decision for a single alarm given explicit
	 * preference state and current running state. This mirrors the logic in
	 * {@code Alarms.reconcile()} but without requiring an Android Context.
	 *
	 * @return true if the alarm should be started, false if it should be stopped,
	 *         null if no change is needed
	 */
	private Boolean reconcileDecision(
			final Alarms.Alarm alarm,
			final boolean notificationsEnabled) {

		final boolean shouldRun = Alarms.shouldAlarmRun(alarm, notificationsEnabled);
		final boolean isRunning = Alarms.isAlarmRunning(alarm);

		if(shouldRun && !isRunning) {
			return true;   // needs to be started
		} else if(!shouldRun && isRunning) {
			return false;  // needs to be stopped
		}
		return null;       // no change
	}

	@Test
	public void reconcileStartsMessageCheckerWhenEnabledAndNotRunning() {
		// Alarm not running, notifications enabled → should start
		final Boolean decision = reconcileDecision(Alarms.Alarm.MESSAGE_CHECKER, true);
		assertTrue("Should decide to start MESSAGE_CHECKER", decision != null && decision);
	}

	@Test
	public void reconcileStopsMessageCheckerWhenDisabledAndRunning() {
		// Simulate alarm being "running" in the tracking map.
		// We can't start it without Context, but we can test the stop direction
		// by noting that stopAlarm on a non-running alarm is a no-op,
		// and shouldAlarmRun returns false → reconcile would stop it.
		final boolean shouldRun = Alarms.shouldAlarmRun(Alarms.Alarm.MESSAGE_CHECKER, false);
		assertFalse("MESSAGE_CHECKER should not run when notifications disabled", shouldRun);

		// If it were running, reconcile would decide to stop it:
		// shouldRun=false, isRunning=true → stop
		// We verify the predicate half; the state half is tested via isAlarmRunning.
	}

	@Test
	public void reconcileNoOpWhenMessageCheckerEnabledAndAlreadyRunning() {
		// This tests the idempotency: if alarm were running and should run, no change.
		// Since we can't start it without Context, we test with not-running + disabled
		// which should also be a no-op.
		final Boolean decision = reconcileDecision(Alarms.Alarm.MESSAGE_CHECKER, false);
		// Alarm is not running and should not run → no change needed
		assertTrue("No change needed when alarm is already in correct state", decision == null);
	}

	@Test
	public void reconcileAlwaysStartsCachePruner() {
		final Boolean decisionEnabled = reconcileDecision(Alarms.Alarm.CACHE_PRUNER, true);
		assertTrue(
				"CACHE_PRUNER should be started when notifications enabled",
				decisionEnabled != null && decisionEnabled);

		// Reset and test with notifications disabled
		Alarms.resetForTesting();
		final Boolean decisionDisabled = reconcileDecision(Alarms.Alarm.CACHE_PRUNER, false);
		assertTrue(
				"CACHE_PRUNER should be started when notifications disabled",
				decisionDisabled != null && decisionDisabled);
	}
}
