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

import type { APIRequestContext, APIResponse } from '@playwright/test'
import { eventNotificationsApiUrl } from '../utils/env'
import type { AuthHeaders } from '../utils/authStorage'

/**
 * Ground truth for these shapes is the Java DTOs under
 * dpdp-accelerator/{components,internal-webapps}/org.wso2.dpdp.accelerator.event.notifications.*
 * (TopicDTO, SubscriptionDTO, EventDTO, EventCreateDTO, ...), not event-notifications.yaml alone -
 * same rationale as ComplaintApiClient.ts. One drift already found: `EventCreateDTO` (the real
 * POST /events request body) has no `groupId` field at all - `group-id` is a required HTTP
 * header (`EventNotificationEndpointConstants.GROUP_ID_HEADER`), never part of the body, even
 * though the frontend's own `EventInput` type optimistically declares a body-level `groupId` that
 * `publishEvent` (topicsApi.ts) never actually sends anywhere - there is no publish-event UI at
 * all (see AGENTS.md).
 */

// Every one of these enums (TopicStatus, Initiator, SubscriptionStatus, PurposeFilterMode,
// DeliveryMode - see the *.java sources under event.notifications.common/.../enums/) is declared
// `@JsonValue getValue()` returning its LOWERCASE string ("active", "pending", "all_except",
// "webhook", ...) - every JSON response field using one of these always comes back lowercase, no
// exceptions. Request bodies are more forgiving: `@JsonCreator fromValue()` matches
// case-insensitively against both the value string and the Java enum name, so a request can send
// "POLL"/"ACTIVE" and still be accepted - but never assert an uppercase value against something
// the server itself returned.
export const TOPIC_STATUSES = ['active', 'deregistered'] as const
export type TopicStatus = (typeof TOPIC_STATUSES)[number]

export const SUBSCRIPTION_STATUSES = ['pending', 'active', 'stale', 'deleted'] as const
export type SubscriptionStatus = (typeof SUBSCRIPTION_STATUSES)[number]

export const PURPOSE_FILTER_MODES = ['all', 'specific', 'all_except'] as const
export type PurposeFilterMode = (typeof PURPOSE_FILTER_MODES)[number]

export const DELIVERY_MODES = ['webhook', 'poll'] as const
export type DeliveryMode = (typeof DELIVERY_MODES)[number]

export interface TopicRecord {
  topicId: string
  name: string
  description?: string
  status: TopicStatus | string
  initiatedBy?: string
}

export interface TopicListParams {
  status?: string
  search?: string
  limit?: number
  offset?: number
  sort?: string
}

export interface FilterConfig {
  type: PurposeFilterMode | string
  purposes?: string[]
}

export interface DeliveryConfig {
  mode: DeliveryMode | string
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

export interface SubscriptionCreateRequest {
  groupId?: string
  topic: string
  filter: FilterConfig
  delivery: DeliveryConfig
}

export interface SubscriptionListParams {
  status?: string
  purposes?: string
  search?: string
  limit?: number
  offset?: number
  sort?: string
}

export interface EventRecord {
  eventId: string
  orgId?: string
  groupId?: string
  topicId?: string
  topic?: string
  payload?: string
  purposes?: string[]
  occurredAt?: string | number
  createdAt?: string | number
  deliveriesCount?: number
}

export interface EventListParams {
  topic?: string
  status?: string
  subscriptionId?: string
  purposes?: string
  search?: string
  groupId?: string
  limit?: number
  offset?: number
}

export interface EventCreateRequest {
  topic: string
  purposes?: string[]
  payload: Record<string, unknown>
}

export interface SubscriptionDeliveryRecord {
  deliveryId: string
  eventId: string
  subscriptionId?: string
  groupId?: string
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
  history?: SubscriptionDeliveryAttemptRecord[]
}

export interface PaginatedResult<T> {
  items: T[]
  total: number
}

/**
 * Wraps `/api/dpdp/event-notifications/v1/*` (EventEndpoint/SubscriptionEndpoint/TopicEndpoint,
 * `org.wso2.dpdp.accelerator.event.notifications.endpoint`) - tenant-qualified the same way the
 * IS-native consent APIs are (see eventNotificationsApiUrl in utils/env.ts), unlike the
 * complaint-server. Auth is just a header pair resolved from whichever persona's captured auth
 * state the caller passes in - scope-isolation tests construct this with the "wrong" persona's
 * headers the same way ComplaintApiClient's callers do.
 */
export class EventNotificationApiClient {
  constructor(
    private readonly request: APIRequestContext,
    private readonly auth: AuthHeaders,
    private readonly tenantDomain?: string,
  ) {}

  private headers(extra?: Record<string, string>): Record<string, string> {
    return { ...this.auth, ...extra }
  }

  private jsonHeaders(extra?: Record<string, string>): Record<string, string> {
    return this.headers({ 'Content-Type': 'application/json', ...extra })
  }

  private url(path: string): string {
    return eventNotificationsApiUrl(path, this.tenantDomain)
  }

  private queryParams(params: object): Record<string, string> {
    return Object.fromEntries(
      Object.entries(params as Record<string, string | number | undefined>)
        .filter(([, value]) => value !== undefined)
        .map(([key, value]) => [key, String(value)]),
    )
  }

  // ------------------------------------------------------------------------------------ Topics

  async createTopic(body: { name: string; description?: string }): Promise<APIResponse> {
    return this.request.post(this.url('/topics'), { headers: this.jsonHeaders(), data: body })
  }

  async listTopics(params: TopicListParams = {}): Promise<APIResponse> {
    return this.request.get(this.url('/topics'), { headers: this.headers(), params: this.queryParams(params) })
  }

  async deleteTopic(topicId: string): Promise<APIResponse> {
    return this.request.delete(this.url(`/topics/${encodeURIComponent(topicId)}`), { headers: this.headers() })
  }

  // ------------------------------------------------------------------------------ Subscriptions

  async createSubscription(body: SubscriptionCreateRequest): Promise<APIResponse> {
    return this.request.post(this.url('/subscriptions'), { headers: this.jsonHeaders(), data: body })
  }

  async listSubscriptions(params: SubscriptionListParams = {}): Promise<APIResponse> {
    return this.request.get(this.url('/subscriptions'), {
      headers: this.headers(),
      params: this.queryParams(params),
    })
  }

  async getSubscription(subscriptionId: string): Promise<APIResponse> {
    return this.request.get(this.url(`/subscriptions/${encodeURIComponent(subscriptionId)}`), {
      headers: this.headers(),
    })
  }

  async deleteSubscription(subscriptionId: string): Promise<APIResponse> {
    return this.request.delete(this.url(`/subscriptions/${encodeURIComponent(subscriptionId)}`), {
      headers: this.headers(),
    })
  }

  async verifySubscription(subscriptionId: string): Promise<APIResponse> {
    return this.request.post(this.url(`/subscriptions/${encodeURIComponent(subscriptionId)}/verify`), {
      headers: this.headers(),
    })
  }

  async listSubscriptionEvents(
    subscriptionId: string,
    params: { limit?: number; offset?: number } = {},
  ): Promise<APIResponse> {
    return this.request.get(this.url(`/subscriptions/${encodeURIComponent(subscriptionId)}/events`), {
      headers: this.headers(),
      params: this.queryParams(params),
    })
  }

  async getSubscriptionEventHistory(subscriptionId: string, deliveryId: string): Promise<APIResponse> {
    return this.request.get(
      this.url(`/subscriptions/${encodeURIComponent(subscriptionId)}/events/${encodeURIComponent(deliveryId)}`),
      { headers: this.headers() },
    )
  }

  // ------------------------------------------------------------------------------------ Events

  /** `groupId` is a required HTTP header on the real API, never a body field - see this file's header comment. */
  async publishEvent(groupId: string, body: EventCreateRequest): Promise<APIResponse> {
    return this.request.post(this.url('/events'), {
      headers: this.jsonHeaders({ 'group-id': groupId }),
      data: body,
    })
  }

  async listEvents(params: EventListParams = {}): Promise<APIResponse> {
    return this.request.get(this.url('/events'), { headers: this.headers(), params: this.queryParams(params) })
  }

  async getEvent(eventId: string): Promise<APIResponse> {
    return this.request.get(this.url(`/events/${encodeURIComponent(eventId)}`), { headers: this.headers() })
  }

  async getEventDeliveries(eventId: string, params: { limit?: number; offset?: number } = {}): Promise<APIResponse> {
    return this.request.get(this.url(`/events/${encodeURIComponent(eventId)}/deliveries`), {
      headers: this.headers(),
      params: this.queryParams(params),
    })
  }

  /** Org-level delivery history, keyed by deliveryId directly - distinct from the subscription-scoped path above, same response shape. */
  async getDeliveryHistory(deliveryId: string): Promise<APIResponse> {
    return this.request.get(this.url(`/events/${encodeURIComponent(deliveryId)}/history`), {
      headers: this.headers(),
    })
  }
}
