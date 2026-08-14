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

import { afterEach, describe, expect, it } from 'vitest'
import { readActingCallback } from '../features/nominee/actingAs/actingApi'
import {
  ACTING_STORAGE_KEY,
  permissionsFromScopes,
  readStoredSession,
} from '../features/nominee/actingAs/actingAsContext'

describe('readActingCallback', () => {
  it('extracts subject token and state from the fragment', () => {
    const result = readActingCallback('#subject_token=abc.def.ghi&state=xyz&session_state=ignored')

    expect(result).toEqual({ subjectToken: 'abc.def.ghi', state: 'xyz' })
  })

  it('tolerates a fragment without the leading hash', () => {
    expect(readActingCallback('subject_token=t&state=s')).toEqual({
      subjectToken: 't',
      state: 's',
    })
  })

  it('returns null for an empty fragment', () => {
    expect(readActingCallback('')).toBeNull()
    expect(readActingCallback('#')).toBeNull()
  })

  it('returns null when the state is missing', () => {
    expect(readActingCallback('#subject_token=abc')).toBeNull()
  })

  it('returns null when the subject token is missing', () => {
    expect(readActingCallback('#state=xyz')).toBeNull()
  })

  it('returns null for an unrelated fragment such as an error redirect', () => {
    expect(readActingCallback('#error=access_denied&error_description=denied')).toBeNull()
  })
})

describe('permissionsFromScopes', () => {
  it('maps nominee scopes to the permission vocabulary', () => {
    const permissions = permissionsFromScopes([
      'openid',
      'internal_user_impersonate',
      'portal:consents:read:self',
      'portal:consents:write:self',
    ])

    expect(permissions).toContain('CONSENT_VIEW')
    expect(permissions).toContain('CONSENT_REVOKE')
  })

  it('ignores scopes that do not govern a nominee permission', () => {
    expect(permissionsFromScopes(['openid', 'profile'])).toEqual([])
  })

  // A view-only nominee's token never carries the write scope, so the UI must
  // not offer revoke.
  it('omits revoke when only the read scope was granted', () => {
    const permissions = permissionsFromScopes(['portal:consents:read:self'])

    expect(permissions).toEqual(['CONSENT_VIEW'])
    expect(permissions).not.toContain('CONSENT_REVOKE')
  })

  it('maps the approve scope to its own permission', () => {
    const permissions = permissionsFromScopes([
      'portal:consents:read:self',
      'portal:consents:approve:self',
    ])

    expect(permissions).toContain('CONSENT_APPROVE')
  })

  // Approving authorises new processing; revoking withdraws it. A nominee
  // trusted to revoke must not silently gain the power to consent.
  it('does not confer approve on a nominee granted only revoke', () => {
    const permissions = permissionsFromScopes([
      'portal:consents:read:self',
      'portal:consents:write:self',
    ])

    expect(permissions).toContain('CONSENT_REVOKE')
    expect(permissions).not.toContain('CONSENT_APPROVE')
  })
})

describe('readStoredSession', () => {
  afterEach(() => {
    window.sessionStorage.clear()
  })

  const store = (value: unknown): void => {
    window.sessionStorage.setItem(ACTING_STORAGE_KEY, JSON.stringify(value))
  }

  it('reads a valid session', () => {
    store({
      ownerId: 'owner-1',
      nomineeId: 'nominee-1',
      scope: ['CONSENT_VIEW'],
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    })

    expect(readStoredSession()?.ownerId).toBe('owner-1')
  })

  // Leaving the UI in acting mode after the token expires would show a nominee
  // an owner's account while every request 401s.
  it('discards an expired session', () => {
    store({
      ownerId: 'owner-1',
      nomineeId: 'nominee-1',
      scope: ['CONSENT_VIEW'],
      expiresAt: new Date(Date.now() - 1_000).toISOString(),
    })

    expect(readStoredSession()).toBeNull()
  })

  it('discards a session missing the nominee', () => {
    store({ ownerId: 'owner-1', scope: ['CONSENT_VIEW'] })

    expect(readStoredSession()).toBeNull()
  })

  it('discards malformed json', () => {
    window.sessionStorage.setItem(ACTING_STORAGE_KEY, 'not-json')

    expect(readStoredSession()).toBeNull()
  })
})
