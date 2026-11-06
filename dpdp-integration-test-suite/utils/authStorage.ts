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

import type { BrowserContext } from '@playwright/test'

// The DPDP Consent Portal application is registered with `bindingType: cookie` /
// `validateTokenBinding: true` (see DPDPConsentPortalAppProvisioningUtil, which sets both when the
// accelerator auto-provisions the application) - WSO2 IS ties every access token to
// an opaque value in this HttpOnly cookie and rejects a token replayed without it (see
// CookieBasedTokenBinder in org.wso2.carbon.identity.oauth). The access token itself is never
// readable by page JS either way (the SPA keeps it inside its auth SDK's web worker, see
// fixtures/auth.fixtures.ts's loginAndCaptureState for how a test captures one anyway) - HttpOnly
// only blocks `document.cookie`, not `BrowserContext.storageState()`, which still captures it.
const ACCESS_TOKEN_BINDING_COOKIE = 'atbv'

export interface AuthHeaders {
  Authorization: string
  Cookie: string
}

/**
 * The exact shape `BrowserContext.storageState()` returns (and `browser.newContext({storageState})`
 * accepts back) when called with no `path`.
 */
export type PersonaStorageState = Awaited<ReturnType<BrowserContext['storageState']>>

/**
 * Everything fixtures/auth.fixtures.ts's getPersonaState needs to authenticate as a persona
 * outside the browser: the token-binding cookie (from storageState) plus the bearer token itself,
 * which storageState never carries - see loginAndCaptureState for where this comes from. Persisted
 * as-is, JSON-serialized, to `.auth/<persona>.json`.
 */
export interface PersonaAuthState {
  storageState: PersonaStorageState
  bearerToken: string
}

function readCookie(state: PersonaStorageState, name: string): string {
  const cookie = state.cookies.find((candidate) => candidate.name === name)
  if (!cookie) {
    throw new Error(`Cookie "${name}" was not found in the persona's session state - login may have failed.`)
  }
  return cookie.value
}

/**
 * Turns a persona's captured auth state into the two headers a raw (non-browser) API call needs
 * to authenticate as that persona against WSO2 IS directly: the bearer token, and the token-binding
 * cookie IS checks it against (see ACCESS_TOKEN_BINDING_COOKIE above) - an HTTP client can send an
 * arbitrary `Cookie` header same as a real browser would, `HttpOnly` notwithstanding.
 */
export function authHeadersFromPersonaState(state: PersonaAuthState): AuthHeaders {
  return {
    Authorization: `Bearer ${state.bearerToken}`,
    Cookie: `${ACCESS_TOKEN_BINDING_COOKIE}=${readCookie(state.storageState, ACCESS_TOKEN_BINDING_COOKIE)}`,
  }
}

export function serializeCookies(state: PersonaStorageState): string {
  return state.cookies.map((cookie) => `${cookie.name}=${cookie.value}`).join('; ')
}

/**
 * Builds `AuthHeaders` for a newly minted access token, updating the token-binding cookie from a
 * token response's `set-cookie` header when present and falling back to the persona's captured
 * session state otherwise.
 */
export function authHeadersForMintedToken(
  state: PersonaStorageState,
  accessToken: string,
  setCookieHeader?: string,
): AuthHeaders {
  const match = setCookieHeader?.match(
    new RegExp(`(?:^|[;\\r\\n]\\s*)${ACCESS_TOKEN_BINDING_COOKIE}=([^;\\r\\n]+)`),
  )
  const bindingValue = match ? match[1] : readCookie(state, ACCESS_TOKEN_BINDING_COOKIE)
  return {
    Authorization: `Bearer ${accessToken}`,
    Cookie: `${ACCESS_TOKEN_BINDING_COOKIE}=${bindingValue}`,
  }
}
