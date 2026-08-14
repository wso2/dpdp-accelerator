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

export type ConsentStatus = 'Active' | 'Pending' | 'Rejected' | 'Revoked' | 'Expired'

export const CONSENT_API_STATUSES = ['CREATED', 'ACTIVE', 'REJECTED', 'REVOKED', 'EXPIRED'] as const

export type ConsentAPIStatus = (typeof CONSENT_API_STATUSES)[number]

export function isConsentAPIStatus(status: string): status is ConsentAPIStatus {
  return CONSENT_API_STATUSES.includes(status as ConsentAPIStatus)
}

export interface ConsentRecord {
  id: string
  groupId: string
  type: string
  status: ConsentAPIStatus
  purposes: string[]
  updatedAt: string
  expirationTime?: number
  canRevoke: boolean
  canApprove: boolean
}

export interface ConsentRegistryFilters {
  status: 'All' | ConsentStatus
  purposeName: string
  groupIds: string
  startDate: string
  endDate: string
}

export interface AdminConsentRegistryFilters extends ConsentRegistryFilters {
  consentId: string
  userIds: string
  purposeVersion: string
  elementName: string
  elementNamespace: string
  elementVersion: string
}

export type ConsentRegistrySortField = 'status' | 'updatedTime' | 'validityTime'
export type ConsentRegistrySortDirection = 'asc' | 'desc'

export interface ConsentListQueryParams {
  consentStatuses?: string
  userIds?: string
  purposeName?: string
  purposeVersion?: string
  groupIds?: string
  elementName?: string
  elementNamespace?: string
  elementVersion?: string
  sort?: string
  fromTime?: number
  toTime?: number
  limit: number
  offset: number
}

export interface ConsentElementApprovalItem {
  elementId: string
  name: string
  namespace: string
  version: string
  displayName?: string
  approved: boolean
  mandatory: boolean
  value?: unknown
  description?: string
}

export interface ConsentApprovalSelection {
  purposeId: string
  purposeVersion: string
  elementId: string
  elementVersion: string
}

export interface ConsentPurposeItem {
  purposeId: string
  name: string
  version: string
  displayName?: string
  description?: string
  elements: ConsentElementApprovalItem[]
}

export interface ConsentAuthorizationResource {
  id: string
  userId?: string
  type: string
  status: string
  updatedTime: number
  resources?: unknown
}

export interface ConsentStatusAuditItem {
  statusAuditId: string
  previousStatus?: string
  currentStatus: ConsentAPIStatus | string
  actionTime: number
  actionBy?: string | null
  reason?: string | null
}

export interface ConsentDetailAPI {
  id: string
  groupId: string
  type: string
  status: ConsentAPIStatus | string
  createdTime: number
  updatedTime: number
  expirationTime?: number
  recurringIndicator?: boolean
  frequency?: number
  dataAccessValidityDuration?: number
  purposes: ConsentPurposeItem[]
  attributes?: Record<string, string>
  authorizations?: ConsentAuthorizationResource[]
  statusHistory?: ConsentStatusAuditItem[]
}

export interface ConsentSearchMetadata {
  total: number
  offset: number
  count: number
  limit: number
}

export interface ConsentSearchResponse {
  data: ConsentDetailAPI[]
  metadata: ConsentSearchMetadata
}
