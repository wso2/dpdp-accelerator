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

import { test, expect, loginAsConsentAdmin } from '../../fixtures/auth.fixtures'
import { AdminConsentPage } from '../../pages/AdminConsentPage'
import { ConsentDetailPage } from '../../pages/ConsentDetailPage'
import { env } from '../../utils/env'
import { seedConsent } from '../../utils/consentSetup'

/**
 * The admin registry (/administration/consents) only ever offers Revoke, never Approve/Reject -
 * ConsentRegistryTable.tsx's `canApprove` prop is never passed on this page and
 * ConsentDetailsPage.tsx only computes canApprove/canReject when `variant === 'self'` - so
 * several tests below assert that invariant directly rather than assuming it. Only Consent
 * creation goes through the admin API (see utils/consentSetup.ts); the Element and Purpose it
 * needs are created through the real admin UI forms first, on this same consentAdminPage.
 */
test.describe('Admin acting on Consents (UI)', () => {
  test('03.04.01 - Admin can revoke an Active consent from the list', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'ACTIVE',
    )

    const registryPage = new AdminConsentPage(consentAdminPage)
    await registryPage.goto()
    await registryPage.searchByConsentId(consentId)
    await registryPage.revokeFromList(consentId)
    await consentAdminPage.getByRole('button', { name: 'Revoke Consent' }).click()

    await expect(registryPage.rowByConsentId(consentId)).toContainText('Revoked')
    await consentAdminPage.context().close()
  })

  test('03.04.02 - The admin detail page shows Revoke but never Approve or Reject for an Active consent', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'ACTIVE',
    )

    const detailPage = new ConsentDetailPage(consentAdminPage, 'admin')
    await detailPage.goto(consentId)
    await expect(consentAdminPage.getByRole('button', { name: 'Revoke', exact: true })).toBeVisible()
    await expect(consentAdminPage.getByRole('button', { name: 'Approve', exact: true })).toHaveCount(0)
    await expect(consentAdminPage.getByRole('button', { name: 'Reject', exact: true })).toHaveCount(0)
    await consentAdminPage.context().close()
  })

  test('03.04.03 - The admin list shows no Approve action for a Pending consent, and no Revoke action either', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'PENDING',
    )

    const registryPage = new AdminConsentPage(consentAdminPage)
    await registryPage.goto()
    await registryPage.searchByConsentId(consentId)
    const row = registryPage.rowByConsentId(consentId)
    await expect(row).toBeVisible()
    await expect(row.getByRole('button', { name: 'Approve' })).toHaveCount(0)
    await expect(row.getByRole('button', { name: 'Revoke' })).toHaveCount(0)
    await consentAdminPage.context().close()
  })

  test('03.04.04 - The admin detail page offers no action at all for a Pending consent', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'PENDING',
    )

    const detailPage = new ConsentDetailPage(consentAdminPage, 'admin')
    await detailPage.goto(consentId)
    await expect(consentAdminPage.getByRole('button', { name: 'Approve', exact: true })).toHaveCount(0)
    await expect(consentAdminPage.getByRole('button', { name: 'Reject', exact: true })).toHaveCount(0)
    await expect(consentAdminPage.getByRole('button', { name: 'Revoke', exact: true })).toHaveCount(0)
    await consentAdminPage.context().close()
  })
})
