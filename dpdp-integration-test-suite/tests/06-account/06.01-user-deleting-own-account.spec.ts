/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import {
  test,
  expect,
  loginAsThrowawayUser,
  type ThrowawaySession,
} from '../../fixtures/auth.fixtures'
import { UserProfileMenuPage } from '../../pages/UserProfileMenuPage'
import { env } from '../../utils/env'
import {
  attemptDeleteAsUser,
  createThrowawayUser,
  deleteThrowawayUser,
  userExists,
  type ThrowawayUser,
} from '../../utils/throwawayUser'

/**
 * Self-service account deletion, end to end against the real Identity Server.
 *
 * Deleting an account is irreversible, so every test here creates its own throwaway user rather
 * than touching the shared personas the rest of the suite depends on staying alive for the whole
 * run - those have to still exist when this file is done with them. Creating and removing that
 * user needs the super-tenant admin, the same persona tests/05-multi-tenancy provisions with.
 */
const PORTAL_USER_ROLE = 'dpdp-consent-user'

test.describe('Self-service account deletion (UI)', () => {
  const admin = env.superAdmin

  let throwaway: ThrowawayUser | undefined
  let session: ThrowawaySession | undefined

  test.beforeEach(async ({ browser }) => {
    throwaway = await createThrowawayUser(PORTAL_USER_ROLE, 'dpdp-e2e-delete')
    session = await loginAsThrowawayUser(browser, throwaway)
  })

  test.afterEach(async () => {
    await session?.page.context().close()
    // Expected to be a no-op on the happy path - the account is already gone. This is here for
    // the failure paths, so a broken run doesn't leave accounts behind.
    if (throwaway) {
      await deleteThrowawayUser(admin, throwaway.id, throwaway.username)
    }
    session = undefined
    throwaway = undefined
  })

  /**
   * Covers both server configurations, because which one applies is the
   * deployment's choice and the portal only finds out from the status code:
   * 204 means the account is gone, 202 means an approval workflow recorded a
   * request and the account is still there. Each outcome has to say the right
   * thing - claiming deletion on a 202 would sign the user out of an account
   * they still have.
   */
  test('06.01.01 - A user deletes their own account, or raises a request when approval is required', async () => {
    const { page } = session!
    const menu = new UserProfileMenuPage(page)

    await page.goto('dashboard')
    await menu.open()
    await menu.deleteAccountItem.click()

    const deleteResponse = page.waitForResponse(
      (response) =>
        response.url().includes('/scim2/Me') && response.request().method() === 'DELETE',
    )
    await menu.confirmDeleteButton().click()
    const status = (await deleteResponse).status()
    expect([202, 204]).toContain(status)

    if (status === 204) {
      await expect(page.getByText('Your account has been deleted')).toBeVisible()
      expect(page.url()).toContain('/account-deleted')
      // The redirect is the portal's own claim; this is the user store's answer.
      expect(await userExists(throwaway!.id)).toBe(false)
      return
    }

    await expect(page.getByText(/submitted for approval/i)).toBeVisible()
    await expect(page.getByText('Your account has been deleted')).toHaveCount(0)
    expect(page.url()).not.toContain('/account-deleted')
    // Still a real account until somebody approves the request.
    expect(await userExists(throwaway!.id)).toBe(true)
  })

  test('06.01.02 - Cancelling leaves the account untouched', async () => {
    const { page } = session!
    const menu = new UserProfileMenuPage(page)

    await page.goto('dashboard')
    await menu.open()
    await menu.deleteAccountItem.click()
    await menu.cancelDeleteButton().click()

    await expect(page.getByText('Your account has been deleted')).toHaveCount(0)
    expect(await userExists(throwaway!.id)).toBe(true)
  })

  test('06.01.03 - The self-delete scope does not authorize deleting anybody else', async () => {
    /*
     * The whole point of the custom account:self:delete scope: the default
     * internal_user_mgt_delete that guards DELETE /scim2/Me by default would have authorized
     * DELETE /scim2/Users/{id} too, letting any portal user delete any other account. This calls
     * SCIM2 with the throwaway user's own access token - the same token the portal's own delete
     * uses - against a second, real account, and expects the server to refuse.
     */
    const victim = await createThrowawayUser(PORTAL_USER_ROLE, 'dpdp-e2e-victim')
    try {
      const status = await attemptDeleteAsUser(session!.bearerToken, victim.id)

      expect([401, 403]).toContain(status)
      expect(await userExists(victim.id)).toBe(true)
    } finally {
      await deleteThrowawayUser(admin, victim.id, victim.username)
    }
  })
})
