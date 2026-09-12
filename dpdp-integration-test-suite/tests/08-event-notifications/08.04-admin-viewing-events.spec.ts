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

// Imported from tenant.fixtures (a superset of auth.fixtures's `test`) rather than auth.fixtures
// directly - only 08.04.05's cross-tenant half actually requests the worker-scoped `tenant`
// fixture, every other test here just uses the same consentAdminEventApi/loginAsConsentAdmin
// auth.fixtures already provides.
import { test, expect } from '../../fixtures/tenant.fixtures'
import { loginAsConsentAdmin } from '../../fixtures/auth.fixtures'
import type { SubscriptionDeliveryRecord } from '../../clients/EventNotificationApiClient'
import { EventsPage } from '../../pages/EventsPage'
import { EventDetailsPage } from '../../pages/EventDetailsPage'
import { seedActiveTopic, seedPollSubscription, publishMarkedEvent } from '../../utils/eventNotificationSetup'
import { uniqueMarker } from '../../utils/testData'

/**
 * `GET /events`, `GET /events/{id}`, `GET /events/{id}/deliveries`, `GET /events/{deliveryId}/history`
 * (EventEndpoint) plus the portal's Events list/detail screens - see
 * AGENTS.md for the "no publish-event UI" and "no `25` rows-per-page"
 * notes this file relies on. Every event published here goes through
 * utils/eventNotificationSetup.ts's publishMarkedEvent, whose unique `marker` in the payload is
 * what search-based assertions key off, since this environment never resets.
 */
test.describe('Admin viewing and searching Events', () => {
  test('08.04.01 - The Events list renders publication and delivery summary data with pagination', async ({
    browser,
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'list-render')
    const subscription = await seedPollSubscription(consentAdminEventApi, topic.name, { type: 'all' })
    const groupId = subscription.groupId!
    const { event, marker } = await publishMarkedEvent(consentAdminEventApi, groupId, topic.name, ['account'])

    const page = await loginAsConsentAdmin(browser)
    try {
      const eventsPage = new EventsPage(page)
      await eventsPage.goto()
      await eventsPage.search(marker)

      const row = eventsPage.rowByEventId(event.eventId)
      await expect(row).toBeVisible()
      await expect(row.getByText(topic.name, { exact: true })).toBeVisible()
      await expect(row.getByText(groupId, { exact: true })).toBeVisible()
      // deliveriesCount is 1 (one matching POLL/ALL subscription) - rendered as a "1 Subscriber" chip.
      await expect(row.getByText('1 Subscriber', { exact: true })).toBeVisible()

      // Pagination controls: Previous is disabled on the first (only) page of a single-row result,
      // and switching rows-per-page doesn't break the table or lose the row.
      await expect(eventsPage.previousPageButton).toBeDisabled()
      await eventsPage.setRowsPerPage(20)
      await expect(eventsPage.rowByEventId(event.eventId)).toBeVisible()
    } finally {
      await page.context().close()
    }
  })

  test('08.04.02 - Search finds an event by a partial payload value', async ({ browser, consentAdminEventApi }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'payload-search')
    // groupId MUST come from a seeded subscription's own returned groupId, never a caller-chosen
    // value directly (see README's "Bugs found" - EventEndpoint.listEvents hardcodes the caller's
    // orgId as GROUP_ID for every GET /events call, `search` included, regardless of what a
    // caller actually asked for; the endpoint doesn't even declare a groupId query param. An
    // event published under any other group id can never be found via GET /events at all, no
    // matter the search term - confirmed live, not a payload/search-specific bug).
    const subscription = await seedPollSubscription(consentAdminEventApi, topic.name, { type: 'all' })
    const groupId = subscription.groupId!
    const { event, marker } = await publishMarkedEvent(consentAdminEventApi, groupId, topic.name)

    // API-level proof first: `search` matches a payload substring even though the UI's search
    // placeholder only advertises "delivery ID, event ID, or topic" - the backend's EventQueryBuilder
    // also matches LOWER(payload) (see EventNotificationCommonDBQueries), so this is real, not a
    // placeholder-text drift. This suite runs against whichever single DB dialect the live
    // deployment uses (H2 for a default local install) - the DAO's per-dialect payload-search query
    // builders (EventNotificationPostgresDBQueries et al.) are covered separately at the Java unit
    // level, not exercised here.
    const searchSegment = marker.split('-').at(-1) ?? marker
    const apiSearchResponse = await consentAdminEventApi.listEvents({ search: searchSegment, limit: 100 })
    expect(apiSearchResponse.ok()).toBe(true)
    const { items } = (await apiSearchResponse.json()) as { items: { eventId: string }[] }
    expect(items.some((item) => item.eventId === event.eventId)).toBe(true)

    const page = await loginAsConsentAdmin(browser)
    try {
      const eventsPage = new EventsPage(page)
      await eventsPage.goto()
      await eventsPage.search(searchSegment)
      await expect(eventsPage.rowByEventId(event.eventId)).toBeVisible()
    } finally {
      await page.context().close()
    }
  })

  test('08.04.03 - Event details show exact payload, metadata, and subscription-specific deliveries', async ({
    browser,
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'details')
    // Two DISJOINT purpose filters, not two overlapping ones - SubscriptionDAOImpl.addSubscription
    // rejects a new subscription whose purpose set *overlaps* any existing one in the same
    // (org, group, topic, deliveryMode) as EN-4090 "Duplicate subscription" (PurposeOverlapUtils
    // .overlaps - confirmed live: even two SPECIFIC filters that merely share one purpose collide,
    // not just identical ones). Publishing an event whose purposes cover BOTH disjoint sets still
    // reaches both subscriptions, since each only needs to overlap the *event's* purposes.
    const subA = await seedPollSubscription(consentAdminEventApi, topic.name, {
      type: 'specific',
      purposes: ['account'],
    })
    const subB = await seedPollSubscription(consentAdminEventApi, topic.name, {
      type: 'specific',
      purposes: ['profile'],
    })
    const groupId = subA.groupId!
    const { event, marker } = await publishMarkedEvent(
      consentAdminEventApi,
      groupId,
      topic.name,
      ['account', 'profile'],
      { nested: { value: 42 } },
    )

    const page = await loginAsConsentAdmin(browser)
    try {
      // Headless Chromium denies navigator.clipboard.writeText() by default - EventDetailsPage.tsx's
      // Copy Payload button calls it directly (no execCommand fallback), so without this the
      // component's own catch branch fires and shows the FAILURE toast instead, which would be a
      // false negative about the app, not a real product bug.
      await page.context().grantPermissions(['clipboard-write', 'clipboard-read'])

      const detailsPage = new EventDetailsPage(page)
      await detailsPage.goto(event.eventId)

      await expect(detailsPage.payloadBlock).toContainText(marker)
      await expect(detailsPage.fieldValue('Topic')).toContainText(topic.name)
      await expect(detailsPage.fieldValue('Group ID')).toContainText(groupId)

      const deliveriesResponse = await consentAdminEventApi.getEventDeliveries(event.eventId)
      const { items: deliveries } = (await deliveriesResponse.json()) as { items: SubscriptionDeliveryRecord[] }
      const deliveryForA = deliveries.find((delivery) => delivery.subscriptionId === subA.subscriptionId)
      const deliveryForB = deliveries.find((delivery) => delivery.subscriptionId === subB.subscriptionId)
      expect(deliveryForA, 'subscription A should have a delivery for this event').toBeTruthy()
      expect(deliveryForB, 'subscription B should have a delivery for this event').toBeTruthy()
      await expect(detailsPage.deliveryRowByDeliveryId(deliveryForA!.deliveryId)).toBeVisible()
      await expect(detailsPage.deliveryRowByDeliveryId(deliveryForB!.deliveryId)).toBeVisible()

      await detailsPage.copyPayload()
      await expect(detailsPage.copyPayloadSuccessToast).toBeVisible()
    } finally {
      await page.context().close()
    }
  })

  test('08.04.04 - An event with no matching subscribers shows the no-deliveries state', async ({
    browser,
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'no-subscribers')
    const { event } = await publishMarkedEvent(consentAdminEventApi, uniqueMarker('group'), topic.name)

    const page = await loginAsConsentAdmin(browser)
    try {
      const detailsPage = new EventDetailsPage(page)
      await detailsPage.goto(event.eventId)
      await expect(detailsPage.payloadBlock).toBeVisible()
      await expect(detailsPage.noDeliveriesHeading).toBeVisible()
    } finally {
      await page.context().close()
    }
  })

  test('08.04.05 - An unknown or cross-tenant event id is not exposed', async ({ browser, tenant }) => {
    const page = await loginAsConsentAdmin(browser)
    try {
      const detailsPage = new EventDetailsPage(page)

      await detailsPage.goto('00000000-0000-0000-0000-000000000000')
      await expect(detailsPage.loadFailedAlert).toBeVisible()

      // A real event id, just one that belongs to a different tenant entirely.
      const tenantTopic = await seedActiveTopic(tenant.ownerEventApi, 'cross-tenant')
      const { event: tenantEvent } = await publishMarkedEvent(
        tenant.ownerEventApi,
        uniqueMarker('group'),
        tenantTopic.name,
      )
      await detailsPage.goto(tenantEvent.eventId)
      await expect(detailsPage.loadFailedAlert).toBeVisible()
      await expect(page.getByText(tenantTopic.name, { exact: true })).toHaveCount(0)
    } finally {
      await page.context().close()
    }
  })
})
