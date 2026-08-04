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
  fetchAdminConsentByID,
  fetchAdminConsents,
  revokeAdminConsent,
} from '../features/admin-consents/api/adminConsentsApi'
import { getNextCursor, getPreviousCursor } from '../utils/cursorPagination'

const fetchMock = vi.fn()

afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
})

function mockJSONResponse(payload: unknown = {}): void {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => payload,
  })
}

describe('administrative consent API', () => {
  it('passes the supported cursor and filter parameters to the BFF', async () => {
    mockJSONResponse({ totalResults: 0, links: [], Consents: [] })

    await fetchAdminConsents({
      limit: 25,
      after: 'Mg==',
      subjectId: 'admin',
      serviceId: 'dpdp-portal',
      state: 'ACTIVE',
    })

    const [requestUrl, requestInit] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/consents')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      limit: '25',
      after: 'Mg==',
      subjectId: 'admin',
      serviceId: 'dpdp-portal',
      state: 'ACTIVE',
    })
    expect(requestInit).toMatchObject({ method: 'GET', credentials: 'include' })
  })

  it('sends a before cursor when paging backwards and omits unset filters', async () => {
    mockJSONResponse({ totalResults: 0, links: [], Consents: [] })

    await fetchAdminConsents({ limit: 10, before: 'MQ==' })

    const [requestUrl] = fetchMock.mock.calls[0] ?? []
    expect(Object.fromEntries(new URL(String(requestUrl)).searchParams)).toEqual({
      limit: '10',
      before: 'MQ==',
    })
  })

  it('reads next and previous cursors out of the returned links', async () => {
    const links = [
      {
        rel: 'next',
        href: 'https://localhost:9443/api/identity/consent-mgt/v2.0/consents?limit=2&after=Mg==',
      },
      {
        rel: 'previous',
        href: 'https://localhost:9443/api/identity/consent-mgt/v2.0/consents?limit=2&before=MA==',
      },
    ]

    expect(getNextCursor(links)).toBe('Mg==')
    expect(getPreviousCursor(links)).toBe('MA==')
    expect(getNextCursor([])).toBeUndefined()
    expect(getPreviousCursor(undefined)).toBeUndefined()
  })

  it('loads encoded consent details without extra query parameters', async () => {
    mockJSONResponse({ id: 'consent/123' })

    await fetchAdminConsentByID('consent/123')

    const [requestUrl] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/consents/consent%2F123')
    expect(Object.fromEntries(url.searchParams)).toEqual({})
  })

  it('revokes with an empty JSON body', async () => {
    mockJSONResponse({ status: 'OK' })

    await revokeAdminConsent('consent-123')

    const [requestUrl, requestInit] = fetchMock.mock.calls[0] ?? []
    expect(String(requestUrl)).toContain('/api/consents/consent-123/revoke')
    expect(requestInit).toMatchObject({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({}),
    })
    expect(new Headers(requestInit?.headers as HeadersInit).get('Content-Type')).toBe(
      'application/json',
    )
  })
})
