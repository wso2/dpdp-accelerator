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
  Paper,
  Popover,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
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
import { type MouseEvent, useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import CopyableText from '../../../components/CopyableText'
import CursorPaginationFooter from '../../../components/CursorPaginationFooter'
import type { ConsentRecord } from '../../../types/consent'
import { formatEpochTimestamp } from '../../../utils/dateTime'
import { CONSENT_REGISTRY_ROWS_PER_PAGE_OPTIONS } from '../constants'
import {
  getConsentStateChipColor,
  getConsentStateLabelKey,
  isConsentApprovableState,
  isConsentRevokableState,
} from '../utils/statusChip'

interface ConsentRegistryTableProps {
  rows: ConsentRecord[]
  isLoading: boolean
  isError: boolean
  rowsPerPage: number
  hasPreviousPage: boolean
  hasNextPage: boolean
  onPreviousPage: () => void
  onNextPage: () => void
  onRowsPerPageChange: (rowsPerPage: number) => void
  onRetry: () => void
  detailBasePath?: string
  showSubject?: boolean
  showPurposes?: boolean
  canApprove?: boolean
  canRevoke?: boolean
  onApprove?: (consentID: string) => void
  onRevoke?: (consentID: string) => void
  isMutating?: boolean
}

const PURPOSE_PREVIEW_COUNT = 2

export default function ConsentRegistryTable({
  rows,
  isLoading,
  isError,
  rowsPerPage,
  hasPreviousPage,
  hasNextPage,
  onPreviousPage,
  onNextPage,
  onRowsPerPageChange,
  onRetry,
  detailBasePath = '/consents',
  showSubject = false,
  showPurposes = true,
  canApprove = false,
  canRevoke = false,
  onApprove,
  onRevoke,
  isMutating = false,
}: ConsentRegistryTableProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const [purposesPopoverAnchor, setPurposesPopoverAnchor] = useState<HTMLElement | null>(null)
  const [selectedPurposes, setSelectedPurposes] = useState<string[]>([])
  const columnCount = 4 + (showSubject ? 1 : 0) + (showPurposes ? 1 : 0)

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
    <TableContainer component={Paper} elevation={1}>
      <Table aria-label={t('consentRegistry.table.tableAriaLabel')} sx={{ tableLayout: 'fixed' }}>
        <TableHead
          sx={(theme) => ({
            '& .MuiTableCell-head': {
              fontWeight: 600,
              ...theme.applyStyles('light', { backgroundColor: theme.palette.grey[50] }),
              ...theme.applyStyles('dark', { backgroundColor: 'rgba(255, 255, 255, 0.04)' }),
            },
          })}
        >
          <TableRow>
            <TableCell sx={{ width: '18%' }}>
              {t('consentRegistry.table.headers.consentId')}
            </TableCell>
            {showSubject ? (
              <TableCell sx={{ width: '14%' }}>{t('consentRegistry.table.headers.user')}</TableCell>
            ) : null}
            <TableCell sx={{ width: '16%' }}>
              {t('consentRegistry.table.headers.service')}
            </TableCell>
            {showPurposes ? (
              <TableCell sx={{ width: '26%' }}>
                {t('consentRegistry.table.headers.purposes')}
              </TableCell>
            ) : null}
            <TableCell sx={{ width: '12%' }}>{t('consentRegistry.table.headers.state')}</TableCell>
            <TableCell sx={{ width: '16%' }}>
              {t('consentRegistry.table.headers.created')}
            </TableCell>
            <TableCell align="center" sx={{ width: '10%' }}>
              {t('consentRegistry.table.headers.actions')}
            </TableCell>
          </TableRow>
        </TableHead>

        <TableBody>
          {isLoading
            ? Array.from({ length: rowsPerPage }, (_, rowIndex) => (
                <TableRow key={`skeleton-row-${String(rowIndex)}`}>
                  {Array.from({ length: columnCount }, (__, cellIndex) => (
                    <TableCell key={`skeleton-cell-${String(rowIndex)}-${String(cellIndex)}`}>
                      <Skeleton variant="text" width="80%" />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            : null}

          {!isLoading && isError ? (
            <TableRow>
              <TableCell colSpan={columnCount} align="center" sx={{ py: 8 }}>
                <Stack spacing={1} alignItems="center" justifyContent="center">
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
              </TableCell>
            </TableRow>
          ) : null}

          {!isLoading && !isError && rows.length === 0 ? (
            <TableRow>
              <TableCell colSpan={columnCount} align="center" sx={{ py: 8 }}>
                <Stack spacing={1} alignItems="center" justifyContent="center">
                  <Search size={28} aria-hidden="true" />
                  <Typography fontWeight={600}>
                    {t('consentRegistry.messages.emptyTitle')}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {t('consentRegistry.messages.empty')}
                  </Typography>
                </Stack>
              </TableCell>
            </TableRow>
          ) : null}

          {!isLoading && !isError
            ? rows.map((row) => {
                const rowPurposes = row.purposes ?? []
                const approvable = canApprove && isConsentApprovableState(row.state)
                const revokable = canRevoke && isConsentRevokableState(row.state)

                return (
                  <TableRow
                    key={row.id}
                    hover
                    data-consent-id={row.id}
                    onClick={handleRowClick}
                    sx={{ cursor: 'pointer' }}
                  >
                    <TableCell>
                      <CopyableText
                        value={row.id}
                        truncateAt={8}
                        monospace
                        textAriaLabel={t('consentRegistry.table.consentIdAriaLabel', {
                          id: row.id,
                        })}
                        copyTooltip={t('consentRegistry.actions.copyConsentId')}
                        copyAriaLabel={t('consentRegistry.actions.copyConsentIdAriaLabel', {
                          id: row.id,
                        })}
                      />
                    </TableCell>
                    {showSubject ? (
                      <TableCell>
                        <Typography variant="body2" noWrap title={row.subjectId}>
                          {row.subjectId}
                        </Typography>
                      </TableCell>
                    ) : null}
                    <TableCell>
                      <Typography variant="body2" noWrap title={row.serviceId}>
                        {row.serviceId}
                      </Typography>
                    </TableCell>
                    {showPurposes ? (
                      <TableCell sx={{ fontWeight: 500 }}>
                        <Box
                          sx={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 0.75,
                            flexWrap: 'wrap',
                          }}
                        >
                          {rowPurposes.slice(0, PURPOSE_PREVIEW_COUNT).map((purpose) => (
                            <Chip
                              key={`${row.id}-${purpose}`}
                              size="small"
                              label={purpose}
                              variant="outlined"
                            />
                          ))}
                          {rowPurposes.length > PURPOSE_PREVIEW_COUNT ? (
                            <Chip
                              size="small"
                              color="primary"
                              variant="outlined"
                              label={t('consentRegistry.table.purposes.more', {
                                count: rowPurposes.length - PURPOSE_PREVIEW_COUNT,
                              })}
                              onClick={(event) => {
                                event.stopPropagation()
                                setPurposesPopoverAnchor(event.currentTarget)
                                setSelectedPurposes(rowPurposes)
                              }}
                            />
                          ) : null}
                        </Box>
                      </TableCell>
                    ) : null}
                    <TableCell>
                      <Chip
                        size="small"
                        color={getConsentStateChipColor(row.state)}
                        label={t(`consentRegistry.status.${getConsentStateLabelKey(row.state)}`)}
                        variant="outlined"
                      />
                    </TableCell>
                    <TableCell sx={{ fontFamily: 'monospace' }}>
                      {formatEpochTimestamp(row.timestamp)}
                    </TableCell>
                    <TableCell align="center">
                      <Stack direction="row" spacing={0.5} justifyContent="center">
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
                        {approvable ? (
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
                        {revokable ? (
                          <Tooltip title={t('consentRegistry.actions.revoke')}>
                            <span>
                              <IconButton
                                size="small"
                                color="error"
                                aria-label={t('consentRegistry.actions.revoke')}
                                disabled={isMutating}
                                data-consent-id={row.id}
                                onClick={handleRevokeClick}
                              >
                                <Ban size={16} />
                              </IconButton>
                            </span>
                          </Tooltip>
                        ) : null}
                      </Stack>
                    </TableCell>
                  </TableRow>
                )
              })
            : null}
        </TableBody>
      </Table>

      <Popover
        open={Boolean(purposesPopoverAnchor)}
        anchorEl={purposesPopoverAnchor}
        onClose={() => {
          setPurposesPopoverAnchor(null)
          setSelectedPurposes([])
        }}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        transformOrigin={{ vertical: -4, horizontal: 'left' }}
      >
        <Box sx={{ p: 2, minWidth: 240, maxWidth: 420 }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
            {t('consentRegistry.table.purposes.title')}
          </Typography>
          <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap' }}>
            {selectedPurposes.map((purpose) => (
              <Chip key={purpose} size="small" label={purpose} variant="outlined" />
            ))}
          </Box>
        </Box>
      </Popover>

      <CursorPaginationFooter
        rowsPerPage={rowsPerPage}
        rowsPerPageOptions={CONSENT_REGISTRY_ROWS_PER_PAGE_OPTIONS}
        hasPreviousPage={hasPreviousPage}
        hasNextPage={hasNextPage}
        disabled={isLoading}
        onRowsPerPageChange={onRowsPerPageChange}
        onPreviousPage={onPreviousPage}
        onNextPage={onNextPage}
      />
    </TableContainer>
  )
}

ConsentRegistryTable.defaultProps = {
  detailBasePath: '/consents',
  showSubject: false,
  showPurposes: true,
  canApprove: false,
  canRevoke: false,
  onApprove: undefined,
  onRevoke: undefined,
  isMutating: false,
}
