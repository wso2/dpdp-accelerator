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
  SubscriptionDeliveryRecord,
  SubscriptionEventHistoryRecord,
  SubscriptionFilters,
  SubscriptionInput,
  SubscriptionListQueryParams,
  SubscriptionRecord,
} from '../../../types/subscription'
import {
  createSubscription,
  deleteSubscription,
  fetchSubscriptionById,
  fetchSubscriptionEventHistory,
  fetchSubscriptionEvents,
  fetchSubscriptions,
  retrySubscriptionDelivery,
  verifySubscription,
} from '../api/subscriptionsApi'

export interface SubscriptionListResult {
  rows: SubscriptionRecord[]
  total: number
  hasNextPage: boolean
}

export interface SubscriptionEventsResult {
  rows: SubscriptionDeliveryRecord[]
  total: number
  hasNextPage: boolean
}

function toListParams(
  filters: SubscriptionFilters,
  page: number,
  rowsPerPage: number,
): SubscriptionListQueryParams {
  return {
    status: filters.status === 'All' ? undefined : filters.status.toLowerCase(),
    search: filters.search.trim() || undefined,
    limit: rowsPerPage,
    offset: page * rowsPerPage,
  }
}

export function useSubscriptionsQuery(
  filters: SubscriptionFilters,
  page: number,
  rowsPerPage: number,
): UseQueryResult<SubscriptionListResult> {
  const params = toListParams(filters, page, rowsPerPage)

  return useQuery({
    queryKey: ['subscriptions', params, filters.deliveryMode],
    queryFn: async (): Promise<SubscriptionListResult> => {
      const response = await fetchSubscriptions(params)
      let items = response.items ?? []

      if (filters.deliveryMode !== 'All') {
        items = items.filter(
          (sub) => sub.delivery?.mode?.toLowerCase() === filters.deliveryMode.toLowerCase(),
        )
      }

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

export function useSubscriptionDetailQuery(
  subscriptionId?: string,
): UseQueryResult<SubscriptionRecord> {
  return useQuery({
    queryKey: ['subscription', subscriptionId],
    queryFn: () => {
      if (!subscriptionId) {
        throw new Error('Subscription ID is required')
      }
      return fetchSubscriptionById(subscriptionId)
    },
    enabled: Boolean(subscriptionId),
  })
}

export function useCreateSubscriptionMutation(): UseMutationResult<
  SubscriptionRecord,
  Error,
  SubscriptionInput
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: SubscriptionInput) => createSubscription(payload),
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['subscriptions'] })
    },
  })
}

export function useDeleteSubscriptionMutation(): UseMutationResult<
  SubscriptionRecord,
  Error,
  string
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (subscriptionId: string) => deleteSubscription(subscriptionId),
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['subscriptions'] })
      await queryClient.invalidateQueries({ queryKey: ['subscription'] })
    },
  })
}

export function useVerifySubscriptionMutation(): UseMutationResult<
  SubscriptionRecord,
  Error,
  string
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (subscriptionId: string) => verifySubscription(subscriptionId),
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['subscriptions'] })
      await queryClient.invalidateQueries({ queryKey: ['subscription'] })
    },
  })
}

export function useSubscriptionEventsQuery(
  subscriptionId?: string,
  page = 0,
  rowsPerPage = 10,
): UseQueryResult<SubscriptionEventsResult> {
  const offset = page * rowsPerPage

  return useQuery({
    queryKey: ['subscription-events', subscriptionId, page, rowsPerPage],
    queryFn: async (): Promise<SubscriptionEventsResult> => {
      if (!subscriptionId) {
        throw new Error('Subscription ID is required')
      }
      const response = await fetchSubscriptionEvents(subscriptionId, {
        limit: rowsPerPage,
        offset,
      })
      const items = response.items ?? []
      const total = response.total ?? items.length

      return {
        rows: items,
        total,
        hasNextPage: offset + items.length < total,
      }
    },
    placeholderData: keepPreviousData,
    enabled: Boolean(subscriptionId),
  })
}

export function useSubscriptionEventHistoryQuery(
  subscriptionId?: string,
  deliveryId?: string,
): UseQueryResult<SubscriptionEventHistoryRecord> {
  return useQuery({
    queryKey: ['subscription-event-history', subscriptionId, deliveryId],
    queryFn: () => {
      if (!subscriptionId || !deliveryId) {
        throw new Error('Subscription ID and Delivery ID are required')
      }
      return fetchSubscriptionEventHistory(subscriptionId, deliveryId)
    },
    enabled: Boolean(subscriptionId && deliveryId),
  })
}

export function useRetrySubscriptionDeliveryMutation(
  subscriptionId?: string,
  deliveryId?: string,
): UseMutationResult<SubscriptionEventHistoryRecord, Error, void> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => {
      if (!subscriptionId || !deliveryId) {
        throw new Error('Subscription ID and Delivery ID are required')
      }
      return retrySubscriptionDelivery(subscriptionId, deliveryId)
    },
    onSuccess: async (): Promise<void> => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ['subscription-event-history', subscriptionId, deliveryId],
        }),
        queryClient.invalidateQueries({ queryKey: ['subscription-events', subscriptionId] }),
        queryClient.invalidateQueries({ queryKey: ['events', 'history'] }),
        queryClient.invalidateQueries({ queryKey: ['events', 'deliveries'] }),
      ])
    },
  })
}
