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
package org.quantumbadger.redreader.test.reddit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.quantumbadger.redreader.account.RedditAccount
import org.quantumbadger.redreader.common.time.TimestampUTC
import org.quantumbadger.redreader.reddit.RedditAPI
import org.quantumbadger.redreader.reddit.api.RedditAPICommentAction
import org.quantumbadger.redreader.reddit.api.RedditPostActions
import org.quantumbadger.redreader.reddit.kthings.RedditIdAndType
import org.quantumbadger.redreader.reddit.prepared.RedditChangeDataManager
import org.robolectric.RobolectricTestRunner

/**
 * Regression tests for the optimistic-update failure-rollback chain.
 *
 * The bug being guarded against: when a vote request failed, the rollback would also wipe the
 * item's *saved* state (in [RedditPostActions] via a stray `markSaved`, and in
 * [RedditAPICommentAction] via a missing `break` that let the vote case fall through into the
 * save case). A rollback must only restore the field(s) the failed action actually touched.
 */
@RunWith(RobolectricTestRunner::class)
class VoteFailureRollbackTest {

	private fun freshManager(name: String): RedditChangeDataManager {
		// RedditChangeDataManager instances are keyed per-account, so a unique account name
		// gives each test case its own isolated manager.
		val account = RedditAccount(name, null, 0, null)
		return RedditChangeDataManager.getInstance(account)
	}

	// ---------------------------------------------------------------------------------------
	// Post rollback (RedditPostActions.revertPostActionOnFailure)
	// ---------------------------------------------------------------------------------------

	@Test
	fun postVoteFailureDoesNotClearSavedState() {
		val mgr = freshManager("post-vote-keeps-saved")
		val id = RedditIdAndType("t3_postsaved")
		val now = TimestampUTC.now()

		mgr.markSaved(now, id, true)
		mgr.markUpvoted(now, id) // optimistic upvote applied

		// Upvote request fails; previous vote direction was "none" (0).
		RedditPostActions.revertPostActionOnFailure(mgr, id, RedditAPI.ACTION_UPVOTE, 0, now)

		assertFalse(mgr.isUpvoted(id))
		assertFalse(mgr.isDownvoted(id))
		// The save state must be untouched by a vote rollback.
		assertTrue(mgr.isSaved(id))
	}

	@Test
	fun postVoteFailureRestoresPreviousVoteDirection() {
		val mgr = freshManager("post-vote-restore-direction")
		val now = TimestampUTC.now()

		val wasDown = RedditIdAndType("t3_wasdown")
		mgr.markUpvoted(now, wasDown) // optimistic change away from the original downvote
		RedditPostActions.revertPostActionOnFailure(mgr, wasDown, RedditAPI.ACTION_UPVOTE, -1, now)
		assertTrue(mgr.isDownvoted(wasDown))
		assertFalse(mgr.isUpvoted(wasDown))

		val wasUp = RedditIdAndType("t3_wasup")
		mgr.markDownvoted(now, wasUp)
		RedditPostActions.revertPostActionOnFailure(mgr, wasUp, RedditAPI.ACTION_DOWNVOTE, 1, now)
		assertTrue(mgr.isUpvoted(wasUp))
		assertFalse(mgr.isDownvoted(wasUp))

		val wasNone = RedditIdAndType("t3_wasnone")
		mgr.markUpvoted(now, wasNone)
		RedditPostActions.revertPostActionOnFailure(mgr, wasNone, RedditAPI.ACTION_UPVOTE, 0, now)
		assertFalse(mgr.isUpvoted(wasNone))
		assertFalse(mgr.isDownvoted(wasNone))
	}

	@Test
	fun postSaveFailureRestoresSavedOnly() {
		val mgr = freshManager("post-save-keeps-vote")
		val id = RedditIdAndType("t3_savedvote")
		val now = TimestampUTC.now()

		mgr.markUpvoted(now, id)
		mgr.markSaved(now, id, true) // optimistic save applied

		// Save request fails; the save rollback must not disturb the vote.
		RedditPostActions.revertPostActionOnFailure(mgr, id, RedditAPI.ACTION_SAVE, 1, now)

		assertFalse(mgr.isSaved(id))
		assertTrue(mgr.isUpvoted(id))
	}

	// ---------------------------------------------------------------------------------------
	// Comment rollback (RedditAPICommentAction.revertOnFailure)
	// ---------------------------------------------------------------------------------------

	@Test
	fun commentVoteFailureDoesNotClearSavedState() {
		val mgr = freshManager("comment-vote-keeps-saved")
		val id = RedditIdAndType("t1_commentsaved")
		val now = TimestampUTC.now()

		mgr.markSaved(now, id, true)
		mgr.markUpvoted(now, id) // optimistic upvote applied

		// Upvote request fails; the comment was previously upvoted (wasUpvoted = true).
		RedditAPICommentAction.revertOnFailure(mgr, id, RedditAPI.ACTION_UPVOTE, true, false)

		assertTrue(mgr.isUpvoted(id))
		// The fall-through bug used to clear this to false; it must stay saved.
		assertTrue(mgr.isSaved(id))
	}

	@Test
	fun commentVoteFailureRestoresPreviousVoteDirection() {
		val mgr = freshManager("comment-vote-restore-direction")
		val now = TimestampUTC.now()

		val wasUp = RedditIdAndType("t1_wasup")
		mgr.markDownvoted(now, wasUp)
		RedditAPICommentAction.revertOnFailure(mgr, wasUp, RedditAPI.ACTION_DOWNVOTE, true, false)
		assertTrue(mgr.isUpvoted(wasUp))
		assertFalse(mgr.isDownvoted(wasUp))

		val wasDown = RedditIdAndType("t1_wasdown")
		mgr.markUpvoted(now, wasDown)
		RedditAPICommentAction.revertOnFailure(mgr, wasDown, RedditAPI.ACTION_UPVOTE, false, true)
		assertTrue(mgr.isDownvoted(wasDown))
		assertFalse(mgr.isUpvoted(wasDown))

		val wasNone = RedditIdAndType("t1_wasnone")
		mgr.markUpvoted(now, wasNone)
		RedditAPICommentAction.revertOnFailure(mgr, wasNone, RedditAPI.ACTION_UPVOTE, false, false)
		assertFalse(mgr.isUpvoted(wasNone))
		assertFalse(mgr.isDownvoted(wasNone))
	}

	@Test
	fun commentSaveFailureRestoresSavedOnly() {
		val mgr = freshManager("comment-save-keeps-vote")
		val id = RedditIdAndType("t1_savedvote")
		val now = TimestampUTC.now()

		mgr.markUpvoted(now, id)
		mgr.markSaved(now, id, true) // optimistic save applied

		RedditAPICommentAction.revertOnFailure(mgr, id, RedditAPI.ACTION_SAVE, false, false)

		assertFalse(mgr.isSaved(id))
		assertTrue(mgr.isUpvoted(id))
	}
}
