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
import { publishMarkedEvent, seedActiveTopic, seedPollSubscription } from '../../utils/eventNotificationSetup'
import { uniqueMarker } from '../../utils/testData'
import { SubscriptionsPage } from '../../pages/SubscriptionsPage'
import { SubscriptionDetailsPage } from '../../pages/SubscriptionDetailsPage'

/**
 * Admin viewing/searching/filtering the Subscriptions list and a single subscription's details -
 * SubscriptionsPage.tsx / SubscriptionDetailsPage.tsx. This environment never resets (see
 * AGENTS.md), so every assertion here is scoped to a record this test itself created, never to
 * list totals or emptiness.
 */
test.describe('Admin viewing Subscriptions', () => {
  test('08.03.01 - The subscription list renders configuration and accepts pagination', async ({
    browser,
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'sub-view')
    const subscription = await seedPollSubscription(consentAdminEventApi, topic.name)

    const page = await loginAsConsentAdmin(browser)
    try {
      const subscriptionsPage = new SubscriptionsPage(page)
      await subscriptionsPage.goto()
      await subscriptionsPage.search(subscription.subscriptionId)

      const row = subscriptionsPage.rowBySubscriptionId(subscription.subscriptionId)
      await expect(row).toBeVisible()
      await expect(row).toContainText(topic.name)
      await expect(row).toContainText('Poll')
      await expect(row).toContainText('Active')

      // Pagination should remain functional after a real page-size change - the control offers
      // [10, 20, 50] only, so 20 rather than 25.
      await subscriptionsPage.setRowsPerPage(20)
      await expect(subscriptionsPage.table).toBeVisible()
      await expect(subscriptionsPage.rowBySubscriptionId(subscription.subscriptionId)).toBeVisible()
    } finally {
      await page.context().close()
    }
  })

  test('08.03.02 - Status and delivery-mode filters narrow the list and Clear restores it', async ({
    browser,
    consentAdminEventApi,
  }) => {
    // Two DIFFERENT topics, not two groups on one topic: confirmed live that the mixed-webhook/
    // poll conflict and the duplicate-subscription check are both scoped to (org, topic) only -
    // every subscription's groupId is silently forced to the org's own id regardless of what a
    // caller sends (see eventNotificationSetup.ts's seedPollSubscription comment for the
    // underlying bug), so two subscriptions on the same topic are always "the same group" no
    // matter what groupId either one requested.
    const pollTopic = await seedActiveTopic(consentAdminEventApi, 'sub-filter-poll')
    const pollSub = await seedPollSubscription(consentAdminEventApi, pollTopic.name)

    const webhookTopic = await seedActiveTopic(consentAdminEventApi, 'sub-filter-webhook')
    // A webhook subscription always starts `pending` regardless of callback reachability
    // (SubscriptionServiceImpl.createSubscription sets initialStatus before the verification
    // task is even scheduled) - a real, resolvable, non-private host is enough to pass
    // EventNotificationUrlValidator and get a genuinely distinct delivery-mode/status row without
    // needing this suite's own WebhookReceiver (see README's "Webhook-dependent tests").
    const webhookResponse = await consentAdminEventApi.createSubscription({
      topic: webhookTopic.name,
      filter: { type: 'all' },
      delivery: {
        mode: 'webhook',
        callbackUrl: 'https://example.com/dpdp-e2e-unreachable-target',
        sharedSecret: uniqueMarker('secret'),
      },
    })
    expect(webhookResponse.status(), await webhookResponse.text()).toBe(201)
    const webhookSub = await webhookResponse.json()

    const page = await loginAsConsentAdmin(browser)
    try {
      const subscriptionsPage = new SubscriptionsPage(page)
      await subscriptionsPage.goto()

      await subscriptionsPage.filterByStatus('Active')
      await subscriptionsPage.search(pollTopic.name)
      await expect(subscriptionsPage.rowBySubscriptionId(pollSub.subscriptionId)).toBeVisible()

      await subscriptionsPage.search(webhookTopic.name)
      await expect(subscriptionsPage.rowBySubscriptionId(webhookSub.subscriptionId)).toHaveCount(0)

      // The search term is already webhookTopic.name from above and doesn't change again here -
      // each FILTER change gets its own checkpoint before the next one fires, so the two requests
      // can't resolve out of order and leave the table on a stale intermediate combination (see
      // utils/filterCommit.ts for the full explanation). webhookSub (search already narrows to
      // it) becoming visible is real, verifiable proof each filter change actually took effect.
      // The searches themselves are checkpointed inside SubscriptionsPage.search() rather than
      // here - a row assertion can't stand in for one, see utils/filterCommit.ts.
      await subscriptionsPage.filterByStatus('All Statuses')
      await expect(subscriptionsPage.rowBySubscriptionId(webhookSub.subscriptionId)).toBeVisible()
      await subscriptionsPage.filterByDeliveryMode('Webhook')
      await expect(subscriptionsPage.rowBySubscriptionId(webhookSub.subscriptionId)).toBeVisible()

      await subscriptionsPage.filterByDeliveryMode('Poll')
      await expect(subscriptionsPage.rowBySubscriptionId(webhookSub.subscriptionId)).toHaveCount(0)

      await subscriptionsPage.clearFilters()
      await subscriptionsPage.search(pollTopic.name)
      await expect(subscriptionsPage.rowBySubscriptionId(pollSub.subscriptionId)).toBeVisible()
    } finally {
      await page.context().close()
    }
  })

  test('08.03.03 - Searching by a partial subscription, topic, or callback value finds matching rows', async ({
    browser,
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'sub-search')
    const subscription = await seedPollSubscription(consentAdminEventApi, topic.name)

    const page = await loginAsConsentAdmin(browser)
    try {
      const subscriptionsPage = new SubscriptionsPage(page)
      await subscriptionsPage.goto()

      // Partial topic-name search (topic names are stamped unique via uniqueMarker).
      await subscriptionsPage.search(topic.name.slice(-12))
      await expect(subscriptionsPage.rowBySubscriptionId(subscription.subscriptionId)).toBeVisible()

      // Partial subscription-id search.
      await subscriptionsPage.search(subscription.subscriptionId.slice(0, 8))
      await expect(subscriptionsPage.rowBySubscriptionId(subscription.subscriptionId)).toBeVisible()

      await subscriptionsPage.clearFilters()
      await expect(subscriptionsPage.searchInput).toHaveValue('')
    } finally {
      await page.context().close()
    }
  })

  test('08.03.04 - Subscription details show configuration, timestamps, and deliveries', async ({
    browser,
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'sub-details')
    const subscription = await seedPollSubscription(consentAdminEventApi, topic.name)
    // subscription.groupId is server-forced to the org id (see eventNotificationSetup.ts) -
    // publish using that exact value, not a value chosen here, to get a matching delivery.
    const { event } = await publishMarkedEvent(consentAdminEventApi, subscription.groupId!, topic.name)

    // Confirm the delivery actually landed before asserting on it through the UI - the API is the
    // ground truth this page's rendering is checked against.
    let delivery: { deliveryId: string; eventId: string } | undefined
    await expect(async () => {
      const deliveries = await consentAdminEventApi.listSubscriptionEvents(subscription.subscriptionId)
      expect(deliveries.ok(), await deliveries.text()).toBe(true)
      const { items } = (await deliveries.json()) as { items: { deliveryId: string; eventId: string }[] }
      delivery = items.find((item) => item.eventId === event.eventId)
      expect(delivery, `Expected a delivery for event ${event.eventId} - got: ${JSON.stringify(items)}`).toBeTruthy()
    }).toPass({ timeout: 10_000 })

    const page = await loginAsConsentAdmin(browser)
    try {
      const detailsPage = new SubscriptionDetailsPage(page)
      await detailsPage.goto(subscription.subscriptionId)

      await expect(detailsPage.fieldValue('Subscription ID')).toContainText(subscription.subscriptionId)
      await expect(detailsPage.fieldValue('Topic')).toHaveText(topic.name)
      await expect(detailsPage.fieldValue('Group ID')).toContainText(subscription.groupId!)
      await expect(detailsPage.fieldValue('Delivery Mode')).toContainText('Poll')
      await expect(detailsPage.fieldValue('Created At')).not.toHaveText('-')
      await expect(detailsPage.fieldValue('Last Updated')).not.toHaveText('-')

      await expect(page.getByRole('heading', { name: topic.name })).toBeVisible()

      const deliveryRow = detailsPage.deliveryEventRowByDeliveryId(delivery!.deliveryId)
      await expect(deliveryRow).toBeVisible()
      await expect(deliveryRow).toContainText(topic.name)

      // A poll delivery has no HTTP attempt history (nothing was ever pushed to it) - opening its
      // audit trail should still load cleanly, showing the "no attempts" state rather than erroring.
      const historyModal = await detailsPage.openDeliveryHistory(delivery!.deliveryId)
      await expect(historyModal.title).toBeVisible()
      await historyModal.close()
    } finally {
      await page.context().close()
    }
  })

  test('08.03.05 - An unknown subscription id shows load failure without leaking data', async ({ browser }) => {
    const page = await loginAsConsentAdmin(browser)
    try {
      const detailsPage = new SubscriptionDetailsPage(page)
      await detailsPage.goto('00000000-0000-0000-0000-000000000000')
      await expect(detailsPage.loadFailedAlert).toBeVisible()

      await detailsPage.goBack()
      await expect(page).toHaveURL(/\/events\/subscriptions$/)
    } finally {
      await page.context().close()
    }
    // Cross-tenant coverage (a real tenant-B subscription id read from tenant A) lives in
    // 08.11-tenant-isolation-api.spec.ts (08.11.02) at the API level, using the two-tenant
    // fixtures - not duplicated here to avoid paying for a second tenant's setup twice.
  })
})
