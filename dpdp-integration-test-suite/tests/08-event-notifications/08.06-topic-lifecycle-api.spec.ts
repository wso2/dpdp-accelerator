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
import { seedActiveTopic, seedPollSubscription } from '../../utils/eventNotificationSetup'

/**
 * Topic lifecycle rules enforced by TopicServiceImpl: the guards on deregistering a topic, and
 * what re-registering a retired name does. API-only - the portal's Topics page can register and
 * deregister (08.01), but nothing in the UI can reach these rules.
 */
test.describe('Topic lifecycle rules', () => {
  test('08.06.01 - A topic with a live subscription cannot be deregistered', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'has-subscription')
    await seedPollSubscription(consentAdminEventApi, topic.name)

    const deleteResponse = await consentAdminEventApi.deleteTopic(topic.topicId)
    expect(deleteResponse.status()).toBe(409)
    const body = await deleteResponse.json()
    expect(body.description).toContain('has active subscriptions')

    const getResponse = await consentAdminEventApi.listTopics({ search: topic.name })
    const { items } = (await getResponse.json()) as { items: { topicId: string; status: string }[] }
    const stillThere = items.find((t) => t.topicId === topic.topicId)
    expect(stillThere?.status.toUpperCase()).toBe('ACTIVE')
  })

  test('08.06.02 - Deregistering the same topic twice does not mutate it again', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'double-deregister')
    const first = await consentAdminEventApi.deleteTopic(topic.topicId)
    expect(first.status()).toBe(200)

    const second = await consentAdminEventApi.deleteTopic(topic.topicId)
    expect(second.status()).toBe(404)

    const listResponse = await consentAdminEventApi.listTopics({ search: topic.name, status: 'DEREGISTERED' })
    const { items } = (await listResponse.json()) as { items: { topicId: string }[] }
    expect(items.filter((t) => t.topicId === topic.topicId)).toHaveLength(1)
  })

  test('08.06.03 - Re-registering a previously deregistered topic name creates a new topic', async ({
    consentAdminEventApi,
  }) => {
    const original = await seedActiveTopic(consentAdminEventApi, 'reused-name')
    const deregisterResponse = await consentAdminEventApi.deleteTopic(original.topicId)
    expect(deregisterResponse.status()).toBe(200)

    const recreateResponse = await consentAdminEventApi.createTopic({ name: original.name })
    expect(recreateResponse.status()).toBe(201)
    const recreated = await recreateResponse.json()
    expect(recreated.topicId).not.toBe(original.topicId)
    expect(recreated.status.toUpperCase()).toBe('ACTIVE')

    const listResponse = await consentAdminEventApi.listTopics({ search: original.name })
    const { items } = (await listResponse.json()) as { items: { topicId: string; status: string }[] }
    const oldRow = items.find((t) => t.topicId === original.topicId)
    const newRow = items.find((t) => t.topicId === recreated.topicId)
    expect(oldRow?.status.toUpperCase()).toBe('DEREGISTERED')
    expect(newRow?.status.toUpperCase()).toBe('ACTIVE')
  })
})
