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
  it('passes all supported administrative filters to the BFF', async () => {
    mockJSONResponse({ data: [], metadata: { total: 0, offset: 0, count: 0, limit: 25 } })

    await fetchAdminConsents({
      consentStatuses: 'ACTIVE',
      userIds: 'user-1,user-2',
      groupIds: 'group-1,group-2',
      purposeName: 'payments',
      purposeVersion: 'v2',
      elementName: 'account-number',
      elementNamespace: 'banking',
      elementVersion: 'v3',
      sort: 'updatedTime:desc',
      fromTime: 1000,
      toTime: 2000,
      limit: 25,
      offset: 50,
    })

    const [requestUrl, requestInit] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/consents')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      consentStatuses: 'ACTIVE',
      userIds: 'user-1,user-2',
      groupIds: 'group-1,group-2',
      purposeName: 'payments',
      purposeVersion: 'v2',
      elementName: 'account-number',
      elementNamespace: 'banking',
      elementVersion: 'v3',
      sort: 'updatedTime:desc',
      fromTime: '1000',
      toTime: '2000',
      limit: '25',
      offset: '50',
      details: 'true',
    })
    expect(requestInit).toMatchObject({ method: 'GET', credentials: 'include' })
  })

  it('loads encoded consent details with history', async () => {
    mockJSONResponse({ id: 'consent/123' })

    await fetchAdminConsentByID('consent/123')

    const [requestUrl] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/consents/consent%2F123')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      details: 'true',
      includeStatusHistory: 'true',
    })
  })

  it('sends the current user as actionBy when revoking', async () => {
    mockJSONResponse()

    await revokeAdminConsent('consent-123', 'user-from-me')

    const [, requestInit] = fetchMock.mock.calls[0] ?? []
    expect(requestInit).toMatchObject({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ actionBy: 'user-from-me' }),
    })
    expect(new Headers(requestInit?.headers as HeadersInit).get('Content-Type')).toBe(
      'application/json',
    )
  })
})
