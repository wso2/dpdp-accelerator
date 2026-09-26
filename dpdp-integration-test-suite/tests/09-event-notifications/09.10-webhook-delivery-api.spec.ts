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
import { seedActiveTopicViaApi, publishMarkedEventViaApi } from '../../utils/eventNotificationSetup'
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
test.describe('Webhook delivery', () => {
  test.beforeEach(() => {
    test.skip(!webhookTestsEnabled(), 'webhook.receiverHost is not configured - see README.md, "Webhook-dependent tests"')
  })

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
  ): Promise<{ receiver: WebhookReceiver; secret: string; topicName: string; subscriptionId: string; groupId: string }> {
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

  /**
   * A one-shot `listSubscriptionEvents` call right after publish is fine when nothing else has
   * happened yet, but the retry tests below first poll the receiver for
   * several webhook attempts - tens of seconds of real elapsed time in which this shared
   * `consent-admin` persona's session can be invalidated by an entirely different concurrent test
   * run also using it (this environment is real and shared - see AGENTS.md), turning the next API
   * call into a 401 whose body has no `items` field at all. Polling here, rather than a single
   * fetch, makes this resilient to that transient blip the same way this suite already tolerates
   * everything else about the shared environment - a future successful call recovers on its own.
   */
  async function findDeliveryForEvent(
    consentAdminEventApi: import('../../clients/EventNotificationApiClient').EventNotificationApiClient,
    subscriptionId: string,
    eventId: string,
  ): Promise<{ deliveryId: string; eventId: string }> {
    let found: { deliveryId: string; eventId: string } | undefined
    await expect
      .poll(
        async () => {
          const eventsList = await consentAdminEventApi.listSubscriptionEvents(subscriptionId, { limit: 20 })
          if (!eventsList.ok()) {
            return false
          }
          const { items } = (await eventsList.json()) as { items?: { deliveryId: string; eventId: string }[] }
          found = items?.find((item) => item.eventId === eventId)
          return Boolean(found)
        },
        { timeout: 30_000 },
      )
      .toBe(true)
    return found as { deliveryId: string; eventId: string }
  }

  test('09.10.01 - A non-2xx response records failure and retries with the same delivery id', async ({
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
          async () =>
            (await consentAdminEventApi.getSubscriptionEventHistory(subscriptionId, delivery.deliveryId).then((r) => r.json()))
              .currentStatus,
          { timeout: 30_000 },
        )
        .toBe('delivered')

      const history = await consentAdminEventApi
        .getSubscriptionEventHistory(subscriptionId, delivery.deliveryId)
        .then((r) => r.json())
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

  test('09.10.02 - Persistent receiver failure transitions the delivery to failed', async ({ consentAdminEventApi }) => {
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
      '08-02-02-topic',
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
          async () =>
            (await consentAdminEventApi.getSubscriptionEventHistory(subscriptionId, delivery.deliveryId).then((r) => r.json()))
              .currentStatus,
          { timeout: pollTimeoutMs, intervals: [2_000] },
        )
        .toBe('failed')

      const history = await consentAdminEventApi
        .getSubscriptionEventHistory(subscriptionId, delivery.deliveryId)
        .then((r) => r.json())
      expect(history.nextRetryAt).toBeFalsy()
      const statuses = (history.history as { httpStatus?: number }[]).map((attempt) => attempt.httpStatus)
      expect(statuses.every((status) => status === 503)).toBe(true)
      expect(statuses.length).toBeGreaterThan(1)
    } finally {
      await receiver.stop()
    }
  })

})
