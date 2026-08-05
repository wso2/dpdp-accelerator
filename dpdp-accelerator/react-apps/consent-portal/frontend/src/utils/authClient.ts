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

export type UserProfile = Record<string, unknown>

interface LogoutResponse {
  logoutUrl: string
}

let refreshPromise: Promise<void> | undefined

function envCookieName(key: string, fallback: string): string {
  return (import.meta.env[key] as string | undefined) || fallback
}

function apiURL(path: string): string {
  const baseURL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''
  const normalizedBase = baseURL.endsWith('/') ? baseURL.slice(0, -1) : baseURL
  // Resolve same-origin bases such as "/consent-portal" to an absolute URL.
  return new URL(`${normalizedBase}${path}`, window.location.origin).toString()
}

function httpURL(value: string): URL {
  const url = new URL(value)
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error('navigation URL must use http or https')
  }
  return url
}

function allowedNavigationOrigins(): Set<string> {
  const origins = new Set([httpURL(apiURL('/')).origin])
  if (window.location.origin) {
    origins.add(window.location.origin)
  }

  const configured = import.meta.env.VITE_AUTH_LOGOUT_ALLOWED_ORIGINS as string | undefined
  const configuredOrigins = configured
    ?.split(',')
    .map((origin) => origin.trim())
    .filter(Boolean)

  configuredOrigins?.forEach((origin) => {
    try {
      origins.add(httpURL(origin).origin)
    } catch {
      // Invalid configured entries fail closed without disabling valid origins.
    }
  })

  return origins
}

function navigate(value: string): void {
  const url = httpURL(value)
  if (!allowedNavigationOrigins().has(url.origin)) {
    throw new Error('navigation URL origin is not allowed')
  }
  window.location.assign(url.toString())
}

export function isAuthEnabled(): boolean {
  return import.meta.env.VITE_AUTH_ENABLED === 'true'
}

export function readCookie(name: string): string | undefined {
  const prefix = `${encodeURIComponent(name)}=`
  const value = document.cookie
    .split(';')
    .map((item) => item.trim())
    .find((item) => item.startsWith(prefix))
  if (value) {
    try {
      return decodeURIComponent(value.slice(prefix.length))
    } catch {
      return undefined
    }
  }
  return undefined
}

export function getAccessTokenPart1(): string | undefined {
  return readCookie(envCookieName('VITE_AUTH_ACCESS_TOKEN_PART1_COOKIE', 'portal-at-p1'))
}

function getRefreshTokenPart1(): string | undefined {
  return readCookie(envCookieName('VITE_AUTH_REFRESH_TOKEN_PART1_COOKIE', 'portal-rt-p1'))
}

function getIDToken(): string | undefined {
  const part1 = readCookie(envCookieName('VITE_AUTH_ID_TOKEN_PART1_COOKIE', 'portal-id-p1'))
  const part2 = readCookie(envCookieName('VITE_AUTH_ID_TOKEN_PART2_COOKIE', 'portal-id-p2'))
  return part1 && part2 ? part1 + part2 : undefined
}

export function isAuthenticated(): boolean {
  return !isAuthEnabled() || Boolean(getAccessTokenPart1())
}

export function getUserProfile(): UserProfile | undefined {
  const idToken = getIDToken()
  if (!idToken) {
    return undefined
  }
  const segments = idToken.split('.')
  if (segments.length !== 3) {
    return undefined
  }
  try {
    const normalized = segments[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    const bytes = Uint8Array.from(atob(padded), (character) => character.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes)) as UserProfile
  } catch {
    return undefined
  }
}

export function login(): void {
  navigate(apiURL('/auth/login'))
}

export function refreshSession(): Promise<void> {
  if (refreshPromise) {
    return refreshPromise
  }
  refreshPromise = (async () => {
    const refreshPart = getRefreshTokenPart1()
    if (!refreshPart) {
      throw new Error('refresh token is unavailable')
    }
    const body = new URLSearchParams({ refresh_token: refreshPart })
    const response = await fetch(apiURL('/auth/refresh'), {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString(),
    })
    if (!response.ok) {
      throw new Error('session refresh failed')
    }
  })().finally(() => {
    refreshPromise = undefined
  })
  return refreshPromise
}

export async function logout(): Promise<void> {
  const accessPart = getAccessTokenPart1()
  if (!accessPart) {
    login()
    return
  }
  const response = await fetch(apiURL('/auth/logout'), {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessPart}`,
    },
  })
  if (!response.ok) {
    throw new Error('logout failed')
  }
  const payload = (await response.json()) as LogoutResponse
  if (!payload.logoutUrl) {
    throw new Error('logout URL is unavailable')
  }
  navigate(payload.logoutUrl)
}
