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

import { expect } from '@playwright/test'
import type {
  EventNotificationApiClient,
  EventRecord,
  FilterConfig,
  SubscriptionRecord,
  TopicRecord,
} from '../clients/EventNotificationApiClient'
import { uniqueMarker } from './testData'

/**
 * Disposable setup for tests that need *a* topic to exist but aren't themselves testing topic
 * creation - stamped unique the same way seedConsent's Element/Purpose are (see
 * utils/consentSetup.ts), since this environment never resets and topic names must be unique per
 * org (EN-4090 on collision).
 */
export async function seedActiveTopic(api: EventNotificationApiClient, label = 'topic'): Promise<TopicRecord> {
  const response = await api.createTopic({ name: uniqueMarker(label) })
  expect(response.status(), await response.text()).toBe(201)
  return (await response.json()) as TopicRecord
}

/**
 * A poll subscription needs no callback URL and is active immediately (no webhook-verification
 * round trip) - the right default for any test that only cares about event/fan-out/authorization
 * behavior, not the webhook transport itself. Webhook-specific tests build their own
 * SubscriptionCreateRequest directly against a WebhookReceiver instead (see
 * AGENTS.md).
 *
 * There is deliberately no `groupId` parameter here: confirmed live, `SubscriptionHandler
 * .createSubscription` (`internal-webapps/.../endpoint/handler/SubscriptionHandler.java`) never
 * reads `request.getGroupId()` at all - it unconditionally sets `groupId = orgId`, silently
 * discarding whatever a caller sends. `event-notifications.yaml`'s own examples show a
 * caller-chosen `groupId` distinct from `orgId` (e.g. `groupId: processor-1` on
 * `orgId: example.com`), so this is a genuine product bug, not documented behavior - see
 * README's "A likely bug found while writing this suite". Every subscription this helper creates
 * therefore always lands in the org's own group; read the *returned* `groupId` back rather than
 * assuming a value you pass takes effect, and pass that same value as `publishMarkedEvent`'s
 * `groupId` argument to get a matching delivery.
 */
export async function seedPollSubscription(
  api: EventNotificationApiClient,
  topic: string,
  filter: FilterConfig = { type: 'all' },
): Promise<SubscriptionRecord> {
  const response = await api.createSubscription({
    topic,
    filter,
    delivery: { mode: 'poll', sharedSecret: uniqueMarker('secret') },
  })
  expect(response.status(), await response.text()).toBe(201)
  return (await response.json()) as SubscriptionRecord
}

/**
 * Publishes an event with a unique marker in its payload, so a search/lookup test can find
 * exactly this event and no other. `groupId` must be the *returned* `groupId` of whatever
 * subscription(s) this event is meant to match (see seedPollSubscription's comment on why a
 * caller-chosen groupId at subscription-creation time is currently silently ignored server-side).
 */
export async function publishMarkedEvent(
  api: EventNotificationApiClient,
  groupId: string,
  topic: string,
  purposes: string[] = [],
  extraPayload: Record<string, unknown> = {},
): Promise<{ event: EventRecord; marker: string }> {
  const marker = uniqueMarker('event')
  const response = await api.publishEvent(groupId, {
    topic,
    purposes,
    payload: { marker, ...extraPayload },
  })
  expect(response.status(), await response.text()).toBe(201)
  return { event: (await response.json()) as EventRecord, marker }
}
