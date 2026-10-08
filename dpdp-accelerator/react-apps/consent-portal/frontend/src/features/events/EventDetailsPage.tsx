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
  Card,
  CardContent,
  CardHeader,
  Chip,
  Divider,
  IconButton,
  Paper,
  Skeleton,
  Snackbar,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Tooltip,
  Typography,
} from '@wso2/oxygen-ui'
import { Clock3, Code2, Copy, Eye, Layers, Tag, Users } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router-dom'
import CopyableText from '../../components/CopyableText'
import DetailBackButton from '../../components/layout/main-layout/DetailBackButton'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import type { EventRecord } from '../../types/event'
import { formatEpochTimestamp } from '../../utils/dateTime'
import DetailGrid from '../catalog/components/DetailGrid'
import EventDetailsModal from './components/EventDetailsModal'
import { useEventDeliveriesQuery, useEventDetailQuery } from './hooks/useEventQueries'
import { getSubscriptionStatusChipColor } from './utils/subscriptionStatusChip'

function formatJsonPayload(payload?: string): string {
  if (!payload) return '{}'
  try {
    const parsed = typeof payload === 'string' ? JSON.parse(payload) : payload
    return JSON.stringify(parsed, null, 2)
  } catch {
    return payload
  }
}

export default function EventDetailsPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const { id } = useParams<{ id: string }>()

  const [page, setPage] = useState(0)
  const [rowsPerPage, setRowsPerPage] = useState(10)

  const eventQuery = useEventDetailQuery(id)
  const deliveriesQuery = useEventDeliveriesQuery(id, page, rowsPerPage)

  const [selectedDelivery, setSelectedDelivery] = useState<EventRecord | undefined>()
  const [snackbarMessage, setSnackbarMessage] = useState<string | null>(null)
  const [snackbarSeverity, setSnackbarSeverity] = useState<'success' | 'error'>('success')

  const event = eventQuery.data
  const deliveries = deliveriesQuery.data?.rows ?? []
  const totalDeliveriesCount =
    deliveriesQuery.data?.total ?? event?.deliveriesCount ?? deliveries.length

  const handleCopyPayload = async (rawPayload?: string) => {
    const formatted = formatJsonPayload(rawPayload)
    try {
      await navigator.clipboard.writeText(formatted)
      setSnackbarSeverity('success')
      setSnackbarMessage(t('events.details.copyPayloadSuccess'))
    } catch {
      setSnackbarSeverity('error')
      setSnackbarMessage(
        t('events.details.copyPayloadFailed', 'Failed to copy payload to clipboard.'),
      )
    }
  }

  if (eventQuery.isLoading) {
    return (
      <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
        <Stack spacing={3}>
          <Stack spacing={1}>
            <DetailBackButton to="/events" />
            <HeaderBreadcrumbs />
          </Stack>
          <Skeleton width={300} height={48} />
          <Skeleton variant="rounded" height={200} />
          <Skeleton variant="rounded" height={220} />
          <Skeleton variant="rounded" height={300} />
        </Stack>
      </Box>
    )
  }

  if (eventQuery.isError || !event) {
    return (
      <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
        <Stack spacing={3}>
          <Stack spacing={1}>
            <DetailBackButton to="/events" />
            <HeaderBreadcrumbs />
          </Stack>
          <Alert severity="error">{t('events.details.loadFailed')}</Alert>
        </Stack>
      </Box>
    )
  }

  const displayTopic = event.topic || event.topicId || '-'
  const purposes = event.purposes ?? []

  const metadataItems = [
    {
      icon: <Tag size={16} />,
      label: t('events.table.eventId'),
      value: <CopyableText value={event.eventId} monospace />,
    },
    {
      icon: <Layers size={16} />,
      label: t('events.table.topic'),
      value: (
        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          {displayTopic}
        </Typography>
      ),
    },
    {
      icon: <Users size={16} />,
      label: t('events.table.purposes'),
      value:
        purposes.length > 0 ? (
          <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
            {purposes.map((p) => (
              <Chip key={p} size="small" variant="outlined" label={p} />
            ))}
          </Box>
        ) : (
          <Chip size="small" variant="outlined" label={t('events.details.allPurposes')} />
        ),
    },
    {
      icon: <Clock3 size={16} />,
      label: t('events.table.occurredAt'),
      value: formatEpochTimestamp(event.occurredAt),
    },
    {
      icon: <Users size={16} />,
      label: t('events.table.deliveries'),
      value: (
        <Chip
          size="small"
          color={totalDeliveriesCount > 0 ? 'primary' : 'default'}
          variant="outlined"
          label={`${totalDeliveriesCount} ${totalDeliveriesCount === 1 ? t('events.details.subscriber') : t('events.details.subscribers')}`}
          sx={{ fontWeight: 600 }}
        />
      ),
    },
  ]

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack spacing={1}>
          <DetailBackButton to="/events" />
          <HeaderBreadcrumbs />
        </Stack>

        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          justifyContent="space-between"
          alignItems={{ xs: 'flex-start', sm: 'center' }}
          spacing={2}
        >
          <Stack spacing={0.5}>
            <Stack direction="row" spacing={1.5} alignItems="center">
              <Typography variant="h4" fontWeight={700}>
                {event.eventId}
              </Typography>
            </Stack>
            <Typography variant="body2" color="text.secondary">
              {displayTopic} • {formatEpochTimestamp(event.occurredAt)}
            </Typography>
          </Stack>
        </Stack>

        {/* Section 1: Summary Metadata */}
        <Card variant="outlined" sx={{ borderRadius: 2 }}>
          <CardHeader
            title={
              <Typography variant="h6" fontWeight={600}>
                {t('events.details.metadataTitle')}
              </Typography>
            }
            subheader={
              <Typography variant="body2" color="text.secondary">
                {t('events.details.metadataSubtitle')}
              </Typography>
            }
          />
          <Divider />
          <CardContent>
            <DetailGrid fields={metadataItems} />
          </CardContent>
        </Card>

        {/* Section 2: Event Payload */}
        <Card variant="outlined" sx={{ borderRadius: 2 }}>
          <CardHeader
            title={
              <Stack direction="row" spacing={1} alignItems="center">
                <Code2 size={18} />
                <Typography variant="h6" fontWeight={600}>
                  {t('events.details.payloadTitle')}
                </Typography>
              </Stack>
            }
            action={
              <Button
                size="small"
                variant="outlined"
                startIcon={<Copy size={14} />}
                onClick={() => handleCopyPayload(event.payload)}
              >
                {t('events.actions.copyPayload')}
              </Button>
            }
          />
          <Divider />
          <CardContent sx={{ p: 0, '&:last-child': { pb: 0 } }}>
            <Box
              component="pre"
              sx={(theme) => ({
                p: 2.5,
                m: 0,
                fontSize: '0.8125rem',
                fontFamily: 'monospace',
                overflowX: 'auto',
                maxHeight: 320,
                ...theme.applyStyles('light', {
                  bgcolor: theme.palette.grey[50],
                }),
                ...theme.applyStyles('dark', {
                  bgcolor: 'rgba(0, 0, 0, 0.3)',
                }),
              })}
            >
              {formatJsonPayload(event.payload)}
            </Box>
          </CardContent>
        </Card>

        {/* Section 3: Downstream Subscriber Deliveries */}
        <Card variant="outlined" sx={{ borderRadius: 2, overflow: 'hidden' }}>
          <CardHeader
            title={
              <Typography variant="h6" fontWeight={600}>
                {t('events.details.deliveriesTitle')}
              </Typography>
            }
            subheader={
              <Typography variant="body2" color="text.secondary">
                {t('events.details.deliveriesSubtitle')}
              </Typography>
            }
          />
          <Divider />
          {deliveries.length === 0 ? (
            <Box sx={{ p: 4, textAlign: 'center' }}>
              <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 0.5 }}>
                {t('events.details.noDeliveries')}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {t('events.details.noDeliveriesMessage')}
              </Typography>
            </Box>
          ) : (
            <TableContainer component={Paper} elevation={0}>
              <Table aria-label={t('events.details.deliveriesTitle')}>
                <TableHead
                  sx={(theme) => ({
                    '& .MuiTableCell-head': {
                      fontWeight: 600,
                      ...theme.applyStyles('light', { backgroundColor: theme.palette.grey[50] }),
                      ...theme.applyStyles('dark', {
                        backgroundColor: 'rgba(255, 255, 255, 0.04)',
                      }),
                    },
                  })}
                >
                  <TableRow>
                    <TableCell>{t('events.table.deliveryId')}</TableCell>
                    <TableCell>
                      {t('subscriptions.table.subscriptionId', 'Subscription ID')}
                    </TableCell>
                    <TableCell>{t('events.table.mode')}</TableCell>
                    <TableCell>{t('events.table.status')}</TableCell>
                    <TableCell>{t('events.table.occurredAt')}</TableCell>
                    <TableCell align="right">{t('events.table.actions')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {deliveries.map((del) => {
                    const status = del.currentStatus || 'PENDING'
                    const rawMode = del.deliveryMode || 'webhook'
                    const mode = t(`subscriptions.deliveryMode.${rawMode.toLowerCase()}`, rawMode)
                    return (
                      <TableRow
                        key={del.deliveryId || del.eventId}
                        hover
                        sx={{ cursor: 'pointer' }}
                        onClick={() => setSelectedDelivery(del)}
                      >
                        <TableCell>
                          <CopyableText
                            value={del.deliveryId || del.eventId}
                            truncateAt={14}
                            monospace
                          />
                        </TableCell>
                        <TableCell>
                          <CopyableText
                            value={del.subscriptionId || '-'}
                            truncateAt={14}
                            monospace
                          />
                        </TableCell>
                        <TableCell>
                          <Chip size="small" variant="outlined" label={mode} />
                        </TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={t(`events.status.${status.toLowerCase()}`, status)}
                            color={getSubscriptionStatusChipColor(status)}
                          />
                        </TableCell>
                        <TableCell>
                          {formatEpochTimestamp(del.occurredAt || event.occurredAt)}
                        </TableCell>
                        <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                          <Tooltip title={t('events.actions.view')}>
                            <span>
                              <IconButton
                                size="small"
                                color="primary"
                                onClick={() => setSelectedDelivery(del)}
                                aria-label={t('events.actions.view')}
                              >
                                <Eye size={16} />
                              </IconButton>
                            </span>
                          </Tooltip>
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
              <TablePagination
                component="div"
                count={totalDeliveriesCount}
                page={page}
                onPageChange={(_, newPage) => setPage(newPage)}
                rowsPerPage={rowsPerPage}
                onRowsPerPageChange={(event) => {
                  setRowsPerPage(parseInt(event.target.value, 10))
                  setPage(0)
                }}
                rowsPerPageOptions={[5, 10, 20, 50]}
              />
            </TableContainer>
          )}
        </Card>

        {selectedDelivery ? (
          <EventDetailsModal
            open
            event={selectedDelivery}
            onClose={() => setSelectedDelivery(undefined)}
          />
        ) : null}

        <Snackbar
          open={Boolean(snackbarMessage)}
          autoHideDuration={3000}
          onClose={() => setSnackbarMessage(null)}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        >
          <Alert
            onClose={() => setSnackbarMessage(null)}
            severity={snackbarSeverity}
            sx={{ width: '100%' }}
          >
            {snackbarMessage}
          </Alert>
        </Snackbar>
      </Stack>
    </Box>
  )
}
