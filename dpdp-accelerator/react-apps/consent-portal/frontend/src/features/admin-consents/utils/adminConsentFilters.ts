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

import type { AdminConsentRegistryFilters, ConsentState } from '../../../types/consent'
import { isConsentState } from '../../../types/consent'

export const EMPTY_ADMIN_CONSENT_FILTERS: AdminConsentRegistryFilters = {
  state: 'All',
  consentId: '',
  subjectId: '',
  serviceId: '',
}

export function normalizeAdminConsentFilters(
  filters: AdminConsentRegistryFilters,
): AdminConsentRegistryFilters {
  return {
    state: filters.state,
    consentId: filters.consentId.trim(),
    subjectId: filters.subjectId.trim(),
    serviceId: filters.serviceId.trim(),
  }
}

export function getAdminConsentFilters(searchParams: URLSearchParams): AdminConsentRegistryFilters {
  const state = searchParams.get('state') ?? ''

  return normalizeAdminConsentFilters({
    state: isConsentState(state) ? (state as ConsentState) : 'All',
    consentId: searchParams.get('consentId') ?? '',
    subjectId: searchParams.get('subjectId') ?? '',
    serviceId: searchParams.get('serviceId') ?? '',
  })
}
