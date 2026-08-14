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
import {
  approveMyConsent,
  fetchMyConsentByID,
  fetchMyConsents,
  rejectMyConsent,
  revokeMyConsent,
} from '../api/consentsApi'
import { normalizeConsentState } from '../utils/statusChip'
import type {
  ConsentDetail,
  ConsentListQueryParams,
  ConsentRecord,
  ConsentRegistryFilters,
} from '../../../types/consent'
import { isConsentState } from '../../../types/consent'

export interface ConsentListResult {
  rows: ConsentRecord[]
  /** True when the page came back full, which is the only hint of more data. */
  hasNextPage: boolean
}

export function toConsentRow(consent: ConsentDetail): ConsentRecord {
  const normalizedState = normalizeConsentState(consent.state)

  if (!isConsentState(normalizedState)) {
    throw new Error(`Unsupported consent state received from API: ${consent.state}`)
  }

  return {
    id: consent.id,
    subjectId: consent.subjectId,
    serviceId: consent.serviceId,
    state: normalizedState,
    timestamp: consent.timestamp,
    purposes: consent.purposes.map((purpose) => purpose.name),
  }
}

function toListParams(
  filters: ConsentRegistryFilters,
  page: number,
  rowsPerPage: number,
): ConsentListQueryParams {
  return {
    consentStatuses: filters.state === 'All' ? undefined : filters.state,
    serviceId: filters.serviceId.trim() || undefined,
    limit: rowsPerPage,
    offset: page * rowsPerPage,
  }
}

export function useConsentListQuery(
  filters: ConsentRegistryFilters,
  page: number,
  rowsPerPage: number,
): UseQueryResult<ConsentListResult> {
  const params = toListParams(filters, page, rowsPerPage)

  return useQuery({
    queryKey: ['consents', params],
    queryFn: async (): Promise<ConsentListResult> => {
      const response = await fetchMyConsents(params)

      return {
        rows: response.data.map(toConsentRow),
        hasNextPage: response.data.length >= params.limit,
      }
    },
    placeholderData: keepPreviousData,
  })
}

export function useConsentDetailQuery(
  consentID: string | undefined,
): UseQueryResult<ConsentDetail> {
  return useQuery<ConsentDetail>({
    queryKey: ['consent', consentID],
    queryFn: async (): Promise<ConsentDetail> => fetchMyConsentByID(String(consentID)),
    enabled: Boolean(consentID),
  })
}

function useConsentLifecycleMutation(
  action: (consentID: string) => Promise<unknown>,
): UseMutationResult<unknown, Error, string> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (consentID: string): Promise<unknown> => action(consentID),
    onSuccess: async (_data, consentID): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['consents'] })
      await queryClient.invalidateQueries({ queryKey: ['consent', consentID] })
    },
  })
}

export function useApproveConsentMutation(): UseMutationResult<unknown, Error, string> {
  return useConsentLifecycleMutation(approveMyConsent)
}

export function useRejectConsentMutation(): UseMutationResult<unknown, Error, string> {
  return useConsentLifecycleMutation(rejectMyConsent)
}

export function useRevokeConsentMutation(): UseMutationResult<unknown, Error, string> {
  return useConsentLifecycleMutation(revokeMyConsent)
}
