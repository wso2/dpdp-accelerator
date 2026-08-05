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
import { apiRequest } from '../utils/apiClient'
import {
  getAccessTokenPart1,
  getUserProfile,
  isAuthenticated,
  isAuthEnabled,
  login,
  logout,
  readCookie,
  refreshSession,
} from '../utils/authClient'

function setCookie(name: string, value: string): void {
  document.cookie = `${encodeURIComponent(name)}=${encodeURIComponent(value)}; Path=/`
}

function clearCookies(): void {
  document.cookie.split(';').forEach((item) => {
    const name = item.split('=')[0]?.trim()
    if (name) {
      document.cookie = `${name}=; Max-Age=0; Path=/`
    }
  })
}

afterEach(() => {
  clearCookies()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

describe('portal auth client', () => {
  it('reconstructs and decodes display-only ID-token cookies', () => {
    const payload = btoa(JSON.stringify({ sub: 'user-1', name: 'Portal User' }))
      .replace(/=/g, '')
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
    const token = `header.${payload}.signature`
    const midpoint = Math.floor(token.length / 2)
    setCookie('portal-id-p1', token.slice(0, midpoint))
    setCookie('portal-id-p2', token.slice(midpoint))

    expect(getUserProfile()).toMatchObject({ sub: 'user-1', name: 'Portal User' })
    expect(readCookie('portal-id-p1')).toBe(token.slice(0, midpoint))
  })

  it.each([
    ['missing second half', 'header.payload.', undefined],
    ['wrong segment count', 'only.two', 'halves'],
    ['invalid base64url', 'header.%%%.signature', 'halves'],
    ['invalid JSON', `header.${btoa('not-json')}.signature`, 'halves'],
  ])('rejects malformed ID-token profile data: %s', (_name, part1, part2) => {
    setCookie('portal-id-p1', part1)
    if (part2) {
      setCookie('portal-id-p2', part2)
    }

    expect(getUserProfile()).toBeUndefined()
  })

  it('returns undefined for malformed cookie encoding', () => {
    document.cookie = 'malformed=%E0%A4%A; Path=/'

    expect(readCookie('malformed')).toBeUndefined()
  })

  it('supports configured cookie names and authentication state', () => {
    vi.stubEnv('VITE_AUTH_ENABLED', 'true')
    vi.stubEnv('VITE_AUTH_ACCESS_TOKEN_PART1_COOKIE', 'custom-at')
    setCookie('custom-at', 'custom-access')

    expect(isAuthEnabled()).toBe(true)
    expect(getAccessTokenPart1()).toBe('custom-access')
    expect(isAuthenticated()).toBe(true)

    clearCookies()
    expect(isAuthenticated()).toBe(false)

    vi.stubEnv('VITE_AUTH_ENABLED', 'false')
    expect(isAuthenticated()).toBe(true)
  })

  it('sends the readable refresh half with credentials and resets after failure', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.example')
    vi.stubEnv('VITE_AUTH_REFRESH_TOKEN_PART1_COOKIE', 'custom-rt')
    setCookie('custom-rt', 'refresh readable/part')
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(refreshSession()).rejects.toThrow('session refresh failed')
    await expect(refreshSession()).resolves.toBeUndefined()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    const [requestURL, requestInit] = fetchMock.mock.calls[1] ?? []
    expect(requestURL).toBe('http://api.example/auth/refresh')
    expect(requestInit).toMatchObject({
      method: 'POST',
      credentials: 'include',
      body: 'refresh_token=refresh+readable%2Fpart',
    })
    expect(new Headers(requestInit?.headers).get('Content-Type')).toBe(
      'application/x-www-form-urlencoded',
    )
  })

  it('does not call the refresh endpoint without the readable refresh half', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.example')
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(refreshSession()).rejects.toThrow('refresh token is unavailable')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('starts login at the BFF auth endpoint', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.example/')
    const assign = vi.fn()
    vi.stubGlobal('window', { location: { assign } })

    login()

    expect(assign).toHaveBeenCalledWith('http://api.example/auth/login')
  })

  it('starts login without calling logout when the access half is unavailable', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.example')
    const fetchMock = vi.fn()
    const assign = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('window', { location: { assign } })

    await logout()

    expect(fetchMock).not.toHaveBeenCalled()
    expect(assign).toHaveBeenCalledWith('http://api.example/auth/login')
  })

  it('logs out with the readable access half and navigates to the returned URL', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.example')
    vi.stubEnv('VITE_AUTH_LOGOUT_ALLOWED_ORIGINS', 'https://idp.example')
    setCookie('portal-at-p1', 'access-part')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ logoutUrl: 'https://idp.example/logout' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const assign = vi.fn()
    vi.stubGlobal('window', { location: { assign } })

    await logout()

    expect(fetchMock).toHaveBeenCalledOnce()
    const [requestURL, requestInit] = fetchMock.mock.calls[0] ?? []
    expect(requestURL).toBe('http://api.example/auth/logout')
    expect(requestInit).toMatchObject({ method: 'POST', credentials: 'include' })
    expect(new Headers(requestInit?.headers).get('Authorization')).toBe('Bearer access-part')
    expect(assign).toHaveBeenCalledWith('https://idp.example/logout')
  })

  it('ignores malformed logout origins without discarding valid configured origins', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.example')
    vi.stubEnv(
      'VITE_AUTH_LOGOUT_ALLOWED_ORIGINS',
      'not-a-url,ftp://unsupported.example,https://idp.example',
    )
    setCookie('portal-at-p1', 'access-part')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ logoutUrl: 'https://idp.example/logout' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const assign = vi.fn()
    vi.stubGlobal('window', { location: { assign, origin: 'http://portal.example' } })

    await logout()

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(assign).toHaveBeenCalledWith('https://idp.example/logout')
  })

  it('rejects a BFF-returned logout URL outside the navigation allowlist', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.example')
    vi.stubEnv('VITE_AUTH_LOGOUT_ALLOWED_ORIGINS', 'https://idp.example')
    setCookie('portal-at-p1', 'access-part')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ logoutUrl: 'https://attacker.example/logout' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )
    const assign = vi.fn()
    vi.stubGlobal('window', { location: { assign, origin: 'http://portal.example' } })

    await expect(logout()).rejects.toThrow('navigation URL origin is not allowed')
    expect(assign).not.toHaveBeenCalled()
  })

  it.each([
    ['failed response', new Response(null, { status: 500 }), 'logout failed'],
    [
      'missing logout URL',
      new Response(JSON.stringify({}), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
      'logout URL is unavailable',
    ],
  ])('rejects logout when the BFF returns a %s', async (_name, response, message) => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.example')
    setCookie('portal-at-p1', 'access-part')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))

    await expect(logout()).rejects.toThrow(message)
  })

  it('deduplicates concurrent refreshes and retries each request once', async () => {
    vi.stubEnv('VITE_AUTH_ENABLED', 'true')
    setCookie('portal-at-p1', 'old-access-part')
    setCookie('portal-rt-p1', 'refresh-part')

    let apiCalls = 0
    let refreshCalls = 0
    const retryHeaders: string[] = []
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const requestURL = String(input)
      if (requestURL.endsWith('/auth/refresh')) {
        refreshCalls += 1
        setCookie('portal-at-p1', 'new-access-part')
        return new Response(null, { status: 204 })
      }
      apiCalls += 1
      if (apiCalls <= 2) {
        return new Response(JSON.stringify({ code: 'UNAUTHORIZED' }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        })
      }
      retryHeaders.push(new Headers(init?.headers).get('Authorization') ?? '')
      return new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    await Promise.all([apiRequest('/first'), apiRequest('/second')])

    expect(refreshCalls).toBe(1)
    expect(apiCalls).toBe(4)
    expect(retryHeaders).toEqual(['Bearer new-access-part', 'Bearer new-access-part'])
  })
})
