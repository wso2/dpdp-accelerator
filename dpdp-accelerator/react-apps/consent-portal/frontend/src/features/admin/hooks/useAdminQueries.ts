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
  type UseMutationResult,
  type UseQueryResult,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import {
  activateNomination,
  deactivateNomination,
  fetchNominationsByOwner,
  fetchPendingNominations,
  searchUsers,
} from '../api/adminApi'
import type { AdminUserSummary } from '../../../types/admin'
import type { NominationResponse } from '../../../types/nominee'
import { APIError } from '../../../utils/apiClient'

export function useUserSearchQuery(query: string): UseQueryResult<AdminUserSummary[]> {
  return useQuery<AdminUserSummary[]>({
    queryKey: ['admin', 'user-search', query],
    queryFn: async (): Promise<AdminUserSummary[]> => searchUsers(query),
    enabled: query.trim().length > 0,
  })
}

/** Every nomination this owner has made. An empty list is a normal state. */
export function useNominationsByOwnerQuery(
  ownerId: string | undefined,
): UseQueryResult<NominationResponse[]> {
  return useQuery<NominationResponse[]>({
    queryKey: ['admin', 'nominations-by-owner', ownerId],
    queryFn: async (): Promise<NominationResponse[]> => {
      try {
        return await fetchNominationsByOwner(String(ownerId))
      } catch (error) {
        if (error instanceof APIError && error.status === 404) {
          return []
        }
        throw error
      }
    },
    enabled: Boolean(ownerId),
  })
}

/** Nominations accepted by the nominee but not yet activated - the admin review queue. */
export function usePendingNominationsQuery(): UseQueryResult<NominationResponse[]> {
  return useQuery<NominationResponse[]>({
    queryKey: ['admin', 'pending-nominations'],
    queryFn: fetchPendingNominations,
  })
}

interface ActivateVariables {
  nominationId: string
  ownerId: string
  ticket: string
}

export function useActivateNomineeMutation(): UseMutationResult<
  NominationResponse,
  Error,
  ActivateVariables
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({ nominationId, ticket }: ActivateVariables): Promise<NominationResponse> =>
      activateNomination(nominationId, ticket),
    onSuccess: async (_data, variables): Promise<void> => {
      await queryClient.invalidateQueries({
        queryKey: ['admin', 'nomination-by-owner', variables.ownerId],
      })
      await queryClient.invalidateQueries({ queryKey: ['admin', 'pending-nominations'] })
    },
  })
}

interface DeactivateVariables {
  nominationId: string
  ownerId: string
  reason: string
}

export function useDeactivateNomineeMutation(): UseMutationResult<
  NominationResponse,
  Error,
  DeactivateVariables
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({
      nominationId,
      reason,
    }: DeactivateVariables): Promise<NominationResponse> =>
      deactivateNomination(nominationId, reason),
    onSuccess: async (_data, variables): Promise<void> => {
      await queryClient.invalidateQueries({
        queryKey: ['admin', 'nomination-by-owner', variables.ownerId],
      })
      await queryClient.invalidateQueries({ queryKey: ['admin', 'pending-nominations'] })
    },
  })
}
