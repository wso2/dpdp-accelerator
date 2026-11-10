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

import { test, expect } from '../../fixtures/auth.fixtures'
import { isSubscriptionConstraintChecksEnabled } from '../../utils/env'
import {
  findDeliveryForEvent,
  publishMarkedEventViaApi,
  seedActiveTopicViaApi,
  seedPollSubscriptionViaApi,
} from '../../utils/eventNotificationSetup'
import { uniqueMarker } from '../../utils/testData'

/**
 * Server-side subscription lifecycle rules (SubscriptionServiceImpl / SubscriptionDAOImpl):
 * overlapping-purpose and mixed-delivery-mode handling on register, what re-verification allows, and
 * what blocks a delete. API-only - SubscriptionRegisterDialog.tsx can register and
 * SubscriptionDeleteDialog.tsx can delete (both exercised via 08.03), but neither surfaces these
 * rules.
 *
 * The webhook-verification-dependent tests that used to sit alongside these (register+verify,
 * callback-URL validation, webhook intent verification, and re-verifying a stale subscription)
 * were removed: they need a network-reachable receiver and were unreliable on a machine whose LAN
 * IP changes mid-session. See TEST-SCENARIOS.md for the resulting gap.
 */
for (const version of ['v1', 'v1.1'] as const) {
  test.describe(`Subscription lifecycle rules (${version})`, () => {
    test.use({ eventApiVersion: version })

    test(`09.07.01 - Overlapping subscriptions with equivalent purposes and callback URL follow the constraint-check setting (${version})`, async ({
      consentAdminEventApi,
    }) => {
      const topic = await seedActiveTopicViaApi(consentAdminEventApi, `duplicate-check-${version}`)
      const callbackUrl = `https://Example.com/${uniqueMarker('hook')}`
      const first = await consentAdminEventApi.createSubscription({
        name: uniqueMarker('sub'),
        topic: topic.name,
        filter: { type: 'specific', purposes: ['Account', 'Profile'] },
        delivery: { mode: 'webhook', callbackUrl, sharedSecret: uniqueMarker('secret') },
      })
      expect(first.status()).toBe(201)

      // Same host with different casing, same purposes with different order/casing/duplicates -
      // CallbackUrlCanonicalizer/PurposeOverlapUtils treat these as equivalent to the original.
      const duplicate = await consentAdminEventApi.createSubscription({
        name: uniqueMarker('sub'),
        topic: topic.name,
        filter: { type: 'specific', purposes: ['profile', 'ACCOUNT', 'account'] },
        delivery: { mode: 'webhook', callbackUrl: callbackUrl.toLowerCase(), sharedSecret: uniqueMarker('secret') },
      })
      const constraintsEnabled = isSubscriptionConstraintChecksEnabled()
      expect(duplicate.status()).toBe(constraintsEnabled ? 409 : 201)
    })

    test(`09.07.02 - Mixing webhook and poll delivery modes on the same tenant/group/topic follows the constraint-check setting (${version})`, async ({
      consentAdminEventApi,
    }) => {
      const topicA = await seedActiveTopicViaApi(consentAdminEventApi, `mixed-mode-a-${version}`)
      const groupA = uniqueMarker('group')
      const webhookFirst = await consentAdminEventApi.createSubscription({
        name: uniqueMarker('sub'),
        topic: topicA.name,
        groupId: groupA,
        filter: { type: 'all' },
        delivery: { mode: 'webhook', callbackUrl: 'https://example.com/hook-a', sharedSecret: uniqueMarker('secret') },
      })
      expect(webhookFirst.status()).toBe(201)
      const pollConflict = await consentAdminEventApi.createSubscription({
        name: uniqueMarker('sub'),
        topic: topicA.name,
        groupId: groupA,
        filter: { type: 'all' },
        delivery: { mode: 'poll', sharedSecret: uniqueMarker('secret') },
      })
      const constraintsEnabled = isSubscriptionConstraintChecksEnabled()
      expect(pollConflict.status()).toBe(constraintsEnabled ? 409 : 201)

      const topicB = await seedActiveTopicViaApi(consentAdminEventApi, `mixed-mode-b-${version}`)
      const groupB = uniqueMarker('group')
      const pollFirst = await consentAdminEventApi.createSubscription({
        name: uniqueMarker('sub'),
        topic: topicB.name,
        groupId: groupB,
        filter: { type: 'all' },
        delivery: { mode: 'poll', sharedSecret: uniqueMarker('secret') },
      })
      expect(pollFirst.status()).toBe(201)
      const webhookConflict = await consentAdminEventApi.createSubscription({
        name: uniqueMarker('sub'),
        topic: topicB.name,
        groupId: groupB,
        filter: { type: 'all' },
        delivery: { mode: 'webhook', callbackUrl: 'https://example.com/hook-b', sharedSecret: uniqueMarker('secret') },
      })
      expect(webhookConflict.status()).toBe(constraintsEnabled ? 409 : 201)
    })

    test(`09.07.03 - An active or deleted subscription cannot be re-verified (${version})`, async ({ consentAdminEventApi }) => {
      // Two separate topics, not one shared topic: every subscription's groupId is currently
      // forced to the org's own id server-side (see eventNotificationSetup.ts), so two
      // same-filter poll subscriptions on the very same topic would collide as duplicates.
      const activeTopic = await seedActiveTopicViaApi(consentAdminEventApi, `sub-reverify-invalid-active-${version}`)
      const activeSub = await seedPollSubscriptionViaApi(consentAdminEventApi, activeTopic.name)

      const activeVerify = await consentAdminEventApi.verifySubscription(activeSub.subscriptionId)
      expect(activeVerify.status()).toBe(409)
      expect((await activeVerify.json()).code).toBe('EN-4003')

      const deletedTopic = await seedActiveTopicViaApi(consentAdminEventApi, `sub-reverify-invalid-deleted-${version}`)
      const toDelete = await seedPollSubscriptionViaApi(consentAdminEventApi, deletedTopic.name)
      const deleteResponse = await consentAdminEventApi.deleteSubscription(toDelete.subscriptionId)
      expect(deleteResponse.status()).toBe(200)

      const deletedVerify = await consentAdminEventApi.verifySubscription(toDelete.subscriptionId)
      expect(deletedVerify.status()).toBe(404)
    })

    test(`09.07.04 - Deleting a subscription soft-deletes it while preserving its record (${version})`, async ({
      consentAdminEventApi,
    }) => {
      const topic = await seedActiveTopicViaApi(consentAdminEventApi, `sub-delete-${version}`)
      // No deliveries at all - deleting is allowed with either completed or no deliveries, and no
      // deliveries is the simplest of the two to seed reliably.
      const subscription = await seedPollSubscriptionViaApi(consentAdminEventApi, topic.name)

      const deleteResponse = await consentAdminEventApi.deleteSubscription(subscription.subscriptionId)
      expect(deleteResponse.status(), await deleteResponse.text()).toBe(200)
      expect((await deleteResponse.json()).status).toBe('deleted')

      // Status changed, but the record itself (and its would-be audit trail) is still readable -
      // "deleted" is a state, not row removal.
      const getResponse = await consentAdminEventApi.getSubscription(subscription.subscriptionId)
      expect(getResponse.ok()).toBe(true)
      expect((await getResponse.json()).status).toBe('deleted')

      const eventsResponse = await consentAdminEventApi.listSubscriptionEvents(subscription.subscriptionId)
      expect(eventsResponse.ok()).toBe(true)
    })

    test(`09.07.05 - A subscription with a pending delivery cannot be deleted (${version})`, async ({ consentAdminEventApi }) => {
      // A POLL subscription's own delivery stays `pending` until it's consumed via the poll
      // endpoint (SubscriptionServiceImpl.deleteSubscription checks
      // subscriptionDAO.hasPendingOrInFlightDeliveries) - the reliable, receiver-free way to get a
      // delivery stuck `pending` for this test.
      const topic = await seedActiveTopicViaApi(consentAdminEventApi, `sub-delete-blocked-${version}`)
      const subscription = await seedPollSubscriptionViaApi(consentAdminEventApi, topic.name)
      // subscription.groupId is server-forced to the org id (see eventNotificationSetup.ts) -
      // publish using that exact value to get a matching delivery.
      await publishMarkedEventViaApi(consentAdminEventApi, subscription.groupId!, topic.name)

      await expect(async () => {
        const eventsResponse = await consentAdminEventApi.listSubscriptionEvents(subscription.subscriptionId)
        const { items } = (await eventsResponse.json()) as { items: unknown[] }
        expect(items.length).toBeGreaterThan(0)
      }).toPass({ timeout: 10_000 })

      const deleteResponse = await consentAdminEventApi.deleteSubscription(subscription.subscriptionId)
      expect(deleteResponse.status()).toBe(409)
      expect((await deleteResponse.json()).code).toBe('EN-4090')

      // Unchanged - still active, delivery still pending.
      const getResponse = await consentAdminEventApi.getSubscription(subscription.subscriptionId)
      expect((await getResponse.json()).status).toBe('active')
    })

    test(`09.07.06 - Deleting an already-deleted subscription returns not found (${version})`, async ({ consentAdminEventApi }) => {
      const topic = await seedActiveTopicViaApi(consentAdminEventApi, `sub-delete-twice-${version}`)
      const subscription = await seedPollSubscriptionViaApi(consentAdminEventApi, topic.name)

      const firstDelete = await consentAdminEventApi.deleteSubscription(subscription.subscriptionId)
      expect(firstDelete.status()).toBe(200)

      const secondDelete = await consentAdminEventApi.deleteSubscription(subscription.subscriptionId)
      expect(secondDelete.status()).toBe(404)
    })

    test(`09.07.07 - Registering with empty topics or duplicate topic names is rejected (${version})`, async ({
      consentAdminEventApi,
    }) => {
      const emptyResponse = await consentAdminEventApi.createSubscription({
        name: uniqueMarker('sub'),
        topics: [],
        filter: { type: 'all' },
        delivery: { mode: 'poll', sharedSecret: uniqueMarker('secret') },
      })
      expect(emptyResponse.status()).toBe(400)
      expect((await emptyResponse.json()).description).toContain('Supply between 1 and')

      const topic = await seedActiveTopicViaApi(consentAdminEventApi, `sub-dupe-topics-${version}`)
      const duplicateNamesResponse = await consentAdminEventApi.createSubscription({
        name: uniqueMarker('sub'),
        topics: [topic.name, topic.name.toUpperCase()],
        filter: { type: 'all' },
        delivery: { mode: 'poll', sharedSecret: uniqueMarker('secret') },
      })
      expect(duplicateNamesResponse.status()).toBe(400)
      expect((await duplicateNamesResponse.json()).description).toContain('Topics must contain unique non-blank names')
    })

    test(`09.07.08 - Registering with a non-existent or inactive topic is rejected (${version})`, async ({
      consentAdminEventApi,
    }) => {
      const activeTopic = await seedActiveTopicViaApi(consentAdminEventApi, `sub-valid-topic-${version}`)
      const response = await consentAdminEventApi.createSubscription({
        name: uniqueMarker('sub'),
        topics: [activeTopic.name, 'non.existent.topic.' + uniqueMarker('bad')],
        filter: { type: 'all' },
        delivery: { mode: 'poll', sharedSecret: uniqueMarker('secret') },
      })
      expect(response.status()).toBe(404)
      expect((await response.json()).description).toContain('A requested topic is not active in this organization')
    })

    test(`09.07.09 - Multi-topic subscriptions containing user lifecycle topics require the all purpose filter (${version})`, async ({
      consentAdminEventApi,
    }) => {
      // user.data.change is shared by every run in the org; running both API versions in
      // parallel makes their overlapping subscriptions conflict (409) and delete each other.
      test.skip(version !== 'v1', 'Shares user.data.change with the v1 run - cannot run in parallel')

      // Clean up any lingering subscription from earlier runs that covers user.data.change
      const existingList = await consentAdminEventApi.listSubscriptions({ limit: 100 })
      if (existingList.ok()) {
        const { items } = (await existingList.json()) as { items?: { subscriptionId: string; topic?: string; topics?: string[] }[] }
        for (const item of items ?? []) {
          if (item.topics?.includes('user.data.change') || item.topic === 'user.data.change') {
            await consentAdminEventApi.deleteSubscription(item.subscriptionId)
          }
        }
      }

      const customTopic = await seedActiveTopicViaApi(consentAdminEventApi, `user-lifecycle-mix-${version}`)
      // user.data.change is a system-seeded user lifecycle topic present in every tenant
      const rejected = await consentAdminEventApi.createSubscription({
        name: uniqueMarker('sub'),
        topics: ['user.data.change', customTopic.name],
        filter: { type: 'specific', purposes: ['marketing'] },
        delivery: { mode: 'poll', sharedSecret: uniqueMarker('secret') },
      })
      expect(rejected.status()).toBe(422)
      expect((await rejected.json()).description).toContain(
        'Subscriptions containing user lifecycle topics require the all purpose filter',
      )

      const accepted = await consentAdminEventApi.createSubscription({
        name: uniqueMarker('sub'),
        topics: ['user.data.change', customTopic.name],
        filter: { type: 'all' },
        delivery: { mode: 'poll', sharedSecret: uniqueMarker('secret') },
      })
      expect(accepted.status(), await accepted.text()).toBe(201)
      await consentAdminEventApi.deleteSubscription((await accepted.json()).subscriptionId)
    })

    test(`09.07.10 - Mixing delivery modes across a shared topic in a multi-topic subscription follows the constraint-check setting (${version})`, async ({
      consentAdminEventApi,
    }) => {
      const topicA = await seedActiveTopicViaApi(consentAdminEventApi, `overlap-a-${version}`)
      const topicB = await seedActiveTopicViaApi(consentAdminEventApi, `overlap-b-${version}`)
      const topicC = await seedActiveTopicViaApi(consentAdminEventApi, `overlap-c-${version}`)

      const webhookResponse = await consentAdminEventApi.createSubscription({
        name: uniqueMarker('sub-hook'),
        topics: [topicA.name, topicB.name],
        filter: { type: 'all' },
        delivery: {
          mode: 'webhook',
          callbackUrl: `https://example.com/${uniqueMarker('hook')}`,
          sharedSecret: uniqueMarker('secret'),
        },
      })
      expect(webhookResponse.status()).toBe(201)

      // Second subscription shares topicB with the webhook subscription above; rejected in poll mode only if constraint checks are enabled
      const pollConflict = await consentAdminEventApi.createSubscription({
        name: uniqueMarker('sub-poll'),
        topics: [topicB.name, topicC.name],
        filter: { type: 'all' },
        delivery: { mode: 'poll', sharedSecret: uniqueMarker('secret') },
      })
      const constraintsEnabled = isSubscriptionConstraintChecksEnabled()
      expect(pollConflict.status()).toBe(constraintsEnabled ? 409 : 201)
    })

    test(`09.07.11 - Multiple poll subscriptions on the same topic each receive their own delivery for a published event (${version})`, async ({
      consentAdminEventApi,
    }) => {
      test.skip(
        isSubscriptionConstraintChecksEnabled(),
        'Requires subscription_constraint_checks_enabled = false so multiple poll subscriptions on the same topic are allowed',
      )
      const topic = await seedActiveTopicViaApi(consentAdminEventApi, `multi-poll-${version}`)
      const firstSub = await seedPollSubscriptionViaApi(consentAdminEventApi, topic.name)
      const secondSub = await seedPollSubscriptionViaApi(consentAdminEventApi, topic.name)

      const { event } = await publishMarkedEventViaApi(consentAdminEventApi, firstSub.groupId, topic.name)
      const firstDelivery = await findDeliveryForEvent(consentAdminEventApi, firstSub.subscriptionId, event.eventId)
      const secondDelivery = await findDeliveryForEvent(consentAdminEventApi, secondSub.subscriptionId, event.eventId)
      expect(firstDelivery.deliveryId).not.toBe(secondDelivery.deliveryId)
    })
  })
}
