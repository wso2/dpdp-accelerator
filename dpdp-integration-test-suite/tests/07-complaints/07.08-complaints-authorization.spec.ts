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

/**
 * The complaints equivalent of tests/04-authorization - route-level redirects and sidebar
 * visibility, gated by App.tsx's REQUIRED_SCOPES.COMPLAINTS_READ_SELF / COMPLAINTS_READ_ANY
 * (utils/scopes.ts) via AuthorizedRoute, same mechanism as every other route in this app.
 *
 * Sidebar label text is read straight from public/i18n/en/common.json, not guessed from the
 * translation key names - AppSidebar.tsx's `sidebar.myComplaints` renders "My Complaints" (the
 * Data Principal's own item, its own unlabeled Sidebar.Category) while `sidebar.complaintManagement`
 * renders just "Complaints" (nested under the "Administration" category, alongside
 * `sidebar.adminConsents` - not "Complaint Management" as the key's own name might suggest).
 *
 * The two entries never appear together for either persona: DPDPIdentityExtensionTenantMgtListener
 * routes every `:self` complaint scope to `dpdp-consent-user` and every `:any` one to
 * `dpdp-consent-admin`, so an admin-only account sees "Complaints" and not "My Complaints" - the
 * same split 04.02's sidebar tests assert for "My Consents" vs "All Consents". An operator who
 * wants both grants the account both roles; no persona in this suite does.
 */
test.describe('Complaints route-level access control and sidebar visibility (UI)', () => {
  test('07.08.01 - A Data Principal navigating directly to /complaints is not redirected away', async ({
    browser,
  }) => {
    const dataPrincipalPage = await loginAsUser(browser)
    await dataPrincipalPage.goto('complaints')
    await expect(dataPrincipalPage).toHaveURL(/\/complaints$/)
    await dataPrincipalPage.context().close()
  })

  test('07.08.02 - A Data Principal navigating directly to /complaint-management is redirected away', async ({
    browser,
  }) => {
    const dataPrincipalPage = await loginAsUser(browser)
    await dataPrincipalPage.goto('complaint-management')
    await expect(dataPrincipalPage).not.toHaveURL(/\/complaint-management$/)
    await dataPrincipalPage.context().close()
  })

  test('07.08.03 - A Data Principal\'s sidebar shows a "My Complaints" entry, not "Complaints"', async ({
    browser,
  }) => {
    const dataPrincipalPage = await loginAsUser(browser)
    await dataPrincipalPage.goto('dashboard')
    const sidebar = new AppSidebarPage(dataPrincipalPage)
    await expect(sidebar.label('My Complaints')).toBeVisible()
    // The officer-facing label never appears at all for a persona lacking COMPLAINTS_READ_ANY -
    // COMPLAINT_ITEMS.filter(hasScope) removes it from the DOM entirely, not merely hides it.
    await expect(sidebar.label('Complaints')).toHaveCount(0)
    await dataPrincipalPage.context().close()
  })

  test('07.08.04 - A Consent Admin can reach /complaint-management directly, and their sidebar shows "Complaints", not "My Complaints"', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    await consentAdminPage.goto('complaint-management')
    await expect(consentAdminPage).toHaveURL(/\/complaint-management$/)

    const sidebar = new AppSidebarPage(consentAdminPage)
    await expect(sidebar.label('Complaints')).toBeVisible()
    await expect(sidebar.label('My Complaints')).toHaveCount(0)
    await consentAdminPage.context().close()
  })
})
