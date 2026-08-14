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
  queryOptions,
  type UseMutationResult,
  type UseQueryResult,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { useEffect } from 'react'
import type {
  AdminConsentRegistryFilters,
  ConsentDetailAPI,
  ConsentListQueryParams,
  ConsentRecord,
  ConsentRegistrySortDirection,
  ConsentRegistrySortField,
} from '../../../types/consent'
import { isConsentAPIStatus } from '../../../types/consent'
import { PORTAL_SCOPES } from '../../../utils/portalScopes'
import {
  toEndOfDayEpochMilliseconds,
  toEpochMilliseconds,
  toStartOfDayEpochMilliseconds,
} from '../../../utils/dateTime'
import useAuthorization from '../../auth/useAuthorization'
import {
  isConsentRevokableStatus,
  normalizeConsentStatus,
} from '../../consent-registry/utils/statusChip'
import {
  fetchAdminConsentByID,
  fetchAdminConsents,
  revokeAdminConsent,
} from '../api/adminConsentsApi'

interface AdminConsentListResult {
  rows: ConsentRecord[]
  total: number
}

function toListParams(
  filters: AdminConsentRegistryFilters,
  page: number,
  rowsPerPage: number,
  sortField: ConsentRegistrySortField,
  sortDirection: ConsentRegistrySortDirection,
): ConsentListQueryParams {
  const statusFilterMap: Record<Exclude<AdminConsentRegistryFilters['status'], 'All'>, string> = {
    Active: 'ACTIVE',
    Pending: 'CREATED',
    Rejected: 'REJECTED',
    Revoked: 'REVOKED',
    Expired: 'EXPIRED',
  }

  return {
    sort: `${sortField}:${sortDirection}`,
    consentStatuses: filters.status === 'All' ? undefined : statusFilterMap[filters.status],
    userIds: filters.userIds || undefined,
    groupIds: filters.groupIds || undefined,
    purposeName: filters.purposeName.trim() || undefined,
    purposeVersion: filters.purposeName.trim()
      ? filters.purposeVersion.trim() || undefined
      : undefined,
    elementName: filters.elementName.trim() || undefined,
    elementNamespace: filters.elementNamespace.trim() || undefined,
    elementVersion:
      filters.elementName.trim() || filters.elementNamespace.trim()
        ? filters.elementVersion.trim() || undefined
        : undefined,
    fromTime: toStartOfDayEpochMilliseconds(filters.startDate),
    toTime: toEndOfDayEpochMilliseconds(filters.endDate),
    limit: rowsPerPage,
    offset: page * rowsPerPage,
  }
}

function toAdminConsentRow(consent: ConsentDetailAPI, canWriteAny: boolean): ConsentRecord {
  const normalizedStatus = normalizeConsentStatus(consent.status)
  if (!isConsentAPIStatus(normalizedStatus)) {
    throw new Error(`Unsupported consent status received from API: ${consent.status}`)
  }

  return {
    id: consent.id,
    groupId: consent.groupId,
    type: consent.type,
    status: normalizedStatus,
    purposes: consent.purposes.map((purpose) => purpose.displayName ?? purpose.name),
    updatedAt: new Date(toEpochMilliseconds(consent.updatedTime) ?? 0).toISOString(),
    expirationTime: consent.expirationTime ?? 0,
    canApprove: false,
    canRevoke: canWriteAny && isConsentRevokableStatus(normalizedStatus),
  }
}

function adminConsentListQueryOptions(
  filters: AdminConsentRegistryFilters,
  page: number,
  rowsPerPage: number,
  sortField: ConsentRegistrySortField,
  sortDirection: ConsentRegistrySortDirection,
  canWriteAny: boolean,
) {
  const consentID = filters.consentId.trim()
  const params = toListParams(filters, page, rowsPerPage, sortField, sortDirection)
  return queryOptions({
    queryKey: ['admin-consents', { consentID, params }, { canWriteAny }],
    queryFn: async (): Promise<AdminConsentListResult> => {
      if (consentID) {
        const consent = await fetchAdminConsentByID(consentID)
        return {
          rows: [toAdminConsentRow(consent, canWriteAny)],
          total: 1,
        }
      }
      const response = await fetchAdminConsents(params)
      return {
        rows: response.data.map((consent) => toAdminConsentRow(consent, canWriteAny)),
        total: response.metadata.total,
      }
    },
    placeholderData: keepPreviousData,
  })
}

export function useAdminConsentListQuery(
  filters: AdminConsentRegistryFilters,
  page: number,
  rowsPerPage: number,
  sortField: ConsentRegistrySortField,
  sortDirection: ConsentRegistrySortDirection,
): UseQueryResult<AdminConsentListResult> {
  const queryClient = useQueryClient()
  const { hasScope } = useAuthorization()
  const canWriteAny = hasScope(PORTAL_SCOPES.CONSENTS_WRITE_ANY)
  const query = useQuery(
    adminConsentListQueryOptions(filters, page, rowsPerPage, sortField, sortDirection, canWriteAny),
  )

  useEffect(() => {
    const nextPage = page + 1
    const hasNextPage = nextPage * rowsPerPage < (query.data?.total ?? 0)
    if (!query.isPlaceholderData && hasNextPage) {
      queryClient
        .prefetchQuery(
          adminConsentListQueryOptions(
            filters,
            nextPage,
            rowsPerPage,
            sortField,
            sortDirection,
            canWriteAny,
          ),
        )
        .catch(() => undefined)
    }
  }, [
    canWriteAny,
    filters,
    page,
    query.data?.total,
    query.isPlaceholderData,
    queryClient,
    rowsPerPage,
    sortDirection,
    sortField,
  ])

  return query
}

export function useAdminConsentDetailQuery(
  consentID: string | undefined,
): UseQueryResult<ConsentDetailAPI> {
  return useQuery({
    queryKey: ['admin-consent', consentID],
    queryFn: () => fetchAdminConsentByID(String(consentID)),
    enabled: Boolean(consentID),
  })
}

interface AdminRevokeVariables {
  consentID: string
  actionBy: string
}

export function useAdminRevokeConsentMutation(): UseMutationResult<
  unknown,
  Error,
  AdminRevokeVariables
> {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ consentID, actionBy }) => revokeAdminConsent(consentID, actionBy),
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ['admin-consents'] })
      await queryClient.invalidateQueries({ queryKey: ['admin-consent', variables.consentID] })
    },
  })
}
