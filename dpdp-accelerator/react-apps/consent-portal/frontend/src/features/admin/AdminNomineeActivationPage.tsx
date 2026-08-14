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
  Box,
  Button,
  Chip,
  ListingTable,
  Skeleton,
  Stack,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import type { TFunction } from 'i18next'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import {
  findNomineePermission,
  type NominationResponse,
  type NominationStatus,
} from '../../types/nominee'
import UserDisplayName from '../nominee/components/UserDisplayName'
import { useUserDisplayQuery } from '../nominee/hooks/useNomineeQueries'
import ActivateNomineeDialog from './components/ActivateNomineeDialog'
import DeactivateNomineeDialog from './components/DeactivateNomineeDialog'
import {
  useActivateNomineeMutation,
  useDeactivateNomineeMutation,
  useNominationsByOwnerQuery,
  usePendingNominationsQuery,
  useUserSearchQuery,
} from './hooks/useAdminQueries'

type ChipColor = 'default' | 'success' | 'warning' | 'error'

/**
 * A status an administrator can read, rather than the wire value. ACCEPTED in
 * particular reads as though something has been granted, when it means the
 * opposite: the nominee has agreed, and the decision is now the reviewer's.
 *
 * The wire value is the fallback of last resort only: it is untranslated, so a
 * status the UI does not know about still shows as English in every locale.
 */
function statusDisplay(
  status: NominationStatus,
  t: TFunction<'common'>,
): { label: string; color: ChipColor } {
  switch (status) {
    case 'ACTIVE':
      return { label: t('admin.status.active', 'Active'), color: 'success' }
    case 'ACCEPTED':
      return { label: t('admin.status.accepted', 'Awaiting approval'), color: 'warning' }
    case 'PENDING':
      return { label: t('admin.status.pending', 'Waiting on nominee'), color: 'default' }
    case 'REJECTED':
      return { label: t('admin.status.rejected', 'Declined'), color: 'error' }
    case 'DEACTIVATED':
      return { label: t('admin.status.deactivated', 'Deactivated'), color: 'error' }
    default:
      return { label: status, color: 'default' }
  }
}

function formatDate(value?: string): string {
  if (!value) {
    return '-'
  }
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime())
    ? '-'
    : parsed.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

/** The rights the owner granted, so the decision is made with them in view. */
function PermissionChips({ nomination }: { nomination: NominationResponse }): React.JSX.Element {
  const { t } = useTranslation('common')
  return (
    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
      {nomination.permissions.map((permission) => {
        const option = findNomineePermission(permission)
        return (
          <Chip
            key={permission}
            size="small"
            variant="outlined"
            color={option?.risky ? 'error' : 'default'}
            label={option ? t(option.labelKey, option.defaultLabel) : permission}
          />
        )
      })}
    </Stack>
  )
}

function AdminNomineeActivationPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const [query, setQuery] = useState<string>('')
  const [selectedOwnerId, setSelectedOwnerId] = useState<string | undefined>(undefined)
  const [activateOpen, setActivateOpen] = useState<boolean>(false)
  const [deactivateOpen, setDeactivateOpen] = useState<boolean>(false)
  const [targetId, setTargetId] = useState<string | null>(null)

  const searchQuery = useUserSearchQuery(query)
  const pendingQuery = usePendingNominationsQuery()
  const nominationsQuery = useNominationsByOwnerQuery(selectedOwnerId)
  const ownerDisplayQuery = useUserDisplayQuery(selectedOwnerId)
  const activateMutation = useActivateNomineeMutation()
  const deactivateMutation = useDeactivateNomineeMutation()

  const pending = pendingQuery.data ?? []
  const results = searchQuery.data ?? []
  const ownerDisplay = ownerDisplayQuery.data ?? ''
  // Falls back to the raw ID rather than an empty string: a confirmation that
  // reads "...consents for ." tells the administrator nothing about who they
  // are about to grant access to.
  const ownerLabel = ownerDisplay || (selectedOwnerId ?? '')

  // An owner may have appointed several nominees, each with its own status and
  // its own activation. A dialog therefore targets one specific nomination,
  // found in whichever list it was opened from.
  const rows = selectedOwnerId ? (nominationsQuery.data ?? []) : pending
  const target = [...rows, ...pending].find((item) => item.id === targetId) ?? null

  const openActivate = (nomination: NominationResponse): void => {
    setSelectedOwnerId(nomination.ownerId)
    setTargetId(nomination.id)
    setActivateOpen(true)
  }

  const openDeactivate = (nomination: NominationResponse): void => {
    setSelectedOwnerId(nomination.ownerId)
    setTargetId(nomination.id)
    setDeactivateOpen(true)
  }

  const isLoading = selectedOwnerId ? nominationsQuery.isLoading : pendingQuery.isLoading

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack spacing={1}>
          <HeaderBreadcrumbs />
          <Typography variant="h4" fontWeight={700}>
            {t('admin.title', 'Nominee approvals')}
          </Typography>
        </Stack>

        <Stack direction="row" spacing={2} alignItems="center" useFlexGap flexWrap="wrap">
          <TextField
            size="small"
            placeholder={t('admin.search.label', 'Search by name or email')}
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
              setSelectedOwnerId(undefined)
            }}
            sx={{ minWidth: 280 }}
          />
          {selectedOwnerId ? (
            <Chip
              size="small"
              variant="outlined"
              label={`${t('admin.controls.owner', 'Account')}: ${ownerDisplay || selectedOwnerId}`}
              onDelete={() => {
                setSelectedOwnerId(undefined)
                setQuery('')
              }}
            />
          ) : null}
        </Stack>

        {query.trim() && !searchQuery.isLoading && results.length > 0 ? (
          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center">
            <Typography variant="caption" color="text.secondary">
              {t('admin.search.results', 'Accounts')}
            </Typography>
            {results.map((result) => (
              <Chip
                key={result.id}
                size="small"
                label={result.name || result.email}
                color={result.id === selectedOwnerId ? 'primary' : 'default'}
                onClick={() => {
                  setSelectedOwnerId(result.id)
                }}
              />
            ))}
          </Stack>
        ) : null}

        {query.trim() && !searchQuery.isLoading && results.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {t('admin.search.empty', 'No matching accounts found.')}
          </Typography>
        ) : null}

        <ListingTable.Container>
          <ListingTable
            density="standard"
            variant="table"
            aria-label={t('admin.table.ariaLabel', 'Nominations')}
          >
            <ListingTable.Head>
              <ListingTable.Row>
                <ListingTable.Cell>{t('admin.table.owner', 'Account')}</ListingTable.Cell>
                <ListingTable.Cell>{t('admin.table.nominee', 'Nominee')}</ListingTable.Cell>
                <ListingTable.Cell>{t('admin.table.permissions', 'Granted')}</ListingTable.Cell>
                <ListingTable.Cell>{t('admin.table.status', 'Status')}</ListingTable.Cell>
                <ListingTable.Cell>{t('admin.table.nominated', 'Nominated')}</ListingTable.Cell>
                <ListingTable.Cell align="center">
                  {t('admin.table.actions', 'Actions')}
                </ListingTable.Cell>
              </ListingTable.Row>
            </ListingTable.Head>

            <ListingTable.Body>
              {isLoading
                ? Array.from({ length: 3 }, (_, index) => (
                    <ListingTable.Row key={`skeleton-${String(index)}`}>
                      {Array.from({ length: 6 }, (__, cell) => (
                        <ListingTable.Cell key={`cell-${String(cell)}`}>
                          <Skeleton variant="text" />
                        </ListingTable.Cell>
                      ))}
                    </ListingTable.Row>
                  ))
                : null}

              {!isLoading && rows.length === 0 ? (
                <ListingTable.Row>
                  <ListingTable.Cell colSpan={6}>
                    <Typography variant="body2" color="text.secondary">
                      {selectedOwnerId
                        ? t('admin.controls.none', 'This account has not appointed anyone.')
                        : t('admin.pending.empty', 'Nothing is waiting on you right now.')}
                    </Typography>
                  </ListingTable.Cell>
                </ListingTable.Row>
              ) : null}

              {!isLoading &&
                rows.map((nomination) => {
                  const status = statusDisplay(nomination.status, t)
                  return (
                    <ListingTable.Row key={nomination.id}>
                      <ListingTable.Cell>
                        <UserDisplayName userId={nomination.ownerId} />
                      </ListingTable.Cell>
                      <ListingTable.Cell>{nomination.nomineeEmail}</ListingTable.Cell>
                      <ListingTable.Cell>
                        <PermissionChips nomination={nomination} />
                      </ListingTable.Cell>
                      <ListingTable.Cell>
                        <Chip
                          size="small"
                          variant="outlined"
                          color={status.color}
                          label={status.label}
                        />
                      </ListingTable.Cell>
                      <ListingTable.Cell>{formatDate(nomination.nominatedAt)}</ListingTable.Cell>
                      <ListingTable.Cell
                        align="center"
                        // The column is auto-sized, so a two-word label would
                        // otherwise break across lines rather than widen it.
                        sx={{ '& .MuiButton-root': { whiteSpace: 'nowrap' } }}
                      >
                        {/* Only the action that applies is offered. A row of
                            greyed-out buttons explains nothing. */}
                        {nomination.status === 'ACCEPTED' ? (
                          <Button
                            size="small"
                            variant="contained"
                            onClick={() => {
                              openActivate(nomination)
                            }}
                          >
                            {t('admin.controls.activate', 'Activate')}
                          </Button>
                        ) : null}
                        {nomination.status === 'ACTIVE' ? (
                          <Button
                            size="small"
                            variant="contained"
                            color="error"
                            onClick={() => {
                              openDeactivate(nomination)
                            }}
                          >
                            {t('admin.controls.deactivate', 'Withdraw')}
                          </Button>
                        ) : null}
                        {nomination.status !== 'ACCEPTED' && nomination.status !== 'ACTIVE' ? (
                          <Typography variant="caption" color="text.secondary">
                            {t('admin.controls.noAction', '-')}
                          </Typography>
                        ) : null}
                      </ListingTable.Cell>
                    </ListingTable.Row>
                  )
                })}
            </ListingTable.Body>
          </ListingTable>
        </ListingTable.Container>

        {target ? (
          <>
            <ActivateNomineeDialog
              key={`activate-${target.id}-${String(activateOpen)}`}
              open={activateOpen}
              ownerName={ownerLabel}
              nomineeEmail={target.nomineeEmail}
              loading={activateMutation.isPending}
              errorMessage={activateMutation.error?.message ?? ''}
              onClose={() => {
                setActivateOpen(false)
                activateMutation.reset()
              }}
              onConfirm={(ticket) => {
                activateMutation.mutate(
                  { nominationId: target.id, ownerId: target.ownerId, ticket },
                  { onSuccess: () => setActivateOpen(false) },
                )
              }}
            />

            <DeactivateNomineeDialog
              open={deactivateOpen}
              ownerName={ownerLabel}
              loading={deactivateMutation.isPending}
              onClose={() => {
                setDeactivateOpen(false)
              }}
              onConfirm={(reason) => {
                deactivateMutation.mutate(
                  { nominationId: target.id, ownerId: target.ownerId, reason },
                  { onSuccess: () => setDeactivateOpen(false) },
                )
              }}
            />
          </>
        ) : null}
      </Stack>
    </Box>
  )
}

export default AdminNomineeActivationPage
