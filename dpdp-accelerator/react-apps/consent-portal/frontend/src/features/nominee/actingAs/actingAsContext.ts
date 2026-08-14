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

import { createContext, useContext } from 'react'
import type { NomineePermission } from '../../../types/nominee'

export interface ActingSession {
  ownerId: string
  /** The real human acting. Recorded so the UI can always name the actor. */
  nomineeId: string
  scope: NomineePermission[]
  /** ISO timestamp at which the impersonation token expires. */
  expiresAt: string
}

/**
 * Maps the OAuth scopes the BFF reports back to the permission vocabulary the
 * UI's route policy is written in.
 *
 * The scopes are the authoritative narrowing - they were fixed by the Identity
 * Server at mint time to what the owner granted. This mapping exists only so the
 * frontend can hide controls the token could never exercise; it is never the
 * thing that enforces them.
 */
const SCOPE_TO_PERMISSION: Record<string, NomineePermission> = {
  'portal:consents:read:self': 'CONSENT_VIEW',
  'portal:consents:write:self': 'CONSENT_REVOKE',
  'portal:consents:approve:self': 'CONSENT_APPROVE',
  'portal:profile:read:self': 'ACCOUNT_VIEW',
  'portal:profile:write:self': 'ACCOUNT_UPDATE',
  'portal:profile:delete:self': 'ACCOUNT_DELETE',
}

export function permissionsFromScopes(scopes: string[]): NomineePermission[] {
  const permissions = new Set<NomineePermission>()
  scopes.forEach((scope) => {
    const permission = SCOPE_TO_PERMISSION[scope]
    if (permission) {
      permissions.add(permission)
    }
  })
  return Array.from(permissions)
}

export interface ActingAsContextValue {
  session: ActingSession | null
  startActing: (session: ActingSession) => void
  stopActing: () => void
}

/** Kept in sessionStorage so each browser tab acts independently. */
export const ACTING_STORAGE_KEY = 'openfgc_acting_as'

export function readStoredSession(): ActingSession | null {
  try {
    const raw = window.sessionStorage.getItem(ACTING_STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as ActingSession
    if (!parsed.ownerId || !parsed.nomineeId || !Array.isArray(parsed.scope)) {
      return null
    }
    // A stored session outliving its token would leave the UI in acting mode
    // while every request 401s. Treat it as no session at all.
    if (parsed.expiresAt && Date.parse(parsed.expiresAt) <= Date.now()) {
      return null
    }
    return parsed
  } catch {
    return null
  }
}

export const ActingAsContext = createContext<ActingAsContextValue>({
  session: null,
  startActing: () => undefined,
  stopActing: () => undefined,
})

export function useActingAs(): ActingAsContextValue {
  return useContext(ActingAsContext)
}
