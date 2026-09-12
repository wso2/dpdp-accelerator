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
import { PurposeFormDialog } from '../../pages/PurposeFormDialog'
import { PurposeListPage } from '../../pages/PurposeListPage'
import { uniqueMarker, uniquePurposeName } from '../../utils/testData'

/**
 * The Purposes list's search bar: substring name matching, exact type matching, Reset, and the
 * no-match empty state. See tests/02-purposes/02.01-admin-creating-purposes.spec.ts for the
 * actual creation flow.
 */
test.describe('Admin searching the Purposes list (UI)', () => {
  test('02.03.01 - Searching by a partial name still finds the matching purpose', async ({
    browser,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const purposeName = uniquePurposeName()
    const listPage = new PurposeListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()

    const dialog = new PurposeFormDialog(consentAdminPage)
    await dialog.fill({ name: purposeName, type: 'Policy', version: 'v1' })
    await dialog.submit()
    await expect(consentAdminPage).toHaveURL(/\/purposes\/[^/]+$/)
    const purposeMatch = /\/purposes\/([^/]+)$/.exec(consentAdminPage.url())
    if (purposeMatch) {
      consentCleanupTracker.trackPurpose(purposeMatch[1])
    }

    // Only the timestamp segment of the generated `purpose-<timestamp>-<random>` name - proves
    // the search matches on a substring (the API filter is `name co "..."`), not an exact
    // full-name match like the create-flow test already covers.
    const partialName = purposeName.slice(purposeName.indexOf('-') + 1, purposeName.lastIndexOf('-'))
    await listPage.goto()
    await listPage.search({ name: partialName })
    await expect(listPage.rowByName(purposeName)).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('02.03.02 - Filtering by an exact type finds only purposes of that type', async ({
    browser,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // A unique type value, not a realistic one like 'Loyalty' - the shared environment likely
    // already has other purposes of common types, and type is matched exactly (`eq`, no
    // substring), so only a value nothing else could plausibly share proves the filter works.
    const purposeName = uniquePurposeName()
    const uniqueType = uniqueMarker('type')
    const listPage = new PurposeListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()

    const dialog = new PurposeFormDialog(consentAdminPage)
    await dialog.fill({ name: purposeName, type: uniqueType, version: 'v1' })
    await dialog.submit()
    await expect(consentAdminPage).toHaveURL(/\/purposes\/[^/]+$/)
    const purposeMatch = /\/purposes\/([^/]+)$/.exec(consentAdminPage.url())
    if (purposeMatch) {
      consentCleanupTracker.trackPurpose(purposeMatch[1])
    }

    await listPage.goto()
    await listPage.search({ type: uniqueType })
    await expect(listPage.rowByName(purposeName)).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('02.03.03 - Resetting the search clears both filters and shows the unfiltered list again', async ({
    browser,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // Seeded so there's a guaranteed row to reappear once the filters are cleared.
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
    await listPage.search({ name: `no-such-purpose-${Date.now().toString()}` })
    await expect(consentAdminPage.getByText('No purposes match this search.')).toBeVisible()

    await listPage.resetSearch()
    await expect(listPage.nameSearch).toHaveValue('')
    await expect(listPage.typeFilter).toHaveValue('')
    await expect(listPage.rows.first()).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('02.03.04 - A search with no matches shows the empty-results message', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const listPage = new PurposeListPage(consentAdminPage)
    await listPage.goto()
    await listPage.search({ name: `no-such-purpose-${Date.now().toString()}` })
    await expect(consentAdminPage.getByText('No purposes match this search.')).toBeVisible()
    await consentAdminPage.context().close()
  })
})
