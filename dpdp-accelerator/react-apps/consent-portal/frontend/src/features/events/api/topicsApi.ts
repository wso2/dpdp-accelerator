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
  TopicInput,
  TopicListQueryParams,
  TopicListResponse,
  TopicRecord,
  TopicStatusUpdate,
} from '../../../types/topic'
import { apiRequest } from '../../../utils/apiClient'

const jsonHeaders = { 'Content-Type': 'application/json' }

export async function fetchTopics(params: TopicListQueryParams): Promise<TopicListResponse> {
  return apiRequest<TopicListResponse>('/api/dpdp/event-notifications/v1.1/topics', {
    method: 'GET',
    query: {
      limit: params.limit,
      offset: params.offset,
      status: params.status,
      search: params.search,
      sort: params.sort,
    },
  })
}

export async function createTopic(payload: TopicInput): Promise<TopicRecord> {
  return apiRequest<TopicRecord>('/api/dpdp/event-notifications/v1.1/topics', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(payload),
  })
}

export async function deleteTopic(topicId: string): Promise<TopicStatusUpdate> {
  return apiRequest<TopicStatusUpdate>(
    `/api/dpdp/event-notifications/v1.1/topics/${encodeURIComponent(topicId)}`,
    {
      method: 'DELETE',
    },
  )
}
