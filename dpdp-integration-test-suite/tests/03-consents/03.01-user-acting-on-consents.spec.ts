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
import { ConsentDetailPage } from '../../pages/ConsentDetailPage'
import { MyConsentPage } from '../../pages/MyConsentPage'
import { env } from '../../utils/env'
import { seedConsent } from '../../utils/consentSetup'

/**
 * Approve/reject/revoke, from both the list and the detail page, plus the terminal-state guard
 * (a Rejected consent offers none of these actions). Only Consent creation goes through the
 * admin API (see utils/consentSetup.ts - it has no create UI at all); the Element and Purpose
 * each seeded consent needs are created through the real admin "Add Element"/"Add Purpose"
 * forms. `internal_login` alone (granted to every signed-in user, no role needed) is enough for
 * both consent scopes here, so the existing user persona needs no extra role for any of this.
 */
test.describe('User acting on Consents (UI)', () => {
  test('03.01.01 - Approving a Pending consent from the list moves it to Active', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const userPage = await loginAsUser(browser)
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId, serviceId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'PENDING',
    )

    const registryPage = new MyConsentPage(userPage)
    await registryPage.goto()
    // Filtered to this test's own unique service id - see the identical comment on the revoke
    // test below.
    await registryPage.searchByService(serviceId)
    await expect(registryPage.rowByConsentId(consentId)).toContainText('Pending')

    await registryPage.approveFromList(consentId)
    await userPage.getByRole('button', { name: 'Approve Consent' }).click()

    await expect(registryPage.rowByConsentId(consentId)).toContainText('Active')
    await userPage.context().close()
    await consentAdminPage.context().close()
  })

  test('03.01.02 - Rejecting a Pending consent from its detail page moves it to Rejected', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const userPage = await loginAsUser(browser)
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'PENDING',
    )

    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto(consentId)
    await detailPage.openActionDialog('reject')
    await expect(detailPage.dialogTitle('reject')).toBeVisible()
    await detailPage.confirmAction('reject')

    // .first(): the metadata card's state chip and the authorizations table's own state chip
    // both render the literal state text once the sole authorizer (this same user)
    // is also moved to Rejected.
    await expect(userPage.getByText('Rejected', { exact: true }).first()).toBeVisible()
    await userPage.context().close()
    await consentAdminPage.context().close()
  })

  test('03.01.03 - Revoking an Active consent from the list moves it to Revoked and removes the revoke action', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const userPage = await loginAsUser(browser)
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId, serviceId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'ACTIVE',
    )

    const registryPage = new MyConsentPage(userPage)
    await registryPage.goto()
    // Filtered to this test's own unique service id: the unfiltered list is sorted and paged,
    // and a persistent environment can easily push a freshly created row off the first page.
    await registryPage.searchByService(serviceId)
    await registryPage.revokeFromList(consentId)
    await userPage.getByRole('button', { name: 'Revoke Consent' }).click()

    await expect(registryPage.rowByConsentId(consentId)).toContainText('Revoked')
    await expect(
      registryPage.rowByConsentId(consentId).getByRole('button', { name: 'Revoke' }),
    ).toHaveCount(0)
    await userPage.context().close()
    await consentAdminPage.context().close()
  })

  test('03.01.04 - Approving from the detail page works the same way as from the list', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const userPage = await loginAsUser(browser)
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'PENDING',
    )

    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto(consentId)
    await detailPage.openActionDialog('approve')
    await detailPage.confirmAction('approve')

    await expect(userPage.getByText('Active', { exact: true }).first()).toBeVisible()
    await userPage.context().close()
    await consentAdminPage.context().close()
  })

  test('03.01.05 - A Rejected consent offers no approve, reject, or revoke action on its detail page', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const userPage = await loginAsUser(browser)
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'REJECTED',
    )

    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto(consentId)
    await expect(userPage.getByRole('button', { name: 'Approve', exact: true })).toHaveCount(0)
    await expect(userPage.getByRole('button', { name: 'Reject', exact: true })).toHaveCount(0)
    await expect(userPage.getByRole('button', { name: 'Revoke', exact: true })).toHaveCount(0)
    await userPage.context().close()
    await consentAdminPage.context().close()
  })
})
