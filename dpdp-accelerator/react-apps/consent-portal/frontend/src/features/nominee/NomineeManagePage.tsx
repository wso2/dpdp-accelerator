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

import { Box, Button, Card, Stack, Tooltip, Typography } from '@wso2/oxygen-ui'
import { ArrowLeft } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import ConsentRegistryFilters from '../consent-registry/components/ConsentRegistryFilters'
import ConsentRevocationDialog from '../consent-registry/components/ConsentRevocationDialog'
import type { ConsentRegistryFilters as ConsentRegistryFiltersModel } from '../../types/consent'
import { NOMINEE_PERMISSIONS } from '../../types/nominee'
import BulkRevokeDialog from './components/BulkRevokeDialog'
import NomineeConsentTable from './components/NomineeConsentTable'
import { useActingAs } from './actingAs/actingAsContext'
import { useRevokeActingConsentMutation, useActingConsentsQuery } from './hooks/useNomineeQueries'

const DEFAULT_FILTERS: ConsentRegistryFiltersModel = {
  status: 'All',
  purposeName: '',
  groupIds: '',
  startDate: '',
  endDate: '',
}

// Account-level actions (everything except the consent view/revoke handled by the table).
const ACCOUNT_ACTIONS = NOMINEE_PERMISSIONS.filter(
  (permission) => !permission.value.startsWith('CONSENT_'),
)

const DEFAULT_ROWS_PER_PAGE = 10

function NomineeManagePage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const { session } = useActingAs()
  const scope = session?.scope ?? []

  const [filters, setFilters] = useState<ConsentRegistryFiltersModel>(DEFAULT_FILTERS)
  const [page, setPage] = useState<number>(0)
  const [rowsPerPage, setRowsPerPage] = useState<number>(DEFAULT_ROWS_PER_PAGE)
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [singleRevokeId, setSingleRevokeId] = useState<string | null>(null)
  const [bulkRevokeOpen, setBulkRevokeOpen] = useState<boolean>(false)
  const [bulkProcessed, setBulkProcessed] = useState<number>(0)
  const [bulkRunning, setBulkRunning] = useState<boolean>(false)

  const resolvedOwnerId = session?.ownerId ?? ''
  const consentsQuery = useActingConsentsQuery(filters, page, rowsPerPage, Boolean(session))
  const revokeMutation = useRevokeActingConsentMutation()

  const rows = consentsQuery.data?.rows ?? []
  const totalCount = consentsQuery.data?.total ?? 0

  const runBulkRevoke = async (): Promise<void> => {
    setBulkRunning(true)
    setBulkProcessed(0)

    await selectedIds.reduce(
      (previousRevoke, consentID) =>
        previousRevoke.then(async () => {
          await revokeMutation.mutateAsync(consentID)
          setBulkProcessed((previous) => previous + 1)
        }),
      Promise.resolve(),
    )

    setBulkRunning(false)
    setBulkRevokeOpen(false)
    setSelectedIds([])
  }

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack spacing={1}>
          <HeaderBreadcrumbs />
          <Stack direction="row" spacing={1.5} alignItems="center">
            <Button
              size="small"
              variant="text"
              startIcon={<ArrowLeft size={16} />}
              component={RouterLink}
              to="/nominations"
            >
              {t('nominee.manage.back', 'Exit Nominee View')}
            </Button>
          </Stack>
          <Typography variant="h4" fontWeight={700}>
            {t('nominee.manage.title', "Managing Owner's Consents")}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t(
              'nominee.manage.subtitle',
              'You are acting on behalf of this account. You can only do what the owner authorised you to do.',
            )}
          </Typography>
        </Stack>

        <Card sx={{ p: 3 }}>
          <Stack spacing={2}>
            <Stack spacing={0.25}>
              <Typography variant="h6" fontWeight={700}>
                {t('nominee.manage.account.title', 'Account access')}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {t(
                  'nominee.manage.account.subtitle',
                  'Actions the owner authorised you to perform on their account.',
                )}
              </Typography>
            </Stack>
            <Stack direction="row" spacing={1.5} flexWrap="wrap" useFlexGap>
              {ACCOUNT_ACTIONS.map((action) => {
                const granted = scope.includes(action.value)
                const button = (
                  <Button
                    variant="outlined"
                    color={action.risky ? 'error' : 'primary'}
                    disabled={!granted}
                  >
                    {t(action.labelKey, action.defaultLabel)}
                  </Button>
                )
                if (granted) {
                  return <Box key={action.value}>{button}</Box>
                }
                return (
                  <Tooltip
                    key={action.value}
                    title={t('nominee.manage.account.notGranted', 'Not granted by the owner')}
                  >
                    <Box>{button}</Box>
                  </Tooltip>
                )
              })}
            </Stack>
          </Stack>
        </Card>

        <Stack spacing={0.25}>
          <Typography variant="h6" fontWeight={700}>
            {t('nominee.manage.consents.title', 'Consents')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t(
              'nominee.manage.consents.subtitle',
              'View and, where permitted, revoke consents granted by this account.',
            )}
          </Typography>
        </Stack>

        <ConsentRegistryFilters
          filters={filters}
          onFilterChange={(nextFilters) => {
            setFilters(nextFilters)
            setPage(0)
          }}
          onClear={() => {
            setFilters(DEFAULT_FILTERS)
            setPage(0)
          }}
        />

        {consentsQuery.isError ? (
          <Typography color="error.main">
            {t('nominee.manage.messages.loadFailed', 'Unable to load consents right now.')}
          </Typography>
        ) : null}

        {!consentsQuery.isError && selectedIds.length > 0 ? (
          <Stack direction="row" spacing={1.5} alignItems="center">
            <Typography variant="body2">
              {t('nominee.manage.selectedCount', {
                count: selectedIds.length,
                defaultValue: '{{count}} selected',
              })}
            </Typography>
            <Button
              size="small"
              color="error"
              variant="contained"
              onClick={() => {
                setBulkRevokeOpen(true)
              }}
            >
              {t('nominee.manage.revokeSelected', 'Revoke Selected')}
            </Button>
          </Stack>
        ) : null}

        {!consentsQuery.isError && (rows.length > 0 || consentsQuery.isFetching) ? (
          <NomineeConsentTable
            rows={rows}
            totalCount={totalCount}
            isLoading={consentsQuery.isLoading}
            page={page}
            rowsPerPage={rowsPerPage}
            selectedIds={selectedIds}
            isMutating={revokeMutation.isPending || bulkRunning}
            onPageChange={setPage}
            onRowsPerPageChange={(nextRowsPerPage) => {
              setRowsPerPage(nextRowsPerPage)
              setPage(0)
            }}
            onSelectionChange={setSelectedIds}
            onRevoke={setSingleRevokeId}
            onRowClick={(consentID) => {
              navigate(`/nominee/manage/${resolvedOwnerId}/consents/${consentID}`)
            }}
          />
        ) : null}

        {!consentsQuery.isFetching && !consentsQuery.isError && rows.length === 0 ? (
          <Typography>
            {t('nominee.manage.messages.empty', 'No consents found for this account.')}
          </Typography>
        ) : null}

        {singleRevokeId ? (
          <ConsentRevocationDialog
            key={`nominee-revoke-${singleRevokeId}`}
            open={Boolean(singleRevokeId)}
            consentId={singleRevokeId}
            loading={revokeMutation.isPending}
            onClose={() => {
              setSingleRevokeId(null)
            }}
            onConfirm={() => {
              revokeMutation.mutate(singleRevokeId, {
                onSuccess: () => {
                  setSingleRevokeId(null)
                },
              })
            }}
          />
        ) : null}

        <BulkRevokeDialog
          open={bulkRevokeOpen}
          count={selectedIds.length}
          processed={bulkProcessed}
          loading={bulkRunning}
          onClose={() => {
            setBulkRevokeOpen(false)
          }}
          onConfirm={() => {
            runBulkRevoke().catch(() => undefined)
          }}
        />
      </Stack>
    </Box>
  )
}

export default NomineeManagePage
