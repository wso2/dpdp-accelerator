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
  EventInput,
  EventListQueryParams,
  EventListResponse,
  EventRecord,
} from '../../../types/event'
import type {
  SubscriptionDeliveryAttemptRecord,
  SubscriptionEventHistoryRecord,
} from '../../../types/subscription'
import { apiRequest } from '../../../utils/apiClient'

const jsonHeaders = { 'Content-Type': 'application/json' }

export async function fetchEvents(params: EventListQueryParams): Promise<EventListResponse> {
  return apiRequest<EventListResponse>('/api/dpdp/event-notifications/v1.1/events', {
    method: 'GET',
    query: {
      limit: params.limit,
      offset: params.offset,
      search: params.search,
      status: params.status,
      topic: params.topic,
      subscriptionId: params.subscriptionId,
      purposes: params.purposes,
    },
  })
}

export async function fetchEventDeliveryHistory(
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

export async function fetchEventById(eventId: string): Promise<EventRecord> {
  return apiRequest<EventRecord>(
    `/api/dpdp/event-notifications/v1.1/events/${encodeURIComponent(eventId)}`,
    {
      method: 'GET',
    },
  )
}

export async function fetchEventDeliveries(
  eventId: string,
  limit = 20,
  offset = 0,
): Promise<EventListResponse> {
  return apiRequest<EventListResponse>(
    `/api/dpdp/event-notifications/v1.1/events/${encodeURIComponent(eventId)}/deliveries`,
    {
      method: 'GET',
      query: {
        limit,
        offset,
      },
    },
  )
}

export async function publishEvent(payload: EventInput): Promise<EventRecord> {
  return apiRequest<EventRecord>('/api/dpdp/event-notifications/v1.1/events', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(payload),
  })
}
