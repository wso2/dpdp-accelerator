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
import { seedActiveTopicViaApi, seedPollSubscriptionViaApi } from '../../utils/eventNotificationSetup'

/**
 * Topic lifecycle rules enforced by TopicServiceImpl: the guards on deleting a topic, and
 * what re-registering a retired name does. API-only - the portal's Topics page can create and
 * delete (08.01), but nothing in the UI can reach these rules.
 */
for (const version of ['v1', 'v1.1'] as const) {
  const deletedStatus = version === 'v1' ? 'DEREGISTERED' : 'DELETED'
  test.describe(`Topic lifecycle rules (${version})`, () => {
    test.use({ eventApiVersion: version })

    test(`09.06.01 - A topic with a live subscription cannot be deleted (${version})`, async ({
      consentAdminEventApi,
    }) => {
      const topic = await seedActiveTopicViaApi(consentAdminEventApi, `has-subscription-${version}`)
      await seedPollSubscriptionViaApi(consentAdminEventApi, topic.name)

      const deleteResponse = await consentAdminEventApi.deleteTopic(topic.topicId)
      expect(deleteResponse.status()).toBe(409)
      const body = await deleteResponse.json()
      expect(body.description).toContain('has active subscriptions')

      const getResponse = await consentAdminEventApi.listTopics({ search: topic.name })
      const { items } = (await getResponse.json()) as { items: { topicId: string; status: string }[] }
      const stillThere = items.find((t) => t.topicId === topic.topicId)
      expect(stillThere?.status.toUpperCase()).toBe('ACTIVE')
    })

    test(`09.06.02 - Deleting the same topic twice does not mutate it again (${version})`, async ({
      consentAdminEventApi,
    }) => {
      const topic = await seedActiveTopicViaApi(consentAdminEventApi, `double-delete-${version}`)
      const first = await consentAdminEventApi.deleteTopic(topic.topicId)
      expect(first.status()).toBe(200)
      expect(((await first.json()) as { status: string }).status.toUpperCase()).toBe(deletedStatus)

      const second = await consentAdminEventApi.deleteTopic(topic.topicId)
      expect(second.status()).toBe(404)

      const listResponse = await consentAdminEventApi.listTopics({ search: topic.name, status: deletedStatus })
      const { items } = (await listResponse.json()) as { items: { topicId: string }[] }
      expect(items.filter((t) => t.topicId === topic.topicId)).toHaveLength(1)
    })

    test(`09.06.03 - Re-registering a previously deleted topic name creates a new topic (${version})`, async ({
      consentAdminEventApi,
    }) => {
      const original = await seedActiveTopicViaApi(consentAdminEventApi, `reused-name-${version}`)
      const deleteResponse = await consentAdminEventApi.deleteTopic(original.topicId)
      expect(deleteResponse.status()).toBe(200)

      const recreateResponse = await consentAdminEventApi.createTopic({ name: original.name })
      expect(recreateResponse.status()).toBe(201)
      const recreated = await recreateResponse.json()
      expect(recreated.topicId).not.toBe(original.topicId)
      expect(recreated.status.toUpperCase()).toBe('ACTIVE')

      const listResponse = await consentAdminEventApi.listTopics({ search: original.name })
      const { items } = (await listResponse.json()) as { items: { topicId: string; status: string }[] }
      const oldRow = items.find((t) => t.topicId === original.topicId)
      const newRow = items.find((t) => t.topicId === recreated.topicId)
      expect(oldRow?.status.toUpperCase()).toBe(deletedStatus)
      expect(newRow?.status.toUpperCase()).toBe('ACTIVE')
    })

    test(`09.06.04 - Any topic linked to a multi-topic subscription cannot be deleted until the subscription is deleted (${version})`, async ({
      consentAdminEventApi,
    }) => {
      const topicA = await seedActiveTopicViaApi(consentAdminEventApi, `multi-sub-guard-a-${version}`)
      const topicB = await seedActiveTopicViaApi(consentAdminEventApi, `multi-sub-guard-b-${version}`)
      const subscription = await seedPollSubscriptionViaApi(consentAdminEventApi, [topicA.name, topicB.name])

      // Both topics are blocked from deletion while the multi-topic subscription is live
      const deleteA = await consentAdminEventApi.deleteTopic(topicA.topicId)
      expect(deleteA.status()).toBe(409)
      expect((await deleteA.json()).description).toContain('has active subscriptions')

      const deleteB = await consentAdminEventApi.deleteTopic(topicB.topicId)
      expect(deleteB.status()).toBe(409)
      expect((await deleteB.json()).description).toContain('has active subscriptions')

      // Delete the subscription (no pending deliveries)
      const deleteSubResponse = await consentAdminEventApi.deleteSubscription(subscription.subscriptionId)
      expect(deleteSubResponse.status()).toBe(200)

      // Now both topics can be safely deleted
      const retryDeleteA = await consentAdminEventApi.deleteTopic(topicA.topicId)
      expect(retryDeleteA.status()).toBe(200)

      const retryDeleteB = await consentAdminEventApi.deleteTopic(topicB.topicId)
      expect(retryDeleteB.status()).toBe(200)
    })
  })
}
