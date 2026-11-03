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
import { createTopic, deleteTopic, fetchTopics } from '../features/events/api/topicsApi'

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

describe('topicsApi', () => {
  it('calls fetchTopics with search and pagination query parameters', async () => {
    respondWith({ items: [], total: 0 })

    await fetchTopics({
      limit: 10,
      offset: 20,
      status: 'active',
      search: 'consent.revoke',
    })

    const req = sentRequest()
    const url = new URL(req.url)
    expect(url.pathname).toBe('/api/dpdp/event-notifications/v1.1/topics')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      limit: '10',
      offset: '20',
      status: 'active',
      search: 'consent.revoke',
    })
    expect(req.method).toBe('GET')
  })

  it('posts a new topic payload', async () => {
    respondWith({ topicId: 'tpc-new', name: 'consent.revoke', status: 'active' })

    const payload = {
      name: 'consent.revoke',
      description: 'Consent revocation topic',
    }

    const result = await createTopic(payload)

    const req = sentRequest()
    const url = new URL(req.url)
    expect(url.pathname).toBe('/api/dpdp/event-notifications/v1.1/topics')
    expect(req.method).toBe('POST')
    expect(JSON.parse(String(req.data))).toEqual(payload)
    expect(result).toEqual({ topicId: 'tpc-new', name: 'consent.revoke', status: 'active' })
  })

  it('deletes a topic and returns slim status update', async () => {
    respondWith({ topicId: 'tpc/1', status: 'deregistered' })

    const result = await deleteTopic('tpc/1')

    const req = sentRequest()
    const url = new URL(req.url)
    expect(url.pathname).toBe('/api/dpdp/event-notifications/v1.1/topics/tpc%2F1')
    expect(req.method).toBe('DELETE')
    expect(result).toEqual({ topicId: 'tpc/1', status: 'deregistered' })
  })
})
