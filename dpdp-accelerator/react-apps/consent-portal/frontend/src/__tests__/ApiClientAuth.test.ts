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

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { APIError, apiRequest, apiRequestNoContent } from '../utils/apiClient'

const authMocks = vi.hoisted(() => ({
  getAccessTokenPart1: vi.fn<() => string | undefined>(),
  isAuthEnabled: vi.fn<() => boolean>(),
  login: vi.fn<() => void>(),
  refreshSession: vi.fn<() => Promise<void>>(),
}))

vi.mock('../utils/authClient', () => authMocks)

function jsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.example/')
  authMocks.getAccessTokenPart1.mockReturnValue('access-part')
  authMocks.isAuthEnabled.mockReturnValue(true)
  authMocks.refreshSession.mockResolvedValue()
})

afterEach(() => {
  vi.clearAllMocks()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

describe('authenticated API client', () => {
  it('attaches the readable access half and credentials to requests', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      apiRequest('/consents', { query: { limit: 10, active: true, ignored: undefined } }),
    ).resolves.toEqual({ ok: true })

    const [requestURL, requestInit] = fetchMock.mock.calls[0] ?? []
    expect(requestURL).toBe('http://api.example/consents?limit=10&active=true')
    expect(requestInit?.credentials).toBe('include')
    const headers = new Headers(requestInit?.headers)
    expect(headers.get('Accept')).toBe('application/json')
    expect(headers.get('Authorization')).toBe('Bearer access-part')
  })

  it('does not overwrite a caller-supplied Authorization header', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/consents', { headers: { Authorization: 'Custom credential' } })

    const [, requestInit] = fetchMock.mock.calls[0] ?? []
    expect(new Headers(requestInit?.headers).get('Authorization')).toBe('Custom credential')
  })

  it('does not refresh or start login for a 403 response', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          jsonResponse({ code: 'FORBIDDEN', message: 'insufficient permissions' }, 403),
        ),
    )

    await expect(apiRequest('/admin')).rejects.toMatchObject({
      name: 'APIError',
      status: 403,
      code: 'FORBIDDEN',
      message: 'insufficient permissions',
    })
    expect(authMocks.refreshSession).not.toHaveBeenCalled()
    expect(authMocks.login).not.toHaveBeenCalled()
  })

  it('retries only once and starts login when the retry is also unauthorized', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ code: 'UNAUTHORIZED' }, 401))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiRequest('/consents')).rejects.toMatchObject({ status: 401 })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(authMocks.refreshSession).toHaveBeenCalledOnce()
    expect(authMocks.login).toHaveBeenCalledOnce()
  })

  it('starts login when refresh fails without creating a retry loop', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ code: 'UNAUTHORIZED' }, 401))
    authMocks.refreshSession.mockRejectedValueOnce(new Error('refresh failed'))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiRequest('/consents')).rejects.toMatchObject({ status: 401 })

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(authMocks.refreshSession).toHaveBeenCalledOnce()
    expect(authMocks.login).toHaveBeenCalledOnce()
  })

  it('does not refresh or start login for a 401 when authentication is disabled', async () => {
    authMocks.isAuthEnabled.mockReturnValue(false)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({}, 401)))

    await expect(apiRequest('/consents')).rejects.toMatchObject({ status: 401 })

    expect(authMocks.refreshSession).not.toHaveBeenCalled()
    expect(authMocks.login).not.toHaveBeenCalled()
  })

  it('supports no-content requests and one refresh retry', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ code: 'UNAUTHORIZED' }, 401))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiRequestNoContent('/consents/1')).resolves.toBeUndefined()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(authMocks.refreshSession).toHaveBeenCalledOnce()
    expect(authMocks.login).not.toHaveBeenCalled()
  })

  it('starts login when a no-content retry remains unauthorized', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({}, 401))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiRequestNoContent('/consents/1')).rejects.toMatchObject({ status: 401 })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(authMocks.refreshSession).toHaveBeenCalledOnce()
    expect(authMocks.login).toHaveBeenCalledOnce()
  })

  it('uses fallback API errors for non-JSON responses', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('failure', { status: 502 })))

    const failure = apiRequest('/consents')
    await expect(failure).rejects.toBeInstanceOf(APIError)
    await expect(failure).rejects.toMatchObject({
      status: 502,
      code: 'API_REQUEST_FAILED',
      message: 'request failed with status 502',
    })
  })

  it('treats an empty API base URL as same-origin', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '')
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({}))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/consents')
    expect(fetchMock).toHaveBeenCalledWith(`${window.location.origin}/consents`, expect.anything())
  })
})
