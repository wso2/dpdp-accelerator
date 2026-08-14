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

import { describe, expect, it } from 'vitest'
import {
  canAccessRoute,
  defaultRouteForScope,
  isFencedRoute,
} from '../features/nominee/actingAs/policy'
import type { NomineePermission } from '../types/nominee'

const VIEW_ONLY: NomineePermission[] = ['CONSENT_VIEW']
const VIEW_AND_REVOKE: NomineePermission[] = ['CONSENT_VIEW', 'CONSENT_REVOKE']

describe('acting route policy', () => {
  it.each(['/consents', '/consents/abc', '/nominee/manage/owner-1'])(
    'lets a nominee holding CONSENT_VIEW open %s',
    (path) => {
      expect(canAccessRoute(path, VIEW_ONLY)).toBe(true)
      expect(canAccessRoute(path, VIEW_AND_REVOKE)).toBe(true)
    },
  )

  it('refuses the consents pages without CONSENT_VIEW', () => {
    expect(canAccessRoute('/consents', [])).toBe(false)
    expect(canAccessRoute('/consents', ['CONSENT_REVOKE'])).toBe(false)
  })

  it('refuses the dashboard unless ACCOUNT_VIEW was granted', () => {
    expect(canAccessRoute('/dashboard', VIEW_AND_REVOKE)).toBe(false)
    expect(canAccessRoute('/dashboard', ['ACCOUNT_VIEW'])).toBe(true)
  })

  // Delegation is not transitive, and an owner's credentials and settings are
  // never exercisable by someone else, whatever they were granted.
  it.each(['/nominations', '/profile', '/settings', '/security', '/admin/nominees'])(
    'keeps %s fenced off however broad the grant',
    (path) => {
      const everything: NomineePermission[] = [
        'CONSENT_VIEW',
        'CONSENT_REVOKE',
        'CONSENT_APPROVE',
        'ACCOUNT_VIEW',
        'ACCOUNT_UPDATE',
        'ACCOUNT_DELETE',
      ]
      expect(canAccessRoute(path, everything)).toBe(false)
    },
  )

  it('sends a nominee to a page their grant actually covers', () => {
    expect(defaultRouteForScope(VIEW_ONLY)).toBe('/consents')
    expect(defaultRouteForScope(['ACCOUNT_VIEW'])).toBe('/dashboard')
  })

  // Landing a nominee on a fenced route produces a page that refuses them and
  // offers to send them to the same place, which cannot be escaped.
  it.each<[string, NomineePermission[]]>([
    ['nothing granted', []],
    ['revoke without view', ['CONSENT_REVOKE']],
    ['approve without view', ['CONSENT_APPROVE']],
    ['update without view', ['ACCOUNT_UPDATE']],
  ])('never lands on a fenced route: %s', (_name, scope) => {
    const landing = defaultRouteForScope(scope)
    expect(landing === null || !isFencedRoute(landing)).toBe(true)
    if (landing !== null) {
      expect(canAccessRoute(landing, scope)).toBe(true)
    }
  })

  // Every grant either resolves to a page the nominee can open, or to nothing at
  // all - it must never resolve to a page that will refuse them.
  it.each<[NomineePermission[]]>([
    [[]],
    [['CONSENT_VIEW']],
    [['CONSENT_REVOKE']],
    [['CONSENT_VIEW', 'CONSENT_REVOKE']],
    [['CONSENT_APPROVE']],
    [['ACCOUNT_VIEW']],
    [['CONSENT_VIEW', 'ACCOUNT_VIEW']],
    [['CONSENT_VIEW', 'CONSENT_REVOKE', 'CONSENT_APPROVE']],
    [['CONSENT_VIEW', 'CONSENT_REVOKE', 'ACCOUNT_UPDATE']],
  ])('resolves %j to an openable page or to nothing', (scope) => {
    const landing = defaultRouteForScope(scope)
    if (landing !== null) {
      expect(canAccessRoute(landing, scope)).toBe(true)
    }
  })
})
