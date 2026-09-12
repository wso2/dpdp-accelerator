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
import { PurposeDetailPage } from '../../pages/PurposeDetailPage'
import { PurposeFormDialog } from '../../pages/PurposeFormDialog'
import { PurposeListPage } from '../../pages/PurposeListPage'
import { uniquePurposeName } from '../../utils/testData'

/**
 * The read-only Purposes list at /purposes: pagination, and the load-failed path for a bad
 * detail-page id. See tests/02-purposes/02.01-admin-creating-purposes.spec.ts for the actual
 * creation flow.
 */
test.describe('Admin viewing the Purposes list (UI)', () => {
  test('02.02.01 - The rows-per-page control accepts a new page size without erroring', async ({
    browser,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // Seeded so the list is guaranteed non-empty regardless of what earlier runs left behind.
    const listPage = new PurposeListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()
    const dialog = new PurposeFormDialog(consentAdminPage)
    await dialog.fill({ name: uniquePurposeName(), type: 'Policy', version: 'v1' })
    await dialog.submit()
    await expect(consentAdminPage).toHaveURL(/\/purposes\/[^/]+$/)
    const purposeMatch = /\/purposes\/([^/]+)$/.exec(consentAdminPage.url())
    if (purposeMatch) {
      consentCleanupTracker.trackPurpose(purposeMatch[1])
    }

    await listPage.goto()
    await expect(listPage.previousPageButton).toBeDisabled()
    await listPage.setRowsPerPage(25)
    await expect(listPage.table).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('02.02.02 - An unknown purpose id shows the load-failed message with a way back to the list', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const detailPage = new PurposeDetailPage(consentAdminPage)
    await detailPage.goto('00000000-0000-0000-0000-000000000000')
    await expect(detailPage.loadFailedMessage).toBeVisible()
    await detailPage.backButton.click()
    await expect(consentAdminPage).toHaveURL(/\/purposes$/)
    await consentAdminPage.context().close()
  })
})
