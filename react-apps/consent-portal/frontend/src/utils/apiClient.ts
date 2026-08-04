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

import { getAccessTokenPart1, isAuthEnabled, login, refreshSession } from './authClient'

export interface APIErrorPayload {
  code?: string
  message?: string
}

export class APIError extends Error {
  public readonly status: number

  public readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'APIError'
    this.status = status
    this.code = code
  }
}

interface RequestOptions extends RequestInit {
  query?: Record<string, string | number | boolean | undefined>
}

function buildHeaders(headers?: HeadersInit): Headers {
  const normalizedHeaders = new Headers(headers)

  if (!normalizedHeaders.has('Accept')) {
    normalizedHeaders.set('Accept', 'application/json')
  }

  const accessTokenPart = getAccessTokenPart1()
  if (accessTokenPart && !normalizedHeaders.has('Authorization')) {
    normalizedHeaders.set('Authorization', `Bearer ${accessTokenPart}`)
  }

  return normalizedHeaders
}

/**
 * Builds an absolute request URL from the configured API base URL and query params.
 *
 * The base URL may be absolute (cross-origin BFF) or a same-origin path such as
 * "/consent-portal" when the portal is served from the same webapp as the BFF.
 */
function buildURL(path: string, query?: RequestOptions['query']): string {
  const baseURL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''

  if (/^https?:\/\//i.test(path)) {
    throw new Error(`apiClient path must be relative, received: "${path}"`)
  }

  const normalizedBase = baseURL.endsWith('/') ? baseURL.slice(0, -1) : baseURL
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const url = new URL(`${normalizedBase}${normalizedPath}`, window.location.origin)

  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value === undefined) {
        return
      }
      url.searchParams.set(key, String(value))
    })
  }

  return url.toString()
}

async function createAPIError(response: Response): Promise<APIError> {
  let payload: APIErrorPayload | undefined

  try {
    payload = (await response.json()) as APIErrorPayload
  } catch {
    payload = undefined
  }

  return new APIError(
    response.status,
    payload?.code ?? 'API_REQUEST_FAILED',
    payload?.message ?? `request failed with status ${response.status}`,
  )
}

/**
 * Sends an API request and parses the response body as JSON.
 *
 * Use this helper for endpoints that always return a JSON payload.
 */
async function apiRequestInternal<T>(
  path: string,
  options: RequestOptions,
  allowRefresh: boolean,
): Promise<T> {
  const { query, headers, ...requestInit } = options
  const response = await fetch(buildURL(path, query), {
    credentials: 'include',
    ...requestInit,
    headers: buildHeaders(headers),
  })

  if (response.status === 401 && allowRefresh && isAuthEnabled()) {
    try {
      await refreshSession()
    } catch {
      login()
      throw await createAPIError(response)
    }
    return apiRequestInternal<T>(path, options, false)
  }
  if (response.status === 401 && !allowRefresh && isAuthEnabled()) {
    login()
  }

  if (!response.ok) {
    throw await createAPIError(response)
  }

  if (response.status === 204) {
    throw new Error(
      `apiRequest received a 204 No Content response for path "${path}". ` +
        'Use apiRequestNoContent instead for endpoints that return no body.',
    )
  }

  return (await response.json()) as T
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return apiRequestInternal<T>(path, options, true)
}

/**
 * Sends an API request for endpoints that are expected to return no content.
 *
 * This helper is intended for APIs that commonly respond with HTTP 204.
 * If a successful response includes a payload unexpectedly, it is ignored.
 */
async function apiRequestNoContentInternal(
  path: string,
  options: RequestOptions,
  allowRefresh: boolean,
): Promise<void> {
  const { query, headers, ...requestInit } = options
  const response = await fetch(buildURL(path, query), {
    credentials: 'include',
    ...requestInit,
    headers: buildHeaders(headers),
  })

  if (response.status === 401 && allowRefresh && isAuthEnabled()) {
    try {
      await refreshSession()
    } catch {
      login()
      throw await createAPIError(response)
    }
    await apiRequestNoContentInternal(path, options, false)
    return
  }
  if (response.status === 401 && !allowRefresh && isAuthEnabled()) {
    login()
  }

  if (!response.ok) {
    throw await createAPIError(response)
  }
}

export async function apiRequestNoContent(
  path: string,
  options: RequestOptions = {},
): Promise<void> {
  return apiRequestNoContentInternal(path, options, true)
}
