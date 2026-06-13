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
import org.junit.Test;
import org.quantumbadger.redreader.common.time.TimestampUTC;
import org.quantumbadger.redreader.reddit.kthings.RedditIdAndType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Unit tests for the pure pruning-selection logic used by {@code RedditChangeDataManager}.
 *
 * <p>These pin down the two failure modes that previously caused pruning to misbehave:
 * <ul>
 *     <li>entries that share an identical timestamp must each be evicted individually when the
 *     count limit is exceeded (previously they collapsed into a single timestamp-keyed map
 *     entry, so the count cap could not remove enough of the oldest items);</li>
 *     <li>entries strictly older than the boundary are removed, while entries exactly on the
 *     boundary are kept.</li>
 * </ul>
 */
public class RedditChangeDataPruneSelectionTest {

	private static final int NO_COUNT_LIMIT = Integer.MAX_VALUE;

	private static RedditIdAndType id(final String value) {
		return new RedditIdAndType(value);
	}

	private static TimestampUTC ts(final long utcMs) {
		return TimestampUTC.fromUtcMs(utcMs);
	}

	private static Set<RedditIdAndType> setOf(final RedditIdAndType... ids) {
		return new HashSet<>(Arrays.asList(ids));
	}

	@Test
	public void testEmptyMapReturnsEmpty() {

		final Set<RedditIdAndType> result = RedditChangeDataManager.selectEntriesToPrune(
				new HashMap<>(), ts(1000), NO_COUNT_LIMIT);

		Assert.assertTrue(result.isEmpty());
	}

	@Test
	public void testRemovesOnlyEntriesStrictlyOlderThanBoundary() {

		final HashMap<RedditIdAndType, TimestampUTC> entries = new HashMap<>();
		entries.put(id("old"), ts(1_000));
		entries.put(id("onBoundary"), ts(5_000));
		entries.put(id("new"), ts(9_000));

		final Set<RedditIdAndType> result = RedditChangeDataManager.selectEntriesToPrune(
				entries, ts(5_000), NO_COUNT_LIMIT);

		Assert.assertEquals(setOf(id("old")), result);
	}

	@Test
	public void testNoRemovalWhenWithinLimits() {

		final HashMap<RedditIdAndType, TimestampUTC> entries = new HashMap<>();
		entries.put(id("a"), ts(10_000));
		entries.put(id("b"), ts(11_000));
		entries.put(id("c"), ts(12_000));

		final Set<RedditIdAndType> result = RedditChangeDataManager.selectEntriesToPrune(
				entries, ts(5_000), 100);

		Assert.assertTrue(result.isEmpty());
	}

	@Test
	public void testEvictsOldestWhenOverCountLimitWithDuplicateTimestamps() {

		final HashMap<RedditIdAndType, TimestampUTC> entries = new HashMap<>();
		for(int i = 0; i < 5; i++) {
			// Every entry shares the exact same timestamp.
			entries.put(id("id" + i), ts(10_000));
		}

		// Boundary in the distant past, so nothing is removed by age; only the count cap
		// applies. This is the regression case: the count cap must still remove (5 - 2) = 3
		// entries even though all timestamps are identical.
		final Set<RedditIdAndType> result = RedditChangeDataManager.selectEntriesToPrune(
				entries, ts(0), 2);

		Assert.assertEquals(3, result.size());

		// Tie-break is deterministic (oldest first, then id ascending), so the lowest ids go.
		Assert.assertEquals(setOf(id("id0"), id("id1"), id("id2")), result);
	}

	@Test
	public void testCombinedAgePruningAndCountEviction() {

		final HashMap<RedditIdAndType, TimestampUTC> entries = new HashMap<>();
		entries.put(id("old1"), ts(1_000));
		entries.put(id("old2"), ts(2_000));
		entries.put(id("n0"), ts(10_000));
		entries.put(id("n1"), ts(11_000));
		entries.put(id("n2"), ts(12_000));
		entries.put(id("n3"), ts(13_000));

		final Set<RedditIdAndType> result = RedditChangeDataManager.selectEntriesToPrune(
				entries, ts(5_000), 2);

		// old1/old2 are removed by age. Of the four survivors, the oldest two (n0, n1) are
		// then evicted to bring the total down to the count limit of 2.
		Assert.assertEquals(setOf(id("old1"), id("old2"), id("n0"), id("n1")), result);
	}
}
