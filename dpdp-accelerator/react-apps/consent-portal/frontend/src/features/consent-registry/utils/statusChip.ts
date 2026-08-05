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

type ConsentStateLabelScope = 'consent' | 'authorization'

type ConsentChipColor = 'success' | 'warning' | 'error' | 'default'

export function normalizeConsentState(state: string): string {
  return state.trim().toUpperCase()
}

export function isConsentApprovableState(state: string): boolean {
  return normalizeConsentState(state) === 'PENDING'
}

export function isConsentRejectableState(state: string): boolean {
  return normalizeConsentState(state) === 'PENDING'
}

export function isConsentRevokableState(state: string): boolean {
  return normalizeConsentState(state) === 'ACTIVE'
}

export function getConsentStateChipColor(state: string): ConsentChipColor {
  switch (normalizeConsentState(state)) {
    case 'ACTIVE':
    case 'APPROVED':
      return 'success'
    case 'PENDING':
      return 'warning'
    case 'REJECTED':
    case 'REVOKED':
      return 'error'
    default:
      return 'default'
  }
}

export function getConsentStateLabelKey(
  state: string,
  scope: ConsentStateLabelScope = 'consent',
): string {
  const normalizedState = normalizeConsentState(state)

  if (
    scope === 'authorization' &&
    (normalizedState === 'APPROVED' || normalizedState === 'ACTIVE')
  ) {
    return 'approved'
  }

  switch (normalizedState) {
    case 'ACTIVE':
    case 'APPROVED':
      return 'active'
    case 'PENDING':
      return 'pending'
    case 'REJECTED':
      return 'rejected'
    case 'REVOKED':
      return 'revoked'
    case 'EXPIRED':
      return 'expired'
    default:
      return normalizedState.toLowerCase()
  }
}
