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

import { test, expect, loginAsConsentAdmin, loginAsUser } from '../../fixtures/auth.fixtures'
import { loginAsTenantOwnerWithToken, test as tenantTest } from '../../fixtures/tenant.fixtures'
import { eventNotificationsApiUrl } from '../../utils/env'
import { AppSidebarPage } from '../../pages/AppSidebarPage'
import { EventsPage } from '../../pages/EventsPage'
import { SubscriptionsPage } from '../../pages/SubscriptionsPage'
import { TopicsPage } from '../../pages/TopicsPage'
import { seedActiveTopic, seedPollSubscription } from '../../utils/eventNotificationSetup'

/**
 * Route/sidebar gating (AuthorizedRoute + AppSidebar.tsx, both keyed on
 * REQUIRED_SCOPES.EVENT_TOPICS_READ / EVENT_SUBSCRIPTIONS_READ / EVENTS_READ - utils/scopes.ts)
 * and API-level scope/authentication enforcement, keyed on the `[[resource.access_control]]`
 * entries in deployment.toml for `/api/dpdp/event-notifications/v1/*` (see the module's own
 * README "Setup" section for the full scope list). `dpdp-consent-admin` holds every
 * `notifications:*` scope; `dpdp-consent-user` holds none - there is no dedicated persona
 * anywhere in this environment holding a strict subset (e.g. read-only), so 08.05.03 documents
 * that gap explicitly rather than asserting something this environment can't actually prove.
 */
test.describe('Event Notification authorization and access control', () => {
  test('08.05.01 - A Consent Admin sees and can open Events, Topics, and Subscriptions', async ({
    browser,
    consentAdminEventApi,
  }) => {
    const adminPage = await loginAsConsentAdmin(browser)
    await adminPage.goto('dashboard')
    const sidebar = new AppSidebarPage(adminPage)
    await expect(sidebar.label('Topics')).toBeVisible()
    await expect(sidebar.label('Subscriptions')).toBeVisible()
    // "Events" the nav ITEM and "Events" the CATEGORY heading it lives under render identical
    // text (sidebar.events names both) - AppSidebarPage.label() alone is ambiguous here, so this
    // targets the item specifically by its button role, confirmed live to disambiguate the two.
    await expect(sidebar.nav.getByRole('button', { name: 'Events', exact: true })).toBeVisible()

    const topicsPage = new TopicsPage(adminPage)
    await topicsPage.goto()
    await expect(adminPage).toHaveURL(/\/events\/topics$/)
    await expect(topicsPage.heading).toBeVisible()

    const subscriptionsPage = new SubscriptionsPage(adminPage)
    await subscriptionsPage.goto()
    await expect(adminPage).toHaveURL(/\/events\/subscriptions$/)
    await expect(subscriptionsPage.heading).toBeVisible()

    const eventsPage = new EventsPage(adminPage)
    await eventsPage.goto()
    await expect(adminPage).toHaveURL(/\/events$/)
    await expect(eventsPage.heading).toBeVisible()

    // A details route too - subscription details, seeded via a poll subscription so no webhook
    // receiver is needed just to prove the route itself is reachable.
    const topic = await seedActiveTopic(consentAdminEventApi, '09-01-01-topic')
    const subscription = await seedPollSubscription(consentAdminEventApi, topic.name)
    await adminPage.goto(`events/subscriptions/${subscription.subscriptionId}`)
    await expect(adminPage).toHaveURL(new RegExp(`/events/subscriptions/${subscription.subscriptionId}$`))

    await adminPage.context().close()
  })

  test('08.05.02 - A Data Principal without Event Notification scopes cannot access event routes', async ({
    browser,
    userEventApi,
  }) => {
    const userPage = await loginAsUser(browser)
    await userPage.goto('dashboard')
    const sidebar = new AppSidebarPage(userPage)
    // Filtered out of the DOM entirely (AppSidebar.tsx's hasScope filter), not merely hidden.
    await expect(sidebar.label('Topics')).toHaveCount(0)
    await expect(sidebar.label('Subscriptions')).toHaveCount(0)
    await expect(sidebar.label('Events')).toHaveCount(0)

    for (const path of ['events', 'events/topics', 'events/subscriptions']) {
      await userPage.goto(path)
      await expect(userPage).not.toHaveURL(new RegExp(`/${path}$`))
    }
    // A details route with a syntactically valid but arbitrary id redirects the same way -
    // AuthorizedRoute checks scope before the page ever tries to load the id.
    await userPage.goto('events/subscriptions/00000000-0000-0000-0000-000000000000')
    await expect(userPage).not.toHaveURL(/\/events\/subscriptions\//)
    await userPage.context().close()

    // Protected APIs return 403 without exposing data - never a 200 with an empty/filtered body.
    expect((await userEventApi.listTopics()).status()).toBe(403)
    expect((await userEventApi.listSubscriptions()).status()).toBe(403)
    expect((await userEventApi.listEvents()).status()).toBe(403)
  })

  test('08.05.03 - A token without write scopes cannot perform write operations', async ({
    consentAdminEventApi,
    userEventApi,
  }) => {
    // This environment has no dedicated read-only Event Notification role/client to test true
    // read/write scope SEPARATION with (dpdp-consent-admin holds every notifications:* scope;
    // dpdp-consent-user holds none) - the same gap the complaint scopes have, since no role
    // separates 'read-only' from 'write' beyond what is already granted. What IS provable: the
    // admin's full-scope token can read, and a token
    // with none of the notifications:* scopes cannot write (or read) anything.
    expect((await consentAdminEventApi.listTopics()).status()).toBe(200)
    expect((await consentAdminEventApi.listSubscriptions()).status()).toBe(200)
    expect((await consentAdminEventApi.listEvents()).status()).toBe(200)

    const topicResponse = await userEventApi.createTopic({ name: 'should-not-be-created' })
    expect(topicResponse.status()).toBe(403)
    const subscriptionResponse = await userEventApi.createSubscription({
      topic: 'consent.update',
      filter: { type: 'ALL' },
      delivery: { mode: 'POLL', sharedSecret: 'unused' },
    })
    expect(subscriptionResponse.status()).toBe(403)
    const publishResponse = await userEventApi.publishEvent('carbon.super', {
      topic: 'consent.update',
      payload: { marker: 'should-not-be-published' },
    })
    expect(publishResponse.status()).toBe(403)

    // No state change from any rejected attempt: the topic name above never appears.
    const listed = await consentAdminEventApi.listTopics({ search: 'should-not-be-created' })
    const { items } = (await listed.json()) as { items: { name: string }[] }
    expect(items.some((topic) => topic.name === 'should-not-be-created')).toBe(false)
  })

  tenantTest(
    '08.05.04 - Missing, expired, or wrong-tenant tokens cannot access Event Notification APIs',
    async ({ browser, request, tenant }) => {
      // No token.
      const noTokenResponse = await request.get(eventNotificationsApiUrl('/topics'))
      expect(noTokenResponse.status()).toBe(401)

      // Malformed/expired-looking token.
      const badTokenResponse = await request.get(eventNotificationsApiUrl('/topics'), {
        headers: { Authorization: 'Bearer this-is-not-a-real-token' },
      })
      expect(badTokenResponse.status()).toBe(401)

      // A genuinely different (throwaway) tenant's own valid, freshly issued admin token,
      // replayed directly against the SUPER tenant's event-notification API base URL - the token
      // authenticates fine, it's simply for the wrong org, which the API must still reject. Note:
      // this deliberately omits the token-binding cookie (`atbv`, see utils/authStorage.ts) the
      // consent-portal app normally sends alongside a Bearer token, so a 401 here may reflect
      // that missing binding rather than tenant enforcement specifically - either way the call
      // must be refused, which is what this asserts; the tenant-specific mechanism is exercised
      // more precisely in 08.11 (tenant isolation).
      const { bearerToken } = await loginAsTenantOwnerWithToken(browser, tenant)
      const wrongTenantResponse = await request.get(eventNotificationsApiUrl('/topics'), {
        headers: { Authorization: `Bearer ${bearerToken}` },
      })
      expect([401, 403]).toContain(wrongTenantResponse.status())
    },
  )
})
