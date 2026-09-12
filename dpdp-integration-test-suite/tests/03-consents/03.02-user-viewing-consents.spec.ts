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

import {
  test,
  expect,
  getPersonaState,
  hasSecondUser,
  loginAsUser,
  loginAsConsentAdmin,
  pageForPersonaState,
} from '../../fixtures/auth.fixtures'
import { ConsentDetailPage } from '../../pages/ConsentDetailPage'
import { env } from '../../utils/env'
import { seedConsent } from '../../utils/consentSetup'

/**
 * A user's own consent detail page at /consents/:id: what it renders, the load-failed
 * path for an unknown id, and that a different user can't reach someone else's
 * consent by guessing its id. See
 * tests/03-consents/03.01-user-acting-on-consents.spec.ts for approve/reject/revoke.
 */
test.describe('User viewing Consents (UI)', () => {
  test('03.02.01 - The detail page renders subject, service, and purpose/element structure', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const userPage = await loginAsUser(browser)
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId, purposeName, elementDisplayName, serviceId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'ACTIVE',
    )

    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto(consentId)
    await expect(userPage.getByText(env.user.username)).toBeVisible()
    await expect(userPage.getByText(serviceId)).toBeVisible()
    await expect(userPage.getByText('Not applicable')).toBeVisible()

    await detailPage.expandPurpose(purposeName)
    await expect(detailPage.elementRow(elementDisplayName)).toBeVisible()
    await userPage.context().close()
    await consentAdminPage.context().close()
  })

  test('03.02.02 - An unknown consent id shows the load-failed message with a way back to the registry', async ({
    browser,
  }) => {
    const userPage = await loginAsUser(browser)
    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto('00000000-0000-0000-0000-000000000000')
    await expect(detailPage.loadFailedMessage).toBeVisible()
    await detailPage.backButton.click()
    await expect(userPage).toHaveURL(/\/consents$/)
    await userPage.context().close()
  })

  test("03.02.03 - A different user cannot open another user's consent by its URL", async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    test.skip(!hasSecondUser(), 'personas.user2 is not configured')
    // hasSecondUser() already confirmed this is set - the skip above guards it.
    const secondUser = env.secondUser()
    if (!secondUser) {
      throw new Error('Unreachable: hasSecondUser() already checked this above.')
    }

    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'ACTIVE',
    )

    // No always-on fixture exists for this persona (it's only ever needed here) - shares the
    // same file-based login cache as every other persona via getPersonaState, so this doesn't
    // log in again if any earlier test in the run already did.
    const secondPersonaState = await getPersonaState(browser, 'user-2', secondUser)
    const otherPage = await pageForPersonaState(browser, secondPersonaState, secondUser)
    const otherDetailPage = new ConsentDetailPage(otherPage, 'self')
    await otherDetailPage.goto(consentId)
    await expect(otherDetailPage.loadFailedMessage).toBeVisible()

    await otherPage.context().close()
    await consentAdminPage.context().close()
  })
})
