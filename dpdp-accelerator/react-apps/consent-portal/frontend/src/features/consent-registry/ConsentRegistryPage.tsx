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

import { Box, Stack, Typography } from '@wso2/oxygen-ui'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import ConsentApprovalDialog from './components/ConsentApprovalDialog'
import ConsentRegistryFilters from './components/ConsentRegistryFilters'
import ConsentRegistryTable from './components/ConsentRegistryTable'
import ConsentRevocationDialog from './components/ConsentRevocationDialog'
import { CONSENT_REGISTRY_ROWS_PER_PAGE_OPTIONS } from './constants'
import type {
  ConsentRegistryFilters as ConsentRegistryFiltersModel,
  ConsentRegistrySortDirection,
  ConsentRegistrySortField,
} from '../../types/consent'
import {
  useApproveConsentMutation,
  useConsentDetailQuery,
  useConsentListQuery,
  useRevokeConsentMutation,
} from './hooks/useConsentQueries'

const DEFAULT_FILTERS: ConsentRegistryFiltersModel = {
  status: 'All',
  purposeName: '',
  groupIds: '',
  startDate: '',
  endDate: '',
}

const FILTER_STATUS_VALUES: ConsentRegistryFiltersModel['status'][] = [
  'All',
  'Active',
  'Pending',
  'Rejected',
  'Revoked',
  'Expired',
]

const DEFAULT_PAGE = 0
const DEFAULT_ROWS_PER_PAGE = 10
const DEFAULT_SORT_FIELD: ConsentRegistrySortField = 'updatedTime'
const DEFAULT_SORT_DIRECTION: ConsentRegistrySortDirection = 'desc'
const SORT_FIELDS: ConsentRegistrySortField[] = ['status', 'updatedTime', 'validityTime']

interface ConsentRegistrySort {
  field: ConsentRegistrySortField
  direction: ConsentRegistrySortDirection
}

function isValidFilterStatus(value: string): value is ConsentRegistryFiltersModel['status'] {
  return FILTER_STATUS_VALUES.includes(value as ConsentRegistryFiltersModel['status'])
}

function getFiltersFromSearchParams(searchParams: URLSearchParams): ConsentRegistryFiltersModel {
  const statusParam = searchParams.get('status')

  return {
    status: statusParam && isValidFilterStatus(statusParam) ? statusParam : DEFAULT_FILTERS.status,
    purposeName: searchParams.get('purposeName') ?? DEFAULT_FILTERS.purposeName,
    groupIds: searchParams.get('groupIds') ?? DEFAULT_FILTERS.groupIds,
    startDate: searchParams.get('startDate') ?? DEFAULT_FILTERS.startDate,
    endDate: searchParams.get('endDate') ?? DEFAULT_FILTERS.endDate,
  }
}

function getPageFromSearchParams(searchParams: URLSearchParams): number {
  const pageParam = searchParams.get('page')
  const pageNumber = pageParam ? Number(pageParam) : Number.NaN

  if (!Number.isInteger(pageNumber) || pageNumber < 1) {
    return DEFAULT_PAGE
  }

  return pageNumber - 1
}

function getRowsPerPageFromSearchParams(searchParams: URLSearchParams): number {
  const rowsPerPageParam = searchParams.get('rowsPerPage')
  const rowsPerPage = rowsPerPageParam ? Number(rowsPerPageParam) : Number.NaN

  return CONSENT_REGISTRY_ROWS_PER_PAGE_OPTIONS.includes(
    rowsPerPage as (typeof CONSENT_REGISTRY_ROWS_PER_PAGE_OPTIONS)[number],
  )
    ? rowsPerPage
    : DEFAULT_ROWS_PER_PAGE
}

function getSortFromSearchParams(searchParams: URLSearchParams): ConsentRegistrySort {
  const sortParam = searchParams.get('sort')

  if (!sortParam || sortParam.includes(',')) {
    return { field: DEFAULT_SORT_FIELD, direction: DEFAULT_SORT_DIRECTION }
  }

  const [field, direction = DEFAULT_SORT_DIRECTION, ...extraParts] = sortParam.split(':')
  const validField = SORT_FIELDS.includes(field as ConsentRegistrySortField)
  const validDirection = direction === 'asc' || direction === 'desc'

  if (!validField || !validDirection || extraParts.length > 0) {
    return { field: DEFAULT_SORT_FIELD, direction: DEFAULT_SORT_DIRECTION }
  }

  return {
    field: field as ConsentRegistrySortField,
    direction,
  }
}

function toSearchParams(
  filters: ConsentRegistryFiltersModel,
  page = DEFAULT_PAGE,
  rowsPerPage = DEFAULT_ROWS_PER_PAGE,
  sortField: ConsentRegistrySortField = DEFAULT_SORT_FIELD,
  sortDirection: ConsentRegistrySortDirection = DEFAULT_SORT_DIRECTION,
): URLSearchParams {
  const params = new URLSearchParams()

  if (filters.status !== DEFAULT_FILTERS.status) {
    params.set('status', filters.status)
  }

  if (filters.purposeName.trim()) {
    params.set('purposeName', filters.purposeName.trim())
  }

  if (filters.groupIds.trim()) {
    params.set('groupIds', filters.groupIds.trim())
  }

  if (filters.startDate) {
    params.set('startDate', filters.startDate)
  }

  if (filters.endDate) {
    params.set('endDate', filters.endDate)
  }

  if (page !== DEFAULT_PAGE) {
    params.set('page', String(page + 1))
  }

  if (rowsPerPage !== DEFAULT_ROWS_PER_PAGE) {
    params.set('rowsPerPage', String(rowsPerPage))
  }

  if (sortField !== DEFAULT_SORT_FIELD || sortDirection !== DEFAULT_SORT_DIRECTION) {
    params.set('sort', `${sortField}:${sortDirection}`)
  }

  return params
}

function ConsentRegistryPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const [searchParams, setSearchParams] = useSearchParams()
  const [approvalDialogOpen, setApprovalDialogOpen] = useState<boolean>(false)
  const [revocationDialogOpen, setRevocationDialogOpen] = useState<boolean>(false)
  const [selectedApprovalConsentID, setSelectedApprovalConsentID] = useState<string | null>(null)
  const [selectedRevocationConsentID, setSelectedRevocationConsentID] = useState<string | null>(
    null,
  )
  const filters = useMemo(() => getFiltersFromSearchParams(searchParams), [searchParams])
  const page = useMemo(() => getPageFromSearchParams(searchParams), [searchParams])
  const rowsPerPage = useMemo(() => getRowsPerPageFromSearchParams(searchParams), [searchParams])
  const sort = useMemo(() => getSortFromSearchParams(searchParams), [searchParams])
  const consentListQuery = useConsentListQuery(
    filters,
    page,
    rowsPerPage,
    sort.field,
    sort.direction,
  )
  const selectedApprovalConsentQuery = useConsentDetailQuery(selectedApprovalConsentID ?? undefined)
  const approveMutation = useApproveConsentMutation()
  const revokeMutation = useRevokeConsentMutation()
  const isTableLoading = consentListQuery.isPending || consentListQuery.isPlaceholderData

  const rows = consentListQuery.data?.rows ?? []
  const totalCount = consentListQuery.data?.total ?? 0

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack spacing={1}>
          <HeaderBreadcrumbs />
          <Typography variant="h4" fontWeight={700}>
            {t('consentRegistry.title')}
          </Typography>
        </Stack>

        <ConsentRegistryFilters
          filters={filters}
          onFilterChange={(nextFilters) => {
            setSearchParams(
              toSearchParams(nextFilters, DEFAULT_PAGE, rowsPerPage, sort.field, sort.direction),
              {
                replace: true,
              },
            )
          }}
          onClear={() => {
            setSearchParams(
              toSearchParams(
                DEFAULT_FILTERS,
                DEFAULT_PAGE,
                rowsPerPage,
                sort.field,
                sort.direction,
              ),
              { replace: true },
            )
          }}
        />

        <ConsentRegistryTable
          rows={rows}
          totalCount={totalCount}
          isLoading={isTableLoading}
          isError={consentListQuery.isError}
          page={page}
          rowsPerPage={rowsPerPage}
          sortField={sort.field}
          sortDirection={sort.direction}
          onPageChange={(nextPage) => {
            setSearchParams(
              toSearchParams(filters, nextPage, rowsPerPage, sort.field, sort.direction),
              { replace: true },
            )
          }}
          onRowsPerPageChange={(nextRowsPerPage) => {
            setSearchParams(
              toSearchParams(filters, DEFAULT_PAGE, nextRowsPerPage, sort.field, sort.direction),
              {
                replace: true,
              },
            )
          }}
          onSortChange={(sortField, sortDirection) => {
            setSearchParams(
              toSearchParams(filters, DEFAULT_PAGE, rowsPerPage, sortField, sortDirection),
              { replace: true },
            )
          }}
          onRetry={() => consentListQuery.refetch()}
          onApprove={(consentID) => {
            setSelectedApprovalConsentID(consentID)
            setApprovalDialogOpen(true)
          }}
          onRevoke={(consentID) => {
            setSelectedRevocationConsentID(consentID)
            setRevocationDialogOpen(true)
          }}
          isMutating={approveMutation.isPending || revokeMutation.isPending}
        />

        {selectedApprovalConsentID ? (
          <ConsentApprovalDialog
            key={`registry-approval-${selectedApprovalConsentID}-${String(approvalDialogOpen)}`}
            open={approvalDialogOpen}
            consentId={selectedApprovalConsentID}
            purposes={selectedApprovalConsentQuery.data?.purposes ?? []}
            loading={
              approveMutation.isPending ||
              selectedApprovalConsentQuery.isLoading ||
              !selectedApprovalConsentQuery.data
            }
            onClose={() => {
              setApprovalDialogOpen(false)
              setSelectedApprovalConsentID(null)
            }}
            onConfirm={(selectedOptionalElements) => {
              approveMutation.mutate(
                {
                  consentID: selectedApprovalConsentID,
                  selectedOptionalElements,
                },
                {
                  onSuccess: () => {
                    setApprovalDialogOpen(false)
                    setSelectedApprovalConsentID(null)
                  },
                },
              )
            }}
          />
        ) : null}

        {selectedRevocationConsentID ? (
          <ConsentRevocationDialog
            key={`registry-revocation-${selectedRevocationConsentID}-${String(revocationDialogOpen)}`}
            open={revocationDialogOpen}
            consentId={selectedRevocationConsentID}
            loading={revokeMutation.isPending}
            onClose={() => {
              setRevocationDialogOpen(false)
              setSelectedRevocationConsentID(null)
            }}
            onConfirm={() => {
              revokeMutation.mutate(selectedRevocationConsentID, {
                onSuccess: () => {
                  setRevocationDialogOpen(false)
                  setSelectedRevocationConsentID(null)
                },
              })
            }}
          />
        ) : null}
      </Stack>
    </Box>
  )
}

export default ConsentRegistryPage
