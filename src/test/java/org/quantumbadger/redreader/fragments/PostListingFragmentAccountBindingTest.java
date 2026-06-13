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

package org.quantumbadger.redreader.fragments;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.quantumbadger.redreader.account.RedditAccount;
import org.robolectric.RobolectricTestRunner;

/**
 * Regression tests for the account binding of a post listing page.
 *
 * <p>A {@code PostListingFragment} is bound to the account that was the default
 * when the page was created. All subsequent requests in the page's lifecycle
 * (pagination, subreddit metadata, comment precaching, subscribe/unsubscribe)
 * must use that bound account rather than re-reading the (possibly changed)
 * global default account. If they did not, switching the default account while a
 * page was still alive would mix posts and cache entries (the cache is keyed by
 * username) across accounts.
 *
 * <p>{@link PostListingFragment#isPostListingAccountStale} is the predicate that
 * decides whether a page has been superseded by an account switch and must stop
 * issuing requests. These tests pin down its behaviour.
 */
@RunWith(RobolectricTestRunner.class)
public class PostListingFragmentAccountBindingTest {

	@NonNull
	private static RedditAccount account(@NonNull final String username) {
		return new RedditAccount(username, null, 0, null);
	}

	@NonNull
	private static RedditAccount anon() {
		return account("");
	}

	@Test
	public void boundToSameUser_isNotStale() {
		assertFalse(
				"A page must not be considered stale while the default account is"
						+ " unchanged",
				PostListingFragment.isPostListingAccountStale(
						account("alice"),
						account("alice")));
	}

	@Test
	public void switchToDifferentUser_isStale() {
		assertTrue(
				"Switching the default account to a different user must mark the page"
						+ " as stale",
				PostListingFragment.isPostListingAccountStale(
						account("alice"),
						account("bob")));
	}

	@Test
	public void switchFromUserToAnon_isStale() {
		assertTrue(
				"Logging out (switching to the anonymous account) must mark the page"
						+ " as stale",
				PostListingFragment.isPostListingAccountStale(
						account("alice"),
						anon()));
	}

	@Test
	public void switchFromAnonToUser_isStale() {
		assertTrue(
				"Logging in (switching from the anonymous account to a user) must"
						+ " mark the page as stale",
				PostListingFragment.isPostListingAccountStale(
						anon(),
						account("alice")));
	}

	@Test
	public void bothAnonymous_isNotStale() {
		assertFalse(
				"An anonymous page must not be considered stale while the default"
						+ " account is still anonymous",
				PostListingFragment.isPostListingAccountStale(anon(), anon()));
	}

	@Test
	public void sameUserDifferentCasing_isNotStale() {
		// Usernames are compared case-insensitively, so a difference in casing must
		// not spuriously invalidate a page.
		assertFalse(
				"The same user with different casing must be treated as the same"
						+ " account",
				PostListingFragment.isPostListingAccountStale(
						account("Alice"),
						account("alice")));
	}
}
