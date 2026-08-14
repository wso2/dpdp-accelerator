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

import type { ConsentAPIStatus, ConsentStatus } from '../../../types/consent'

type ConsentStatusLike = ConsentAPIStatus | ConsentStatus | string
type ConsentStatusLabelScope = 'consent' | 'authorization'

type ConsentChipColor = 'success' | 'warning' | 'error' | 'default'

export function normalizeConsentStatus(status: ConsentStatusLike): string {
  return status.trim().toUpperCase()
}

export function isConsentApprovableStatus(status: ConsentStatusLike): boolean {
  return normalizeConsentStatus(status) === 'CREATED'
}

export function isConsentRejectableStatus(status: ConsentStatusLike): boolean {
  return normalizeConsentStatus(status) === 'CREATED'
}

export function isConsentRevokableStatus(status: ConsentStatusLike): boolean {
  return normalizeConsentStatus(status) === 'ACTIVE'
}

export function getConsentStatusChipColor(status: ConsentStatusLike): ConsentChipColor {
  switch (normalizeConsentStatus(status)) {
    case 'ACTIVE':
    case 'APPROVED':
      return 'success'
    case 'CREATED':
    case 'PENDING':
      return 'warning'
    case 'REJECTED':
    case 'REVOKED':
      return 'error'
    case 'EXPIRED':
    case 'SYS_EXPIRED':
    case 'SYS_REVOKED':
      return 'default'
    default:
      return 'default'
  }
}

export function getConsentStatusLabelKey(
  status: ConsentStatusLike,
  scope: ConsentStatusLabelScope = 'consent',
): string {
  const normalizedStatus = normalizeConsentStatus(status)

  switch (scope) {
    case 'authorization':
      switch (normalizedStatus) {
        case 'APPROVED':
        case 'ACTIVE':
          return 'approved'
        case 'CREATED':
        case 'PENDING':
          return 'pending'
        case 'REJECTED':
          return 'rejected'
        case 'REVOKED':
          return 'revoked'
        case 'EXPIRED':
          return 'expired'
        case 'SYS_EXPIRED':
          return 'systemExpired'
        case 'SYS_REVOKED':
          return 'systemRevoked'
        default:
          return normalizedStatus.toLowerCase()
      }
    case 'consent':
    default:
      switch (normalizedStatus) {
        case 'ACTIVE':
        case 'APPROVED':
          return 'active'
        case 'CREATED':
        case 'PENDING':
          return 'pending'
        case 'REJECTED':
          return 'rejected'
        case 'REVOKED':
          return 'revoked'
        case 'EXPIRED':
          return 'expired'
        default:
          return normalizedStatus.toLowerCase()
      }
  }
}
