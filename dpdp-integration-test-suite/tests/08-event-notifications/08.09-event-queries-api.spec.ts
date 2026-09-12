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

/**
 * Query and scoping rules on the event/delivery read endpoints (EventEndpoint): the
 * `subscriptionId` list filter, and the guard that a delivery id can only be read through the
 * subscription that actually owns it. API-only - the portal's Events screens (08.04) expose
 * neither.
 *
 * Both tests use two subscriptions on one topic with DISJOINT purpose filters:
 * SubscriptionDAOImpl.addSubscription rejects a new subscription whose purpose set *overlaps* any
 * existing one in the same (org, group, topic, deliveryMode) as EN-4090, so even two SPECIFIC
 * filters sharing a single purpose collide.
 */
test.describe('Event query and delivery scoping', () => {
  test('08.09.01 - The subscriptionId filter returns only events delivered to that subscription', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'subscription-filter')
    const subA = await seedPollSubscription(consentAdminEventApi, topic.name, {
      type: 'specific',
      purposes: ['account'],
    })
    const subB = await seedPollSubscription(consentAdminEventApi, topic.name, {
      type: 'specific',
      purposes: ['marketing'],
    })
    const groupId = subA.groupId!

    const { event: accountEvent } = await publishMarkedEvent(consentAdminEventApi, groupId, topic.name, ['account'])
    const { event: marketingEvent } = await publishMarkedEvent(consentAdminEventApi, groupId, topic.name, [
      'marketing',
    ])

    const forA = (
      (await (await consentAdminEventApi.listEvents({ subscriptionId: subA.subscriptionId, limit: 100 })).json()) as {
        items: { eventId: string }[]
      }
    ).items
    expect(forA.some((item) => item.eventId === accountEvent.eventId)).toBe(true)
    expect(forA.some((item) => item.eventId === marketingEvent.eventId)).toBe(false)

    const forB = (
      (await (await consentAdminEventApi.listEvents({ subscriptionId: subB.subscriptionId, limit: 100 })).json()) as {
        items: { eventId: string }[]
      }
    ).items
    expect(forB.some((item) => item.eventId === marketingEvent.eventId)).toBe(true)
    expect(forB.some((item) => item.eventId === accountEvent.eventId)).toBe(false)
  })

  test("08.09.02 - A delivery id belonging to another subscription can't be read through the wrong subscription path", async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'wrong-subscription-path')
    // Disjoint SPECIFIC filters, not overlapping ones - see 08.04.03's comment on why two
    // subscriptions with overlapping purpose sets on the same topic 409 as duplicates.
    const subA = await seedPollSubscription(consentAdminEventApi, topic.name, {
      type: 'specific',
      purposes: ['account'],
    })
    const subB = await seedPollSubscription(consentAdminEventApi, topic.name, {
      type: 'specific',
      purposes: ['other'],
    })
    const groupId = subA.groupId!
    const { event } = await publishMarkedEvent(consentAdminEventApi, groupId, topic.name, ['account', 'other'])

    const deliveriesForB = (
      (await (await consentAdminEventApi.listSubscriptionEvents(subB.subscriptionId)).json()) as {
        items: SubscriptionDeliveryRecord[]
      }
    ).items
    const deliveryOfB = deliveriesForB.find((delivery) => delivery.eventId === event.eventId)
    expect(deliveryOfB, 'subscription B should have a delivery for this event').toBeTruthy()

    const mismatchedResponse = await consentAdminEventApi.getSubscriptionEventHistory(
      subA.subscriptionId,
      deliveryOfB!.deliveryId,
    )
    expect(mismatchedResponse.status()).toBe(404)

    const validResponse = await consentAdminEventApi.getSubscriptionEventHistory(
      subB.subscriptionId,
      deliveryOfB!.deliveryId,
    )
    expect(validResponse.ok()).toBe(true)
  })
})
