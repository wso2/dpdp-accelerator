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

import { apiRequest, apiRequestNoContent } from '../../../utils/apiClient'

export interface ActingSessionResponse {
  ownerId: string
  nomineeId: string
  scopes: string[]
  expiresAt: string
}

/**
 * Result of reading the impersonation callback fragment.
 *
 * `subjectToken` is single-use and short-lived. It is exchanged immediately and
 * never stored: on its own it is not a credential, because the exchange also
 * requires the client secret which only the BFF holds.
 */
export interface ActingCallbackParams {
  subjectToken: string
  state: string
}

/** Whether the acting flow is configured well enough to be started at all. */
export function canStartActing(): boolean {
  return Boolean(import.meta.env.VITE_API_BASE_URL)
}

/**
 * Begins the impersonation flow by navigating the whole browser to the BFF.
 *
 * This must be a top-level navigation, not fetch(): the Identity Server
 * identifies the impersonator from its own session cookie, and answers with a
 * cross-origin redirect whose fragment only the browser can read. An XHR would
 * carry neither.
 */
export function redirectToActingStart(ownerId: string): void {
  const baseURL = import.meta.env.VITE_API_BASE_URL
  if (!baseURL) {
    throw new Error('VITE_API_BASE_URL is required to start an acting session.')
  }
  const normalizedBase = baseURL.endsWith('/') ? baseURL.slice(0, -1) : baseURL
  // Resolved against the page origin: the acting flow is a full-page
  // navigation built by hand, and a path-only base is not a valid URL.
  const url = new URL(`${normalizedBase}/acting-api/start`, window.location.origin)
  url.searchParams.set('ownerId', ownerId)
  window.location.assign(url.toString())
}

/**
 * Extracts the impersonation result from a callback URL fragment.
 *
 * Returns null when the fragment carries no subject token, which is the normal
 * case for any other navigation to this route.
 */
export function readActingCallback(hash: string): ActingCallbackParams | null {
  const fragment = hash.startsWith('#') ? hash.slice(1) : hash
  if (!fragment) {
    return null
  }
  const params = new URLSearchParams(fragment)
  const subjectToken = params.get('subject_token')
  const state = params.get('state')
  if (!subjectToken || !state) {
    return null
  }
  return { subjectToken, state }
}

/**
 * Exchanges the subject token for an impersonation access token.
 *
 * The BFF performs the exchange server-side and stores the resulting token in an
 * HttpOnly cookie, so the token itself never enters JavaScript.
 */
export async function exchangeActingSession(
  params: ActingCallbackParams,
): Promise<ActingSessionResponse> {
  return apiRequest<ActingSessionResponse>('/acting-api/exchange', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  })
}

/** Ends the acting session for this browser by clearing the BFF's cookies. */
export async function stopActingSession(): Promise<void> {
  await apiRequestNoContent('/acting-api/stop', { method: 'POST' })
}
