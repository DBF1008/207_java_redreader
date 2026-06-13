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

package org.quantumbadger.redreader.test.common;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.quantumbadger.redreader.common.Alarms;

/**
 * Regression tests for the alarm scheduling decision, which is the heart of keeping the
 * preferences the single source of truth for background side effects.
 *
 * <p>Only the pure decision ({@link Alarms#shouldBeScheduled}) is exercised here; actually
 * (de)registering alarms requires Android's {@code AlarmManager} and is covered by the runtime
 * reconcile path rather than these JVM unit tests.
 */
public class AlarmsTest {

	@Test
	public void messageCheckerScheduledOnlyWhenNotificationsEnabled() {

		// The original bug: the message-checker alarm kept running after notifications were
		// disabled. It must now follow the notification preference exactly.

		assertTrue(Alarms.shouldBeScheduled(
				Alarms.Alarm.MESSAGE_CHECKER,
				new Alarms.AlarmPrefs(true)));

		assertFalse(Alarms.shouldBeScheduled(
				Alarms.Alarm.MESSAGE_CHECKER,
				new Alarms.AlarmPrefs(false)));
	}

	@Test
	public void cachePrunerAlwaysScheduled() {

		// The cache pruner is not tied to any preference, so it should always be scheduled
		// regardless of the notification preference.

		assertTrue(Alarms.shouldBeScheduled(
				Alarms.Alarm.CACHE_PRUNER,
				new Alarms.AlarmPrefs(true)));

		assertTrue(Alarms.shouldBeScheduled(
				Alarms.Alarm.CACHE_PRUNER,
				new Alarms.AlarmPrefs(false)));
	}
}
