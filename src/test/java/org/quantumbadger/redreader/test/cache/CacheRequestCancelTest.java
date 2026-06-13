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

package org.quantumbadger.redreader.test.cache;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quantumbadger.redreader.account.RedditAccount;
import org.quantumbadger.redreader.cache.CacheRequest;
import org.quantumbadger.redreader.cache.CacheRequestCallbacks;
import org.quantumbadger.redreader.cache.downloadstrategy.DownloadStrategyAlways;
import org.quantumbadger.redreader.common.Constants;
import org.quantumbadger.redreader.common.GenericFactory;
import org.quantumbadger.redreader.common.Priority;
import org.quantumbadger.redreader.common.RRError;
import org.quantumbadger.redreader.common.UriString;
import org.quantumbadger.redreader.common.datastream.SeekableInputStream;
import org.quantumbadger.redreader.common.time.TimestampUTC;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class CacheRequestCancelTest {

	private Context context;
	private CountingCallbacks callbacks;
	private CacheRequest request;

	@Before
	public void setUp() {
		context = RuntimeEnvironment.getApplication();
		callbacks = new CountingCallbacks();
		request = new CacheRequest(
				new UriString("https://example.com/test"),
				new RedditAccount("", null, 0, null),
				null,
				new Priority(1),
				DownloadStrategyAlways.INSTANCE,
				Constants.FileType.POST_LIST,
				CacheRequest.DownloadQueueType.IMMEDIATE,
				false,
				context,
				callbacks);
	}

	@Test
	public void testIsCancelledInitiallyFalse() {
		assertFalse(request.isCancelled());
	}

	@Test
	public void testIsCancelledAfterCancel() {
		request.cancel();
		assertTrue(request.isCancelled());
	}

	@Test
	public void testNotifyDataStreamAvailable_suppressedAfterCancel() {
		request.cancel();

		final GenericFactory<SeekableInputStream, IOException> factory = unusedFactory();
		request.notifyDataStreamAvailable(
				factory, TimestampUTC.now(), UUID.randomUUID(), false, "text/plain");

		assertEquals(0, callbacks.onDataStreamAvailableCount.get());
	}

	@Test
	public void testNotifyDataStreamAvailable_worksBeforeCancel() {
		final GenericFactory<SeekableInputStream, IOException> factory = unusedFactory();
		request.notifyDataStreamAvailable(
				factory, TimestampUTC.now(), UUID.randomUUID(), false, "text/plain");

		assertEquals(1, callbacks.onDataStreamAvailableCount.get());
	}

	@Test
	public void testNotifyDataStreamComplete_suppressedAfterCancel() {
		request.cancel();

		final GenericFactory<SeekableInputStream, IOException> factory = unusedFactory();
		request.notifyDataStreamComplete(
				factory, TimestampUTC.now(), UUID.randomUUID(), false, "text/plain");

		assertEquals(0, callbacks.onDataStreamCompleteCount.get());
	}

	@Test
	public void testNotifyDataStreamComplete_worksBeforeCancel() {
		final GenericFactory<SeekableInputStream, IOException> factory = unusedFactory();
		request.notifyDataStreamComplete(
				factory, TimestampUTC.now(), UUID.randomUUID(), false, "text/plain");

		assertEquals(1, callbacks.onDataStreamCompleteCount.get());
	}

	@Test
	public void testNotifyFailure_suppressedAfterCancel() {
		request.cancel();

		request.notifyFailure(new RRError("test", "test"));

		assertEquals(0, callbacks.onFailureCount.get());
	}

	@Test
	public void testNotifyFailure_worksBeforeCancel() {
		request.notifyFailure(new RRError("test", "test"));

		assertEquals(1, callbacks.onFailureCount.get());
	}

	@Test
	public void testNotifyProgress_suppressedAfterCancel() {
		request.cancel();

		request.notifyProgress(false, 100, 200);

		assertEquals(0, callbacks.onProgressCount.get());
	}

	@Test
	public void testNotifyProgress_worksBeforeCancel() {
		request.notifyProgress(false, 100, 200);

		assertEquals(1, callbacks.onProgressCount.get());
	}

	@Test
	public void testNotifyCacheFileWritten_suppressedAfterCancel() {
		request.cancel();

		request.notifyCacheFileWritten(
				null, TimestampUTC.now(), UUID.randomUUID(), false, "text/plain");

		assertEquals(0, callbacks.onCacheFileWrittenCount.get());
	}

	@Test
	public void testNotifyCacheFileWritten_worksBeforeCancel() {
		request.notifyCacheFileWritten(
				null, TimestampUTC.now(), UUID.randomUUID(), false, "text/plain");

		assertEquals(1, callbacks.onCacheFileWrittenCount.get());
	}

	@Test
	public void testNotifyDownloadNecessary_suppressedAfterCancel() {
		request.cancel();

		request.notifyDownloadNecessary();

		assertEquals(0, callbacks.onDownloadNecessaryCount.get());
	}

	@Test
	public void testNotifyDownloadNecessary_worksBeforeCancel() {
		request.notifyDownloadNecessary();

		assertEquals(1, callbacks.onDownloadNecessaryCount.get());
	}

	@Test
	public void testNotifyDownloadStarted_suppressedAfterCancel() {
		request.cancel();

		request.notifyDownloadStarted();

		assertEquals(0, callbacks.onDownloadStartedCount.get());
	}

	@Test
	public void testNotifyDownloadStarted_worksBeforeCancel() {
		request.notifyDownloadStarted();

		assertEquals(1, callbacks.onDownloadStartedCount.get());
	}

	@Test
	public void testMultipleCancelIsIdempotent() {
		request.cancel();
		request.cancel();
		request.cancel();

		assertTrue(request.isCancelled());

		// None of these should reach the callbacks
		request.notifyFailure(new RRError("test", "test"));
		request.notifyProgress(false, 0, 0);
		request.notifyDownloadNecessary();
		request.notifyDownloadStarted();

		assertEquals(0, callbacks.onFailureCount.get());
		assertEquals(0, callbacks.onProgressCount.get());
		assertEquals(0, callbacks.onDownloadNecessaryCount.get());
		assertEquals(0, callbacks.onDownloadStartedCount.get());
	}

	@Test
	public void testCancelPreventsSubsequentCallbacks() {
		// First, some callbacks should work
		request.notifyProgress(false, 50, 200);
		assertEquals(1, callbacks.onProgressCount.get());

		// Now cancel
		request.cancel();

		// All subsequent callbacks should be suppressed
		request.notifyProgress(false, 100, 200);
		request.notifyDataStreamComplete(
				unusedFactory(), TimestampUTC.now(), UUID.randomUUID(), false, null);
		request.notifyFailure(new RRError("late error", null));

		assertEquals(1, callbacks.onProgressCount.get());
		assertEquals(0, callbacks.onDataStreamCompleteCount.get());
		assertEquals(0, callbacks.onFailureCount.get());
	}

	/**
	 * Returns a stream factory whose create() method is never expected to be called
	 * during these tests (callbacks only count invocations, never open streams).
	 */
	private static GenericFactory<SeekableInputStream, IOException> unusedFactory() {
		return () -> {
			throw new IOException("unexpected call to create() in test");
		};
	}

	/**
	 * Simple counting implementation of CacheRequestCallbacks that tracks
	 * how many times each callback method was invoked.
	 */
	private static class CountingCallbacks implements CacheRequestCallbacks {

		final AtomicInteger onDataStreamAvailableCount = new AtomicInteger(0);
		final AtomicInteger onDataStreamCompleteCount = new AtomicInteger(0);
		final AtomicInteger onFailureCount = new AtomicInteger(0);
		final AtomicInteger onProgressCount = new AtomicInteger(0);
		final AtomicInteger onCacheFileWrittenCount = new AtomicInteger(0);
		final AtomicInteger onDownloadNecessaryCount = new AtomicInteger(0);
		final AtomicInteger onDownloadStartedCount = new AtomicInteger(0);

		@Override
		public void onDownloadNecessary() {
			onDownloadNecessaryCount.incrementAndGet();
		}

		@Override
		public void onDownloadStarted() {
			onDownloadStartedCount.incrementAndGet();
		}

		@Override
		public void onDataStreamAvailable(
				@NonNull final GenericFactory<SeekableInputStream, IOException> streamFactory,
				final TimestampUTC timestamp,
				@NonNull final UUID session,
				final boolean fromCache,
				@Nullable final String mimetype) {
			onDataStreamAvailableCount.incrementAndGet();
		}

		@Override
		public void onDataStreamComplete(
				@NonNull final GenericFactory<SeekableInputStream, IOException> streamFactory,
				final TimestampUTC timestamp,
				@NonNull final UUID session,
				final boolean fromCache,
				@Nullable final String mimetype) {
			onDataStreamCompleteCount.incrementAndGet();
		}

		@Override
		public void onProgress(
				final boolean authorizationInProgress,
				final long bytesRead,
				final long totalBytes) {
			onProgressCount.incrementAndGet();
		}

		@Override
		public void onFailure(@NonNull final RRError error) {
			onFailureCount.incrementAndGet();
		}

		@Override
		public void onCacheFileWritten(
				@NonNull final org.quantumbadger.redreader.cache.CacheManager.ReadableCacheFile cacheFile,
				final TimestampUTC timestamp,
				@NonNull final UUID session,
				final boolean fromCache,
				@Nullable final String mimetype) {
			onCacheFileWrittenCount.incrementAndGet();
		}
	}
}
