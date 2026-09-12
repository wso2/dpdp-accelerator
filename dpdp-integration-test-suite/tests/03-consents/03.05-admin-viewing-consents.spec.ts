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
 * The admin registry's read surface: a freshly-created consent appearing in the list, and the
 * load-failed path for an unknown id. See
 * tests/03-consents/03.04-admin-acting-on-consents.spec.ts for Revoke and the action-gating
 * invariants.
 */
test.describe('Admin viewing Consents (UI)', () => {
  test('03.05.01 - A consent created via the API appears in the admin list with its subject', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId, serviceId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'ACTIVE',
    )

    const registryPage = new AdminConsentPage(consentAdminPage)
    await registryPage.goto()
    // The unfiltered list is sorted oldest-first with no way to jump pages, so a freshly
    // created row is found by its own id rather than by browsing.
    await registryPage.searchByConsentId(consentId)
    await expect(registryPage.rowByConsentId(consentId)).toContainText(env.user.username)
    await expect(registryPage.rowByConsentId(consentId)).toContainText(serviceId)
    await consentAdminPage.context().close()
  })

  test('03.05.02 - An unknown consent id shows the load-failed message with a way back to the registry', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const detailPage = new ConsentDetailPage(consentAdminPage, 'admin')
    await detailPage.goto('00000000-0000-0000-0000-000000000000')
    await expect(detailPage.loadFailedMessage).toBeVisible()
    await detailPage.backButton.click()
    await expect(consentAdminPage).toHaveURL(/\/administration\/consents$/)
    await consentAdminPage.context().close()
  })
})
