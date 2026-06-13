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

package org.quantumbadger.redreader.test.receivers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.quantumbadger.redreader.common.General;
import org.quantumbadger.redreader.common.SharedPrefsWrapper;
import org.quantumbadger.redreader.receivers.NewMessageChecker;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.UUID;

@RunWith(RobolectricTestRunner.class)
public class NewMessageCheckerTest {

	// Pure dedup decision: must match the historical semantics exactly.
	@Test
	public void testIsNewMessageLogic() {

		// First ever check for an account (empty saved id, zero timestamp) -> notify.
		assertTrue(NewMessageChecker.isNewMessage("", 0L, "t1_a", 100L));

		// A missing (null) saved id is also treated as "new".
		assertTrue(NewMessageChecker.isNewMessage(null, 0L, "t1_a", 100L));

		// The exact same message as last time -> already seen, no notification.
		assertFalse(NewMessageChecker.isNewMessage("t1_a", 100L, "t1_a", 100L));

		// A different message that is older than the last notified one -> suppressed.
		assertFalse(NewMessageChecker.isNewMessage("t1_a", 100L, "t1_b", 50L));

		// A different, newer message -> notify.
		assertTrue(NewMessageChecker.isNewMessage("t1_a", 100L, "t1_b", 150L));

		// A different message with the same timestamp -> notify (the boundary is inclusive).
		assertTrue(NewMessageChecker.isNewMessage("t1_a", 100L, "t1_b", 100L));
	}

	@Test
	public void testPerAccountKeysAreDistinct() {

		final String aliceIdKey = NewMessageChecker.getSavedMessageIdPrefKey("alice");
		final String bobIdKey = NewMessageChecker.getSavedMessageIdPrefKey("bob");

		// Different accounts must map to different storage keys.
		assertNotEquals(aliceIdKey, bobIdKey);
		assertTrue(aliceIdKey.contains("alice"));

		// The id key and timestamp key for the same account must not collide.
		final String aliceTimestampKey
				= NewMessageChecker.getSavedMessageTimestampPrefKey("alice");
		assertNotEquals(aliceIdKey, aliceTimestampKey);
	}

	// Regression test for the multi-account notification cross-contamination bug: each account's
	// "last notified message" state must be tracked independently, so switching between accounts
	// can neither suppress a genuinely-new message nor cause a duplicate notification.
	@Test
	public void testStateIsIsolatedPerAccount() {

		final Context context = RuntimeEnvironment.getApplication();
		final SharedPrefsWrapper prefs = General.getSharedPrefs(context);

		// Unique usernames so the test starts from a clean slate regardless of any other state.
		final String suffix = UUID.randomUUID().toString();
		final String alice = "alice_" + suffix;
		final String bob = "bob_" + suffix;

		// Alice has a new unread message -> notify, and remember it.
		assertTrue(NewMessageChecker.checkAndUpdateLastMessage(
				prefs, alice, "t1_alice1", 1000L));

		// The same message again -> already seen, no notification.
		assertFalse(NewMessageChecker.checkAndUpdateLastMessage(
				prefs, alice, "t1_alice1", 1000L));

		// Bob's first unread message has an OLDER timestamp than Alice's last notified message.
		// Under the previous global-state model this would be wrongly suppressed (500 <= 1000).
		// With per-account state Bob is compared against his own (empty) state, so it must notify.
		assertTrue(NewMessageChecker.checkAndUpdateLastMessage(
				prefs, bob, "t1_bob1", 500L));

		// Persisting Bob's state must not disturb Alice: her already-seen message is still
		// deduplicated rather than re-notified.
		assertFalse(NewMessageChecker.checkAndUpdateLastMessage(
				prefs, alice, "t1_alice1", 1000L));

		// Each account still notifies independently for its own genuinely-new messages.
		assertTrue(NewMessageChecker.checkAndUpdateLastMessage(
				prefs, bob, "t1_bob2", 600L));
		assertTrue(NewMessageChecker.checkAndUpdateLastMessage(
				prefs, alice, "t1_alice2", 1100L));
	}
}
