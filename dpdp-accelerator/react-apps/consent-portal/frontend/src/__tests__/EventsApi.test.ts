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
  fetchEventById,
  fetchEventDeliveries,
  fetchEventDeliveryHistory,
  fetchEvents,
  publishEvent,
} from '../features/events/api/eventsApi'

const transport = vi.hoisted(() => ({
  httpRequest: vi.fn(),
  login: vi.fn(),
}))

vi.mock('../utils/authClient', () => ({
  httpRequest: transport.httpRequest,
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

describe('eventsApi', () => {
  it('calls fetchEvents with all filters and pagination query parameters', async () => {
    respondWith({ items: [], total: 0 })

    await fetchEvents({
      limit: 10,
      offset: 20,
      search: 'consent.revoke',
      status: 'DELIVERED',
      topic: 'consent-events',
      subscriptionId: 'sub-1',
    })

    const req = sentRequest()
    const url = new URL(req.url)
    expect(url.pathname).toBe('/api/dpdp/event-notifications/v1.1/events')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      limit: '10',
      offset: '20',
      search: 'consent.revoke',
      status: 'DELIVERED',
      topic: 'consent-events',
      subscriptionId: 'sub-1',
    })
    expect(req.method).toBe('GET')
  })

  it('fetches event delivery history by deliveryId', async () => {
    respondWith({
      deliveryId: 'dlv-123',
      eventId: 'evt-1',
      topic: 'consent-events',
      currentStatus: 'DELIVERED',
      deliveryMode: 'webhook',
      history: [],
    })

    const result = await fetchEventDeliveryHistory('dlv-123')

    const req1 = sentRequest(0)
    const req2 = sentRequest(1)
    const url1 = new URL(req1.url)
    const url2 = new URL(req2.url)
    expect(url1.pathname).toBe('/api/dpdp/event-notifications/v1.1/deliveries/dlv-123')
    expect(req1.method).toBe('GET')
    expect(url2.pathname).toBe('/api/dpdp/event-notifications/v1.1/deliveries/dlv-123/history')
    expect(req2.method).toBe('GET')
    expect(result.deliveryId).toBe('dlv-123')
  })

  it('posts a new event payload for publishing', async () => {
    respondWith({ eventId: 'evt-1', topicId: 'topic-1' })

    const payload = {
      topic: 'consent.revoke',
      purposes: ['MARKETING'],
      payload: { consentId: 'c1', status: 'REVOKED' },
    }

    await publishEvent(payload)

    const req = sentRequest()
    const url = new URL(req.url)
    expect(url.pathname).toBe('/api/dpdp/event-notifications/v1.1/events')
    expect(req.method).toBe('POST')
    expect(JSON.parse(String(req.data))).toEqual(payload)
  })

  it('fetches event details by eventId', async () => {
    respondWith({
      eventId: 'evt-101',
      topic: 'consent.revoke',
      payload: '{"key":"val"}',
      purposes: ['MARKETING'],
      occurredAt: 1710000000000,
    })

    const result = await fetchEventById('evt-101')

    const req = sentRequest()
    const url = new URL(req.url)
    expect(url.pathname).toBe('/api/dpdp/event-notifications/v1.1/events/evt-101')
    expect(req.method).toBe('GET')
    expect(result.eventId).toBe('evt-101')
  })

  it('fetches event downstream deliveries by eventId', async () => {
    respondWith({
      items: [
        {
          deliveryId: 'dlv-1',
          eventId: 'evt-101',
          subscriptionId: 'sub-1',
          currentStatus: 'DELIVERED',
        },
      ],
      total: 1,
    })

    const result = await fetchEventDeliveries('evt-101', 10, 0)

    const req = sentRequest()
    const url = new URL(req.url)
    expect(url.pathname).toBe('/api/dpdp/event-notifications/v1.1/events/evt-101/deliveries')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      limit: '10',
      offset: '0',
    })
    expect(req.method).toBe('GET')
    expect(result.items).toHaveLength(1)
  })
})
