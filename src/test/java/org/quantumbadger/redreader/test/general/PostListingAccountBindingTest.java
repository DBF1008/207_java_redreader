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

package org.quantumbadger.redreader.test.general;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quantumbadger.redreader.account.RedditAccount;
import org.quantumbadger.redreader.account.RedditAccountManager;
import org.quantumbadger.redreader.reddit.api.RedditOAuth;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for account context binding in PostListingFragment.
 *
 * These tests verify that:
 * 1. RedditAccount equality correctly identifies same vs different accounts
 * 2. RedditAccountManager returns a stable default account reference
 *    when no account switch occurs
 * 3. After an account switch, the default account differs from the
 *    previously captured reference
 *
 * This guards against the bug where PostListingFragment called
 * getDefaultAccount() at multiple points during its lifecycle,
 * causing cross-account context mixing when the user switched the
 * default account while a listing was still alive.
 */
@RunWith(RobolectricTestRunner.class)
public class PostListingAccountBindingTest {

	private RedditAccountManager accountManager;

	@Before
	public void setUp() throws Exception {
		// Reset the singleton between tests so that each test starts with a
		// fresh account manager. Without this, tests that add/switch accounts
		// would pollute state for subsequent tests.
		final Field singletonField = RedditAccountManager.class
				.getDeclaredField("singleton");
		singletonField.setAccessible(true);
		singletonField.set(null, null);

		final Context context = ApplicationProvider.getApplicationContext();
		accountManager = RedditAccountManager.getInstance(context);
	}

	/**
	 * Verifies that RedditAccount correctly identifies two instances
	 * representing the same named account as equal.
	 */
	@Test
	public void testRedditAccountEquality_sameAccount() {
		final RedditOAuth.RefreshToken token = new RedditOAuth.RefreshToken("test_token_1");
		final RedditAccount account1 = new RedditAccount("TestUser", token, 1, "client_a");
		final RedditAccount account2 = new RedditAccount("TestUser", token, 1, "client_a");

		assertEquals(account1, account2);
		assertEquals(account1.hashCode(), account2.hashCode());
	}

	/**
	 * Verifies that RedditAccount correctly identifies two instances
	 * with different usernames as not equal.
	 */
	@Test
	public void testRedditAccountEquality_differentUsername() {
		final RedditOAuth.RefreshToken token = new RedditOAuth.RefreshToken("test_token_1");
		final RedditAccount account1 = new RedditAccount("UserA", token, 1, "client_a");
		final RedditAccount account2 = new RedditAccount("UserB", token, 1, "client_a");

		assertNotEquals(account1, account2);
	}

	/**
	 * Verifies that RedditAccount treats different clientIds as different
	 * accounts even with the same username.
	 */
	@Test
	public void testRedditAccountEquality_differentClientId() {
		final RedditOAuth.RefreshToken token = new RedditOAuth.RefreshToken("test_token_1");
		final RedditAccount account1 = new RedditAccount("TestUser", token, 1, "client_a");
		final RedditAccount account2 = new RedditAccount("TestUser", token, 1, "client_b");

		assertNotEquals(account1, account2);
	}

	/**
	 * Verifies case-insensitive username comparison (canonical form).
	 */
	@Test
	public void testRedditAccountEquality_caseInsensitiveUsername() {
		final RedditOAuth.RefreshToken token = new RedditOAuth.RefreshToken("test_token_1");
		final RedditAccount account1 = new RedditAccount("TestUser", token, 1, "client_a");
		final RedditAccount account2 = new RedditAccount("testuser", token, 1, "client_a");

		assertEquals(account1, account2);
		assertEquals(account1.canonicalUsername, account2.canonicalUsername);
	}

	/**
	 * Verifies that the anonymous account is correctly identified.
	 */
	@Test
	public void testAnonymousAccount() {
		final RedditAccount anon = RedditAccountManager.getAnon();

		assertTrue(anon.isAnonymous());
		assertFalse(anon.isNotAnonymous());
		assertEquals("", anon.username);
	}

	/**
	 * Verifies that getDefaultAccount() returns a stable reference when
	 * no account switch occurs. This is the foundation for the account
	 * binding pattern: capturing the account at construction time and
	 * reusing it must be safe.
	 */
	@Test
	public void testDefaultAccountStableReference() {
		final RedditAccount first = accountManager.getDefaultAccount();
		final RedditAccount second = accountManager.getDefaultAccount();

		// Must be the exact same object reference when no switch occurred
		assertSame(
				"getDefaultAccount() must return the same reference when no account"
						+ " switch has occurred — this is required for the account"
						+ " binding pattern in PostListingFragment",
				first,
				second);
	}

	/**
	 * Simulates the PostListingFragment account binding scenario:
	 * captures the default account (as the fragment does at construction),
	 * then verifies it still matches the current default when no switch
	 * has occurred.
	 */
	@Test
	public void testBoundAccountMatchesDefault_noAccountSwitch() {
		// Simulates: mBoundAccount = RedditAccountManager.getInstance(...).getDefaultAccount()
		final RedditAccount boundAccount = accountManager.getDefaultAccount();

		// Simulates: later call during onLoadMoreItemsCheck, precacheComments, etc.
		final RedditAccount currentDefault = accountManager.getDefaultAccount();

		// The bound account must still be the same as the current default
		assertSame(
				"Bound account must match current default when no switch occurred",
				boundAccount,
				currentDefault);
	}

	/**
	 * Verifies that after adding a new account and setting it as default,
	 * the getDefaultAccount() returns a different account than the
	 * previously captured reference.
	 *
	 * This tests the scenario where the user switches accounts while a
	 * PostListingFragment is alive.
	 */
	@Test
	public void testBoundAccountDiffersAfterSwitch() {
		// Capture the initial default (anonymous)
		final RedditAccount boundAccount = accountManager.getDefaultAccount();

		// Add a new named account and set it as default
		final RedditAccount newAccount = new RedditAccount(
				"switched_user",
				new RedditOAuth.RefreshToken("new_token"),
				0, // lower priority = higher precedence
				"new_client_id");

		accountManager.addAccount(newAccount);
		accountManager.setDefaultAccount(newAccount);

		// The current default must now differ from the bound account
		final RedditAccount currentDefault = accountManager.getDefaultAccount();

		assertNotSame(
				"After account switch, getDefaultAccount() must return a different"
						+ " object than the previously bound account",
				boundAccount,
				currentDefault);

		assertFalse(
				"After account switch, bound account must not equal current default",
				boundAccount.equals(currentDefault));
	}

	/**
	 * Verifies the full account binding lifecycle: capture, verify stable,
	 * switch, verify stale.
	 *
	 * This mirrors the PostListingFragment lifecycle:
	 * 1. Constructor captures mBoundAccount
	 * 2. Various operations (pagination, subscribe, precache) use mBoundAccount
	 * 3. If user switches accounts, mBoundAccount becomes stale
	 */
	@Test
	public void testFullAccountBindingLifecycle() {
		// Step 1: Fragment construction — capture bound account
		final RedditAccount boundAccount = accountManager.getDefaultAccount();

		// Step 2: Simulate onLoadMoreItemsCheck — verify still same
		assertSame(boundAccount, accountManager.getDefaultAccount());

		// Step 3: Simulate onSubscribe — verify still same
		assertSame(boundAccount, accountManager.getDefaultAccount());

		// Step 4: Simulate precacheComments — verify still same
		assertSame(boundAccount, accountManager.getDefaultAccount());

		// Step 5: User switches account
		final RedditAccount switchedUser = new RedditAccount(
				"lifecycle_user",
				new RedditOAuth.RefreshToken("lifecycle_token"),
				-1,
				"lifecycle_client");
		accountManager.addAccount(switchedUser);
		accountManager.setDefaultAccount(switchedUser);

		// Step 6: Verify the bound account is now stale
		final RedditAccount afterSwitch = accountManager.getDefaultAccount();
		assertNotSame(
				"Bound account must differ from current default after switch",
				boundAccount,
				afterSwitch);

		// Step 7: Verify the new account's identity
		assertEquals("lifecycle_user", afterSwitch.username);
	}
}
