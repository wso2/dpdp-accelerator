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
import { seedActiveTopic, seedPollSubscription, publishMarkedEvent } from '../../utils/eventNotificationSetup'
import { uniqueMarker } from '../../utils/testData'

/**
 * `POST /events` (EventEndpoint.publishEvent, backed by EventPublishServiceImpl#publishEvent) -
 * real API calls against a real deployment, no UI (there is no publish-event screen anywhere in
 * the portal, see AGENTS.md).
 *
 * Every subscription here is POLL-mode (see utils/eventNotificationSetup.ts's seedPollSubscription):
 * fan-out matching itself has nothing to do with delivery transport, and POLL needs no callback
 * URL/webhook receiver at all. Fan-out is matched by exact SQL equality on
 * `(ORG_ID, GROUP_ID, TOPIC_ID)` (EventNotificationCommonDBQueries) - `SubscriptionHandler
 * .createSubscription` silently ignores whatever `groupId` a caller sends and always forces it to
 * the org id (see seedPollSubscription's own comment for the full story), so every test below
 * reads the subscription's *returned* `groupId` back and publishes with that exact value, rather
 * than inventing its own.
 */
test.describe('Publisher publishing events', () => {
  test('08.08.01 - Publishing an event creates matching delivery records atomically', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'atomic')
    const subscription = await seedPollSubscription(consentAdminEventApi, topic.name, { type: 'all' })
    const groupId = subscription.groupId!

    const { event, marker } = await publishMarkedEvent(consentAdminEventApi, groupId, topic.name, [
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

  test('08.08.02 - Publishing without a group-id header is rejected', async ({ consentAdminEventApi }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'no-group-id')
    // An empty header value hits the exact same `groupId == null || groupId.trim().isEmpty()`
    // check the server uses for a genuinely absent header (EventPublishServiceImpl#publishEvent).
    const response = await consentAdminEventApi.publishEvent('', { topic: topic.name, payload: { ok: true } })
    expect(response.status()).toBe(400)
    const body = await response.json()
    expect(body.code).toBe('EN-4001')
  })

  test('08.08.03 - Publishing to an unknown or deregistered topic is rejected', async ({ consentAdminEventApi }) => {
    const groupId = uniqueMarker('group')

    const unknownResponse = await consentAdminEventApi.publishEvent(groupId, {
      topic: uniqueMarker('no-such-topic'),
      payload: { ok: true },
    })
    expect(unknownResponse.status()).toBe(404)
    expect((await unknownResponse.json()).code).toBe('EN-4041')

    const topic = await seedActiveTopic(consentAdminEventApi, 'to-deregister')
    const deregisterResponse = await consentAdminEventApi.deleteTopic(topic.topicId)
    expect(deregisterResponse.status()).toBe(200)

    const deregisteredResponse = await consentAdminEventApi.publishEvent(groupId, {
      topic: topic.name,
      payload: { ok: true },
    })
    expect(deregisteredResponse.status()).toBe(404)
    expect((await deregisteredResponse.json()).code).toBe('EN-4041')
  })

  test('08.08.04 - A null or missing payload is rejected rather than treated as an empty object', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'null-payload')
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

  test('08.08.05 - An ALL-filter subscription receives every event regardless of purposes', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'all-filter')
    const subscription = await seedPollSubscription(consentAdminEventApi, topic.name, { type: 'all' })
    const groupId = subscription.groupId!

    for (const purposes of [[], ['account'], ['account', 'profile']]) {
      const { event } = await publishMarkedEvent(consentAdminEventApi, groupId, topic.name, purposes)
      const deliveriesResponse = await consentAdminEventApi.getEventDeliveries(event.eventId)
      const { items: deliveries } = (await deliveriesResponse.json()) as { items: SubscriptionDeliveryRecord[] }
      const matching = deliveries.filter((delivery) => delivery.subscriptionId === subscription.subscriptionId)
      expect(matching, `purposes=${JSON.stringify(purposes)}`).toHaveLength(1)
    }
  })

  test('08.08.06 - SPECIFIC purpose matching is case-insensitive and requires overlap', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'specific-filter')
    const subscription = await seedPollSubscription(consentAdminEventApi, topic.name, {
      type: 'specific',
      purposes: ['Account-Management'],
    })
    const groupId = subscription.groupId!

    const { event: overlapping } = await publishMarkedEvent(consentAdminEventApi, groupId, topic.name, [
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

    const { event: unrelated } = await publishMarkedEvent(consentAdminEventApi, groupId, topic.name, ['marketing'])
    const unrelatedDeliveries = (
      (await (await consentAdminEventApi.getEventDeliveries(unrelated.eventId)).json()) as {
        items: SubscriptionDeliveryRecord[]
      }
    ).items
    expect(unrelatedDeliveries.some((delivery) => delivery.subscriptionId === subscription.subscriptionId)).toBe(
      false,
    )
  })

  test('08.08.07 - ALL_EXCEPT matches only when the event carries a purpose outside the exclusion set', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'all-except-filter')
    const subscription = await seedPollSubscription(consentAdminEventApi, topic.name, {
      type: 'all_except',
      purposes: ['marketing'],
    })
    const groupId = subscription.groupId!

    const { event: excludedOnly } = await publishMarkedEvent(consentAdminEventApi, groupId, topic.name, [
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

    const { event: mixed } = await publishMarkedEvent(consentAdminEventApi, groupId, topic.name, [
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

  // There is no test-only hook anywhere in this codebase to force a DELIVERY insert to fail mid
  // fan-out transaction - see AGENTS.md, "What this suite cannot
  // verify". Adding one would mean shipping production code whose only purpose is to be
  // exploitable by a test, which is out of scope here.
  test.skip(
    '08.08.08 - A fan-out persistence failure rolls back the event and its purposes',
    () => {
      // Intentionally not implemented - see the skip reason above.
    },
  )
})
