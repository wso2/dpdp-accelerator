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
  SubscriptionDeliveryAttemptRecord,
  SubscriptionDeliveryRecord,
  SubscriptionEventHistoryRecord,
  SubscriptionInput,
  SubscriptionListQueryParams,
  SubscriptionListResponse,
  SubscriptionRecord,
  SubscriptionStatusUpdate,
} from '../../../types/subscription'
import { apiRequest } from '../../../utils/apiClient'

const jsonHeaders = { 'Content-Type': 'application/json' }

export async function fetchSubscriptions(
  params: SubscriptionListQueryParams,
): Promise<SubscriptionListResponse> {
  return apiRequest<SubscriptionListResponse>('/api/dpdp/event-notifications/v1.1/subscriptions', {
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
    `/api/dpdp/event-notifications/v1.1/subscriptions/${encodeURIComponent(subscriptionId)}`,
    {
      method: 'GET',
    },
  )
}

export async function createSubscription(payload: SubscriptionInput): Promise<SubscriptionRecord> {
  const body: Record<string, unknown> = {
    name: payload.name,
    topics:
      payload.topics && payload.topics.length > 0
        ? payload.topics
        : payload.topic
          ? [payload.topic]
          : [],
    filter: payload.filter,
    delivery: payload.delivery,
  }
  return apiRequest<SubscriptionRecord>('/api/dpdp/event-notifications/v1.1/subscriptions', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(body),
  })
}

export async function deleteSubscription(
  subscriptionId: string,
): Promise<SubscriptionStatusUpdate> {
  return apiRequest<SubscriptionStatusUpdate>(
    `/api/dpdp/event-notifications/v1.1/subscriptions/${encodeURIComponent(subscriptionId)}`,
    {
      method: 'DELETE',
    },
  )
}

export async function verifySubscription(
  subscriptionId: string,
): Promise<SubscriptionStatusUpdate> {
  return apiRequest<SubscriptionStatusUpdate>(
    `/api/dpdp/event-notifications/v1.1/subscriptions/${encodeURIComponent(subscriptionId)}/verify`,
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
    `/api/dpdp/event-notifications/v1.1/subscriptions/${encodeURIComponent(subscriptionId)}/deliveries`,
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
  _subscriptionId: string,
  deliveryId: string,
): Promise<SubscriptionEventHistoryRecord> {
  const [delivery, history] = await Promise.all([
    apiRequest<Omit<SubscriptionEventHistoryRecord, 'history'>>(
      `/api/dpdp/event-notifications/v1.1/deliveries/${encodeURIComponent(deliveryId)}`,
      {
        method: 'GET',
      },
    ),
    apiRequest<{
      deliveryId: string
      eventId?: string
      history?: SubscriptionDeliveryAttemptRecord[]
    }>(`/api/dpdp/event-notifications/v1.1/deliveries/${encodeURIComponent(deliveryId)}/history`, {
      method: 'GET',
    }),
  ])

  return {
    ...delivery,
    history: history.history ?? [],
  }
}

export async function retrySubscriptionDelivery(
  _subscriptionId: string,
  deliveryId: string,
): Promise<SubscriptionEventHistoryRecord> {
  return apiRequest<SubscriptionEventHistoryRecord>(
    `/api/dpdp/event-notifications/v1.1/deliveries/${encodeURIComponent(deliveryId)}/retry`,
    {
      method: 'POST',
    },
  )
}
