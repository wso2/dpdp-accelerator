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

import { Box, Button, Chip, Stack, Typography } from '@wso2/oxygen-ui'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import type {
  AdminConsentRegistryFilters,
  ConsentRegistrySortDirection,
  ConsentRegistrySortField,
} from '../../types/consent'
import { PORTAL_SCOPES } from '../../utils/portalScopes'
import useAuthorization from '../auth/useAuthorization'
import ConsentRegistryTable from '../consent-registry/components/ConsentRegistryTable'
import ConsentRevocationDialog from '../consent-registry/components/ConsentRevocationDialog'
import { CONSENT_REGISTRY_ROWS_PER_PAGE_OPTIONS } from '../consent-registry/constants'
import AdminConsentFilters from './components/AdminConsentFilters'
import {
  useAdminConsentListQuery,
  useAdminRevokeConsentMutation,
} from './hooks/useAdminConsentQueries'
import {
  EMPTY_ADMIN_CONSENT_FILTERS,
  getAdminConsentFilters,
  normalizeAdminConsentFilters,
} from './utils/adminConsentFilters'

const DEFAULT_PAGE = 0
const DEFAULT_ROWS_PER_PAGE = 10
const DEFAULT_SORT_FIELD: ConsentRegistrySortField = 'updatedTime'
const DEFAULT_SORT_DIRECTION: ConsentRegistrySortDirection = 'desc'
const SORT_FIELDS: ConsentRegistrySortField[] = ['status', 'updatedTime', 'validityTime']

function getPage(searchParams: URLSearchParams): number {
  const value = Number(searchParams.get('page') ?? '1')
  return Number.isInteger(value) && value > 0 ? value - 1 : DEFAULT_PAGE
}

function getRowsPerPage(searchParams: URLSearchParams): number {
  const value = Number(searchParams.get('rowsPerPage') ?? String(DEFAULT_ROWS_PER_PAGE))
  return CONSENT_REGISTRY_ROWS_PER_PAGE_OPTIONS.includes(
    value as (typeof CONSENT_REGISTRY_ROWS_PER_PAGE_OPTIONS)[number],
  )
    ? value
    : DEFAULT_ROWS_PER_PAGE
}

function getSort(searchParams: URLSearchParams): {
  field: ConsentRegistrySortField
  direction: ConsentRegistrySortDirection
} {
  const value = searchParams.get('sort')
  if (!value || value.includes(',')) {
    return { field: DEFAULT_SORT_FIELD, direction: DEFAULT_SORT_DIRECTION }
  }
  const [field, direction = DEFAULT_SORT_DIRECTION, ...extra] = value.split(':')
  return SORT_FIELDS.includes(field as ConsentRegistrySortField) &&
    (direction === 'asc' || direction === 'desc') &&
    extra.length === 0
    ? { field: field as ConsentRegistrySortField, direction }
    : { field: DEFAULT_SORT_FIELD, direction: DEFAULT_SORT_DIRECTION }
}

function toSearchParams(
  rawFilters: AdminConsentRegistryFilters,
  page = DEFAULT_PAGE,
  rowsPerPage = DEFAULT_ROWS_PER_PAGE,
  sortField = DEFAULT_SORT_FIELD,
  sortDirection = DEFAULT_SORT_DIRECTION,
): URLSearchParams {
  const filters = normalizeAdminConsentFilters(rawFilters)
  const params = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => {
    if (key === 'status' ? value !== 'All' : Boolean(value)) params.set(key, value)
  })
  if (page > 0) params.set('page', String(page + 1))
  if (rowsPerPage !== DEFAULT_ROWS_PER_PAGE) params.set('rowsPerPage', String(rowsPerPage))
  if (sortField !== DEFAULT_SORT_FIELD || sortDirection !== DEFAULT_SORT_DIRECTION) {
    params.set('sort', `${sortField}:${sortDirection}`)
  }
  return params
}

export default function AdminConsentRegistryPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const [searchParams, setSearchParams] = useSearchParams()
  const [selectedRevocationConsentID, setSelectedRevocationConsentID] = useState<string>()
  const filters = useMemo(() => getAdminConsentFilters(searchParams), [searchParams])
  const page = useMemo(() => getPage(searchParams), [searchParams])
  const rowsPerPage = useMemo(() => getRowsPerPage(searchParams), [searchParams])
  const sort = useMemo(() => getSort(searchParams), [searchParams])
  const consentListQuery = useAdminConsentListQuery(
    filters,
    page,
    rowsPerPage,
    sort.field,
    sort.direction,
  )
  const revokeMutation = useAdminRevokeConsentMutation()
  const { currentUser, hasScope } = useAuthorization()
  const canWriteAny = hasScope(PORTAL_SCOPES.CONSENTS_WRITE_ANY)
  const canReadElements = hasScope(PORTAL_SCOPES.ELEMENTS_READ)

  const updateParams = (
    nextFilters: AdminConsentRegistryFilters,
    nextPage = DEFAULT_PAGE,
    nextRowsPerPage = rowsPerPage,
    sortField = sort.field,
    sortDirection = sort.direction,
  ): void => {
    setSearchParams(
      toSearchParams(nextFilters, nextPage, nextRowsPerPage, sortField, sortDirection),
      { replace: true },
    )
  }

  const activeFilters = Object.entries(filters).filter(([key, value]) => {
    if (key === 'status') return value !== 'All'
    return Boolean(value)
  }) as Array<[keyof AdminConsentRegistryFilters, string]>

  const filterLabels: Record<keyof AdminConsentRegistryFilters, string> = {
    status: t('consentRegistry.filters.status'),
    consentId: t('consentRegistry.details.consentId'),
    purposeName: t('catalog.fields.purposeName'),
    purposeVersion: t('catalog.fields.purposeVersion'),
    userIds: t('adminConsents.filters.userIds'),
    groupIds: t('adminConsents.filters.groupIds'),
    elementName: t('catalog.fields.elementName'),
    elementNamespace: t('catalog.fields.elementNamespace'),
    elementVersion: t('catalog.fields.elementVersion'),
    startDate: t('consentRegistry.filters.startDate'),
    endDate: t('consentRegistry.filters.endDate'),
  }

  const removeFilter = (key: keyof AdminConsentRegistryFilters): void => {
    const nextFilters = {
      ...filters,
      [key]: key === 'status' ? 'All' : '',
    } as AdminConsentRegistryFilters
    if (key === 'purposeName') nextFilters.purposeVersion = ''
    if (key === 'elementName' && !filters.elementNamespace) nextFilters.elementVersion = ''
    if (key === 'elementNamespace' && !filters.elementName) nextFilters.elementVersion = ''
    updateParams(nextFilters)
  }

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack spacing={1}>
          <HeaderBreadcrumbs />
          <Typography variant="h4" fontWeight={700}>
            {t('adminConsents.title')}
          </Typography>
        </Stack>

        <AdminConsentFilters
          key={searchParams.toString()}
          filters={filters}
          canReadElements={canReadElements}
          onFilterChange={(nextFilters) => updateParams(nextFilters)}
          onClear={() => updateParams(EMPTY_ADMIN_CONSENT_FILTERS)}
        />

        {activeFilters.length > 0 ? (
          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center">
            <Typography variant="caption" color="text.secondary">
              {t('adminConsents.filters.active')}
            </Typography>
            {activeFilters.map(([key, value]) => (
              <Chip
                key={key}
                size="small"
                variant="outlined"
                label={`${filterLabels[key]}: ${value}`}
                onDelete={() => removeFilter(key)}
              />
            ))}
            <Button size="small" onClick={() => updateParams(EMPTY_ADMIN_CONSENT_FILTERS)}>
              {t('consentRegistry.filters.clear')}
            </Button>
          </Stack>
        ) : null}

        <ConsentRegistryTable
          rows={consentListQuery.data?.rows ?? []}
          totalCount={consentListQuery.data?.total ?? 0}
          isLoading={consentListQuery.isPending || consentListQuery.isPlaceholderData}
          isError={consentListQuery.isError}
          page={page}
          rowsPerPage={rowsPerPage}
          sortField={sort.field}
          sortDirection={sort.direction}
          detailBasePath="/administration/consents"
          showApproveAction={false}
          showMutationActions={canWriteAny}
          onPageChange={(nextPage) => updateParams(filters, nextPage)}
          onRowsPerPageChange={(nextRowsPerPage) =>
            updateParams(filters, DEFAULT_PAGE, nextRowsPerPage)
          }
          onSortChange={(sortField, sortDirection) =>
            updateParams(filters, DEFAULT_PAGE, rowsPerPage, sortField, sortDirection)
          }
          onRetry={() => consentListQuery.refetch()}
          onRevoke={setSelectedRevocationConsentID}
          isMutating={revokeMutation.isPending}
        />

        {selectedRevocationConsentID ? (
          <ConsentRevocationDialog
            open
            consentId={selectedRevocationConsentID}
            loading={revokeMutation.isPending}
            onClose={() => setSelectedRevocationConsentID(undefined)}
            onConfirm={() =>
              revokeMutation.mutate(
                { consentID: selectedRevocationConsentID, actionBy: currentUser.userId },
                { onSuccess: () => setSelectedRevocationConsentID(undefined) },
              )
            }
          />
        ) : null}
      </Stack>
    </Box>
  )
}
