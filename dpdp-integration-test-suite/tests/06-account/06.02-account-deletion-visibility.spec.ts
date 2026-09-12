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

import { test, expect, loginAsUser, loginAsConsentAdmin } from '../../fixtures/auth.fixtures'
import { UserProfileMenuPage } from '../../pages/UserProfileMenuPage'

/**
 * Self-service account deletion is granted through `account:self:delete`, which tenant
 * provisioning puts on dpdp-consent-user and deliberately keeps off dpdp-consent-admin, so an
 * administrator cannot delete their own account from the portal and leave a tenant without one.
 *
 * Nothing here deletes an account - that is 05.01, which needs a throwaway user. These two only
 * assert who is offered the option, which is safe to run against the shared personas.
 */
test.describe('Account deletion visibility (UI)', () => {
  test('06.02.01 - A user is offered account deletion in the profile menu', async ({ browser }) => {
    const userPage = await loginAsUser(browser)
    await userPage.goto('dashboard')
    const menu = new UserProfileMenuPage(userPage)

    await menu.open()
    await expect(menu.signOutItem).toBeVisible()
    await expect(menu.deleteAccountItem).toBeVisible()
    await userPage.context().close()
  })

  test('06.02.02 - A Consent Admin is not offered account deletion', async ({ browser }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    await consentAdminPage.goto('dashboard')
    const menu = new UserProfileMenuPage(consentAdminPage)

    await menu.open()
    await expect(menu.signOutItem).toBeVisible()
    // No such menu item exists for this persona - the scope never reaches the token.
    await expect(menu.deleteAccountItem).toHaveCount(0)
    await consentAdminPage.context().close()
  })
})
