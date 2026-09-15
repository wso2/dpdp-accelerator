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

import type {
  SubscriptionDeliveryRecord,
  SubscriptionEventHistoryRecord,
  SubscriptionInput,
  SubscriptionListQueryParams,
  SubscriptionListResponse,
  SubscriptionRecord,
} from '../../../types/subscription'
import { apiRequest } from '../../../utils/apiClient'

const jsonHeaders = { 'Content-Type': 'application/json' }

export async function fetchSubscriptions(
  params: SubscriptionListQueryParams,
): Promise<SubscriptionListResponse> {
  return apiRequest<SubscriptionListResponse>('/api/dpdp/event-notifications/v1/subscriptions', {
    method: 'GET',
    query: {
      limit: params.limit,
      offset: params.offset,
      status: params.status,
      purposes: params.purposes,
      search: params.search,
      sort: params.sort,
    },
  })
}

export async function fetchSubscriptionById(subscriptionId: string): Promise<SubscriptionRecord> {
  return apiRequest<SubscriptionRecord>(
    `/api/dpdp/event-notifications/v1/subscriptions/${encodeURIComponent(subscriptionId)}`,
    {
      method: 'GET',
    },
  )
}

export async function createSubscription(payload: SubscriptionInput): Promise<SubscriptionRecord> {
  return apiRequest<SubscriptionRecord>('/api/dpdp/event-notifications/v1/subscriptions', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(payload),
  })
}

export async function deleteSubscription(subscriptionId: string): Promise<SubscriptionRecord> {
  return apiRequest<SubscriptionRecord>(
    `/api/dpdp/event-notifications/v1/subscriptions/${encodeURIComponent(subscriptionId)}`,
    {
      method: 'DELETE',
    },
  )
}

export async function verifySubscription(subscriptionId: string): Promise<SubscriptionRecord> {
  return apiRequest<SubscriptionRecord>(
    `/api/dpdp/event-notifications/v1/subscriptions/${encodeURIComponent(subscriptionId)}/verify`,
    {
      method: 'POST',
    },
  )
}

export async function fetchSubscriptionEvents(
  subscriptionId: string,
  params: { limit: number; offset: number },
): Promise<{ items: SubscriptionDeliveryRecord[]; total: number }> {
  return apiRequest<{ items: SubscriptionDeliveryRecord[]; total: number }>(
    `/api/dpdp/event-notifications/v1/subscriptions/${encodeURIComponent(subscriptionId)}/events`,
    {
      method: 'GET',
      query: {
        limit: params.limit,
        offset: params.offset,
      },
    },
  )
}

export async function fetchSubscriptionEventHistory(
  subscriptionId: string,
  deliveryId: string,
): Promise<SubscriptionEventHistoryRecord> {
  return apiRequest<SubscriptionEventHistoryRecord>(
    `/api/dpdp/event-notifications/v1/subscriptions/${encodeURIComponent(subscriptionId)}/events/${encodeURIComponent(deliveryId)}`,
    {
      method: 'GET',
    },
  )
}

export async function retrySubscriptionDelivery(
  subscriptionId: string,
  deliveryId: string,
): Promise<SubscriptionEventHistoryRecord> {
  return apiRequest<SubscriptionEventHistoryRecord>(
    `/api/dpdp/event-notifications/v1/subscriptions/${encodeURIComponent(subscriptionId)}/events/${encodeURIComponent(deliveryId)}/retry`,
    {
      method: 'POST',
    },
  )
}
