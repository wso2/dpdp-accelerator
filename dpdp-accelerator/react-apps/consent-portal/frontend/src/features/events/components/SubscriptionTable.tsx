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
  Alert,
  Button,
  Chip,
  IconButton,
  LinearProgress,
  Paper,
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
import { Eye, Trash2 } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import SubscriptionTopicChips from './SubscriptionTopicChips'
import CopyableText from '../../../components/CopyableText'
import CursorPaginationFooter from '../../../components/CursorPaginationFooter'
import type { SubscriptionRecord } from '../../../types/subscription'
import { getSubscriptionStatusChipColor } from '../utils/subscriptionStatusChip'

const ROWS_PER_PAGE_OPTIONS = [10, 20, 50] as const

interface SubscriptionTableProps {
  rows: SubscriptionRecord[]
  isLoading: boolean
  isError: boolean
  rowsPerPage: number
  hasPreviousPage: boolean
  hasNextPage: boolean
  canWrite: boolean
  isMutating?: boolean
  onPreviousPage: () => void
  onNextPage: () => void
  onRowsPerPageChange: (rowsPerPage: number) => void
  onRetry: () => void
  onViewDetails: (subscription: SubscriptionRecord) => void
  onDelete: (subscription: SubscriptionRecord) => void
}

export default function SubscriptionTable({
  rows,
  isLoading,
  isError,
  rowsPerPage,
  hasPreviousPage,
  hasNextPage,
  canWrite,
  isMutating = false,
  onPreviousPage,
  onNextPage,
  onRowsPerPageChange,
  onRetry,
  onViewDetails,
  onDelete,
}: SubscriptionTableProps): React.JSX.Element {
  const { t } = useTranslation('common')

  if (isError) {
    return (
      <Paper sx={{ p: 3, textAlign: 'center' }}>
        <Alert severity="error" sx={{ mb: 2 }}>
          {t('subscriptions.loadFailed')}
        </Alert>
        <Button variant="outlined" onClick={onRetry}>
          {t('authorization.tryAgain')}
        </Button>
      </Paper>
    )
  }

  return (
    <Paper sx={{ width: '100%', overflow: 'hidden', boxShadow: 1 }}>
      {isLoading ? <LinearProgress /> : null}
      <TableContainer>
        <Table aria-label={t('subscriptions.table.ariaLabel')}>
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
              <TableCell>{t('subscriptions.table.name', 'Name')}</TableCell>
              <TableCell>{t('subscriptions.table.subscriptionId', 'Subscription ID')}</TableCell>
              <TableCell>{t('subscriptions.topicUi.topics', 'Topics')}</TableCell>
              <TableCell>{t('subscriptions.table.filter', 'Filter')}</TableCell>
              <TableCell>{t('subscriptions.table.deliveryMode', 'Delivery Mode')}</TableCell>
              <TableCell>{t('subscriptions.table.status', 'Status')}</TableCell>
              <TableCell align="right">{t('subscriptions.table.actions', 'Actions')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.length === 0 && !isLoading ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 4 }}>
                  <Typography color="text.secondary">{t('subscriptions.table.empty')}</Typography>
                </TableCell>
              </TableRow>
            ) : null}
            {rows.map((sub) => {
              const statusStr = (sub.status || 'ACTIVE').toUpperCase()
              const isDeleted = statusStr === 'DELETED'
              const deliveryMode = (sub.delivery?.mode || 'webhook').toLowerCase()
              const isWebhook = deliveryMode === 'webhook'
              const filterType = sub.filter?.type || 'all'
              const purposeCount = sub.filter?.purposes?.length ?? 0

              const defaultFilterName = filterType === 'all' ? 'All Purposes' : filterType
              let filterLabel = t(`subscriptions.filterType.${filterType}`, defaultFilterName)
              if (filterType !== 'all' && purposeCount > 0) {
                filterLabel += ` (${purposeCount})`
              }

              return (
                <TableRow
                  key={sub.subscriptionId}
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => onViewDetails(sub)}
                >
                  <TableCell>
                    <Typography
                      variant="body2"
                      fontWeight={600}
                      sx={{
                        wordBreak: 'break-word',
                        maxWidth: 180,
                        whiteSpace: 'normal',
                      }}
                    >
                      {sub.name || '-'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <CopyableText value={sub.subscriptionId} truncateAt={14} monospace />
                  </TableCell>
                  <TableCell>
                    <SubscriptionTopicChips topics={sub.topics ?? (sub.topic ? [sub.topic] : [])} />
                  </TableCell>
                  <TableCell>
                    <Tooltip
                      title={
                        sub.filter?.purposes?.length
                          ? sub.filter.purposes.join(', ')
                          : t('subscriptions.filterType.allDescription', 'Listens to all purposes')
                      }
                    >
                      <Chip size="small" variant="outlined" label={filterLabel} />
                    </Tooltip>
                  </TableCell>
                  <TableCell>
                    <Tooltip
                      title={
                        isWebhook && sub.delivery?.callbackUrl
                          ? sub.delivery.callbackUrl
                          : t(`subscriptions.deliveryMode.${deliveryMode}`)
                      }
                    >
                      <Chip
                        size="small"
                        variant="outlined"
                        label={t(`subscriptions.deliveryMode.${deliveryMode}`, deliveryMode)}
                      />
                    </Tooltip>
                  </TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      color={getSubscriptionStatusChipColor(statusStr)}
                      label={t(`subscriptions.status.${statusStr.toLowerCase()}`, statusStr)}
                    />
                  </TableCell>
                  <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                    <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                      <Tooltip title={t('subscriptions.actions.view')}>
                        <span>
                          <IconButton
                            size="small"
                            color="primary"
                            onClick={() => onViewDetails(sub)}
                            aria-label={t('subscriptions.actions.view')}
                          >
                            <Eye size={16} />
                          </IconButton>
                        </span>
                      </Tooltip>

                      {canWrite ? (
                        <Tooltip
                          title={
                            isDeleted
                              ? t('subscriptions.actions.alreadyDeleted')
                              : t('subscriptions.actions.delete')
                          }
                        >
                          <span>
                            <IconButton
                              size="small"
                              color="error"
                              disabled={isDeleted || isMutating}
                              onClick={() => onDelete(sub)}
                              aria-label={t('subscriptions.actions.delete')}
                            >
                              <Trash2 size={16} />
                            </IconButton>
                          </span>
                        </Tooltip>
                      ) : null}
                    </Stack>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </TableContainer>
      <CursorPaginationFooter
        rowsPerPage={rowsPerPage}
        rowsPerPageOptions={ROWS_PER_PAGE_OPTIONS}
        hasPreviousPage={hasPreviousPage}
        hasNextPage={hasNextPage}
        disabled={isLoading || isMutating}
        onRowsPerPageChange={onRowsPerPageChange}
        onPreviousPage={onPreviousPage}
        onNextPage={onNextPage}
      />
    </Paper>
  )
}

SubscriptionTable.defaultProps = {
  isMutating: false,
}
