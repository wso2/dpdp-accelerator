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
import type { AdminConsentRegistryFilters } from '../../types/consent'
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

const DEFAULT_ROWS_PER_PAGE = 10

interface AdminConsentCursor {
  after?: string
  before?: string
}

function getRowsPerPage(searchParams: URLSearchParams): number {
  const value = Number(searchParams.get('rowsPerPage') ?? String(DEFAULT_ROWS_PER_PAGE))

  return CONSENT_REGISTRY_ROWS_PER_PAGE_OPTIONS.includes(
    value as (typeof CONSENT_REGISTRY_ROWS_PER_PAGE_OPTIONS)[number],
  )
    ? value
    : DEFAULT_ROWS_PER_PAGE
}

function getCursor(searchParams: URLSearchParams): AdminConsentCursor {
  return {
    after: searchParams.get('after') ?? undefined,
    before: searchParams.get('before') ?? undefined,
  }
}

function toSearchParams(
  rawFilters: AdminConsentRegistryFilters,
  cursor: AdminConsentCursor,
  rowsPerPage = DEFAULT_ROWS_PER_PAGE,
): URLSearchParams {
  const filters = normalizeAdminConsentFilters(rawFilters)
  const params = new URLSearchParams()

  Object.entries(filters).forEach(([key, value]) => {
    if (key === 'state' ? value !== 'All' : Boolean(value)) params.set(key, value)
  })
  if (cursor.after) params.set('after', cursor.after)
  if (cursor.before) params.set('before', cursor.before)
  if (rowsPerPage !== DEFAULT_ROWS_PER_PAGE) params.set('rowsPerPage', String(rowsPerPage))

  return params
}

export default function AdminConsentRegistryPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const [searchParams, setSearchParams] = useSearchParams()
  const [selectedRevocationConsentID, setSelectedRevocationConsentID] = useState<string>()
  const filters = useMemo(() => getAdminConsentFilters(searchParams), [searchParams])
  const rowsPerPage = useMemo(() => getRowsPerPage(searchParams), [searchParams])
  const cursor = useMemo(() => getCursor(searchParams), [searchParams])
  const consentListQuery = useAdminConsentListQuery(filters, rowsPerPage, cursor)
  const revokeMutation = useAdminRevokeConsentMutation()
  const { hasScope } = useAuthorization()
  const canWriteAny = hasScope(PORTAL_SCOPES.CONSENTS_WRITE_ANY)

  const updateParams = (
    nextFilters: AdminConsentRegistryFilters,
    nextCursor: AdminConsentCursor = {},
    nextRowsPerPage = rowsPerPage,
  ): void => {
    setSearchParams(toSearchParams(nextFilters, nextCursor, nextRowsPerPage), { replace: true })
  }

  const activeFilters = Object.entries(filters).filter(([key, value]) => {
    if (key === 'state') return value !== 'All'
    return Boolean(value)
  }) as Array<[keyof AdminConsentRegistryFilters, string]>

  const filterLabels: Record<keyof AdminConsentRegistryFilters, string> = {
    state: t('consentRegistry.filters.state'),
    consentId: t('consentRegistry.details.consentId'),
    subjectId: t('adminConsents.filters.subjectId'),
    serviceId: t('adminConsents.filters.serviceId'),
  }

  const removeFilter = (key: keyof AdminConsentRegistryFilters): void => {
    updateParams({
      ...filters,
      [key]: key === 'state' ? 'All' : '',
    } as AdminConsentRegistryFilters)
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
          isLoading={consentListQuery.isPending || consentListQuery.isPlaceholderData}
          isError={consentListQuery.isError}
          rowsPerPage={rowsPerPage}
          hasPreviousPage={Boolean(consentListQuery.data?.previousCursor)}
          hasNextPage={Boolean(consentListQuery.data?.nextCursor)}
          onPreviousPage={() =>
            updateParams(filters, { before: consentListQuery.data?.previousCursor })
          }
          onNextPage={() => updateParams(filters, { after: consentListQuery.data?.nextCursor })}
          onRowsPerPageChange={(nextRowsPerPage) => updateParams(filters, {}, nextRowsPerPage)}
          onRetry={() => consentListQuery.refetch()}
          detailBasePath="/administration/consents"
          showSubject
          showPurposes={Boolean(filters.consentId)}
          canRevoke={canWriteAny}
          onRevoke={setSelectedRevocationConsentID}
          isMutating={revokeMutation.isPending}
        />

        {selectedRevocationConsentID ? (
          <ConsentRevocationDialog
            open
            consentId={selectedRevocationConsentID}
            loading={revokeMutation.isPending}
            error={revokeMutation.error?.message}
            onClose={() => {
              setSelectedRevocationConsentID(undefined)
              revokeMutation.reset()
            }}
            onConfirm={() =>
              revokeMutation.mutate(selectedRevocationConsentID, {
                onSuccess: () => setSelectedRevocationConsentID(undefined),
              })
            }
          />
        ) : null}
      </Stack>
    </Box>
  )
}
