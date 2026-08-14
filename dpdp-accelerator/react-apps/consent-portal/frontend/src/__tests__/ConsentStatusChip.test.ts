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
  getConsentStatusChipColor,
  getConsentStatusLabelKey,
  isConsentApprovableStatus,
  isConsentRejectableStatus,
  isConsentRevokableStatus,
} from '../features/consent-registry/utils/statusChip'

describe('getConsentStatusChipColor', () => {
  it('maps API and display statuses to the expected chip colors', () => {
    expect(getConsentStatusChipColor('ACTIVE')).toBe('success')
    expect(getConsentStatusChipColor('Active')).toBe('success')
    expect(getConsentStatusChipColor('APPROVED')).toBe('success')
    expect(getConsentStatusChipColor('CREATED')).toBe('warning')
    expect(getConsentStatusChipColor('Pending')).toBe('warning')
    expect(getConsentStatusChipColor('REJECTED')).toBe('error')
    expect(getConsentStatusChipColor('REVOKED')).toBe('error')
    expect(getConsentStatusChipColor('Revoked')).toBe('error')
    expect(getConsentStatusChipColor('EXPIRED')).toBe('default')
    expect(getConsentStatusChipColor('Expired')).toBe('default')
    expect(getConsentStatusChipColor('SYS_EXPIRED')).toBe('default')
    expect(getConsentStatusChipColor('SYS_REVOKED')).toBe('default')
  })

  it('maps statuses to the expected translation keys', () => {
    expect(getConsentStatusLabelKey('ACTIVE')).toBe('active')
    expect(getConsentStatusLabelKey('CREATED')).toBe('pending')
    expect(getConsentStatusLabelKey('REJECTED')).toBe('rejected')
    expect(getConsentStatusLabelKey('REVOKED')).toBe('revoked')
    expect(getConsentStatusLabelKey('EXPIRED')).toBe('expired')
    expect(getConsentStatusLabelKey('APPROVED', 'authorization')).toBe('approved')
    expect(getConsentStatusLabelKey('CREATED', 'authorization')).toBe('pending')
    expect(getConsentStatusLabelKey('SYS_EXPIRED', 'authorization')).toBe('systemExpired')
    expect(getConsentStatusLabelKey('SYS_REVOKED', 'authorization')).toBe('systemRevoked')
  })

  it('limits lifecycle actions to their supported statuses', () => {
    expect(isConsentApprovableStatus('CREATED')).toBe(true)
    expect(isConsentRejectableStatus('CREATED')).toBe(true)
    expect(isConsentRevokableStatus('ACTIVE')).toBe(true)
    expect(isConsentApprovableStatus('ACTIVE')).toBe(false)
    expect(isConsentRejectableStatus('REJECTED')).toBe(false)
    expect(isConsentRevokableStatus('CREATED')).toBe(false)
  })
})
