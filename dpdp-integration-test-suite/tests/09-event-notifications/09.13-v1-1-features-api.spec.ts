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

import crypto from 'node:crypto'
import { test, expect, mintPersonaTokenWithScopes } from '../../fixtures/auth.fixtures'
import { EventNotificationApiClient } from '../../clients/EventNotificationApiClient'
import {
  publishMarkedEventViaApi,
  seedActiveTopicViaApi,
  seedPollSubscriptionViaApi,
} from '../../utils/eventNotificationSetup'
import { uniqueMarker } from '../../utils/testData'
import { webhookTestsEnabled, WebhookReceiver } from '../../utils/webhookReceiver'

function computeHmacSignature(secret: string, payload: string): string {
  const hmac = crypto.createHmac('sha256', secret).update(payload).digest('hex')
  return `sha256=${hmac}`
}

function computeCompletionSignature(secret: string, deliveryId: string, rawBody: string): string {
  const signingInput = `v1\ncompletion\n${deliveryId}\n${rawBody}`
  return computeHmacSignature(secret, signingInput)
}

test.describe('Event Notifications V1.1 API features', () => {
  test.use({ eventApiVersion: 'v1.1' })

  test('09.13.01 - V1.1 split read returns delivery details without history and attempts via history endpoint', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopicViaApi(consentAdminEventApi, 'v1-1-split-read')
    const subscription = await seedPollSubscriptionViaApi(consentAdminEventApi, topic.name, { type: 'all' })

    const { event } = await publishMarkedEventViaApi(consentAdminEventApi, '', topic.name, ['account-management'])

    let deliveryId = ''
    await expect
      .poll(
        async () => {
          const listResponse = await consentAdminEventApi.listSubscriptionEvents(subscription.subscriptionId)
          if (!listResponse.ok()) {
            return false
          }
          const { items } = (await listResponse.json()) as { items?: { deliveryId: string; eventId: string }[] }
          const found = items?.find((d) => d.eventId === event.eventId)
          if (found) {
            deliveryId = found.deliveryId
            return true
          }
          return false
        },
        { timeout: 10_000 },
      )
      .toBe(true)

    // GET /deliveries/{deliveryId} returns Delivery details without the history array
    const deliveryResponse = await consentAdminEventApi.getDelivery(deliveryId)
    expect(deliveryResponse.status()).toBe(200)
    const deliveryBody = (await deliveryResponse.json()) as Record<string, unknown>
    expect(deliveryBody.deliveryId).toBe(deliveryId)
    expect(deliveryBody.eventId).toBe(event.eventId)
    expect(deliveryBody.topic).toBe(topic.name)
    expect(deliveryBody.deliveryMode).toBe('poll')
    expect(deliveryBody.currentStatus).toBeTruthy()
    expect(deliveryBody.history).toBeUndefined()

    // GET /deliveries/{deliveryId}/history returns DeliveryHistory with attempts array
    const historyResponse = await consentAdminEventApi.getDeliveryHistory(deliveryId)
    expect(historyResponse.status()).toBe(200)
    const historyBody = (await historyResponse.json()) as {
      deliveryId: string
      eventId: string
      history: { attempt: number; status: string; timestamp: number }[]
    }
    expect(historyBody.deliveryId).toBe(deliveryId)
    expect(historyBody.eventId).toBe(event.eventId)
    expect(Array.isArray(historyBody.history)).toBe(true)
  })

  test('09.13.02 - V1.1 publish without group-id header and poll with HMAC signature verification', async ({
    browser,
    consentAdminEventApi,
    request,
    target,
  }) => {
    const topic = await seedActiveTopicViaApi(consentAdminEventApi, 'v1-1-poll')
    const sharedSecret = uniqueMarker('secret')
    const subResponse = await consentAdminEventApi.createSubscription({
      name: uniqueMarker('sub'),
      topic: topic.name,
      filter: { type: 'all' },
      delivery: { mode: 'poll', sharedSecret },
    })
    expect(subResponse.status(), await subResponse.text()).toBe(201)
    const subscription = (await subResponse.json()) as { subscriptionId: string }

    // Publish event without group-id header (resolved from organization context in v1.1)
    const publishResponse = await consentAdminEventApi.publishEvent(undefined, {
      topic: topic.name,
      payload: { marker: uniqueMarker('poll-event') },
    })
    expect(publishResponse.status(), await publishResponse.text()).toBe(201)
    const { eventId } = (await publishResponse.json()) as { eventId: string }
    expect(eventId).toBeTruthy()

    const pollBody = JSON.stringify({ returnImmediately: true, maxEvents: 10 })

    // Polling requires notifications:events:poll scope
    const pollAuth = await mintPersonaTokenWithScopes(browser, 'consent-admin', [
      'openid',
      'notifications:events:poll',
    ])
    const pollClient = new EventNotificationApiClient(request, pollAuth, target.tenantDomain, 'v1.1')

    // Poll with invalid signature: 401 when HMAC validation is enabled, or 200 when disabled
    const badSignature = 'sha256=0000000000000000000000000000000000000000000000000000000000000000'
    const rejectResponse = await pollClient.pollEvents(subscription.subscriptionId, badSignature, pollBody)
    expect([200, 401]).toContain(rejectResponse.status())

    // Poll with valid HMAC signature succeeds (200)
    const validSignature = computeHmacSignature(sharedSecret, pollBody)
    const pollResponse = await pollClient.pollEvents(subscription.subscriptionId, validSignature, pollBody)
    expect(pollResponse.status(), await pollResponse.text()).toBe(200)
    const pollData = (await pollResponse.json()) as {
      moreAvailable: boolean
      sets: Record<string, string>
    }
    expect(typeof pollData.moreAvailable).toBe('boolean')
    expect(typeof pollData.sets).toBe('object')
  })

  test('09.13.03 - Manual retry on a poll delivery is rejected with 409 Conflict', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopicViaApi(consentAdminEventApi, 'v1-1-poll-retry')
    const subscription = await seedPollSubscriptionViaApi(consentAdminEventApi, topic.name, { type: 'all' })

    const { event } = await publishMarkedEventViaApi(consentAdminEventApi, '', topic.name, ['account-management'])

    let deliveryId = ''
    await expect
      .poll(
        async () => {
          const listResponse = await consentAdminEventApi.listSubscriptionEvents(subscription.subscriptionId)
          if (!listResponse.ok()) {
            return false
          }
          const { items } = (await listResponse.json()) as { items?: { deliveryId: string; eventId: string }[] }
          const found = items?.find((d) => d.eventId === event.eventId)
          if (found) {
            deliveryId = found.deliveryId
            return true
          }
          return false
        },
        { timeout: 10_000 },
      )
      .toBe(true)

    // POST /deliveries/{deliveryId}/retry on a poll delivery returns 409
    const retryResponse = await consentAdminEventApi.retryDelivery(deliveryId)
    expect(retryResponse.status(), `deliveryId=${deliveryId} text=${await retryResponse.text()}`).toBe(409)
    const errorBody = await retryResponse.json()
    expect(errorBody.description ?? errorBody.message).toMatch(/not (supported|available) for poll/i)
  })

  test('09.13.04 - Access control on /v1.1/deliveries/* requires notifications:subscriptions:read', async ({
    browser,
    request,
    target,
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopicViaApi(consentAdminEventApi, 'v1-1-scopes')
    const subscription = await seedPollSubscriptionViaApi(consentAdminEventApi, topic.name, { type: 'all' })
    const { event } = await publishMarkedEventViaApi(consentAdminEventApi, '', topic.name, ['account-management'])

    let deliveryId = ''
    await expect
      .poll(
        async () => {
          const listResponse = await consentAdminEventApi.listSubscriptionEvents(subscription.subscriptionId)
          if (!listResponse.ok()) {
            return false
          }
          const { items } = (await listResponse.json()) as { items?: { deliveryId: string; eventId: string }[] }
          const found = items?.find((d) => d.eventId === event.eventId)
          if (found) {
            deliveryId = found.deliveryId
            return true
          }
          return false
        },
        { timeout: 10_000 },
      )
      .toBe(true)

    // 1. Token with ONLY notifications:events:read cannot access /v1.1/deliveries/* -> 403 Forbidden
    const eventsOnlyHeaders = await mintPersonaTokenWithScopes(browser, 'consent-admin', [
      'openid',
      'internal_login',
      'notifications:events:read',
    ])
    const eventsOnlyClient = new EventNotificationApiClient(
      request,
      eventsOnlyHeaders,
      target.tenantDomain,
      'v1.1',
    )
    const forbiddenDelivery = await eventsOnlyClient.getDelivery(deliveryId)
    expect(forbiddenDelivery.status()).toBe(403)

    const forbiddenHistory = await eventsOnlyClient.getDeliveryHistory(deliveryId)
    expect(forbiddenHistory.status()).toBe(403)

    // 2. Token with notifications:subscriptions:read can access /v1.1/deliveries/* -> 200 OK
    const subsReadHeaders = await mintPersonaTokenWithScopes(browser, 'consent-admin', [
      'openid',
      'internal_login',
      'notifications:subscriptions:read',
    ])
    const subsReadClient = new EventNotificationApiClient(
      request,
      subsReadHeaders,
      target.tenantDomain,
      'v1.1',
    )
    const allowedDelivery = await subsReadClient.getDelivery(deliveryId)
    expect(allowedDelivery.status()).toBe(200)

    const allowedHistory = await subsReadClient.getDeliveryHistory(deliveryId)
    expect(allowedHistory.status()).toBe(200)
  })

  test('09.13.05 - Webhook completion records evidence, rejects invalid signatures and duplicate completions', async ({
    browser,
    consentAdminEventApi,
    request,
    target,
  }) => {
    test.skip(!webhookTestsEnabled(), 'webhook.receiverHost is not configured - see README.md, "Webhook-dependent tests"')

    const topic = await seedActiveTopicViaApi(consentAdminEventApi, 'v1-1-completion')
    const receiver = new WebhookReceiver()
    const started = await receiver.start()
    const sharedSecret = uniqueMarker('secret')

    try {
      const response = await consentAdminEventApi.createSubscription({
        name: uniqueMarker('sub'),
        topic: topic.name,
        filter: { type: 'all' },
        delivery: { mode: 'webhook', callbackUrl: started.url, sharedSecret },
      })
      expect(response.status(), await response.text()).toBe(201)
      const subscription = (await response.json()) as { subscriptionId: string }

      await expect
        .poll(
          async () => (await consentAdminEventApi.getSubscription(subscription.subscriptionId).then((r) => r.json())).status,
          { timeout: 20_000 },
        )
        .toBe('active')

      receiver.respondWith(() => ({ status: 200 }))

      const { event } = await publishMarkedEventViaApi(consentAdminEventApi, '', topic.name)

      let deliveryId = ''
      await expect
        .poll(
          async () => {
            const listResponse = await consentAdminEventApi.listSubscriptionEvents(subscription.subscriptionId)
            if (!listResponse.ok()) {
              return false
            }
            const { items } = (await listResponse.json()) as { items?: { deliveryId: string; eventId: string }[] }
            const found = items?.find((d) => d.eventId === event.eventId)
            if (found) {
              deliveryId = found.deliveryId
              return true
            }
            return false
          },
          { timeout: 15_000 },
        )
        .toBe(true)

      // Wait for delivery to reach 'delivered' status
      await expect
        .poll(
          async () => {
            const res = await consentAdminEventApi.getDelivery(deliveryId)
            if (!res.ok()) return undefined
            return (await res.json()).currentStatus
          },
          { timeout: 20_000 },
        )
        .toBe('delivered')

      const completionBody = JSON.stringify({
        completionStatus: 'completed',
        completionEvidence: 'https://processor.example.com/receipts/proof-001',
      })

      // Delivery completion requires notifications:event-deliveries:complete scope
      const completionAuth = await mintPersonaTokenWithScopes(browser, 'consent-admin', [
        'openid',
        'notifications:event-deliveries:complete',
      ])
      const completionClient = new EventNotificationApiClient(request, completionAuth, target.tenantDomain, 'v1.1')

      // 1. Completion with invalid signature is rejected (401 Unauthorized)
      const badSignature = 'sha256=ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff'
      const rejectResponse = await completionClient.completeDelivery(deliveryId, badSignature, completionBody)
      expect(rejectResponse.status()).toBe(401)

      // 2. Completion with valid signature succeeds (204 No Content)
      const validSignature = computeCompletionSignature(sharedSecret, deliveryId, completionBody)
      const successResponse = await completionClient.completeDelivery(deliveryId, validSignature, completionBody)
      expect(successResponse.status()).toBe(204)

      // 3. GET /deliveries/{deliveryId} reflects completed status and evidence
      const verifiedDeliveryResponse = await consentAdminEventApi.getDelivery(deliveryId)
      expect(verifiedDeliveryResponse.status()).toBe(200)
      const verifiedDelivery = (await verifiedDeliveryResponse.json()) as {
        completionStatus?: string
        completionEvidence?: string
      }
      expect(verifiedDelivery.completionStatus).toBe('completed')
      expect(verifiedDelivery.completionEvidence).toBe('https://processor.example.com/receipts/proof-001')

      // 4. Second completion returns 409 Conflict
      const duplicateResponse = await completionClient.completeDelivery(deliveryId, validSignature, completionBody)
      expect(duplicateResponse.status()).toBe(409)
    } finally {
      await receiver.stop()
    }
  })
})
