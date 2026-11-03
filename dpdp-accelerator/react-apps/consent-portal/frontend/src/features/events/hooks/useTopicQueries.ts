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

import {
  keepPreviousData,
  type UseMutationResult,
  type UseQueryResult,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import type {
  TopicFilters,
  TopicInput,
  TopicListQueryParams,
  TopicRecord,
  TopicStatusUpdate,
} from '../../../types/topic'
import { createTopic, deleteTopic, fetchTopics } from '../api/topicsApi'

export interface TopicListResult {
  rows: TopicRecord[]
  total: number
  hasNextPage: boolean
}

function toListParams(
  filters: TopicFilters,
  page: number,
  rowsPerPage: number,
): TopicListQueryParams {
  return {
    status: filters.status === 'All' ? undefined : filters.status,
    search: filters.search.trim() || undefined,
    limit: rowsPerPage,
    offset: page * rowsPerPage,
  }
}

export function useTopicsQuery(
  filters: TopicFilters,
  page: number,
  rowsPerPage: number,
): UseQueryResult<TopicListResult> {
  const params = toListParams(filters, page, rowsPerPage)

  return useQuery({
    queryKey: ['topics', params],
    queryFn: async (): Promise<TopicListResult> => {
      const response = await fetchTopics(params)
      const items = response.items ?? []
      const total = response.total ?? items.length

      return {
        rows: items,
        total,
        hasNextPage: params.offset + items.length < total,
      }
    },
    placeholderData: keepPreviousData,
  })
}

export function useCreateTopicMutation(): UseMutationResult<TopicRecord, Error, TopicInput> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: TopicInput) => createTopic(payload),
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['topics'] })
    },
  })
}

export function useDeleteTopicMutation(): UseMutationResult<TopicStatusUpdate, Error, string> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (topicId: string) => deleteTopic(topicId),
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['topics'] })
    },
  })
}
