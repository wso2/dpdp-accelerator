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
import { ElementFormDialog } from '../../pages/ElementFormDialog'
import { ElementListPage } from '../../pages/ElementListPage'
import { uniqueElementName } from '../../utils/testData'

/**
 * The "Add Element" form's validation rules. The happy-path creation flow is not duplicated here:
 * every consent test drives this same form as setup via `seedConsent` (utils/consentSetup.ts), so
 * a passing 03-consents run already proves it. Elements created here are registered with
 * `consentCleanupTracker` so they're deleted again once the test finishes - see
 * fixtures/auth.fixtures.ts's ConsentCleanupTracker.
 */
test.describe('Admin creating Elements (UI)', () => {
  test('01.01.01 - Leaving name empty shows the required-field error and blocks submission', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const listPage = new ElementListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()

    const dialog = new ElementFormDialog(consentAdminPage)
    await dialog.blurName()
    await dialog.submit()

    await expect(dialog.root.getByText('Name is required.')).toBeVisible()
    await expect(dialog.root).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('01.01.02 - Creating an element with a name that already exists shows the duplicate-name message', async ({
    browser,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const elementName = uniqueElementName()

    const listPage = new ElementListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()
    const firstDialog = new ElementFormDialog(consentAdminPage)
    await firstDialog.fill({ name: elementName })
    await firstDialog.submit()
    await expect(consentAdminPage).toHaveURL(/\/elements\/[^/]+$/)
    const match = /\/elements\/([^/]+)$/.exec(consentAdminPage.url())
    if (match) {
      consentCleanupTracker.trackElement(match[1])
    }

    await listPage.goto()
    await listPage.openCreateDialog()
    const dialog = new ElementFormDialog(consentAdminPage)
    await dialog.fill({ name: elementName })
    await dialog.submit()

    await expect(
      dialog.root.getByText(`An element named "${elementName}" already exists. Choose a different name.`),
    ).toBeVisible()
    // Still on the dialog - the duplicate was rejected, not silently created twice.
    await expect(dialog.root).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('01.01.03 - A property value with no key blocks submission until the key is filled in or the row is removed', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const listPage = new ElementListPage(consentAdminPage)
    await listPage.goto()
    await listPage.openCreateDialog()

    const dialog = new ElementFormDialog(consentAdminPage)
    await dialog.fill({ name: uniqueElementName() })
    await dialog.addProperty('', 'orphaned-value')

    await expect(dialog.root.getByText('Add a key, or this value will not be saved.')).toBeVisible()
    await expect(dialog.createButton).toBeDisabled()
    await consentAdminPage.context().close()
  })
})
