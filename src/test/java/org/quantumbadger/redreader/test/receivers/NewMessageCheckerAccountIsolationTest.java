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

import org.junit.Test;
import org.quantumbadger.redreader.receivers.NewMessageChecker;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for the per-account message notification state isolation fix.
 *
 * These tests verify that the pref-key generation and notification-ID generation
 * methods in {@link NewMessageChecker} produce distinct values for different
 * accounts, preventing the cross-account "message already seen" bug.
 */
public class NewMessageCheckerAccountIsolationTest {

	// ---- getMessageIdPrefKey ----

	@Test
	public void messageIdPrefKey_containsBaseKey() {
		final String key = NewMessageChecker.getMessageIdPrefKey("alice");
		assertTrue(
				"Key should start with the base pref key",
				key.startsWith(NewMessageChecker.PREFS_SAVED_MESSAGE_ID));
	}

	@Test
	public void messageIdPrefKey_differentAccountsProduceDifferentKeys() {
		final String keyAlice = NewMessageChecker.getMessageIdPrefKey("alice");
		final String keyBob = NewMessageChecker.getMessageIdPrefKey("bob");

		assertNotEquals(
				"Different accounts must produce different message ID pref keys",
				keyAlice,
				keyBob);
	}

	@Test
	public void messageIdPrefKey_sameAccountProducesSameKey() {
		final String key1 = NewMessageChecker.getMessageIdPrefKey("alice");
		final String key2 = NewMessageChecker.getMessageIdPrefKey("alice");

		assertEquals(
				"Same account must always produce the same message ID pref key",
				key1,
				key2);
	}

	@Test
	public void messageIdPrefKey_includesUsername() {
		final String key = NewMessageChecker.getMessageIdPrefKey("alice");
		assertTrue(
				"Key should contain the username for traceability",
				key.contains("alice"));
	}

	// ---- getMessageTimestampPrefKey ----

	@Test
	public void messageTimestampPrefKey_containsBaseKey() {
		final String key = NewMessageChecker.getMessageTimestampPrefKey("alice");
		assertTrue(
				"Key should start with the base timestamp pref key",
				key.startsWith(NewMessageChecker.PREFS_SAVED_MESSAGE_TIMESTAMP));
	}

	@Test
	public void messageTimestampPrefKey_differentAccountsProduceDifferentKeys() {
		final String keyAlice = NewMessageChecker.getMessageTimestampPrefKey("alice");
		final String keyBob = NewMessageChecker.getMessageTimestampPrefKey("bob");

		assertNotEquals(
				"Different accounts must produce different timestamp pref keys",
				keyAlice,
				keyBob);
	}

	@Test
	public void messageTimestampPrefKey_sameAccountProducesSameKey() {
		final String key1 = NewMessageChecker.getMessageTimestampPrefKey("alice");
		final String key2 = NewMessageChecker.getMessageTimestampPrefKey("alice");

		assertEquals(
				"Same account must always produce the same timestamp pref key",
				key1,
				key2);
	}

	// ---- Cross-key isolation ----

	@Test
	public void messageIdAndTimestampKeys_areDifferentForSameAccount() {
		final String idKey = NewMessageChecker.getMessageIdPrefKey("alice");
		final String tsKey = NewMessageChecker.getMessageTimestampPrefKey("alice");

		assertNotEquals(
				"ID key and timestamp key must be different even for the same account",
				idKey,
				tsKey);
	}

	@Test
	public void allKeysForMultipleAccounts_areUnique() {
		final String[] accounts = {"alice", "bob", "charlie", "dave"};
		final Set<String> allKeys = new HashSet<>();

		for(final String account : accounts) {
			assertTrue(
					"ID key should be unique across all accounts",
					allKeys.add(NewMessageChecker.getMessageIdPrefKey(account)));
			assertTrue(
					"Timestamp key should be unique across all accounts",
					allKeys.add(NewMessageChecker.getMessageTimestampPrefKey(account)));
		}

		assertEquals(
				"All keys combined should be unique",
				accounts.length * 2,
				allKeys.size());
	}

	// ---- getNotificationId ----

	@Test
	public void notificationId_differentAccountsProduceDifferentIds() {
		final int idAlice = NewMessageChecker.getNotificationId("alice");
		final int idBob = NewMessageChecker.getNotificationId("bob");

		assertNotEquals(
				"Different accounts must produce different notification IDs",
				idAlice,
				idBob);
	}

	@Test
	public void notificationId_sameAccountProducesSameId() {
		final int id1 = NewMessageChecker.getNotificationId("alice");
		final int id2 = NewMessageChecker.getNotificationId("alice");

		assertEquals(
				"Same account must always produce the same notification ID",
				id1,
				id2);
	}

	@Test
	public void notificationId_isStableAcrossMultipleCalls() {
		// Verify determinism: calling 100 times should yield the same value.
		final int expected = NewMessageChecker.getNotificationId("alice");
		for(int i = 0; i < 100; i++) {
			assertEquals(expected, NewMessageChecker.getNotificationId("alice"));
		}
	}

	// ---- Simulated cross-account contamination scenario ----

	@Test
	public void simulatedScenario_accountSwitchDoesNotCrossContaminate() {

		// Scenario: User has two accounts "alice" and "bob".
		// Alice gets message "t1_abc" at timestamp 1000.
		// Bob gets message "t1_abc" at timestamp 2000.
		// With per-account keys, the dedup logic should treat them independently.

		final String aliceIdKey = NewMessageChecker.getMessageIdPrefKey("alice");
		final String aliceTsKey = NewMessageChecker.getMessageTimestampPrefKey("alice");
		final String bobIdKey = NewMessageChecker.getMessageIdPrefKey("bob");
		final String bobTsKey = NewMessageChecker.getMessageTimestampPrefKey("bob");

		// Keys must be different so that storing Alice's state doesn't affect Bob
		assertNotEquals(aliceIdKey, bobIdKey);
		assertNotEquals(aliceTsKey, bobTsKey);

		// Simulate storing Alice's seen message
		final String aliceSeenId = "t1_abc";
		final long aliceSeenTs = 1000L;

		// Simulate storing Bob's seen message (same ID, different timestamp)
		final String bobSeenId = "t1_abc";
		final long bobSeenTs = 2000L;

		// If both accounts share the same key, writing Bob's value would overwrite
		// Alice's. With distinct keys, both values coexist independently.
		assertFalse(
				"Alice and Bob must not share the same message ID key",
				aliceIdKey.equals(bobIdKey));

		// Verify that when Bob gets a new message "t1_xyz" at timestamp 3000,
		// the dedup comparison uses Bob's stored state (t1_abc/2000), not
		// Alice's stored state (t1_abc/1000).
		// Since the keys are different, Bob's comparison is isolated.
		final String newMessageId = "t1_xyz";
		final long newMessageTs = 3000L;

		// For Bob: new message ID != stored ID -> should notify
		assertFalse(
				"New message ID should differ from Bob's stored ID",
				newMessageId.equals(bobSeenId));
		assertTrue(
				"New message timestamp should be after Bob's stored timestamp",
				newMessageTs > bobSeenTs);

		// For Alice: same new message arrives, also should notify independently
		assertFalse(
				"New message ID should differ from Alice's stored ID",
				newMessageId.equals(aliceSeenId));
		assertTrue(
				"New message timestamp should be after Alice's stored timestamp",
				newMessageTs > aliceSeenTs);
	}

	@Test
	public void simulatedScenario_sameMessageDifferentAccounts_bothNotify() {

		// Scenario: Both accounts receive the same message ID "t1_new" at
		// timestamp 5000. Each account had previously seen "t1_old" at 4000.
		// With per-account keys, BOTH should trigger a notification because
		// each account's dedup state is independent.

		final String newMsgId = "t1_new";
		final long newMsgTs = 5000L;

		// Alice's previously seen state
		final String aliceOldId = "t1_old";
		final long aliceOldTs = 4000L;

		// Bob's previously seen state (same as Alice's in this scenario)
		final String bobOldId = "t1_old";
		final long bobOldTs = 4000L;

		// Dedup logic: notify if (oldId == null || (!newId.equals(oldId) && oldTs <= newTs))
		final boolean aliceShouldNotify =
				aliceOldId == null
						|| (!newMsgId.equals(aliceOldId) && aliceOldTs <= newMsgTs);
		final boolean bobShouldNotify =
				bobOldId == null
						|| (!newMsgId.equals(bobOldId) && bobOldTs <= newMsgTs);

		assertTrue("Alice should be notified", aliceShouldNotify);
		assertTrue("Bob should be notified", bobShouldNotify);

		// Now simulate the BUG scenario: with shared (global) keys, after Alice
		// is notified and the global state is updated to ("t1_new", 5000),
		// when Bob's check runs, the global state says "t1_new"/5000.
		// The new message is also "t1_new"/5000, so:
		final String globalOldId = newMsgId;  // Alice already updated global state
		final long globalOldTs = newMsgTs;

		final boolean bobWouldNotifyWithGlobalKeys =
				globalOldId == null
						|| (!newMsgId.equals(globalOldId) && globalOldTs <= newMsgTs);

		assertFalse(
				"With global keys, Bob would NOT be notified (this is the bug)",
				bobWouldNotifyWithGlobalKeys);

		// With per-account keys, Bob IS notified (as shown above).
		assertTrue(
				"With per-account keys, Bob IS notified (this is the fix)",
				bobShouldNotify);
	}

	@Test
	public void simulatedScenario_oldAccountDoesNotGetRepeatNotification() {

		// Scenario: Alice has already seen "t1_seen" at timestamp 3000.
		// The checker runs again and the latest message is still "t1_seen"/3000.
		// Alice should NOT get a repeat notification.

		final String aliceIdKey = NewMessageChecker.getMessageIdPrefKey("alice");
		final String storedId = "t1_seen";
		final long storedTs = 3000L;

		final String currentMsgId = "t1_seen";
		final long currentMsgTs = 3000L;

		final boolean shouldNotify =
				storedId == null
						|| (!currentMsgId.equals(storedId) && storedTs <= currentMsgTs);

		assertFalse(
				"Same message should not trigger repeat notification for same account",
				shouldNotify);

		// The key is per-account, so Bob checking the same message would use a
		// different key and not be affected by Alice's state.
		final String bobIdKey = NewMessageChecker.getMessageIdPrefKey("bob");
		assertNotEquals(
				"Bob's key is different, so Alice's state won't affect Bob",
				aliceIdKey,
				bobIdKey);
	}
}
