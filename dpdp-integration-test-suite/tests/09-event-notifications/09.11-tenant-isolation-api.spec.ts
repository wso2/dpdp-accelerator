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

import { test, expect } from '../../fixtures/tenant.fixtures'
import { seedActiveTopicViaApi, seedPollSubscriptionViaApi, publishMarkedEventViaApi } from '../../utils/eventNotificationSetup'
import { uniqueMarker } from '../../utils/testData'

const SYSTEM_TOPICS = ['consent.update', 'consent.revoke', 'consent.expire', 'user.data.change', 'user.account.delete']

/**
 * `tenant`/`tenantB` (fixtures/tenant.fixtures.ts) are two independently-provisioned throwaway
 * tenants, each with its own admin already assigned dpdp-consent-admin (which carries every
 * notifications:* scope) - `ownerEventApi` on each is already tenant-qualified. Every event
 * notification resource lives under `TENANT.ORG_ID` (DPDPTenantContext.getOrganizationId()), so
 * these tests exercise that column as the isolation boundary directly, the same way
 * tests/06-multi-tenancy does for consent-mgt resources.
 */
for (const version of ['v1', 'v1.1'] as const) {
  test.describe(`Event Notification tenant isolation (${version})`, () => {
    test.use({ eventApiVersion: version })

    test(`09.11.01 - Tenants with the same topic name receive separate topic identities and lists (${version})`, async ({
      tenant,
      tenantB,
    }) => {
      const apiA = tenant.ownerEventApi.withVersion(version)
      const apiB = tenantB.ownerEventApi.withVersion(version)
      const sharedName = uniqueMarker(`consent-status-changed-${version}`)

      const responseA = await apiA.createTopic({ name: sharedName })
      expect(responseA.status(), await responseA.text()).toBe(201)
      const topicA = await responseA.json()

      const responseB = await apiB.createTopic({ name: sharedName })
      expect(responseB.status(), await responseB.text()).toBe(201)
      const topicB = await responseB.json()

      expect(topicA.topicId).not.toBe(topicB.topicId)

      const listedA = await apiA.listTopics({ search: sharedName })
      const { items: itemsA } = (await listedA.json()) as { items: { topicId: string }[] }
      expect(itemsA.map((t) => t.topicId)).toContain(topicA.topicId)
      expect(itemsA.map((t) => t.topicId)).not.toContain(topicB.topicId)

      const listedB = await apiB.listTopics({ search: sharedName })
      const { items: itemsB } = (await listedB.json()) as { items: { topicId: string }[] }
      expect(itemsB.map((t) => t.topicId)).toContain(topicB.topicId)
      expect(itemsB.map((t) => t.topicId)).not.toContain(topicA.topicId)
    })

    test(`09.11.02 - Tenant A cannot read, delete, verify, or list history for tenant B resources (${version})`, async ({
      tenant,
      tenantB,
    }) => {
      const apiA = tenant.ownerEventApi.withVersion(version)
      const apiB = tenantB.ownerEventApi.withVersion(version)

      const topicB = await seedActiveTopicViaApi(apiB, `09-02-02-topic-${version}`)
      const subscriptionB = await seedPollSubscriptionViaApi(apiB, topicB.name)
      const { event: eventB } = await publishMarkedEventViaApi(apiB, tenantB.domain, topicB.name)

      // Tenant A's own token, tenant B's resource ids - every operation must behave exactly like
      // an unknown id, never exposing that the id belongs to someone else.
      expect((await apiA.deleteTopic(topicB.topicId)).status()).toBe(404)
      expect((await apiA.getSubscription(subscriptionB.subscriptionId)).status()).toBe(404)
      expect((await apiA.deleteSubscription(subscriptionB.subscriptionId)).status()).toBe(404)
      expect((await apiA.verifySubscription(subscriptionB.subscriptionId)).status()).toBe(404)
      expect((await apiA.getEvent(eventB.eventId)).status()).toBe(404)
      expect((await apiA.getDeliveryHistory(eventB.eventId)).status()).toBe(404)
      expect(
        (await apiA.getSubscriptionEventHistory(subscriptionB.subscriptionId, eventB.eventId)).status(),
      ).toBe(404)

      // Tenant B's own view of its own resources is unaffected by the rejected cross-tenant attempts.
      const stillThere = await apiB.getSubscription(subscriptionB.subscriptionId)
      expect(stillThere.status()).toBe(200)
      expect((await stillThere.json()).status).toBe('active')
    })

    test(`09.11.03 - A newly created tenant receives Event Notification authorization and default topics (${version})`, async ({
      tenant,
    }) => {
      const api = tenant.ownerEventApi.withVersion(version)
      const listed = await api.listTopics({ limit: 100 })
      expect(listed.status()).toBe(200)
      const { items } = (await listed.json()) as { items: { name: string; status: string; initiatedBy?: string }[] }
      for (const name of SYSTEM_TOPICS) {
        const systemTopic = items.find((t) => t.name === name)
        expect(systemTopic, `Expected system topic "${name}" to be provisioned for tenant ${tenant.domain}`).toBeTruthy()
        expect(systemTopic?.status).toBe('active')
        expect(systemTopic?.initiatedBy?.toLowerCase()).toBe('system')
      }

      const topic = await seedActiveTopicViaApi(api, `09-02-04-user-topic-${version}`)
      const subscription = await seedPollSubscriptionViaApi(api, topic.name)
      expect(subscription.status).toBe('active')
    })
  })
}
