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

export const TOPIC_STATUSES = ['ACTIVE', 'DELETED'] as const

export type TopicStatus = (typeof TOPIC_STATUSES)[number]

export function isTopicStatus(status: string): status is TopicStatus {
  return TOPIC_STATUSES.includes(status as TopicStatus)
}

export interface TopicRecord {
  topicId: string
  name: string
  description?: string
  status: TopicStatus | string
  initiatedBy?: string
  subscriptionCount?: number
}

export interface TopicListQueryParams {
  limit: number
  offset: number
  status?: string
  search?: string
  sort?: string
}

export interface TopicListResponse {
  items: TopicRecord[]
  total: number
}

export interface TopicInput {
  name: string
  description?: string
}

export interface TopicFilters {
  status: 'All' | TopicStatus
  search: string
}
