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

import { Box, Button, Card, Chip, ListingTable, Popover, Stack, Typography } from '@wso2/oxygen-ui'
import { ArrowRight, UserPlus, Users } from '@wso2/oxygen-ui-icons-react'
import { type MouseEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import { DEFAULT_NOMINEE_PERMISSIONS, findNomineePermission } from '../../types/nominee'
import type { NominationResponse, NominationStatus, NomineePermission } from '../../types/nominee'
import AddNomineeDialog from './components/AddNomineeDialog'
import PersonCell from './components/PersonCell'
import RemoveNomineeDialog from './components/RemoveNomineeDialog'
import {
  useAcceptNominationMutation,
  useRejectNominationMutation,
  useMyNominationsQuery,
  useNominatedForQuery,
  useRemoveNominationMutation,
  useAddNominationMutation,
} from './hooks/useNomineeQueries'

const STATUS_COLOR: Record<NominationStatus, 'success' | 'info' | 'warning' | 'default'> = {
  ACTIVE: 'success',
  ACCEPTED: 'info',
  PENDING: 'warning',
  REJECTED: 'default',
  DEACTIVATED: 'default',
}

const NOMINEE_COLUMN_WIDTHS = {
  person: '38%',
  permissions: '32%',
  status: '18%',
  actions: '12%',
} as const

// Status matches NOMINEE_COLUMN_WIDTHS so both tables on this page put that
// column at the same width. Actions is wider than the table above because these
// rows carry labelled buttons rather than icons, and the widest case is the two
// buttons a pending row shows.
const NOMINATED_FOR_COLUMN_WIDTHS = {
  account: '64%',
  status: '18%',
  actions: '18%',
} as const

/** How many permission chips are shown inline before the rest collapse into "+N more". */
const PERMISSION_PREVIEW_COUNT = 2

function NominationsPage(): React.JSX.Element {
  const { t } = useTranslation('common')

  const myNominationsQuery = useMyNominationsQuery()
  const nominatedForQuery = useNominatedForQuery()
  const addNominationMutation = useAddNominationMutation()
  const acceptNominationMutation = useAcceptNominationMutation()
  const rejectNominationMutation = useRejectNominationMutation()
  const removeNominationMutation = useRemoveNominationMutation()

  const [dialogOpen, setDialogOpen] = useState<boolean>(false)
  // Which nomination the remove dialog is for. An owner may have many, so the
  // dialog has to be told which one rather than assuming "the" nominee.
  const [removalTarget, setRemovalTarget] = useState<NominationResponse | null>(null)
  const [permissionsAnchor, setPermissionsAnchor] = useState<HTMLElement | null>(null)
  const [overflowPermissions, setOverflowPermissions] = useState<NomineePermission[]>([])

  const myNominations = myNominationsQuery.data ?? []
  const nominatedFor = nominatedForQuery.data ?? []

  const statusLabel = (status: NominationStatus): string =>
    ({
      ACTIVE: t('nominee.mine.status.active', 'Active'),
      ACCEPTED: t('nominee.mine.status.accepted', 'Awaiting approval'),
      PENDING: t('nominee.mine.status.pending', 'Awaiting acceptance'),
      REJECTED: t('nominee.mine.status.rejected', 'Declined'),
      DEACTIVATED: t('nominee.mine.status.deactivated', 'Suspended'),
    })[status]

  // On this side of the relationship the reader is the one who has to accept,
  // so "Awaiting acceptance" would be describing them in the third person.
  const receivedStatusLabel = (status: NominationStatus): string =>
    status === 'PENDING'
      ? t('nominee.nominatedFor.actionNeeded', 'Action needed')
      : statusLabel(status)

  const permissionLabel = (permission: string): string => {
    const option = findNomineePermission(permission)
    return option ? t(option.labelKey, option.defaultLabel) : permission
  }

  const openPermissionsPopover = (
    event: MouseEvent<HTMLElement>,
    permissions: NomineePermission[],
  ): void => {
    setOverflowPermissions(permissions)
    setPermissionsAnchor(event.currentTarget)
  }

  const emptyRow = (colSpan: number, message: string): React.JSX.Element => (
    <ListingTable.Row variant="table">
      <ListingTable.Cell colSpan={colSpan} align="center" sx={{ py: 6 }}>
        <Stack spacing={1} alignItems="center" justifyContent="center">
          <Users size={28} aria-hidden="true" />
          <Typography fontWeight={600}>{message}</Typography>
        </Stack>
      </ListingTable.Cell>
    </ListingTable.Row>
  )

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          justifyContent="space-between"
          alignItems={{ sm: 'flex-end' }}
          spacing={2}
        >
          <Stack spacing={1}>
            <HeaderBreadcrumbs />
            <Typography variant="h4" fontWeight={700}>
              {t('nominee.title', 'Nominations')}
            </Typography>
          </Stack>
          <Button
            variant="contained"
            startIcon={<UserPlus size={18} />}
            onClick={() => {
              setDialogOpen(true)
            }}
          >
            {t('nominee.mine.add', 'Add nominee')}
          </Button>
        </Stack>

        {myNominationsQuery.isError || nominatedForQuery.isError ? (
          <Typography color="error.main">
            {t('nominee.messages.loadFailed', 'Unable to load nominations right now.')}
          </Typography>
        ) : null}

        <Card>
          <Stack sx={{ p: 3, pb: 2 }}>
            <Typography variant="h6" fontWeight={700}>
              {t('nominee.mine.title', 'Nominees')}
            </Typography>
          </Stack>

          <ListingTable.Container>
            <ListingTable
              density="standard"
              variant="table"
              aria-label={t('nominee.mine.ariaLabel', 'Nominees')}
              sx={{ tableLayout: 'fixed', minWidth: 720 }}
            >
              <ListingTable.Head>
                <ListingTable.Row>
                  <ListingTable.Cell sx={{ width: NOMINEE_COLUMN_WIDTHS.person }}>
                    {t('nominee.mine.table.person', 'Person')}
                  </ListingTable.Cell>
                  <ListingTable.Cell sx={{ width: NOMINEE_COLUMN_WIDTHS.permissions }}>
                    {t('nominee.mine.table.permissions', 'Permissions')}
                  </ListingTable.Cell>
                  <ListingTable.Cell align="center" sx={{ width: NOMINEE_COLUMN_WIDTHS.status }}>
                    {t('nominee.mine.table.status', 'Status')}
                  </ListingTable.Cell>
                  <ListingTable.Cell align="center" sx={{ width: NOMINEE_COLUMN_WIDTHS.actions }}>
                    {t('nominee.mine.table.actions', 'Actions')}
                  </ListingTable.Cell>
                </ListingTable.Row>
              </ListingTable.Head>

              <ListingTable.Body>
                {myNominations.length === 0
                  ? emptyRow(4, t('nominee.mine.empty', "You haven't nominated anyone yet."))
                  : myNominations.map((nomination: NominationResponse) => {
                      const visible = nomination.permissions.slice(0, PERMISSION_PREVIEW_COUNT)
                      const overflow = nomination.permissions.slice(PERMISSION_PREVIEW_COUNT)

                      return (
                        <ListingTable.Row key={nomination.id} variant="table">
                          <ListingTable.Cell sx={{ width: NOMINEE_COLUMN_WIDTHS.person }}>
                            <PersonCell
                              userId={nomination.nomineeId}
                              email={nomination.nomineeEmail}
                            />
                          </ListingTable.Cell>

                          <ListingTable.Cell sx={{ width: NOMINEE_COLUMN_WIDTHS.permissions }}>
                            <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                              {visible.map((permission: NomineePermission) => (
                                <Chip
                                  key={permission}
                                  size="small"
                                  variant="outlined"
                                  label={permissionLabel(permission)}
                                  color={
                                    findNomineePermission(permission)?.risky ? 'error' : 'default'
                                  }
                                />
                              ))}
                              {overflow.length > 0 ? (
                                <Chip
                                  size="small"
                                  variant="outlined"
                                  label={t('nominee.mine.table.morePermissions', {
                                    count: overflow.length,
                                    defaultValue: '+{{count}} more',
                                  })}
                                  onClick={(event) => {
                                    openPermissionsPopover(event, overflow)
                                  }}
                                />
                              ) : null}
                            </Stack>
                          </ListingTable.Cell>

                          <ListingTable.Cell
                            align="center"
                            sx={{ width: NOMINEE_COLUMN_WIDTHS.status }}
                          >
                            <Chip
                              size="small"
                              color={STATUS_COLOR[nomination.status]}
                              variant="outlined"
                              label={statusLabel(nomination.status)}
                            />
                          </ListingTable.Cell>

                          <ListingTable.Cell
                            align="center"
                            sx={{ width: NOMINEE_COLUMN_WIDTHS.actions }}
                          >
                            <Button
                              variant="contained"
                              color="error"
                              size="small"
                              onClick={() => {
                                setRemovalTarget(nomination)
                              }}
                            >
                              {t('nominee.mine.remove', 'Remove')}
                            </Button>
                          </ListingTable.Cell>
                        </ListingTable.Row>
                      )
                    })}
              </ListingTable.Body>
            </ListingTable>
          </ListingTable.Container>
        </Card>

        <Card>
          <Stack sx={{ p: 3, pb: 2 }}>
            <Typography variant="h6" fontWeight={700}>
              {t('nominee.nominatedFor.title', 'Nominated Accounts')}
            </Typography>
          </Stack>

          <ListingTable.Container>
            <ListingTable
              density="standard"
              variant="table"
              aria-label={t('nominee.nominatedFor.ariaLabel', 'Nominated accounts')}
              sx={{ tableLayout: 'fixed', minWidth: 720 }}
            >
              <ListingTable.Head>
                <ListingTable.Row>
                  <ListingTable.Cell sx={{ width: NOMINATED_FOR_COLUMN_WIDTHS.account }}>
                    {t('nominee.nominatedFor.table.account', 'Account')}
                  </ListingTable.Cell>
                  <ListingTable.Cell
                    align="center"
                    sx={{ width: NOMINATED_FOR_COLUMN_WIDTHS.status }}
                  >
                    {t('nominee.nominatedFor.table.status', 'Status')}
                  </ListingTable.Cell>
                  <ListingTable.Cell
                    align="center"
                    sx={{ width: NOMINATED_FOR_COLUMN_WIDTHS.actions }}
                  >
                    {t('nominee.nominatedFor.table.actions', 'Actions')}
                  </ListingTable.Cell>
                </ListingTable.Row>
              </ListingTable.Head>

              <ListingTable.Body>
                {nominatedFor.length === 0
                  ? emptyRow(3, t('nominee.nominatedFor.empty', 'No one has nominated you yet.'))
                  : nominatedFor.map((entry: NominationResponse) => (
                      <ListingTable.Row key={entry.id} variant="table">
                        <ListingTable.Cell sx={{ width: NOMINATED_FOR_COLUMN_WIDTHS.account }}>
                          <PersonCell userId={entry.ownerId} email="" />
                        </ListingTable.Cell>

                        <ListingTable.Cell
                          align="center"
                          sx={{ width: NOMINATED_FOR_COLUMN_WIDTHS.status }}
                        >
                          <Chip
                            size="small"
                            color={STATUS_COLOR[entry.status]}
                            variant="outlined"
                            label={receivedStatusLabel(entry.status)}
                          />
                        </ListingTable.Cell>

                        <ListingTable.Cell
                          align="center"
                          sx={{ width: NOMINATED_FOR_COLUMN_WIDTHS.actions }}
                        >
                          <Stack
                            direction="row"
                            spacing={1}
                            justifyContent="center"
                            sx={{ '& .MuiButton-root': { whiteSpace: 'nowrap' } }}
                          >
                            {entry.status === 'PENDING' ? (
                              <>
                                <Button
                                  size="small"
                                  variant="contained"
                                  disabled={acceptNominationMutation.isPending}
                                  onClick={() => {
                                    acceptNominationMutation.mutate(entry.id)
                                  }}
                                >
                                  {t('nominee.nominatedFor.accept', 'Accept')}
                                </Button>
                                <Button
                                  size="small"
                                  variant="contained"
                                  color="error"
                                  disabled={rejectNominationMutation.isPending}
                                  onClick={() => {
                                    rejectNominationMutation.mutate(entry.id)
                                  }}
                                >
                                  {t('nominee.nominatedFor.reject', 'Decline')}
                                </Button>
                              </>
                            ) : null}
                            {entry.status === 'ACTIVE' ? (
                              <Button
                                size="small"
                                variant="contained"
                                endIcon={<ArrowRight size={16} />}
                                onClick={() => {
                                  // Opened without "noopener" on purpose. A browser only
                                  // lets a script close a tab it opened as an auxiliary
                                  // context; "noopener" makes it an independent one, and
                                  // after the sign-in round trip its history is several
                                  // entries deep, so Exit could no longer close it. The
                                  // target is this same application on the same origin,
                                  // so an opener reference gives away nothing.
                                  window.open(
                                    `${import.meta.env.BASE_URL.replace(/\/$/, '')}/acting/${entry.ownerId}`,
                                    '_blank',
                                  )
                                }}
                              >
                                {t('nominee.nominatedFor.openAccount', 'Open account')}
                              </Button>
                            ) : null}
                          </Stack>
                        </ListingTable.Cell>
                      </ListingTable.Row>
                    ))}
              </ListingTable.Body>
            </ListingTable>
          </ListingTable.Container>
        </Card>

        <Popover
          open={Boolean(permissionsAnchor)}
          anchorEl={permissionsAnchor}
          onClose={() => {
            setPermissionsAnchor(null)
          }}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        >
          <Stack spacing={0.5} sx={{ p: 1.5, maxWidth: 280 }}>
            {overflowPermissions.map((permission: NomineePermission) => (
              <Chip
                key={permission}
                size="small"
                variant="outlined"
                label={permissionLabel(permission)}
                color={findNomineePermission(permission)?.risky ? 'error' : 'default'}
              />
            ))}
          </Stack>
        </Popover>

        <AddNomineeDialog
          key={`nominee-dialog-${String(dialogOpen)}`}
          open={dialogOpen}
          loading={addNominationMutation.isPending}
          errorMessage={addNominationMutation.error?.message ?? ''}
          mode="add"
          initialEmail=""
          initialPermissions={DEFAULT_NOMINEE_PERMISSIONS}
          onClose={() => {
            setDialogOpen(false)
          }}
          onConfirm={(submission) => {
            addNominationMutation.mutate(submission, {
              onSuccess: () => {
                setDialogOpen(false)
              },
            })
          }}
        />

        {removalTarget ? (
          <RemoveNomineeDialog
            key={`remove-nominee-dialog-${removalTarget.id}`}
            open
            nomineeEmail={removalTarget.nomineeEmail}
            loading={removeNominationMutation.isPending}
            onClose={() => {
              setRemovalTarget(null)
            }}
            onConfirm={() => {
              removeNominationMutation.mutate(removalTarget.id, {
                onSuccess: () => {
                  setRemovalTarget(null)
                },
              })
            }}
          />
        ) : null}
      </Stack>
    </Box>
  )
}

export default NominationsPage
