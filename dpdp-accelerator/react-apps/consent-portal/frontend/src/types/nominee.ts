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

/**
 * A right the owner can grant to a nominee. These match Nominee Service's
 * NomineePermission enum names exactly (wire format).
 */
export type NomineePermission =
  | 'CONSENT_VIEW'
  | 'CONSENT_REVOKE'
  | 'CONSENT_APPROVE'
  | 'ACCOUNT_VIEW'
  | 'ACCOUNT_UPDATE'
  | 'ACCOUNT_DELETE'

export interface NomineePermissionOption {
  value: NomineePermission
  labelKey: string
  defaultLabel: string
  risky?: boolean
  /**
   * Withdrawn from the portal: no longer offered when nominating, but still
   * listed here so nominations that were granted it before - or granted it by
   * another client - resolve to a readable label instead of the wire value.
   */
  retired?: boolean
}

/** Every right the service can express, including ones the portal no longer grants. */
export const NOMINEE_PERMISSIONS: NomineePermissionOption[] = [
  {
    value: 'CONSENT_VIEW',
    labelKey: 'nominee.permissions.consentView',
    defaultLabel: 'View consents',
  },
  {
    value: 'CONSENT_REVOKE',
    labelKey: 'nominee.permissions.consentRevoke',
    defaultLabel: 'Revoke consents',
  },
  {
    value: 'CONSENT_APPROVE',
    labelKey: 'nominee.permissions.consentApprove',
    defaultLabel: 'Approve pending consents',
    risky: true,
  },
  {
    value: 'ACCOUNT_VIEW',
    labelKey: 'nominee.permissions.accountView',
    defaultLabel: 'View personal account',
  },
  {
    value: 'ACCOUNT_UPDATE',
    labelKey: 'nominee.permissions.accountUpdate',
    defaultLabel: 'Update personal account',
  },
  {
    value: 'ACCOUNT_DELETE',
    labelKey: 'nominee.permissions.accountDelete',
    defaultLabel: 'Delete personal account',
    risky: true,
  },
]

/** The rights the portal still offers when nominating someone. */
export const GRANTABLE_NOMINEE_PERMISSIONS: NomineePermissionOption[] = NOMINEE_PERMISSIONS.filter(
  (permission) => !permission.retired,
)

export const DEFAULT_NOMINEE_PERMISSIONS: NomineePermission[] = ['CONSENT_VIEW', 'CONSENT_REVOKE']

/** Looks up a permission's display option so the UI can show a readable label. */
export function findNomineePermission(value: string): NomineePermissionOption | undefined {
  return NOMINEE_PERMISSIONS.find((item) => item.value === value)
}

export type NominationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'ACTIVE' | 'DEACTIVATED'

/** Matches Nominee Service's NominationResponse exactly. */
export interface NominationResponse {
  id: string
  ownerId: string
  nomineeId: string
  nomineeEmail: string
  permissions: NomineePermission[]
  status: NominationStatus
  nominatedAt: string
  acceptedAt?: string
  activatedBy?: string
  activatedAt?: string
  activationTicket?: string
}

export interface CreateNominationRequest {
  nomineeId: string
  nomineeEmail: string
  permissions: NomineePermission[]
}

export interface ActivateNominationRequest {
  ticketReference: string
}

export interface DeactivateNominationRequest {
  reason: string
}

export interface NomineeConsentRecord {
  id: string
  clientName: string
  type: string
  status: string
  purposes: string[]
  updatedAt: string
}
