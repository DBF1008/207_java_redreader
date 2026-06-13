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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quantumbadger.redreader.account.RedditAccount;
import org.quantumbadger.redreader.cache.CacheManager;
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

/**
 * Regression tests for {@link CacheRequest#cancel()}.
 *
 * Cancelling a request must stop every callback from reaching the (now invalidated) UI.
 * Previously {@code cancel()} only set the flag and aborted the download, but the
 * {@code notify*} dispatch methods ignored it, so stale callbacks - including the
 * CANCELLED failure raised by {@code CacheDownload.cancel()} itself - could still append
 * posts, show cache banners, insert error views or overwrite fresh page state.
 */
@RunWith(RobolectricTestRunner.class)
public class CacheRequestCancellationTest {

	private static final class RecordingCallbacks implements CacheRequestCallbacks {

		int onDownloadNecessary;
		int onDownloadStarted;
		int onDataStreamAvailable;
		int onDataStreamComplete;
		int onProgress;
		int onFailure;
		int onCacheFileWritten;

		int total() {
			return onDownloadNecessary
					+ onDownloadStarted
					+ onDataStreamAvailable
					+ onDataStreamComplete
					+ onProgress
					+ onFailure
					+ onCacheFileWritten;
		}

		@Override
		public void onDownloadNecessary() {
			onDownloadNecessary++;
		}

		@Override
		public void onDownloadStarted() {
			onDownloadStarted++;
		}

		@Override
		public void onDataStreamAvailable(
				final GenericFactory<SeekableInputStream, IOException> streamFactory,
				final TimestampUTC timestamp,
				final UUID session,
				final boolean fromCache,
				final String mimetype) {
			onDataStreamAvailable++;
		}

		@Override
		public void onDataStreamComplete(
				final GenericFactory<SeekableInputStream, IOException> streamFactory,
				final TimestampUTC timestamp,
				final UUID session,
				final boolean fromCache,
				final String mimetype) {
			onDataStreamComplete++;
		}

		@Override
		public void onProgress(
				final boolean authorizationInProgress,
				final long bytesRead,
				final long totalBytes) {
			onProgress++;
		}

		@Override
		public void onFailure(final RRError error) {
			onFailure++;
		}

		@Override
		public void onCacheFileWritten(
				final CacheManager.ReadableCacheFile cacheFile,
				final TimestampUTC timestamp,
				final UUID session,
				final boolean fromCache,
				final String mimetype) {
			onCacheFileWritten++;
		}
	}

	private CacheRequest makeRequest(final CacheRequestCallbacks callbacks) {
		return new CacheRequest(
				new UriString("https://example.com/test.json"),
				new RedditAccount("", null, 0, null),
				null,
				new Priority(0),
				DownloadStrategyAlways.INSTANCE,
				Constants.FileType.POST_LIST,
				CacheRequest.DownloadQueueType.REDDIT_API,
				false,
				RuntimeEnvironment.getApplication(),
				callbacks);
	}

	private void invokeAllCallbacks(final CacheRequest request) {
		request.notifyDownloadNecessary();
		request.notifyDownloadStarted();
		request.notifyDataStreamAvailable(
				() -> null, null, UUID.randomUUID(), false, "text/plain");
		request.notifyProgress(false, 1L, 2L);
		request.notifyDataStreamComplete(
				() -> null, null, UUID.randomUUID(), false, "text/plain");
		request.notifyCacheFileWritten(
				null, null, UUID.randomUUID(), false, "text/plain");
		request.notifyFailure(new RRError(null, null, true, new RuntimeException("test")));
	}

	@Test
	public void testCallbacksDeliveredWhenNotCancelled() {

		final RecordingCallbacks callbacks = new RecordingCallbacks();
		final CacheRequest request = makeRequest(callbacks);

		invokeAllCallbacks(request);

		Assert.assertEquals(1, callbacks.onDownloadNecessary);
		Assert.assertEquals(1, callbacks.onDownloadStarted);
		Assert.assertEquals(1, callbacks.onDataStreamAvailable);
		Assert.assertEquals(1, callbacks.onProgress);
		Assert.assertEquals(1, callbacks.onDataStreamComplete);
		Assert.assertEquals(1, callbacks.onCacheFileWritten);
		Assert.assertEquals(1, callbacks.onFailure);
		Assert.assertEquals(7, callbacks.total());
	}

	@Test
	public void testCallbacksSuppressedAfterCancel() {

		final RecordingCallbacks callbacks = new RecordingCallbacks();
		final CacheRequest request = makeRequest(callbacks);

		request.cancel();

		invokeAllCallbacks(request);

		Assert.assertEquals(0, callbacks.onDownloadNecessary);
		Assert.assertEquals(0, callbacks.onDownloadStarted);
		Assert.assertEquals(0, callbacks.onDataStreamAvailable);
		Assert.assertEquals(0, callbacks.onProgress);
		Assert.assertEquals(0, callbacks.onDataStreamComplete);
		Assert.assertEquals(0, callbacks.onCacheFileWritten);
		Assert.assertEquals(0, callbacks.onFailure);
		Assert.assertEquals(0, callbacks.total());
	}
}
