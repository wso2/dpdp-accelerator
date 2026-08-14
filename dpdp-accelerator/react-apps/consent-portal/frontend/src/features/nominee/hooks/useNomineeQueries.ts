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
  acceptNomination,
  rejectNomination,
  activateNomination,
  deactivateNomination,
  addMyNomination,
  fetchMyNominations,
  fetchNominatedFor,
  fetchActingConsentByID,
  fetchActingConsents,
  lookupUserByEmail,
  lookupUserByID,
  removeMyNomination,
  revokeActingConsent,
  updateMyNominationPermissions,
} from '../api/nomineeApi'
import type {
  ConsentDetailAPI,
  ConsentListQueryParams,
  ConsentRegistryFilters,
} from '../../../types/consent'
import type {
  ActivateNominationRequest,
  CreateNominationRequest,
  DeactivateNominationRequest,
  NomineeConsentRecord,
  NominationResponse,
} from '../../../types/nominee'
import { APIError } from '../../../utils/apiClient'
import { normalizeConsentStatus } from '../../consent-registry/utils/statusChip'
import {
  toEndOfDayEpochMilliseconds,
  toEpochMilliseconds,
  toStartOfDayEpochMilliseconds,
} from '../../../utils/dateTime'

interface NomineeConsentListResult {
  rows: NomineeConsentRecord[]
  total: number
}

function toListParams(
  filters: ConsentRegistryFilters,
  page: number,
  rowsPerPage: number,
): ConsentListQueryParams {
  const statusFilterMap: Record<Exclude<ConsentRegistryFilters['status'], 'All'>, string> = {
    Active: 'ACTIVE',
    Pending: 'CREATED',
    Rejected: 'REJECTED',
    Revoked: 'REVOKED',
    Expired: 'EXPIRED',
  }

  return {
    consentStatuses: filters.status === 'All' ? undefined : statusFilterMap[filters.status],
    purposeName: filters.purposeName.trim() || undefined,
    groupIds: filters.groupIds.trim() || undefined,
    fromTime: toStartOfDayEpochMilliseconds(filters.startDate),
    toTime: toEndOfDayEpochMilliseconds(filters.endDate),
    limit: rowsPerPage,
    offset: page * rowsPerPage,
  }
}

function toNomineeConsentRow(consent: ConsentDetailAPI): NomineeConsentRecord {
  return {
    id: consent.id,
    // The consent server groups a user's consents by the application that
    // asked for them, and groupId is that application. It replaces the
    // clientId this row used to read, which the consent API no longer returns.
    clientName: consent.groupId,
    type: consent.type,
    status: normalizeConsentStatus(consent.status),
    purposes: consent.purposes.map((purpose) => purpose.name),
    updatedAt: new Date(toEpochMilliseconds(consent.updatedTime) ?? 0).toISOString(),
  }
}

/** Every nominee this owner has appointed. Empty list when none. */
export function useMyNominationsQuery(): UseQueryResult<NominationResponse[]> {
  return useQuery<NominationResponse[]>({
    queryKey: ['nominee', 'my-nominations'],
    queryFn: async (): Promise<NominationResponse[]> => {
      try {
        return await fetchMyNominations()
      } catch (error) {
        if (error instanceof APIError && error.status === 404) {
          return []
        }
        throw error
      }
    },
  })
}

export function useNominatedForQuery(): UseQueryResult<NominationResponse[]> {
  return useQuery<NominationResponse[]>({
    queryKey: ['nominee', 'nominated-for'],
    queryFn: fetchNominatedFor,
  })
}

interface SetNominationVariables {
  nomineeEmail: string
  permissions: CreateNominationRequest['permissions']
}

export function useAddNominationMutation(): UseMutationResult<
  NominationResponse,
  Error,
  SetNominationVariables
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (variables: SetNominationVariables): Promise<NominationResponse> => {
      const nominee = await lookupUserByEmail(variables.nomineeEmail)
      return addMyNomination({
        nomineeId: nominee.id,
        nomineeEmail: variables.nomineeEmail,
        permissions: variables.permissions,
      })
    },
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['nominee', 'my-nominations'] })
    },
  })
}

interface UpdatePermissionsVariables {
  nominationId: string
  permissions: CreateNominationRequest['permissions']
}

export function useUpdateNominationPermissionsMutation(): UseMutationResult<
  NominationResponse,
  Error,
  UpdatePermissionsVariables
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (variables: UpdatePermissionsVariables): Promise<NominationResponse> =>
      updateMyNominationPermissions(variables.nominationId, variables.permissions),
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['nominee', 'my-nominations'] })
    },
  })
}

/** Resolves an ID to a display name, cached across every place that needs it. */
export function useUserDisplayQuery(userId: string | undefined): UseQueryResult<string> {
  return useQuery<string>({
    queryKey: ['user-display', userId],
    queryFn: async (): Promise<string> => (await lookupUserByID(String(userId))).name,
    enabled: Boolean(userId),
    staleTime: 5 * 60 * 1000,
  })
}

export function useRemoveNominationMutation(): UseMutationResult<void, Error, string> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (nominationId: string): Promise<void> => removeMyNomination(nominationId),
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['nominee', 'my-nominations'] })
    },
  })
}

export function useAcceptNominationMutation(): UseMutationResult<
  NominationResponse,
  Error,
  string
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: acceptNomination,
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['nominee', 'nominated-for'] })
    },
  })
}

export function useRejectNominationMutation(): UseMutationResult<
  NominationResponse,
  Error,
  string
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: rejectNomination,
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['nominee', 'nominated-for'] })
    },
  })
}

export function useActivateNominationMutation(): UseMutationResult<
  NominationResponse,
  Error,
  { nominationId: string; request: ActivateNominationRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ nominationId, request }) => activateNomination(nominationId, request),
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['nominee'] })
    },
  })
}

export function useDeactivateNominationMutation(): UseMutationResult<
  NominationResponse,
  Error,
  { nominationId: string; request: DeactivateNominationRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ nominationId, request }) => deactivateNomination(nominationId, request),
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['nominee'] })
    },
  })
}

/**
 * Acting-mode queries.
 *
 * The impersonation token identifies both the owner and the nominee, so these
 * take no owner id. Authorization is decided entirely server-side, on every
 * request, from the token's scopes and a live nomination check.
 */
export function useActingConsentsQuery(
  filters: ConsentRegistryFilters,
  page: number,
  rowsPerPage: number,
  enabled = true,
): UseQueryResult<NomineeConsentListResult> {
  const params = toListParams(filters, page, rowsPerPage)

  return useQuery<NomineeConsentListResult>({
    queryKey: ['nominee', 'acting-consents', params],
    queryFn: async (): Promise<NomineeConsentListResult> => {
      const response = await fetchActingConsents(params)
      return {
        rows: response.data.map(toNomineeConsentRow),
        total: response.metadata.total,
      }
    },
    enabled,
    placeholderData: keepPreviousData,
  })
}

export function useActingConsentDetailQuery(
  consentID: string | undefined,
  enabled = true,
): UseQueryResult<ConsentDetailAPI> {
  return useQuery<ConsentDetailAPI>({
    queryKey: ['nominee', 'acting-consent-detail', consentID],
    queryFn: async (): Promise<ConsentDetailAPI> => fetchActingConsentByID(String(consentID)),
    enabled: enabled && Boolean(consentID),
  })
}

export function useRevokeActingConsentMutation(): UseMutationResult<void, Error, string> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (consentID: string): Promise<void> => revokeActingConsent(consentID),
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['nominee', 'acting-consents'] })
    },
  })
}
