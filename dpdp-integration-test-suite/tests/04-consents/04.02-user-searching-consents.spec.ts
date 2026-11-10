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

import { test, expect, loginAsUser } from '../../fixtures/auth.fixtures'
import { MyConsentPage } from '../../pages/MyConsentPage'
import { seedConsentViaApi } from '../../utils/consentSetup'
import { randomServiceId } from '../../utils/testData'

/**
 * The self-service registry's state filter and service search: narrowing, Clear, exact-match
 * semantics, and the no-match empty state. See
 * tests/04-consents/04.03-user-acting-on-consents.spec.ts for approve/reject/revoke.
 */
test.describe('User searching Consents (UI)', () => {
  test('04.02.01 - The state filter narrows the list to only the selected state', async ({
    browser,
    target,
    consentAdminConsentApi,
  }) => {
    const userPage = await loginAsUser(browser)
    // Both seeded under the same unique service id and narrowed to it first, so the state
    // filter's effect is checked within a controlled two-row set instead of the full,
    // ever-growing unfiltered list.
    const serviceId = randomServiceId()
    const pending = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
      serviceId,
    )
    const active = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'ACTIVE',
      serviceId,
    )

    const registryPage = new MyConsentPage(userPage)
    await registryPage.goto()
    await registryPage.searchByService(serviceId)
    await registryPage.filterByState('Pending')

    await expect(registryPage.rowByConsentId(pending.consentId)).toBeVisible()
    await expect(registryPage.rowByConsentId(active.consentId)).toHaveCount(0)

    await registryPage.clearFilters()
    await expect(registryPage.serviceSearch).toHaveValue('')

    // Re-narrow to just these two seeded consents to check the state filter was actually reset
    // to "All" too, not just the service box - the unfiltered list itself is unbounded and
    // paginated in this shared environment, so a specific row's visibility there can't be
    // asserted reliably (same caveat as browsing vs. searching by id elsewhere in this suite).
    await registryPage.searchByService(serviceId)
    await expect(registryPage.rowByConsentId(active.consentId)).toBeVisible()
    await userPage.context().close()
  })

  test('04.02.02 - Searching by the exact service id finds the matching consent', async ({
    browser,
    target,
    consentAdminConsentApi,
  }) => {
    const userPage = await loginAsUser(browser)
    const serviceId = randomServiceId()
    const { consentId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'ACTIVE',
      serviceId,
    )

    const registryPage = new MyConsentPage(userPage)
    await registryPage.goto()
    await registryPage.searchByService(serviceId)
    await expect(registryPage.rowByConsentId(consentId)).toBeVisible()
    await userPage.context().close()
  })

  test('04.02.03 - A service filter matching nothing shows the empty-results message', async ({
    browser,
  }) => {
    const userPage = await loginAsUser(browser)
    const registryPage = new MyConsentPage(userPage)
    await registryPage.goto()
    await registryPage.searchByService(`no-such-service-${Date.now().toString()}`)
    await expect(registryPage.emptyStateMessage).toBeVisible()
    await userPage.context().close()
  })

  test('04.02.04 - A service search for only a partial match finds nothing', async ({
    browser,
    target,
    consentAdminConsentApi,
  }) => {
    const userPage = await loginAsUser(browser)
    const { serviceId } = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'ACTIVE',
    )

    // The serviceId filter is an exact match against the server, not a substring/contains match
    // like the Elements/Purposes catalog's name search - this is an exact match behavior (a direct API
    // call with a substring returned zero results), not documented anywhere.
    const partialServiceId = serviceId.slice(serviceId.indexOf('-') + 1, serviceId.lastIndexOf('-'))

    const registryPage = new MyConsentPage(userPage)
    await registryPage.goto()
    await registryPage.searchByService(partialServiceId)
    await expect(registryPage.emptyStateMessage).toBeVisible()
    await userPage.context().close()
  })

  test('04.02.05 - The pending view keeps state locked while filtering by Relation (Personal vs Managed)', async ({
    browser,
    target,
    consentAdminConsentApi,
  }) => {
    const userPage = await loginAsUser(browser)
    const serviceId = randomServiceId()
    const personalPending = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user.username,
      'PENDING',
      serviceId,
      undefined,
      [{ userId: target.personas.user2.username, type: 'PARENT' }],
    )
    const managedPending = await seedConsentViaApi(
      consentAdminConsentApi,
      target.personas.user2.username,
      'PENDING',
      serviceId,
      undefined,
      [{ userId: target.personas.user.username, type: 'PARENT' }],
    )

    const registryPage = new MyConsentPage(userPage)
    await registryPage.gotoPending()
    await expect(registryPage.stateFilter).toHaveAttribute('aria-disabled', 'true')
    await expect(registryPage.relationFilter).not.toHaveAttribute('aria-disabled', 'true')

    await registryPage.searchByService(serviceId)
    await expect(registryPage.rowByConsentId(personalPending.consentId)).toBeVisible()
    await expect(registryPage.rowByConsentId(managedPending.consentId)).toBeVisible()

    await registryPage.filterByRelation('Personal')
    await expect(registryPage.rowByConsentId(personalPending.consentId)).toBeVisible()
    await expect(registryPage.rowByConsentId(managedPending.consentId)).toHaveCount(0)

    await registryPage.filterByRelation('Managed')
    await expect(registryPage.rowByConsentId(managedPending.consentId)).toBeVisible()
    await expect(registryPage.rowByConsentId(personalPending.consentId)).toHaveCount(0)

    await registryPage.clearFilters()
    await registryPage.searchByService(serviceId)
    await expect(registryPage.stateFilter).toHaveAttribute('aria-disabled', 'true')
    await expect(registryPage.rowByConsentId(personalPending.consentId)).toBeVisible()
    await expect(registryPage.rowByConsentId(managedPending.consentId)).toBeVisible()
    await userPage.context().close()
  })
})
