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
import type { SubscriptionDeliveryRecord } from '../../clients/EventNotificationApiClient'
import { seedActiveTopicViaApi, seedPollSubscriptionViaApi, publishMarkedEventViaApi } from '../../utils/eventNotificationSetup'
import { uniqueMarker } from '../../utils/testData'

/**
 * `POST /events` (EventEndpoint.publishEvent, backed by EventPublishServiceImpl#publishEvent) -
 * real API calls against a real deployment, no UI (there is no publish-event screen anywhere in
 * the portal, see AGENTS.md).
 *
 * Every subscription here is POLL-mode (see utils/eventNotificationSetup.ts's seedPollSubscriptionViaApi):
 * fan-out matching itself has nothing to do with delivery transport, and POLL needs no callback
 * URL/webhook receiver at all. Fan-out is matched by exact SQL equality on
 * `(ORG_ID, GROUP_ID, TOPIC_ID)` (EventNotificationCommonDBQueries) - `SubscriptionHandler
 * .createSubscription` silently ignores whatever `groupId` a caller sends and always forces it to
 * the org id (see seedPollSubscriptionViaApi's own comment for the full story), so every test below
 * reads the subscription's *returned* `groupId` back and publishes with that exact value, rather
 * than inventing its own.
 */
test.describe('Publisher publishing events', () => {
  test('09.08.01 - Publishing an event creates matching delivery records atomically', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopicViaApi(consentAdminEventApi, 'atomic')
    const subscription = await seedPollSubscriptionViaApi(consentAdminEventApi, topic.name, { type: 'all' })
    const groupId = subscription.groupId!

    const { event, marker } = await publishMarkedEventViaApi(consentAdminEventApi, groupId, topic.name, [
      'account-management',
    ])
    expect(typeof event.eventId).toBe('string')
    expect(event.eventId.length).toBeGreaterThan(0)

    // The event itself is readable straight away.
    const eventResponse = await consentAdminEventApi.getEvent(event.eventId)
    expect(eventResponse.ok()).toBe(true)
    const eventBody = await eventResponse.json()
    expect(eventBody.eventId).toBe(event.eventId)
    expect((JSON.parse(eventBody.payload as string) as { marker: string }).marker).toBe(marker)

    // ...and so is the delivery it fanned out to, from both the event side and the subscription side.
    const deliveriesResponse = await consentAdminEventApi.getEventDeliveries(event.eventId)
    expect(deliveriesResponse.ok()).toBe(true)
    const { items: deliveries } = (await deliveriesResponse.json()) as { items: SubscriptionDeliveryRecord[] }
    expect(deliveries.some((delivery) => delivery.subscriptionId === subscription.subscriptionId)).toBe(true)

    const subscriptionEventsResponse = await consentAdminEventApi.listSubscriptionEvents(subscription.subscriptionId)
    expect(subscriptionEventsResponse.ok()).toBe(true)
    const { items: subscriptionDeliveries } = (await subscriptionEventsResponse.json()) as {
      items: SubscriptionDeliveryRecord[]
    }
    expect(subscriptionDeliveries.some((delivery) => delivery.eventId === event.eventId)).toBe(true)
  })

  test('09.08.02 - Publishing without a group-id header is rejected', async ({ consentAdminEventApi }) => {
    const topic = await seedActiveTopicViaApi(consentAdminEventApi, 'no-group-id')
    // An empty header value hits the exact same `groupId == null || groupId.trim().isEmpty()`
    // check the server uses for a genuinely absent header (EventPublishServiceImpl#publishEvent).
    const response = await consentAdminEventApi.publishEvent('', { topic: topic.name, payload: { ok: true } })
    expect(response.status()).toBe(400)
    const body = await response.json()
    expect(body.code).toBe('EN-4001')
  })

  test('09.08.03 - Publishing to an unknown or deleted topic is rejected', async ({ consentAdminEventApi }) => {
    const groupId = uniqueMarker('group')

    const unknownResponse = await consentAdminEventApi.publishEvent(groupId, {
      topic: uniqueMarker('no-such-topic'),
      payload: { ok: true },
    })
    expect(unknownResponse.status()).toBe(404)
    expect((await unknownResponse.json()).code).toBe('EN-4041')

    const topic = await seedActiveTopicViaApi(consentAdminEventApi, 'to-delete')
    const deleteResponse = await consentAdminEventApi.deleteTopic(topic.topicId)
    expect(deleteResponse.status()).toBe(200)

    const deletedResponse = await consentAdminEventApi.publishEvent(groupId, {
      topic: topic.name,
      payload: { ok: true },
    })
    expect(deletedResponse.status()).toBe(404)
    expect((await deletedResponse.json()).code).toBe('EN-4041')
  })

  test('09.08.04 - A null or missing payload is rejected rather than treated as an empty object', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopicViaApi(consentAdminEventApi, 'null-payload')
    const groupId = uniqueMarker('group')

    const nullPayloadResponse = await consentAdminEventApi.publishEvent(groupId, {
      topic: topic.name,
      payload: null as unknown as Record<string, unknown>,
    })
    expect(nullPayloadResponse.status()).toBe(422)
    expect((await nullPayloadResponse.json()).code).toBe('EN-4002')

    const missingPayloadResponse = await consentAdminEventApi.publishEvent(groupId, {
      topic: topic.name,
    } as unknown as { topic: string; payload: Record<string, unknown> })
    expect(missingPayloadResponse.status()).toBe(422)
    expect((await missingPayloadResponse.json()).code).toBe('EN-4002')
  })

  test('09.08.05 - An ALL-filter subscription receives every event regardless of purposes', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopicViaApi(consentAdminEventApi, 'all-filter')
    const subscription = await seedPollSubscriptionViaApi(consentAdminEventApi, topic.name, { type: 'all' })
    const groupId = subscription.groupId!

    for (const purposes of [[], ['account'], ['account', 'profile']]) {
      const { event } = await publishMarkedEventViaApi(consentAdminEventApi, groupId, topic.name, purposes)
      const deliveriesResponse = await consentAdminEventApi.getEventDeliveries(event.eventId)
      const { items: deliveries } = (await deliveriesResponse.json()) as { items: SubscriptionDeliveryRecord[] }
      const matching = deliveries.filter((delivery) => delivery.subscriptionId === subscription.subscriptionId)
      expect(matching, `purposes=${JSON.stringify(purposes)}`).toHaveLength(1)
    }
  })

  test('09.08.06 - SPECIFIC purpose matching is case-insensitive and requires overlap', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopicViaApi(consentAdminEventApi, 'specific-filter')
    const subscription = await seedPollSubscriptionViaApi(consentAdminEventApi, topic.name, {
      type: 'specific',
      purposes: ['Account-Management'],
    })
    const groupId = subscription.groupId!

    const { event: overlapping } = await publishMarkedEventViaApi(consentAdminEventApi, groupId, topic.name, [
      'account-management',
      'marketing',
    ])
    const overlappingDeliveries = (
      (await (await consentAdminEventApi.getEventDeliveries(overlapping.eventId)).json()) as {
        items: SubscriptionDeliveryRecord[]
      }
    ).items
    expect(overlappingDeliveries.some((delivery) => delivery.subscriptionId === subscription.subscriptionId)).toBe(
      true,
    )

    const { event: unrelated } = await publishMarkedEventViaApi(consentAdminEventApi, groupId, topic.name, ['marketing'])
    const unrelatedDeliveries = (
      (await (await consentAdminEventApi.getEventDeliveries(unrelated.eventId)).json()) as {
        items: SubscriptionDeliveryRecord[]
      }
    ).items
    expect(unrelatedDeliveries.some((delivery) => delivery.subscriptionId === subscription.subscriptionId)).toBe(
      false,
    )
  })

  test('09.08.07 - ALL_EXCEPT matches only when the event carries a purpose outside the exclusion set', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopicViaApi(consentAdminEventApi, 'all-except-filter')
    const subscription = await seedPollSubscriptionViaApi(consentAdminEventApi, topic.name, {
      type: 'all_except',
      purposes: ['marketing'],
    })
    const groupId = subscription.groupId!

    const { event: excludedOnly } = await publishMarkedEventViaApi(consentAdminEventApi, groupId, topic.name, [
      'marketing',
    ])
    const excludedOnlyDeliveries = (
      (await (await consentAdminEventApi.getEventDeliveries(excludedOnly.eventId)).json()) as {
        items: SubscriptionDeliveryRecord[]
      }
    ).items
    expect(excludedOnlyDeliveries.some((delivery) => delivery.subscriptionId === subscription.subscriptionId)).toBe(
      false,
    )

    const { event: mixed } = await publishMarkedEventViaApi(consentAdminEventApi, groupId, topic.name, [
      'marketing',
      'account-management',
    ])
    const mixedDeliveries = (
      (await (await consentAdminEventApi.getEventDeliveries(mixed.eventId)).json()) as {
        items: SubscriptionDeliveryRecord[]
      }
    ).items
    expect(mixedDeliveries.some((delivery) => delivery.subscriptionId === subscription.subscriptionId)).toBe(true)
  })

  test('09.08.08 - A multi-topic subscription receives fan-out deliveries across all registered topics', async ({
    consentAdminEventApi,
  }) => {
    const topicA = await seedActiveTopicViaApi(consentAdminEventApi, 'multi-topic-a')
    const topicB = await seedActiveTopicViaApi(consentAdminEventApi, 'multi-topic-b')

    const response = await consentAdminEventApi.createSubscription({
      name: uniqueMarker('multi-sub'),
      topics: [topicA.name, topicB.name],
      filter: { type: 'all' },
      delivery: { mode: 'poll', sharedSecret: uniqueMarker('secret') },
    })
    expect(response.status(), await response.text()).toBe(201)
    const subscription = (await response.json()) as { subscriptionId: string; groupId: string; topics?: string[] }
    expect(subscription.topics).toEqual(expect.arrayContaining([topicA.name, topicB.name]))

    const groupId = subscription.groupId

    // Event on topicA delivers to this subscription
    const { event: eventA } = await publishMarkedEventViaApi(consentAdminEventApi, groupId, topicA.name)
    const deliveriesA = (
      (await (await consentAdminEventApi.getEventDeliveries(eventA.eventId)).json()) as {
        items: SubscriptionDeliveryRecord[]
      }
    ).items
    expect(deliveriesA.some((delivery) => delivery.subscriptionId === subscription.subscriptionId)).toBe(true)

    // Event on topicB delivers to the very same subscription
    const { event: eventB } = await publishMarkedEventViaApi(consentAdminEventApi, groupId, topicB.name)
    const deliveriesB = (
      (await (await consentAdminEventApi.getEventDeliveries(eventB.eventId)).json()) as {
        items: SubscriptionDeliveryRecord[]
      }
    ).items
    expect(deliveriesB.some((delivery) => delivery.subscriptionId === subscription.subscriptionId)).toBe(true)

    // Subscription's own event history reflects deliveries from both topics
    const subscriptionEvents = (
      (await (await consentAdminEventApi.listSubscriptionEvents(subscription.subscriptionId)).json()) as {
        items: SubscriptionDeliveryRecord[]
      }
    ).items
    expect(subscriptionEvents.some((delivery) => delivery.eventId === eventA.eventId)).toBe(true)
    expect(subscriptionEvents.some((delivery) => delivery.eventId === eventB.eventId)).toBe(true)
  })
})
