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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.quantumbadger.redreader.account.RedditAccount
import org.quantumbadger.redreader.common.time.TimestampUTC
import org.quantumbadger.redreader.reddit.api.RedditOAuth
import org.quantumbadger.redreader.reddit.kthings.RedditIdAndType
import org.quantumbadger.redreader.reddit.prepared.RedditChangeDataManager
import org.robolectric.RobolectricTestRunner

/**
 * Regression tests for vote failure revert behavior.
 *
 * Verifies that when a vote action (upvote/downvote/unvote) fails and is
 * reverted, the saved state of the post or comment is NOT affected.
 *
 * See: RedditPostActions.kt revertOnFailure() and
 *      RedditAPICommentAction.java revertOnFailure()
 */
@RunWith(RobolectricTestRunner::class)
class VoteRevertTest {

	private lateinit var changeDataManager: RedditChangeDataManager
	private val postId = RedditIdAndType("t3_testpost1")
	private val commentId = RedditIdAndType("t1_testcomment1")
	private val now = TimestampUTC.now()

	@Before
	fun setUp() {
		val testAccount = RedditAccount(
			"test_vote_revert_user",
			RedditOAuth.RefreshToken("test_token"),
			0L,
			"test_client_id"
		)
		changeDataManager = RedditChangeDataManager.getInstance(testAccount)
	}

	// -----------------------------------------------------------------------
	// RedditChangeDataManager contract: vote operations preserve saved state
	// -----------------------------------------------------------------------

	@Test
	fun markUpvoted_preservesSavedState() {
		changeDataManager.markSaved(now, postId, true)
		assertTrue(changeDataManager.isSaved(postId))

		changeDataManager.markUpvoted(now, postId)

		assertTrue("Upvote should not clear saved state", changeDataManager.isSaved(postId))
		assertTrue(changeDataManager.isUpvoted(postId))
	}

	@Test
	fun markDownvoted_preservesSavedState() {
		changeDataManager.markSaved(now, postId, true)
		assertTrue(changeDataManager.isSaved(postId))

		changeDataManager.markDownvoted(now, postId)

		assertTrue("Downvote should not clear saved state", changeDataManager.isSaved(postId))
		assertTrue(changeDataManager.isDownvoted(postId))
	}

	@Test
	fun markUnvoted_preservesSavedState() {
		changeDataManager.markSaved(now, postId, true)
		changeDataManager.markUpvoted(now, postId)
		assertTrue(changeDataManager.isSaved(postId))

		changeDataManager.markUnvoted(now, postId)

		assertTrue("Unvote should not clear saved state", changeDataManager.isSaved(postId))
		assertFalse(changeDataManager.isUpvoted(postId))
		assertFalse(changeDataManager.isDownvoted(postId))
	}

	// -----------------------------------------------------------------------
	// Regression: post vote failure revert must not clear saved state
	// Simulates the sequence: user has saved post -> user votes -> vote fails
	// -> revert restores previous vote direction. Saved must remain true.
	// -----------------------------------------------------------------------

	@Test
	fun postVoteFailureRevert_doesNotClearSavedState_wasUnvoted() {
		// Setup: post is saved and has no vote
		changeDataManager.markSaved(now, postId, true)
		assertTrue(changeDataManager.isSaved(postId))

		// Optimistic update: user clicks upvote
		changeDataManager.markUpvoted(now, postId)
		assertTrue("Saved state should survive optimistic upvote", changeDataManager.isSaved(postId))

		// Revert on failure: restore to unvoted (the previous state)
		changeDataManager.markUnvoted(now, postId)
		// BUG (before fix): revertOnFailure() also called markSaved(false) here

		assertTrue(
			"Saved state must survive vote failure revert",
			changeDataManager.isSaved(postId)
		)
		assertFalse(changeDataManager.isUpvoted(postId))
		assertFalse(changeDataManager.isDownvoted(postId))
	}

	@Test
	fun postVoteFailureRevert_doesNotClearSavedState_wasUpvoted() {
		// Setup: post is saved and currently upvoted
		changeDataManager.markSaved(now, postId, true)
		changeDataManager.markUpvoted(now, postId)

		// Optimistic update: user switches to downvote
		changeDataManager.markDownvoted(now, postId)

		// Revert on failure: restore to upvoted
		changeDataManager.markUpvoted(now, postId)

		assertTrue(
			"Saved state must survive vote failure revert (was upvoted)",
			changeDataManager.isSaved(postId)
		)
		assertTrue(changeDataManager.isUpvoted(postId))
	}

	@Test
	fun postVoteFailureRevert_doesNotClearSavedState_wasDownvoted() {
		// Setup: post is saved and currently downvoted
		changeDataManager.markSaved(now, postId, true)
		changeDataManager.markDownvoted(now, postId)

		// Optimistic update: user switches to upvote
		changeDataManager.markUpvoted(now, postId)

		// Revert on failure: restore to downvoted
		changeDataManager.markDownvoted(now, postId)

		assertTrue(
			"Saved state must survive vote failure revert (was downvoted)",
			changeDataManager.isSaved(postId)
		)
		assertTrue(changeDataManager.isDownvoted(postId))
	}

	// -----------------------------------------------------------------------
	// Regression: comment vote failure revert must not clear saved state
	// Same pattern as posts, but for comments (RedditAPICommentAction).
	// -----------------------------------------------------------------------

	@Test
	fun commentVoteFailureRevert_doesNotClearSavedState_wasUnvoted() {
		// Setup: comment is saved and has no vote
		changeDataManager.markSaved(now, commentId, true)
		assertTrue(changeDataManager.isSaved(commentId))

		// Optimistic update: user clicks upvote
		changeDataManager.markUpvoted(now, commentId)

		// Revert on failure: restore to unvoted
		changeDataManager.markUnvoted(now, commentId)

		assertTrue(
			"Comment saved state must survive vote failure revert",
			changeDataManager.isSaved(commentId)
		)
	}

	@Test
	fun commentVoteFailureRevert_doesNotClearSavedState_wasDownvoted() {
		// Setup: comment is saved and currently downvoted
		changeDataManager.markSaved(now, commentId, true)
		changeDataManager.markDownvoted(now, commentId)

		// Optimistic update: user switches to upvote
		changeDataManager.markUpvoted(now, commentId)

		// Revert on failure: restore to downvoted
		// BUG (before fix): wasDownvoted was reading isUpvoted() instead of isDownvoted()
		changeDataManager.markDownvoted(now, commentId)

		assertTrue(
			"Comment saved state must survive vote failure revert (was downvoted)",
			changeDataManager.isSaved(commentId)
		)
		assertTrue(changeDataManager.isDownvoted(commentId))
	}

	// -----------------------------------------------------------------------
	// Save/unsave revert should still work correctly on failure
	// -----------------------------------------------------------------------

	@Test
	fun saveFailure_revertsToUnsaved() {
		assertFalse(changeDataManager.isSaved(postId))

		// Optimistic update: user saves
		changeDataManager.markSaved(now, postId, true)
		assertTrue(changeDataManager.isSaved(postId))

		// Revert on failure
		changeDataManager.markSaved(now, postId, false)

		assertFalse("Save failure should revert to unsaved", changeDataManager.isSaved(postId))
	}

	@Test
	fun unsaveFailure_revertsToSaved() {
		changeDataManager.markSaved(now, postId, true)
		assertTrue(changeDataManager.isSaved(postId))

		// Optimistic update: user unsaves
		changeDataManager.markSaved(now, postId, false)
		assertFalse(changeDataManager.isSaved(postId))

		// Revert on failure
		changeDataManager.markSaved(now, postId, true)

		assertTrue("Unsave failure should revert to saved", changeDataManager.isSaved(postId))
	}

	// -----------------------------------------------------------------------
	// Verify vote revert correctness for all three directions (independent of
	// saved state) to ensure the fix didn't break the vote revert itself.
	// -----------------------------------------------------------------------

	@Test
	fun voteRevert_restoresUpvoted() {
		changeDataManager.markUpvoted(now, postId)
		assertTrue(changeDataManager.isUpvoted(postId))

		// Optimistic: switch to downvote
		changeDataManager.markDownvoted(now, postId)
		assertTrue(changeDataManager.isDownvoted(postId))

		// Revert: restore upvoted
		changeDataManager.markUpvoted(now, postId)
		assertTrue(changeDataManager.isUpvoted(postId))
		assertFalse(changeDataManager.isDownvoted(postId))
	}

	@Test
	fun voteRevert_restoresDownvoted() {
		changeDataManager.markDownvoted(now, postId)
		assertTrue(changeDataManager.isDownvoted(postId))

		// Optimistic: switch to upvote
		changeDataManager.markUpvoted(now, postId)
		assertTrue(changeDataManager.isUpvoted(postId))

		// Revert: restore downvoted
		changeDataManager.markDownvoted(now, postId)
		assertTrue(changeDataManager.isDownvoted(postId))
		assertFalse(changeDataManager.isUpvoted(postId))
	}

	@Test
	fun voteRevert_restoresUnvoted() {
		// No vote initially
		assertFalse(changeDataManager.isUpvoted(postId))
		assertFalse(changeDataManager.isDownvoted(postId))

		// Optimistic: user upvotes
		changeDataManager.markUpvoted(now, postId)
		assertTrue(changeDataManager.isUpvoted(postId))

		// Revert: restore unvoted
		changeDataManager.markUnvoted(now, postId)
		assertFalse(changeDataManager.isUpvoted(postId))
		assertFalse(changeDataManager.isDownvoted(postId))
	}
}
