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

export const SUBSCRIPTION_STATUSES = ['ACTIVE', 'PENDING', 'STALE', 'DELETED'] as const

export type SubscriptionStatus = (typeof SUBSCRIPTION_STATUSES)[number]

export function isSubscriptionStatus(status: string): status is SubscriptionStatus {
  return SUBSCRIPTION_STATUSES.includes(status.toUpperCase() as SubscriptionStatus)
}

export const PURPOSE_FILTER_MODES = ['all', 'specific', 'all_except'] as const

export type PurposeFilterMode = (typeof PURPOSE_FILTER_MODES)[number]

export function isPurposeFilterMode(mode: string): mode is PurposeFilterMode {
  return PURPOSE_FILTER_MODES.includes(mode.toLowerCase() as PurposeFilterMode)
}

export const DELIVERY_MODES = ['webhook', 'poll'] as const

export type DeliveryMode = (typeof DELIVERY_MODES)[number]

export function isDeliveryMode(mode: string): mode is DeliveryMode {
  return DELIVERY_MODES.includes(mode.toLowerCase() as DeliveryMode)
}

export interface FilterConfig {
  type: PurposeFilterMode
  purposes?: string[]
}

export interface DeliveryConfig {
  mode: DeliveryMode
  callbackUrl?: string
  sharedSecret?: string
}

export interface SubscriptionRecord {
  subscriptionId: string
  orgId?: string
  groupId?: string
  topic: string
  filter?: FilterConfig
  delivery?: DeliveryConfig
  status: SubscriptionStatus | string
  createdAt?: number
  updatedAt?: number
  alreadyExists?: boolean
  message?: string
}

export interface SubscriptionInput {
  groupId?: string
  topic: string
  filter: FilterConfig
  delivery: DeliveryConfig
}

export interface SubscriptionListQueryParams {
  limit: number
  offset: number
  status?: string
  purposes?: string
  search?: string
  sort?: string
}

export interface SubscriptionListResponse {
  items: SubscriptionRecord[]
  total: number
}

export interface SubscriptionFilters {
  status: 'All' | SubscriptionStatus
  deliveryMode: 'All' | DeliveryMode
  search: string
}

export interface SubscriptionDeliveryRecord {
  deliveryId: string
  eventId: string
  topic: string
  currentStatus: string
  deliveryMode: string
  occurredAt: number
}

export interface SubscriptionDeliveryAttemptRecord {
  attempt: number
  status: string
  timestamp: number
  httpStatus?: number
  error?: string
}

export interface SubscriptionEventHistoryRecord {
  deliveryId: string
  eventId: string
  topic: string
  deliveryMode: string
  currentStatus: string
  occurredAt: number
  nextRetryAt?: number
  completionStatus?: string
  completionEvidence?: string
  manualRetryUsed: boolean
  manualRetryAvailable: boolean
  history?: SubscriptionDeliveryAttemptRecord[]
}
