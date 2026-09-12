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

/**
 * App.tsx's AuthorizedRoute wraps every protected route - both the list pages
 * (/purposes, /elements, /administration/consents) and their detail pages
 * (/purposes/:id, /elements/:id, /administration/consents/:id) alike, since AuthorizedRoute
 * checks the route's required scope before the page component ever tries to fetch the
 * resource the id points at. On a missing scope it does NOT render an inline 403/blocked
 * message - it computes firstAuthorizedPath(currentUser.scopes) (a fixed priority list:
 * /dashboard, /consents, /purposes, /elements, /administration/consents, in that order) and
 * issues a client-side replace redirect to the first path the user IS authorized for.
 * NoAccessPage ("No portal access") only renders when a user holds none of those five scopes at
 * all - no persona in this suite's configuration is scope-less, so that page isn't reachable here and
 * isn't covered below.
 *
 * The user persona (`ctizen1`, plain `internal_login`) holds only
 * CONSENTS_READ_SELF/WRITE_SELF - not PURPOSES_READ, ELEMENTS_READ, or CONSENTS_READ_ANY - so it
 * is exactly the persona these routes are meant to keep out. Since CONSENTS_READ_SELF sits
 * first in firstAuthorizedPath's priority list, every one of these redirects lands on
 * /dashboard. The id in each detail-page URL below is an arbitrary placeholder, not a real
 * resource - the redirect fires purely on scope, before any lookup by id happens, so whether
 * the resource actually exists is irrelevant here.
 */
test.describe('Route-level access control (UI)', () => {
  test('04.01.01 - A user navigating directly to /purposes is redirected to the dashboard', async ({
    browser,
  }) => {
    const userPage = await loginAsUser(browser)
    await userPage.goto('purposes')
    await expect(userPage).toHaveURL(/\/dashboard$/)
    await userPage.context().close()
  })

  test('04.01.02 - A user navigating directly to /elements is redirected to the dashboard', async ({
    browser,
  }) => {
    const userPage = await loginAsUser(browser)
    await userPage.goto('elements')
    await expect(userPage).toHaveURL(/\/dashboard$/)
    await userPage.context().close()
  })

  test('04.01.03 - A user navigating directly to /administration/consents is redirected to the dashboard', async ({
    browser,
  }) => {
    const userPage = await loginAsUser(browser)
    await userPage.goto('administration/consents')
    await expect(userPage).toHaveURL(/\/dashboard$/)
    await userPage.context().close()
  })

  test('04.01.04 - A user navigating directly to a Purpose detail page by link is redirected to the dashboard', async ({
    browser,
  }) => {
    const userPage = await loginAsUser(browser)
    await userPage.goto('purposes/00000000-0000-0000-0000-000000000000')
    await expect(userPage).toHaveURL(/\/dashboard$/)
    await userPage.context().close()
  })

  test('04.01.05 - A user navigating directly to an Element detail page by link is redirected to the dashboard', async ({
    browser,
  }) => {
    const userPage = await loginAsUser(browser)
    await userPage.goto('elements/00000000-0000-0000-0000-000000000000')
    await expect(userPage).toHaveURL(/\/dashboard$/)
    await userPage.context().close()
  })

  test('04.01.06 - A user navigating directly to an admin Consent detail page by link is redirected to the dashboard', async ({
    browser,
  }) => {
    const userPage = await loginAsUser(browser)
    await userPage.goto('administration/consents/00000000-0000-0000-0000-000000000000')
    await expect(userPage).toHaveURL(/\/dashboard$/)
    await userPage.context().close()
  })
})
