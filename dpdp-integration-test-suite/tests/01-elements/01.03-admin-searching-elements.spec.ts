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

import type { Page } from '@playwright/test'
import { test, expect, loginAsConsentAdmin, type ConsentCleanupTracker } from '../../fixtures/auth.fixtures'
import { ElementFormDialog } from '../../pages/ElementFormDialog'
import { ElementListPage } from '../../pages/ElementListPage'
import { uniqueElementName } from '../../utils/testData'

/**
 * The Elements list's search box: substring matching, Reset, and the no-match empty state. See
 * tests/01-elements/01.01-admin-creating-elements.spec.ts for the actual creation flow.
 */

/** Creates an element through the UI, tracks it for cleanup, and returns its id. */
async function createElementViaUi(page: Page, tracker: ConsentCleanupTracker): Promise<string> {
  const listPage = new ElementListPage(page)
  await listPage.goto()
  await listPage.openCreateDialog()
  const dialog = new ElementFormDialog(page)
  await dialog.fill({ name: uniqueElementName() })
  await dialog.submit()
  await expect(page).toHaveURL(/\/elements\/[^/]+$/)
  const match = /\/elements\/([^/]+)$/.exec(page.url())
  if (!match) {
    throw new Error(`Could not read an element id out of the detail URL: ${page.url()}`)
  }
  tracker.trackElement(match[1])
  return match[1]
}

test.describe('Admin searching the Elements list (UI)', () => {
  test('01.03.01 - Searching by a partial name still finds the matching element', async ({
    browser,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const elementName = uniqueElementName()
    const listPage = new ElementListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()

    const dialog = new ElementFormDialog(consentAdminPage)
    await dialog.fill({ name: elementName })
    await dialog.submit()
    await expect(consentAdminPage).toHaveURL(/\/elements\/[^/]+$/)
    const match = /\/elements\/([^/]+)$/.exec(consentAdminPage.url())
    if (match) {
      consentCleanupTracker.trackElement(match[1])
    }

    // Only the timestamp segment of the generated `element-<timestamp>-<random>` name - proves
    // the search matches on a substring (the API filter is `name co "..."`), not just an exact
    // full-name match like the create-flow test already covers.
    const partialName = elementName.slice(elementName.indexOf('-') + 1, elementName.lastIndexOf('-'))
    await listPage.goto()
    await listPage.searchByName(partialName)
    await expect(listPage.rowByName(elementName)).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('01.03.02 - Resetting the search clears the filter and shows the unfiltered list again', async ({
    browser,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // Seeded so there's a guaranteed row to reappear once the filter is cleared.
    await createElementViaUi(consentAdminPage, consentCleanupTracker)

    const listPage = new ElementListPage(consentAdminPage)
    await listPage.goto()
    const searchTerm = `no-such-element-${Date.now().toString()}`
    await listPage.searchByName(searchTerm)
    // The empty-results placeholder is itself a <TableRow>, so it's the row count staying at 1
    // (not 0) plus this message that together prove the search actually filtered the list.
    await expect(consentAdminPage.getByText(`No elements match "${searchTerm}".`)).toBeVisible()

    await listPage.resetSearch()
    await expect(listPage.nameSearch).toHaveValue('')
    await expect(listPage.rows.first()).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('01.03.03 - A search with no matches shows the empty-results message', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const listPage = new ElementListPage(consentAdminPage)
    await listPage.goto()

    const searchTerm = `no-such-element-${Date.now().toString()}`
    await listPage.searchByName(searchTerm)

    await expect(consentAdminPage.getByText(`No elements match "${searchTerm}".`)).toBeVisible()
    await consentAdminPage.context().close()
  })
})
