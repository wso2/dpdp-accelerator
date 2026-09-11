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

import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createSubscription,
  deleteSubscription,
  fetchSubscriptionById,
  fetchSubscriptionEventHistory,
  fetchSubscriptionEvents,
  fetchSubscriptions,
  retrySubscriptionDelivery,
  verifySubscription,
} from '../features/events/api/subscriptionsApi'

const transport = vi.hoisted(() => ({
  httpRequest: vi.fn(),
  login: vi.fn(),
}))

vi.mock('../utils/authClient', () => ({
  httpRequest: transport.httpRequest,
  isAuthEnabled: () => true,
  login: transport.login,
}))

afterEach(() => {
  vi.clearAllMocks()
})

function respondWith(payload: unknown, status = 200): void {
  transport.httpRequest.mockResolvedValue({ status, data: payload })
}

function sentRequest(index = 0): { url: string; method?: string; data?: unknown } {
  return transport.httpRequest.mock.calls[index]?.[0] as ReturnType<typeof sentRequest>
}

describe('subscriptionsApi', () => {
  it('calls fetchSubscriptions with search and pagination query parameters', async () => {
    respondWith({ items: [], total: 0 })

    await fetchSubscriptions({
      limit: 10,
      offset: 20,
      status: 'active',
      search: 'consent.revoke',
    })

    const req = sentRequest()
    const url = new URL(req.url)
    expect(url.pathname).toBe('/api/dpdp/event-notifications/v1/subscriptions')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      limit: '10',
      offset: '20',
      status: 'active',
      search: 'consent.revoke',
    })
    expect(req.method).toBe('GET')
  })

  it('fetches a single subscription by ID with URI encoding', async () => {
    respondWith({ subscriptionId: 'sub/123' })

    await fetchSubscriptionById('sub/123')

    const req = sentRequest()
    const url = new URL(req.url)
    expect(url.pathname).toBe('/api/dpdp/event-notifications/v1/subscriptions/sub%2F123')
    expect(req.method).toBe('GET')
  })

  it('posts a new subscription payload', async () => {
    respondWith({ subscriptionId: 'sub-new', status: 'ACTIVE' })

    const payload = {
      topic: 'consent.revoke',
      filter: { type: 'specific' as const, purposes: ['MARKETING'] },
      delivery: {
        mode: 'webhook' as const,
        callbackUrl: 'https://example.com/callback',
        sharedSecret: 'secret123',
      },
    }

    await createSubscription(payload)

    const req = sentRequest()
    const url = new URL(req.url)
    expect(url.pathname).toBe('/api/dpdp/event-notifications/v1/subscriptions')
    expect(req.method).toBe('POST')
    expect(JSON.parse(String(req.data))).toEqual(payload)
  })

  it('deletes a subscription by ID', async () => {
    respondWith({ subscriptionId: 'sub-1', status: 'DELETED' })

    await deleteSubscription('sub-1')

    const req = sentRequest()
    const url = new URL(req.url)
    expect(url.pathname).toBe('/api/dpdp/event-notifications/v1/subscriptions/sub-1')
    expect(req.method).toBe('DELETE')
  })

  it('triggers verification on a subscription', async () => {
    respondWith({ subscriptionId: 'sub-1', status: 'ACTIVE' })

    await verifySubscription('sub-1')

    const req = sentRequest()
    const url = new URL(req.url)
    expect(url.pathname).toBe('/api/dpdp/event-notifications/v1/subscriptions/sub-1/verify')
    expect(req.method).toBe('POST')
  })

  it('fetches subscription delivery events with pagination', async () => {
    respondWith({ items: [], total: 0 })

    await fetchSubscriptionEvents('sub-1', { limit: 5, offset: 10 })

    const req = sentRequest()
    const url = new URL(req.url)
    expect(url.pathname).toBe('/api/dpdp/event-notifications/v1/subscriptions/sub-1/events')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      limit: '5',
      offset: '10',
    })
    expect(req.method).toBe('GET')
  })

  it('fetches detailed delivery attempt history', async () => {
    respondWith({ deliveryId: 'dlv-1', history: [] })

    await fetchSubscriptionEventHistory('sub-1', 'dlv-1')

    const req = sentRequest()
    const url = new URL(req.url)
    expect(url.pathname).toBe('/api/dpdp/event-notifications/v1/subscriptions/sub-1/events/dlv-1')
    expect(req.method).toBe('GET')
  })

  it('submits a one-time manual delivery retry', async () => {
    respondWith({ deliveryId: 'dlv/1', manualRetryUsed: true, manualRetryAvailable: false })

    await retrySubscriptionDelivery('sub/1', 'dlv/1')

    const req = sentRequest()
    const url = new URL(req.url)
    expect(url.pathname).toBe(
      '/api/dpdp/event-notifications/v1/subscriptions/sub%2F1/events/dlv%2F1/retry',
    )
    expect(req.method).toBe('POST')
  })
})
