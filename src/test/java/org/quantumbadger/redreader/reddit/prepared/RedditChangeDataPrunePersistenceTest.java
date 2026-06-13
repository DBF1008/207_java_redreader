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

package org.quantumbadger.redreader.reddit.prepared;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quantumbadger.redreader.account.RedditAccount;
import org.quantumbadger.redreader.common.time.TimeDuration;
import org.quantumbadger.redreader.common.time.TimestampUTC;
import org.quantumbadger.redreader.io.RedditChangeDataIO;
import org.quantumbadger.redreader.reddit.kthings.RedditIdAndType;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Regression tests for the persistence side of pruning.
 *
 * <p>Previously {@code RedditChangeDataManager.prune(...)} removed entries from memory but never
 * asked {@link RedditChangeDataIO} to persist the result. The stale entries therefore remained
 * in the data file and "revived" on the next cold start. These tests exercise the real prune
 * path through the public API and assert that a write is requested exactly when entries change.
 *
 * <p>Robolectric is required because the public mutators post to the UI thread handler.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RedditChangeDataPrunePersistenceTest {

	private static RedditAccount newUniqueAccount(final String label) {
		// A unique username keeps each test isolated within the manager's static instance map.
		return new RedditAccount(
				"prunetest_" + label + "_" + System.nanoTime(),
				null,
				0,
				null);
	}

	@Before
	public void clearPendingWriteFlag() {
		RedditChangeDataIO.consumeStaticUpdatePending();
	}

	@Test
	public void testPruneRemovesOldEntriesAndRequestsWrite() {

		final RedditAccount account = newUniqueAccount("removes");
		final RedditChangeDataManager manager = RedditChangeDataManager.getInstance(account);

		final RedditIdAndType oldThing = new RedditIdAndType("t3_old");
		final RedditIdAndType freshThing = new RedditIdAndType("t3_fresh");

		manager.markUpvoted(TimestampUTC.fromUtcMs(1000), oldThing);
		manager.markUpvoted(TimestampUTC.now(), freshThing);

		// Discard the write requests triggered by populating the data above.
		RedditChangeDataIO.consumeStaticUpdatePending();

		RedditChangeDataManager.pruneAllUsersWhereOlderThan(TimeDuration.days(1));

		Assert.assertFalse(
				"Entry older than the boundary should be pruned from memory",
				manager.isUpvoted(oldThing));
		Assert.assertTrue(
				"Recent entry should be kept",
				manager.isUpvoted(freshThing));
		Assert.assertTrue(
				"Pruning that removes entries must request a persistence write",
				RedditChangeDataIO.consumeStaticUpdatePending());
	}

	@Test
	public void testPruneWithoutChangesDoesNotRequestWrite() {

		final RedditAccount account = newUniqueAccount("noop");
		final RedditChangeDataManager manager = RedditChangeDataManager.getInstance(account);

		final RedditIdAndType freshThing = new RedditIdAndType("t3_keep");
		manager.markUpvoted(TimestampUTC.now(), freshThing);

		RedditChangeDataIO.consumeStaticUpdatePending();

		// A huge max age places the boundary far in the past, so nothing is old enough to
		// prune. The count cap cannot be reached in a test, so no entry is removed for any
		// account, and therefore no write should be requested.
		RedditChangeDataManager.pruneAllUsersWhereOlderThan(TimeDuration.days(3_650_000));

		Assert.assertTrue(
				"Recent entry should be untouched",
				manager.isUpvoted(freshThing));
		Assert.assertFalse(
				"Pruning that removes nothing must not request a persistence write",
				RedditChangeDataIO.consumeStaticUpdatePending());
	}
}
