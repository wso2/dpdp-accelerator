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

import type { AdminConsentRegistryFilters } from '../../../types/consent'

export const EMPTY_ADMIN_CONSENT_FILTERS: AdminConsentRegistryFilters = {
  status: 'All',
  consentId: '',
  purposeName: '',
  purposeVersion: '',
  userIds: '',
  groupIds: '',
  elementName: '',
  elementNamespace: '',
  elementVersion: '',
  startDate: '',
  endDate: '',
}

const VALID_STATUSES: AdminConsentRegistryFilters['status'][] = [
  'All',
  'Active',
  'Pending',
  'Rejected',
  'Revoked',
  'Expired',
]

export function normalizeCommaSeparatedIDs(value: string): string {
  return Array.from(
    new Set(
      value
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  ).join(',')
}

export function normalizeAdminConsentFilters(
  filters: AdminConsentRegistryFilters,
): AdminConsentRegistryFilters {
  const purposeName = filters.purposeName.trim()
  const elementName = filters.elementName.trim()
  const elementNamespace = filters.elementNamespace.trim()
  return {
    ...filters,
    consentId: filters.consentId.trim(),
    purposeName,
    purposeVersion: purposeName ? filters.purposeVersion.trim() : '',
    userIds: normalizeCommaSeparatedIDs(filters.userIds),
    groupIds: normalizeCommaSeparatedIDs(filters.groupIds),
    elementName,
    elementNamespace,
    elementVersion: elementName || elementNamespace ? filters.elementVersion.trim() : '',
  }
}

export function getAdminConsentFilters(searchParams: URLSearchParams): AdminConsentRegistryFilters {
  const status = searchParams.get('status')
  return normalizeAdminConsentFilters({
    status: VALID_STATUSES.includes(status as AdminConsentRegistryFilters['status'])
      ? (status as AdminConsentRegistryFilters['status'])
      : 'All',
    consentId: searchParams.get('consentId') ?? '',
    purposeName: searchParams.get('purposeName') ?? '',
    purposeVersion: searchParams.get('purposeVersion') ?? '',
    userIds: searchParams.get('userIds') ?? '',
    groupIds: searchParams.get('groupIds') ?? '',
    elementName: searchParams.get('elementName') ?? '',
    elementNamespace: searchParams.get('elementNamespace') ?? '',
    elementVersion: searchParams.get('elementVersion') ?? '',
    startDate: searchParams.get('startDate') ?? '',
    endDate: searchParams.get('endDate') ?? '',
  })
}
