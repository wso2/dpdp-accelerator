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
  approveMyConsent,
  fetchMyConsents,
  rejectMyConsent,
  revokeMyConsent,
} from '../features/consent-registry/api/consentsApi'
import { APIError, apiRequest } from '../utils/apiClient'

const fetchMock = vi.fn()

afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
})

function mockOkResponse(payload: unknown = { status: 'OK' }): void {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => payload,
  })
}

describe('self-service consent API', () => {
  it('sends only the supported list parameters', async () => {
    mockOkResponse({ data: [], metadata: { total: 0, offset: 0, count: 0, limit: 10 } })

    await fetchMyConsents({
      limit: 10,
      offset: 20,
      consentStatuses: 'PENDING',
      serviceId: 'dpdp-portal',
    })

    const [requestUrl] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/me/consents')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      consentStatuses: 'PENDING',
      serviceId: 'dpdp-portal',
      limit: '10',
      offset: '20',
    })
  })

  it('approves the whole consent with an empty body', async () => {
    mockOkResponse()

    await approveMyConsent('consent/123?draft')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [requestUrl, requestInit] = fetchMock.mock.calls[0] ?? []
    const requestHeaders = new Headers((requestInit?.headers as HeadersInit | undefined) ?? {})

    expect(String(requestUrl)).toContain('/me/consents/consent%2F123%3Fdraft/approve')
    expect(requestInit).toMatchObject({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({}),
    })
    expect(requestHeaders.get('Accept')).toBe('application/json')
    expect(requestHeaders.get('Content-Type')).toBe('application/json')
  })

  it('rejects consent with POST and an empty body', async () => {
    mockOkResponse()

    await rejectMyConsent('consent/123?draft')

    const [requestUrl, requestInit] = fetchMock.mock.calls[0] ?? []
    expect(String(requestUrl)).toContain('/me/consents/consent%2F123%3Fdraft/reject')
    expect(requestInit).toMatchObject({ method: 'POST', body: JSON.stringify({}) })
  })

  it('revokes consent with POST and an empty body', async () => {
    mockOkResponse()

    await revokeMyConsent('consent-123')

    const [requestUrl, requestInit] = fetchMock.mock.calls[0] ?? []
    expect(String(requestUrl)).toContain('/me/consents/consent-123/revoke')
    expect(requestInit).toMatchObject({ method: 'POST', body: JSON.stringify({}) })
  })

  it('surfaces the INVALID_CONSENT_STATE message from a 409 response', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({
        code: 'INVALID_CONSENT_STATE',
        message: 'Consent db1f6e7a is not in PENDING state.',
      }),
    })

    await expect(approveMyConsent('db1f6e7a')).rejects.toMatchObject({
      code: 'INVALID_CONSENT_STATE',
      status: 409,
      message: 'Consent db1f6e7a is not in PENDING state.',
    })
    await expect(approveMyConsent('db1f6e7a')).rejects.toBeInstanceOf(APIError)
  })
})

describe('apiRequest', () => {
  it('rejects successful responses without a JSON body', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 204,
    })

    await expect(apiRequest<unknown>('/empty')).rejects.toThrow('Use apiRequestNoContent instead')
  })

  it('rejects absolute paths before sending a request', async () => {
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiRequest<unknown>('https://example.com/consents')).rejects.toThrow(
      'apiClient path must be relative',
    )
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
