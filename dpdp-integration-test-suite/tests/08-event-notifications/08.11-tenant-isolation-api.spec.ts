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
import { seedActiveTopic, seedPollSubscription, publishMarkedEvent } from '../../utils/eventNotificationSetup'
import { uniqueMarker } from '../../utils/testData'

const SYSTEM_TOPICS = ['consent.update', 'consent.revoke', 'consent.expire', 'user.data.change', 'user.account.delete']

/**
 * `tenant`/`tenantB` (fixtures/tenant.fixtures.ts) are two independently-provisioned throwaway
 * tenants, each with its own admin already assigned dpdp-consent-admin (which carries every
 * notifications:* scope) - `ownerEventApi` on each is already tenant-qualified. Every event
 * notification resource lives under `TENANT.ORG_ID` (DPDPTenantContext.getOrganizationId()), so
 * these tests exercise that column as the isolation boundary directly, the same way
 * tests/05-multi-tenancy does for consent-mgt resources.
 */
test.describe('Event Notification tenant isolation', () => {
  test('08.11.01 - Tenants with the same topic name receive separate topic identities and lists', async ({
    tenant,
    tenantB,
  }) => {
    const sharedName = uniqueMarker('consent-status-changed')

    const responseA = await tenant.ownerEventApi.createTopic({ name: sharedName })
    expect(responseA.status(), await responseA.text()).toBe(201)
    const topicA = await responseA.json()

    const responseB = await tenantB.ownerEventApi.createTopic({ name: sharedName })
    expect(responseB.status(), await responseB.text()).toBe(201)
    const topicB = await responseB.json()

    expect(topicA.topicId).not.toBe(topicB.topicId)

    const listedA = await tenant.ownerEventApi.listTopics({ search: sharedName })
    const { items: itemsA } = (await listedA.json()) as { items: { topicId: string }[] }
    expect(itemsA.map((t) => t.topicId)).toContain(topicA.topicId)
    expect(itemsA.map((t) => t.topicId)).not.toContain(topicB.topicId)

    const listedB = await tenantB.ownerEventApi.listTopics({ search: sharedName })
    const { items: itemsB } = (await listedB.json()) as { items: { topicId: string }[] }
    expect(itemsB.map((t) => t.topicId)).toContain(topicB.topicId)
    expect(itemsB.map((t) => t.topicId)).not.toContain(topicA.topicId)
  })

  test("08.11.02 - Tenant A cannot read, delete, verify, or list history for tenant B resources", async ({
    tenant,
    tenantB,
  }) => {
    const topicB = await seedActiveTopic(tenantB.ownerEventApi, '09-02-02-topic')
    const subscriptionB = await seedPollSubscription(tenantB.ownerEventApi, topicB.name)
    const { event: eventB } = await publishMarkedEvent(tenantB.ownerEventApi, tenantB.domain, topicB.name)

    // Tenant A's own token, tenant B's resource ids - every operation must behave exactly like
    // an unknown id, never exposing that the id belongs to someone else.
    expect((await tenant.ownerEventApi.deleteTopic(topicB.topicId)).status()).toBe(404)
    expect((await tenant.ownerEventApi.getSubscription(subscriptionB.subscriptionId)).status()).toBe(404)
    expect((await tenant.ownerEventApi.deleteSubscription(subscriptionB.subscriptionId)).status()).toBe(404)
    expect((await tenant.ownerEventApi.verifySubscription(subscriptionB.subscriptionId)).status()).toBe(404)
    expect((await tenant.ownerEventApi.getEvent(eventB.eventId)).status()).toBe(404)
    expect((await tenant.ownerEventApi.getDeliveryHistory(eventB.eventId)).status()).toBe(404)
    expect(
      (await tenant.ownerEventApi.getSubscriptionEventHistory(subscriptionB.subscriptionId, eventB.eventId)).status(),
    ).toBe(404)

    // Tenant B's own view of its own resources is unaffected by the rejected cross-tenant attempts.
    const stillThere = await tenantB.ownerEventApi.getSubscription(subscriptionB.subscriptionId)
    expect(stillThere.status()).toBe(200)
    expect((await stillThere.json()).status).toBe('active')
  })

  test('08.11.03 - A newly created tenant receives Event Notification authorization and default topics', async ({
    tenant,
  }) => {
    // tenant.fixtures.ts's own setup already proves the portal app/role provisioning succeeded
    // (the owner signed into their tenant-qualified portal for real to build ownerEventApi) - this
    // test asserts the two things provisioning must get right: the system topics exist as
    // active/system, and the owner can manage user topics/subscriptions with no extra manual
    // API-resource registration (i.e. ordinary calls just work).
    const listed = await tenant.ownerEventApi.listTopics({ limit: 100 })
    expect(listed.status()).toBe(200)
    const { items } = (await listed.json()) as { items: { name: string; status: string; initiatedBy?: string }[] }
    for (const name of SYSTEM_TOPICS) {
      const systemTopic = items.find((t) => t.name === name)
      expect(systemTopic, `Expected system topic "${name}" to be provisioned for tenant ${tenant.domain}`).toBeTruthy()
      expect(systemTopic?.status).toBe('active')
      expect(systemTopic?.initiatedBy?.toLowerCase()).toBe('system')
    }

    const topic = await seedActiveTopic(tenant.ownerEventApi, '09-02-04-user-topic')
    const subscription = await seedPollSubscription(tenant.ownerEventApi, topic.name)
    expect(subscription.status).toBe('active')
  })
})
