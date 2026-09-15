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
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@wso2/oxygen-ui'
import { History } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import CopyableText from '../../../components/CopyableText'
import type { EventRecord } from '../../../types/event'
import type { SubscriptionDeliveryAttemptRecord } from '../../../types/subscription'
import { formatEpochTimestamp } from '../../../utils/dateTime'
import { useEventDeliveryHistoryQuery } from '../hooks/useEventQueries'
import { useRetrySubscriptionDeliveryMutation } from '../hooks/useSubscriptionQueries'
import { getSubscriptionStatusChipColor } from '../utils/subscriptionStatusChip'

interface EventDetailsModalProps {
  open: boolean
  event?: EventRecord
  onClose: () => void
}

export default function EventDetailsModal({
  open,
  event,
  onClose,
}: EventDetailsModalProps): React.JSX.Element | null {
  const { t } = useTranslation('common')
  const deliveryId = event?.deliveryId || event?.eventId
  const retryMutation = useRetrySubscriptionDeliveryMutation(
    event?.subscriptionId,
    event?.deliveryId,
  )

  const {
    data: historyData,
    isLoading: isHistoryLoading,
    isError: isHistoryError,
  } = useEventDeliveryHistoryQuery(open ? deliveryId : undefined)

  if (!event) return null

  const displayDeliveryId = event.deliveryId || event.eventId
  const displayStatus = historyData?.currentStatus || event.currentStatus || 'PENDING'
  const displayMode = (event.deliveryMode || historyData?.deliveryMode || 'webhook').toUpperCase()

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
      PaperProps={{
        sx: (theme) => ({
          borderRadius: 1,
          ...theme.applyStyles('light', { bgcolor: theme.palette.grey[50] }),
          ...theme.applyStyles('dark', { bgcolor: 'rgba(255, 255, 255, 0.06)' }),
        }),
      }}
    >
      <DialogTitle
        sx={{
          p: 3,
          borderBottom: 1,
          borderColor: 'divider',
        }}
      >
        <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={2}>
          <Stack direction="row" alignItems="center" spacing={1.5}>
            <History size={22} />
            <Box>
              <Typography variant="h6" fontWeight={700}>
                {t('events.details.historyTitle')}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {t('events.details.historySubtitle')}
              </Typography>
            </Box>
          </Stack>
          <Stack direction="row" spacing={1} alignItems="center">
            <Chip size="small" variant="outlined" label={displayMode} />
            <Chip
              size="small"
              label={t(`events.status.${displayStatus.toLowerCase()}`, displayStatus)}
              color={getSubscriptionStatusChipColor(displayStatus)}
            />
          </Stack>
        </Stack>
      </DialogTitle>

      <DialogContent sx={{ p: 3 }}>
        <Stack spacing={2.5} sx={{ mt: 1 }}>
          {/* Delivery Context Banner */}
          <Box
            sx={{
              p: 2,
              borderRadius: 1,
              border: 1,
              borderColor: 'divider',
              bgcolor: 'background.paper',
            }}
          >
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              justifyContent="space-between"
              alignItems={{ xs: 'flex-start', sm: 'center' }}
              spacing={1.5}
            >
              <Stack direction="row" spacing={1} alignItems="center">
                <Typography variant="body2" color="text.secondary">
                  {t('events.table.deliveryId')}:
                </Typography>
                <CopyableText value={displayDeliveryId} monospace />
              </Stack>
              {event.subscriptionId ? (
                <Stack direction="row" spacing={1} alignItems="center">
                  <Typography variant="body2" color="text.secondary">
                    {t('subscriptions.table.subscriptionId', 'Subscription ID')}:
                  </Typography>
                  <CopyableText value={event.subscriptionId} monospace />
                </Stack>
              ) : null}
            </Stack>
          </Box>

          {/* Delivery Attempt History */}
          {isHistoryLoading ? (
            <Box sx={{ display: 'grid', placeItems: 'center', py: 6 }}>
              <CircularProgress size={32} />
            </Box>
          ) : isHistoryError ? (
            <Alert severity="warning">{t('events.details.noHistory')}</Alert>
          ) : (
            <Stack spacing={2}>
              {historyData?.completionStatus ? (
                <Box
                  sx={{
                    p: 2,
                    borderRadius: 1,
                    border: 1,
                    borderColor: 'divider',
                    bgcolor: 'background.paper',
                  }}
                >
                  <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 0.5 }}>
                    {t('events.details.completionTitle')}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    <strong>{historyData.completionStatus}</strong>
                    {historyData.completionEvidence ? ` — ${historyData.completionEvidence}` : ''}
                  </Typography>
                </Box>
              ) : null}

              {retryMutation.isError ? (
                <Alert severity="error">{t('subscriptions.deliveryHistory.loadFailed')}</Alert>
              ) : null}

              <Paper sx={{ width: '100%', overflow: 'hidden' }}>
                <TableContainer>
                  <Table size="small">
                    <TableHead
                      sx={(theme) => ({
                        '& .MuiTableCell-head': {
                          fontWeight: 600,
                          ...theme.applyStyles('light', {
                            backgroundColor: theme.palette.grey[100],
                          }),
                          ...theme.applyStyles('dark', {
                            backgroundColor: 'rgba(255, 255, 255, 0.08)',
                          }),
                        },
                      })}
                    >
                      <TableRow>
                        <TableCell>{t('subscriptions.deliveryHistory.attemptNumber')}</TableCell>
                        <TableCell>{t('subscriptions.deliveryHistory.attemptStatus')}</TableCell>
                        <TableCell>{t('subscriptions.deliveryHistory.httpStatus')}</TableCell>
                        <TableCell>{t('subscriptions.deliveryHistory.timestamp')}</TableCell>
                        <TableCell>{t('subscriptions.deliveryHistory.errorDetails')}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {(historyData?.history ?? []).length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={5} align="center" sx={{ py: 3 }}>
                            <Typography color="text.secondary">
                              {t('events.details.noHistory')}
                            </Typography>
                          </TableCell>
                        </TableRow>
                      ) : (
                        historyData?.history?.map((attempt: SubscriptionDeliveryAttemptRecord) => (
                          <TableRow key={attempt.attempt} hover>
                            <TableCell>#{attempt.attempt}</TableCell>
                            <TableCell>
                              <Chip
                                size="small"
                                color={getSubscriptionStatusChipColor(attempt.status)}
                                label={attempt.status}
                              />
                            </TableCell>
                            <TableCell>{attempt.httpStatus ?? '-'}</TableCell>
                            <TableCell>{formatEpochTimestamp(attempt.timestamp)}</TableCell>
                            <TableCell>
                              <Typography variant="body2" color="text.secondary">
                                {attempt.error || '-'}
                              </Typography>
                            </TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Paper>
            </Stack>
          )}
        </Stack>
      </DialogContent>

      <DialogActions
        sx={{
          p: 2.5,
          borderTop: 1,
          borderColor: 'divider',
          bgcolor: 'background.default',
        }}
      >
        {historyData?.manualRetryAvailable && event.subscriptionId && event.deliveryId ? (
          <Button
            variant="contained"
            disabled={retryMutation.isPending}
            onClick={() => retryMutation.mutate()}
            startIcon={retryMutation.isPending ? <CircularProgress size={16} /> : undefined}
          >
            {t('catalog.actions.retry')}
          </Button>
        ) : null}
        <Button variant="outlined" onClick={onClose}>
          {t('events.actions.close')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
