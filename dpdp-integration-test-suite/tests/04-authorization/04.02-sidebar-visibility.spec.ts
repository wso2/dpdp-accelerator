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
import { AppSidebarPage } from '../../pages/AppSidebarPage'

test.describe('Sidebar navigation visibility (UI)', () => {
  test("04.02.01 - A user's sidebar shows only the Dashboard and Consent sections", async ({
    browser,
  }) => {
    const userPage = await loginAsUser(browser)
    await userPage.goto('dashboard')
    const sidebar = new AppSidebarPage(userPage)

    await expect(sidebar.label('Dashboard')).toBeVisible()
    await expect(sidebar.label('Consent')).toBeVisible()
    await expect(sidebar.label('My Consents')).toBeVisible()
    await expect(sidebar.label('My Pending Consents')).toBeVisible()

    await expect(sidebar.label('Definitions')).toHaveCount(0)
    await expect(sidebar.label('Purposes')).toHaveCount(0)
    await expect(sidebar.label('Elements')).toHaveCount(0)
    await expect(sidebar.label('Administration')).toHaveCount(0)
    await expect(sidebar.label('All Consents')).toHaveCount(0)
    await userPage.context().close()
  })

  test('04.02.02 - A Consent Admin\'s sidebar shows every section, including Definitions and Administration', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    await consentAdminPage.goto('dashboard')
    const sidebar = new AppSidebarPage(consentAdminPage)

    await expect(sidebar.label('Dashboard')).toBeVisible()
    await expect(sidebar.label('Definitions')).toBeVisible()
    await expect(sidebar.label('Purposes')).toBeVisible()
    await expect(sidebar.label('Elements')).toBeVisible()
    await expect(sidebar.label('Administration')).toBeVisible()
    await expect(sidebar.label('All Consents')).toBeVisible()

    await expect(sidebar.label('My Consents')).toHaveCount(0)
    await expect(sidebar.label('My Pending Consents')).toHaveCount(0)
    await expect(sidebar.label('Consent')).toHaveCount(0)
    await consentAdminPage.context().close()
  })
})
