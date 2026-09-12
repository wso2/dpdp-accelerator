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

import { test, expect } from '../../fixtures/tenant.fixtures'
import { uniquePurposeName } from '../../utils/testData'

/**
 * docs/configuration-guide.md's claim that "consents, catalog data, roles and sessions are all
 * partitioned per tenant by the server" has no automated coverage before this. Uses only the
 * consent-mgt v2 API - confirmed live to work tenant-qualified over a real bearer token (see
 * fixtures/tenant.fixtures.ts) - never SCIM2, which is confirmed broken for a secondary tenant on
 * this build.
 */
test.describe('Tenant data isolation (API)', () => {
  test('05.02.01 - A Purpose created in a tenant is invisible from the super tenant, and vice versa', async ({
    tenant,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const tenantPurposeName = uniquePurposeName()
    const superTenantPurposeName = uniquePurposeName()

    const tenantCreate = await tenant.ownerConsentApi.createPurpose({
      name: tenantPurposeName,
      type: 'Policy',
      version: 'v1',
    })
    expect(tenantCreate.ok()).toBeTruthy()

    const superTenantCreate = await consentAdminConsentApi.createPurpose({
      name: superTenantPurposeName,
      type: 'Policy',
      version: 'v1',
    })
    expect(superTenantCreate.ok()).toBeTruthy()
    const superTenantPurposeId = (await superTenantCreate.json()).id as string
    consentCleanupTracker.trackPurpose(superTenantPurposeId)

    // Not visible from carbon.super. Field is capitalized ("Purposes") in the consent-mgt v2
    // API's own list response - confirmed live against a real server, not guessed.
    const foundInSuperTenant = await consentAdminConsentApi.findPurposeByName(tenantPurposeName)
    expect(foundInSuperTenant.ok()).toBeTruthy()
    expect((await foundInSuperTenant.json()).totalResults).toBe(0)

    // ...and vice versa: the super tenant's Purpose is not visible from inside the tenant.
    const foundInTenant = await tenant.ownerConsentApi.findPurposeByName(superTenantPurposeName)
    expect(foundInTenant.ok()).toBeTruthy()
    expect((await foundInTenant.json()).totalResults).toBe(0)
  })
})
