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
  IconButton,
  ListingTable,
  Popover,
  Skeleton,
  Stack,
  TablePagination,
  Tooltip,
  Typography,
} from '@wso2/oxygen-ui'
import {
  Ban,
  CircleCheckBig,
  CircleSlash,
  Eye,
  RefreshCw,
  Search,
} from '@wso2/oxygen-ui-icons-react'
import { Fragment, type MouseEvent, useCallback, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import type {
  ConsentRecord,
  ConsentRegistrySortDirection,
  ConsentRegistrySortField,
} from '../../../types/consent'
import { formatEpochTimestamp, formatIsoDateTime } from '../../../utils/dateTime'
import { CONSENT_REGISTRY_ROWS_PER_PAGE_OPTIONS } from '../constants'
import { getConsentStatusChipColor, getConsentStatusLabelKey } from '../utils/statusChip'

interface ConsentRegistryTableProps {
  rows: ConsentRecord[]
  totalCount: number
  isLoading: boolean
  isError: boolean
  page: number
  rowsPerPage: number
  sortField: ConsentRegistrySortField
  sortDirection: ConsentRegistrySortDirection
  onPageChange: (page: number) => void
  onRowsPerPageChange: (rowsPerPage: number) => void
  onSortChange: (field: ConsentRegistrySortField, direction: ConsentRegistrySortDirection) => void
  onRetry: () => void
  detailBasePath?: string
  showApproveAction?: boolean
  showMutationActions?: boolean
  onApprove?: (consentID: string) => void
  onRevoke?: (consentID: string) => void
  isMutating: boolean
}

const PURPOSE_PREVIEW_COUNT = 2

const CONSENT_REGISTRY_COLUMN_WIDTHS = {
  purposes: '47%',
  status: '10%',
  updated: '17%',
  expiration: '17%',
  actions: '8%',
} as const

export default function ConsentRegistryTable({
  rows,
  totalCount,
  isLoading,
  isError,
  page,
  rowsPerPage,
  sortField,
  sortDirection,
  onPageChange,
  onRowsPerPageChange,
  onSortChange,
  onRetry,
  detailBasePath = '/consents',
  showApproveAction = true,
  showMutationActions = true,
  onApprove,
  onRevoke,
  isMutating,
}: ConsentRegistryTableProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const [purposesPopoverAnchor, setPurposesPopoverAnchor] = useState<HTMLElement | null>(null)
  const [selectedPurposes, setSelectedPurposes] = useState<string[]>([])

  const groupedRows = useMemo(() => {
    const groupedMap = new Map<string, ConsentRecord[]>()

    rows.forEach((row) => {
      const existingRows = groupedMap.get(row.groupId)

      if (existingRows) {
        groupedMap.set(row.groupId, [...existingRows, row])
        return
      }

      groupedMap.set(row.groupId, [row])
    })

    return Array.from(groupedMap.entries()).map(([groupId, groupRows]) => ({
      groupId,
      groupRows,
    }))
  }, [rows])

  const selectedRowIds: readonly string[] = []
  const isPurposesPopoverOpen = Boolean(purposesPopoverAnchor)

  const handlePurposesPopoverClose = (): void => {
    setPurposesPopoverAnchor(null)
    setSelectedPurposes([])
  }

  const handleStopPropagation = (event: MouseEvent<HTMLElement>): void => {
    event.stopPropagation()
  }

  const handleRowClick = useCallback(
    (event: MouseEvent<HTMLElement>): void => {
      const consentID = event.currentTarget.dataset.consentId

      if (consentID) {
        navigate(`${detailBasePath}/${encodeURIComponent(consentID)}`)
      }
    },
    [detailBasePath, navigate],
  )

  const handleApproveClick = useCallback(
    (event: MouseEvent<HTMLElement>): void => {
      event.stopPropagation()
      const consentID = event.currentTarget.dataset.consentId

      if (consentID) {
        onApprove?.(consentID)
      }
    },
    [onApprove],
  )

  const handleRevokeClick = useCallback(
    (event: MouseEvent<HTMLElement>): void => {
      event.stopPropagation()
      const consentID = event.currentTarget.dataset.consentId

      if (consentID) {
        onRevoke?.(consentID)
      }
    },
    [onRevoke],
  )

  return (
    <ListingTable.Provider
      density="standard"
      page={page}
      rowsPerPage={rowsPerPage}
      totalCount={totalCount}
      selected={selectedRowIds}
      sortField={sortField}
      sortDirection={sortDirection}
      isSelected={() => false}
      onBulkDelete={() => {}}
      onClearSelection={() => {}}
      onDensityChange={() => {}}
      onPageChange={onPageChange}
      onRowsPerPageChange={onRowsPerPageChange}
      onSearchChange={() => {}}
      onSelectAll={() => {}}
      onSelectionChange={() => {}}
      onSortChange={(nextField, nextDirection) => {
        onSortChange(nextField as ConsentRegistrySortField, nextDirection)
      }}
    >
      <ListingTable.Container sx={{ minWidth: 1080 }}>
        <ListingTable
          density="standard"
          variant="table"
          aria-label={t('consentRegistry.table.tableAriaLabel')}
          sx={{ tableLayout: 'fixed' }}
        >
          <ListingTable.Head>
            <ListingTable.Row>
              <ListingTable.Cell sx={{ width: CONSENT_REGISTRY_COLUMN_WIDTHS.purposes }}>
                {t('consentRegistry.table.headers.purposes')}
              </ListingTable.Cell>
              <ListingTable.Cell sx={{ width: CONSENT_REGISTRY_COLUMN_WIDTHS.status }}>
                <ListingTable.SortLabel field="status">
                  {t('consentRegistry.table.headers.status')}
                </ListingTable.SortLabel>
              </ListingTable.Cell>
              <ListingTable.Cell sx={{ width: CONSENT_REGISTRY_COLUMN_WIDTHS.updated }}>
                <ListingTable.SortLabel field="updatedTime">
                  {t('consentRegistry.table.headers.updated')}
                </ListingTable.SortLabel>
              </ListingTable.Cell>
              <ListingTable.Cell sx={{ width: CONSENT_REGISTRY_COLUMN_WIDTHS.expiration }}>
                <ListingTable.SortLabel field="validityTime">
                  {t('consentRegistry.table.headers.expiration')}
                </ListingTable.SortLabel>
              </ListingTable.Cell>
              <ListingTable.Cell
                align="center"
                sx={{ width: CONSENT_REGISTRY_COLUMN_WIDTHS.actions }}
              >
                {t('consentRegistry.table.headers.actions')}
              </ListingTable.Cell>
            </ListingTable.Row>
          </ListingTable.Head>

          <ListingTable.Body>
            {isLoading
              ? Array.from({ length: rowsPerPage }, (_, rowIndex) => (
                  <ListingTable.Row key={`skeleton-row-${rowIndex}`} variant="table">
                    <ListingTable.Cell sx={{ width: CONSENT_REGISTRY_COLUMN_WIDTHS.purposes }}>
                      <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap' }}>
                        <Skeleton variant="rounded" width={140} height={24} />
                      </Box>
                    </ListingTable.Cell>
                    <ListingTable.Cell sx={{ width: CONSENT_REGISTRY_COLUMN_WIDTHS.status }}>
                      <Skeleton variant="rounded" width={72} height={24} />
                    </ListingTable.Cell>
                    <ListingTable.Cell sx={{ width: CONSENT_REGISTRY_COLUMN_WIDTHS.updated }}>
                      <Skeleton variant="text" width="86%" />
                    </ListingTable.Cell>
                    <ListingTable.Cell sx={{ width: CONSENT_REGISTRY_COLUMN_WIDTHS.expiration }}>
                      <Skeleton variant="text" width="86%" />
                    </ListingTable.Cell>
                    <ListingTable.Cell
                      align="center"
                      sx={{ width: CONSENT_REGISTRY_COLUMN_WIDTHS.actions }}
                    >
                      <Box sx={{ display: 'flex', justifyContent: 'center', gap: 0.75 }}>
                        <Skeleton variant="circular" width={24} height={24} />
                        <Skeleton variant="circular" width={24} height={24} />
                      </Box>
                    </ListingTable.Cell>
                  </ListingTable.Row>
                ))
              : null}
            {!isLoading && isError ? (
              <ListingTable.Row variant="table">
                <ListingTable.Cell colSpan={6} align="center" sx={{ py: 8 }}>
                  <Stack
                    spacing={1}
                    alignItems="center"
                    justifyContent="center"
                    sx={{ minHeight: 264 }}
                  >
                    <CircleSlash size={28} aria-hidden="true" />
                    <Typography fontWeight={600}>
                      {t('consentRegistry.messages.loadFailed')}
                    </Typography>
                    <Button
                      size="small"
                      variant="outlined"
                      startIcon={<RefreshCw size={16} />}
                      onClick={onRetry}
                    >
                      {t('catalog.actions.retry')}
                    </Button>
                  </Stack>
                </ListingTable.Cell>
              </ListingTable.Row>
            ) : null}
            {!isLoading && !isError && groupedRows.length === 0 ? (
              <ListingTable.Row variant="table">
                <ListingTable.Cell colSpan={6} align="center" sx={{ py: 8 }}>
                  <Stack
                    spacing={1}
                    alignItems="center"
                    justifyContent="center"
                    sx={{ minHeight: 264 }}
                  >
                    <Search size={28} aria-hidden="true" />
                    <Typography fontWeight={600}>
                      {t('consentRegistry.messages.emptyTitle')}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {t('consentRegistry.messages.empty')}
                    </Typography>
                  </Stack>
                </ListingTable.Cell>
              </ListingTable.Row>
            ) : null}
            {!isLoading && !isError && groupedRows.length > 0
              ? groupedRows.map((group) => (
                  <Fragment key={group.groupId}>
                    <ListingTable.Row
                      variant="table"
                      sx={{
                        bgcolor: 'action.hover',
                      }}
                    >
                      <ListingTable.Cell colSpan={6} sx={{ fontWeight: 700 }}>
                        {t('consentRegistry.table.groupLabel', { groupId: group.groupId })}
                      </ListingTable.Cell>
                    </ListingTable.Row>

                    {group.groupRows.map((row) => (
                      <ListingTable.Row
                        key={row.id}
                        hover
                        variant="table"
                        data-consent-id={row.id}
                        onClick={handleRowClick}
                        sx={{ cursor: 'pointer' }}
                      >
                        <ListingTable.Cell
                          sx={{ width: CONSENT_REGISTRY_COLUMN_WIDTHS.purposes, fontWeight: 500 }}
                        >
                          <Box
                            sx={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 0.75,
                              flexWrap: 'wrap',
                            }}
                          >
                            {row.purposes.slice(0, PURPOSE_PREVIEW_COUNT).map((purpose) => (
                              <Chip
                                key={`${row.id}-${purpose}`}
                                size="small"
                                label={purpose}
                                variant="outlined"
                              />
                            ))}
                            {row.purposes.length > PURPOSE_PREVIEW_COUNT ? (
                              <Chip
                                size="small"
                                color="primary"
                                variant="outlined"
                                label={t('consentRegistry.table.purposes.more', {
                                  count: row.purposes.length - PURPOSE_PREVIEW_COUNT,
                                  defaultValue: '+{{count}} more',
                                })}
                                onClick={(event) => {
                                  event.stopPropagation()
                                  setPurposesPopoverAnchor(event.currentTarget)
                                  setSelectedPurposes(row.purposes)
                                }}
                              />
                            ) : null}
                          </Box>
                        </ListingTable.Cell>
                        <ListingTable.Cell sx={{ width: CONSENT_REGISTRY_COLUMN_WIDTHS.status }}>
                          <Chip
                            size="small"
                            color={getConsentStatusChipColor(row.status)}
                            label={t(
                              `consentRegistry.status.${getConsentStatusLabelKey(row.status)}`,
                            )}
                            variant="outlined"
                          />
                        </ListingTable.Cell>
                        <ListingTable.Cell
                          sx={{
                            width: CONSENT_REGISTRY_COLUMN_WIDTHS.updated,
                            fontFamily: 'monospace',
                          }}
                        >
                          {formatIsoDateTime(row.updatedAt)}
                        </ListingTable.Cell>
                        <ListingTable.Cell
                          sx={
                            row.expirationTime === 0
                              ? {
                                  width: CONSENT_REGISTRY_COLUMN_WIDTHS.expiration,
                                  color: 'text.disabled',
                                  fontFamily: 'monospace',
                                }
                              : {
                                  width: CONSENT_REGISTRY_COLUMN_WIDTHS.expiration,
                                  fontFamily: 'monospace',
                                }
                          }
                        >
                          {row.expirationTime === 0
                            ? t('consentRegistry.table.notApplicable')
                            : formatEpochTimestamp(row.expirationTime)}
                        </ListingTable.Cell>
                        <ListingTable.Cell
                          align="center"
                          sx={{ width: CONSENT_REGISTRY_COLUMN_WIDTHS.actions }}
                        >
                          <ListingTable.RowActions visibility="always">
                            <Tooltip title={t('consentRegistry.actions.view')}>
                              <IconButton
                                size="small"
                                component={RouterLink}
                                to={`${detailBasePath}/${encodeURIComponent(row.id)}`}
                                aria-label={t('consentRegistry.actions.view')}
                                onClick={handleStopPropagation}
                              >
                                <Eye size={16} />
                              </IconButton>
                            </Tooltip>
                            {showMutationActions && showApproveAction && row.canApprove ? (
                              <Tooltip title={t('consentRegistry.actions.approve')}>
                                <span>
                                  <IconButton
                                    size="small"
                                    color="warning"
                                    aria-label={t('consentRegistry.actions.approve')}
                                    disabled={isMutating}
                                    data-consent-id={row.id}
                                    onClick={handleApproveClick}
                                  >
                                    <CircleCheckBig size={16} />
                                  </IconButton>
                                </span>
                              </Tooltip>
                            ) : null}
                            {showMutationActions && !(showApproveAction && row.canApprove) ? (
                              <Tooltip title={t('consentRegistry.actions.revoke')}>
                                <span>
                                  <IconButton
                                    size="small"
                                    color="error"
                                    disabled={!row.canRevoke || isMutating}
                                    aria-label={t('consentRegistry.actions.revoke')}
                                    data-consent-id={row.id}
                                    onClick={handleRevokeClick}
                                  >
                                    <Ban size={16} />
                                  </IconButton>
                                </span>
                              </Tooltip>
                            ) : null}
                          </ListingTable.RowActions>
                        </ListingTable.Cell>
                      </ListingTable.Row>
                    ))}
                  </Fragment>
                ))
              : null}
          </ListingTable.Body>
        </ListingTable>

        <Popover
          open={isPurposesPopoverOpen}
          anchorEl={purposesPopoverAnchor}
          onClose={handlePurposesPopoverClose}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
          transformOrigin={{ vertical: -4, horizontal: 'left' }}
          PaperProps={{
            sx: {
              mt: 0.5,
              borderRadius: 1,
              border: 2,
              borderColor: 'divider',
              boxShadow: 6,
              overflow: 'hidden',
            },
          }}
        >
          <Box sx={{ minWidth: 260, maxWidth: 420 }}>
            <Box
              sx={{
                px: 2,
                py: 1.5,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                bgcolor: 'action.hover',
                borderBottom: 1,
                borderColor: 'divider',
              }}
            >
              <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                {t('consentRegistry.table.purposes.title', 'Consent purposes')}
              </Typography>
              <Chip
                size="small"
                color="default"
                variant="filled"
                label={selectedPurposes.length}
                sx={{ height: 20, '& .MuiChip-label': { px: 0.75, fontWeight: 600 } }}
              />
            </Box>
            <Box
              sx={{
                p: 2,
                display: 'flex',
                gap: 0.75,
                flexWrap: 'wrap',
                maxHeight: 280,
                overflowY: 'auto',
              }}
            >
              {selectedPurposes.map((purpose) => (
                <Chip key={purpose} size="small" label={purpose} variant="outlined" />
              ))}
            </Box>
            <Box
              sx={{
                px: 2,
                py: 1,
                borderTop: 1,
                borderColor: 'divider',
                bgcolor: 'background.paper',
              }}
            >
              <Typography variant="caption" color="text.secondary">
                {t('consentRegistry.table.purposes.hint', 'Showing all purposes of the consent')}
              </Typography>
            </Box>
          </Box>
        </Popover>

        <TablePagination
          component="div"
          count={totalCount}
          page={page}
          rowsPerPage={rowsPerPage}
          rowsPerPageOptions={[...CONSENT_REGISTRY_ROWS_PER_PAGE_OPTIONS]}
          onPageChange={(_, nextPage) => {
            onPageChange(nextPage)
          }}
          onRowsPerPageChange={(event) => {
            onRowsPerPageChange(Number(event.target.value))
          }}
        />
      </ListingTable.Container>
    </ListingTable.Provider>
  )
}

ConsentRegistryTable.defaultProps = {
  detailBasePath: '/consents',
  showApproveAction: true,
  showMutationActions: true,
  onApprove: undefined,
  onRevoke: undefined,
}
