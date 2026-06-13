package org.quantumbadger.redreader.reddit.prepared;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quantumbadger.redreader.account.RedditAccount;
import org.quantumbadger.redreader.common.time.TimeDuration;
import org.quantumbadger.redreader.common.time.TimestampUTC;
import org.quantumbadger.redreader.reddit.kthings.RedditIdAndType;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class RedditChangeDataManagerPruneTest {

	private RedditAccount testAccount;

	@Before
	public void setUp() {
		RedditChangeDataManager.clearAllInstancesForTesting();
		RedditChangeDataManager.removePrunePersistenceListenerForTesting();
		testAccount = new RedditAccount("testUser", null, 0, null);
	}

	@After
	public void tearDown() {
		RedditChangeDataManager.removePrunePersistenceListenerForTesting();
	}

	@Test
	public void prune_expiredEntries_areRemoved() {
		final RedditChangeDataManager manager =
				RedditChangeDataManager.getInstance(testAccount);

		final TimestampUTC old = TimestampUTC.fromUtcMs(1_000_000L);       // 1970
		final TimestampUTC recent = TimestampUTC.fromUtcMs(1_700_000_000_000L); // ~2023

		manager.markRead(old, new RedditIdAndType("t3_old"), true);
		manager.markRead(recent, new RedditIdAndType("t3_recent"), true);

		assertTrue("old entry should be read before prune",
				manager.isRead(new RedditIdAndType("t3_old")));
		assertTrue("recent entry should be read before prune",
				manager.isRead(new RedditIdAndType("t3_recent")));

		// maxAge=0 means boundary=now, so everything older than now is pruned.
		// "recent" is in the past too, so it will also be pruned.
		// To test selective pruning, use a very large maxAge so nothing is pruned,
		// then use maxAge=0 to prune everything.
		RedditChangeDataManager.pruneAllUsersWhereOlderThan(TimeDuration.ms(0));

		assertFalse("old entry should be pruned",
				manager.isRead(new RedditIdAndType("t3_old")));
		assertFalse("recent entry should also be pruned (it is in the past)",
				manager.isRead(new RedditIdAndType("t3_recent")));
	}

	@Test
	public void prune_unexpiredEntries_areKept() {
		final RedditChangeDataManager manager =
				RedditChangeDataManager.getInstance(testAccount);

		final TimestampUTC now = TimestampUTC.now();
		final TimestampUTC old = TimestampUTC.fromUtcMs(1_000_000L);

		manager.markRead(old, new RedditIdAndType("t3_old"), true);
		manager.markRead(now, new RedditIdAndType("t3_now"), true);

		// Only prune entries older than 30 days. "now" entry should survive.
		RedditChangeDataManager.pruneAllUsersWhereOlderThan(TimeDuration.days(30));

		assertFalse("old entry should be pruned",
				manager.isRead(new RedditIdAndType("t3_old")));
		assertTrue("current entry should survive 30-day prune",
				manager.isRead(new RedditIdAndType("t3_now")));
	}

	/**
	 * Regression test: the old implementation used TreeMap&lt;TimestampUTC, RedditIdAndType&gt;
	 * which treats compareTo()==0 as equality, silently dropping entries that share a
	 * timestamp. This meant MAX_ENTRY_COUNT eviction could not remove enough entries.
	 */
	@Test
	public void prune_entriesWithSameTimestamp_allPreserved() {
		final RedditChangeDataManager manager =
				RedditChangeDataManager.getInstance(testAccount);

		final TimestampUTC sameTime = TimestampUTC.fromUtcMs(5_000_000_000L);
		final int count = 15;

		for(int i = 0; i < count; i++) {
			manager.markRead(sameTime, new RedditIdAndType("t3_item_" + i), true);
		}

		// Verify all entries exist (no silent overwrite).
		for(int i = 0; i < count; i++) {
			assertTrue("entry " + i + " should exist before prune",
					manager.isRead(new RedditIdAndType("t3_item_" + i)));
		}

		// Prune with maxAge=0 → boundary=now, all entries are in the past.
		RedditChangeDataManager.pruneAllUsersWhereOlderThan(TimeDuration.ms(0));

		// All entries must be removed. With the old TreeMap bug, some would survive
		// because the TreeMap collapsed them into a single key and the eviction
		// loop would stop prematurely.
		for(int i = 0; i < count; i++) {
			assertFalse("entry " + i + " should be pruned",
					manager.isRead(new RedditIdAndType("t3_item_" + i)));
		}
	}

	/**
	 * Regression test: the old prune() never called RedditChangeDataIO.notifyUpdateStatic(),
	 * so deletions were not written to disk. On next cold start the entries would "resurrect".
	 */
	@Test
	public void prune_triggersPersistenceNotification() {
		final List<Boolean> notifications = new ArrayList<>();
		RedditChangeDataManager.setPrunePersistenceListenerForTesting(
				() -> notifications.add(true));

		final RedditChangeDataManager manager =
				RedditChangeDataManager.getInstance(testAccount);

		final TimestampUTC old = TimestampUTC.fromUtcMs(1_000_000L);
		manager.markRead(old, new RedditIdAndType("t3_old"), true);

		// Sanity: entry is present before prune.
		assertTrue(manager.isRead(new RedditIdAndType("t3_old")));

		notifications.clear();
		RedditChangeDataManager.pruneAllUsersWhereOlderThan(TimeDuration.ms(0));

		assertFalse("entry should be gone after prune",
				manager.isRead(new RedditIdAndType("t3_old")));
		assertTrue("prune must trigger persistence notification so disk is updated",
				notifications.size() > 0);
	}

	@Test
	public void prune_nothingToRemove_noNotification() {
		final List<Boolean> notifications = new ArrayList<>();
		RedditChangeDataManager.setPrunePersistenceListenerForTesting(
				() -> notifications.add(true));

		// Get the manager but don't add any entries.
		RedditChangeDataManager.getInstance(testAccount);

		notifications.clear();
		RedditChangeDataManager.pruneAllUsersWhereOlderThan(TimeDuration.ms(0));

		assertEquals("no notification expected when nothing is pruned",
				0, notifications.size());
	}

	@Test
	public void prune_mixedAges_onlyOldRemoved() {
		final RedditChangeDataManager manager =
				RedditChangeDataManager.getInstance(testAccount);

		final long DAY_MS = 86_400_000L;
		final TimestampUTC now = TimestampUTC.now();
		final TimestampUTC tenDaysAgo = TimestampUTC.fromUtcMs(now.toUtcMs() - 10 * DAY_MS);
		final TimestampUTC fiftyDaysAgo = TimestampUTC.fromUtcMs(now.toUtcMs() - 50 * DAY_MS);
		final TimestampUTC hundredDaysAgo = TimestampUTC.fromUtcMs(now.toUtcMs() - 100 * DAY_MS);

		manager.markRead(hundredDaysAgo, new RedditIdAndType("t3_100d"), true);
		manager.markRead(fiftyDaysAgo,   new RedditIdAndType("t3_50d"),  true);
		manager.markRead(tenDaysAgo,     new RedditIdAndType("t3_10d"),  true);
		manager.markRead(now,            new RedditIdAndType("t3_now"),  true);

		// Prune entries older than 30 days.
		RedditChangeDataManager.pruneAllUsersWhereOlderThan(TimeDuration.days(30));

		assertFalse("100-day-old entry should be pruned",
				manager.isRead(new RedditIdAndType("t3_100d")));
		assertFalse("50-day-old entry should be pruned",
				manager.isRead(new RedditIdAndType("t3_50d")));
		assertTrue("10-day-old entry should survive",
				manager.isRead(new RedditIdAndType("t3_10d")));
		assertTrue("current entry should survive",
				manager.isRead(new RedditIdAndType("t3_now")));
	}
}
