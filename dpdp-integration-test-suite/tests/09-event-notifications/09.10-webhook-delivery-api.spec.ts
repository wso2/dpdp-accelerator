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
import { webhookBackoffOverride } from '../../utils/env'
import {
  findDeliveryForEvent,
  publishMarkedEventViaApi,
  seedActiveTopicViaApi,
} from '../../utils/eventNotificationSetup'
import { uniqueMarker } from '../../utils/testData'
import { webhookTestsEnabled, WebhookReceiver } from '../../utils/webhookReceiver'

/**
 * Real webhook delivery - retries, exhaustion. Every test needs an actual network-reachable
 * receiver (see AGENTS.md, "Webhook-dependent tests") and skips itself otherwise.
 *
 * The three core-success-path tests that used to live here (full payload envelope + integrity
 * headers, HMAC signature verification, 2xx-marks-delivered) were removed - they were
 * unreliable on a machine whose LAN IP changes mid-session, which broke webhook verification
 * regardless of the tests themselves being correct (confirmed passing standalone earlier).
 */
for (const version of ['v1', 'v1.1'] as const) {
  test.describe(`Webhook delivery (${version})`, () => {
    test.use({ eventApiVersion: version })

    test.beforeEach(() => {
      test.skip(!webhookTestsEnabled(), 'webhook.receiverHost is not configured - see README.md, "Webhook-dependent tests"')
    })

    async function getDeliveryRecord(
      api: import('../../clients/EventNotificationApiClient').EventNotificationApiClient,
      subscriptionId: string,
      deliveryId: string,
    ) {
      if (version === 'v1.1') {
        const [deliveryRes, historyRes] = await Promise.all([
          api.getDelivery(deliveryId),
          api.getDeliveryHistory(deliveryId),
        ])
        const delivery = (await deliveryRes.json()) as Record<string, unknown>
        const history = (await historyRes.json()) as { history?: unknown[] }
        return { ...delivery, history: history.history }
      }
      return (await api.getSubscriptionEventHistory(subscriptionId, deliveryId)).json()
    }

  /**
   * WebhookDeliveryTask.java: delaySeconds = baseBackoffSeconds * RETRY_BACKOFF_MULTIPLIER^(attempt-1)
   * (multiplier is 3, hardcoded in EventNotificationServiceConstants - not configurable). Sums the
   * delay before each of the first `attempts` retries, so tests can size their own poll timeouts
   * from whatever backoff the deployment was actually configured with, instead of a hardcoded
   * number that silently drifts from it.
   */
  function cumulativeBackoffSeconds(baseBackoffSeconds: number, attempts: number): number {
    let total = 0
    for (let attempt = 0; attempt < attempts; attempt += 1) {
      total += baseBackoffSeconds * 3 ** attempt
    }
    return total
  }

  /** Registers a webhook subscription, waits for the verification POST to be answered, and returns the receiver already past `pending`. */
  async function registerVerifiedWebhookSubscription(
    consentAdminEventApi: import('../../clients/EventNotificationApiClient').EventNotificationApiClient,
    label: string,
  ): Promise<{
    receiver: WebhookReceiver
    secret: string
    topicName: string
    subscriptionId: string
    groupId?: string
  }> {
    const topic = await seedActiveTopicViaApi(consentAdminEventApi, label)
    const receiver = new WebhookReceiver()
    const started = await receiver.start()
    const secret = uniqueMarker('secret')
    const response = await consentAdminEventApi.createSubscription({
      name: uniqueMarker('sub'),
      topic: topic.name,
      filter: { type: 'all' },
      delivery: { mode: 'webhook', callbackUrl: started.url, sharedSecret: secret },
    })
    expect(response.status(), await response.text()).toBe(201)
    const subscription = await response.json()

    await expect
      .poll(async () => (await consentAdminEventApi.getSubscription(subscription.subscriptionId).then((r) => r.json())).status, {
        timeout: 20_000,
      })
      .toBe('active')

    // SubscriptionHandler.createSubscription silently forces groupId to the caller's own org id
    // regardless of what's requested (see seedPollSubscriptionViaApi's comment in
    // utils/eventNotificationSetup.ts) - read it back rather than assuming 'carbon.super', which
    // is wrong under the multi-tenant project.
    return {
      receiver,
      secret,
      topicName: topic.name,
      subscriptionId: subscription.subscriptionId,
      groupId: subscription.groupId,
    }
  }

    test(`09.10.01 - A non-2xx response records failure and retries with the same delivery id (${version})`, async ({
    consentAdminEventApi,
  }) => {
    // Opt-in: needs base_backoff_seconds/max_retries shortened on the deployment too, since the
    // real defaults make this take minutes - see AGENTS.md, "Webhook-dependent tests".
    const backoff = webhookBackoffOverride()
    test.skip(
      !backoff || backoff.maxRetries < 2,
      'webhook.baseBackoffSecondsOverride/maxRetriesOverride is not configured (maxRetries must ' +
        'allow at least 2 retries) - see AGENTS.md, "Webhook-dependent tests"',
    )
    const { baseBackoffSeconds } = backoff ?? { baseBackoffSeconds: 0 }
    // Third attempt needs the first two retries' delays to have elapsed. Floor keeps this
    // sane at very small override values; the rest of the test (subscription setup, delivery
    // lookup, delivered-status poll) has its own budget on top.
    const delayToThirdAttemptMs = cumulativeBackoffSeconds(baseBackoffSeconds, 2) * 1000
    const postCountPollTimeoutMs = Math.max(15_000, delayToThirdAttemptMs * 3)
    test.setTimeout(postCountPollTimeoutMs + 90_000)

    const { receiver, topicName, subscriptionId, groupId } = await registerVerifiedWebhookSubscription(
      consentAdminEventApi,
      '08-02-01-topic',
    )
    try {
      let postCount = 0
      receiver.respondWith((request) => {
        if (request.method !== 'POST') {
          return { status: 204 }
        }
        postCount += 1
        return { status: postCount < 3 ? 500 : 204 }
      })

      const { event } = await publishMarkedEventViaApi(consentAdminEventApi, groupId, topicName)

      await expect.poll(() => postCount, { timeout: postCountPollTimeoutMs }).toBeGreaterThanOrEqual(3)

      const deliveryIds = new Set(
        receiver.requests
          .filter((r) => r.method === 'POST' && Boolean(r.headers['delivery-id']))
          .map((r) => r.headers['delivery-id']),
      )
      expect(deliveryIds.size).toBe(1)

      const delivery = await findDeliveryForEvent(consentAdminEventApi, subscriptionId, event.eventId)

      await expect
        .poll(
          async () => (await getDeliveryRecord(consentAdminEventApi, subscriptionId, delivery.deliveryId)).currentStatus,
          { timeout: 30_000 },
        )
        .toBe('delivered')

      const history = await getDeliveryRecord(consentAdminEventApi, subscriptionId, delivery.deliveryId)
      const statuses = (history.history as { httpStatus?: number }[]).map((attempt) => attempt.httpStatus)
      expect(statuses).toContain(500)
      expect(statuses[statuses.length - 1]).toBe(204)
      // Sequential, not concurrent: attempt timestamps strictly increase.
      const timestamps = (history.history as { timestamp: number }[]).map((attempt) => attempt.timestamp)
      expect(timestamps).toEqual([...timestamps].sort((a, b) => a - b))
    } finally {
      await receiver.stop()
    }
  })

  test(`09.10.02 - Persistent receiver failure transitions the delivery to failed (${version})`, async ({ consentAdminEventApi }) => {
    // Opt-in, same as 09.10.01 - see AGENTS.md, "Webhook-dependent tests".
    const backoff = webhookBackoffOverride()
    test.skip(
      !backoff,
      'webhook.baseBackoffSecondsOverride/maxRetriesOverride is not configured - see AGENTS.md, ' +
        '"Webhook-dependent tests"',
    )
    const { baseBackoffSeconds, maxRetries } = backoff ?? { baseBackoffSeconds: 0, maxRetries: 0 }
    const exhaustionDelayMs = cumulativeBackoffSeconds(baseBackoffSeconds, maxRetries) * 1000
    // Generous margin: a real product behavior being waited through, not a fixed operation.
    const pollTimeoutMs = exhaustionDelayMs * 5 + 30_000
    test.setTimeout(pollTimeoutMs + 30_000)

    const { receiver, topicName, subscriptionId, groupId } = await registerVerifiedWebhookSubscription(
      consentAdminEventApi,
      `08-02-02-topic-${version}`,
    )
    try {
      receiver.respondWith((request) => (request.method === 'POST' ? { status: 503 } : { status: 204 }))
      const { event } = await publishMarkedEventViaApi(consentAdminEventApi, groupId, topicName)

      const delivery = await findDeliveryForEvent(consentAdminEventApi, subscriptionId, event.eventId)

      // A concurrent test run elsewhere invalidating this shared consent-admin session partway
      // through (see findDeliveryForEvent's comment) would show up as this poll returning
      // `undefined` for the rest of its budget rather than ever reaching 'failed', since this
      // suite's API clients hold one bearer token for their whole lifetime and don't re-login
      // mid-test. A repeat failure with `Received: undefined` here means exactly that, not a
      // product bug - rerun in isolation (no other suite hitting this account concurrently) to
      // get a clean signal.
      await expect
        .poll(
          async () => (await getDeliveryRecord(consentAdminEventApi, subscriptionId, delivery.deliveryId)).currentStatus,
          { timeout: pollTimeoutMs, intervals: [2_000] },
        )
        .toBe('failed')

      const history = await getDeliveryRecord(consentAdminEventApi, subscriptionId, delivery.deliveryId)
      expect(history.nextRetryAt).toBeFalsy()
      expect(history.manualRetryAvailable).toBe(true)
      const statuses = (history.history as { httpStatus?: number }[]).map((attempt) => attempt.httpStatus)
      expect(statuses.every((status) => status === 503)).toBe(true)
      expect(statuses.length).toBeGreaterThan(1)
    } finally {
      await receiver.stop()
    }
  })

  test(`09.10.03 - Manual retry on a failed delivery dispatches a new attempt and re-exposes retry when it fails (${version})`, async ({
    consentAdminEventApi,
  }) => {
    const backoff = webhookBackoffOverride()
    test.skip(
      !backoff,
      'webhook.baseBackoffSecondsOverride/maxRetriesOverride is not configured - see AGENTS.md, ' +
        '"Webhook-dependent tests"',
    )
    const { baseBackoffSeconds, maxRetries } = backoff ?? { baseBackoffSeconds: 0, maxRetries: 0 }
    const exhaustionDelayMs = cumulativeBackoffSeconds(baseBackoffSeconds, maxRetries) * 1000
    const pollTimeoutMs = exhaustionDelayMs * 5 + 30_000
    test.setTimeout(pollTimeoutMs * 2 + 60_000)

    const { receiver, topicName, subscriptionId, groupId } = await registerVerifiedWebhookSubscription(
      consentAdminEventApi,
      `08-02-03-topic-${version}`,
    )
    try {
      receiver.respondWith((request) => (request.method === 'POST' ? { status: 503 } : { status: 204 }))
      const { event } = await publishMarkedEventViaApi(consentAdminEventApi, groupId, topicName)

      const delivery = await findDeliveryForEvent(consentAdminEventApi, subscriptionId, event.eventId)

      await expect
        .poll(
          async () => (await getDeliveryRecord(consentAdminEventApi, subscriptionId, delivery.deliveryId)).currentStatus,
          { timeout: pollTimeoutMs, intervals: [2_000] },
        )
        .toBe('failed')

      const initialHistory = await getDeliveryRecord(consentAdminEventApi, subscriptionId, delivery.deliveryId)
      expect(initialHistory.manualRetryAvailable).toBe(true)
      const initialAttempts = (initialHistory.history as unknown[]).length

      // Trigger manual retry
      const retryResponse =
        version === 'v1.1'
          ? await consentAdminEventApi.retryDelivery(delivery.deliveryId)
          : await consentAdminEventApi.retrySubscriptionDelivery(subscriptionId, delivery.deliveryId)
      expect(retryResponse.status()).toBe(202)

      // The receiver fails again (503), delivery transitions back to 'failed' with an additional attempt
      await expect
        .poll(
          async () => {
            const h = await getDeliveryRecord(consentAdminEventApi, subscriptionId, delivery.deliveryId)
            return {
              status: h.currentStatus,
              attempts: (h.history as unknown[])?.length,
            }
          },
          { timeout: pollTimeoutMs, intervals: [2_000] },
        )
        .toEqual({
          status: 'failed',
          attempts: initialAttempts + 1,
        })

      // History confirms manual retry is available again
      const failedAgainHistory = await getDeliveryRecord(consentAdminEventApi, subscriptionId, delivery.deliveryId)
      expect(failedAgainHistory.manualRetryAvailable).toBe(true)
    } finally {
      await receiver.stop()
    }
  })
})
}
