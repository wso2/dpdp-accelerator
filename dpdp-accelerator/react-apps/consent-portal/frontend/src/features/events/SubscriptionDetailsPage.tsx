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
  Skeleton,
  Snackbar,
  Stack,
  Tooltip,
  Typography,
} from '@wso2/oxygen-ui'
import {
  ArrowLeft,
  Check,
  Clock3,
  Copy,
  Eye,
  EyeOff,
  Globe,
  Lock,
  RefreshCw,
  Tag,
  Trash2,
} from '@wso2/oxygen-ui-icons-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import SubscriptionTopicsSection from './components/SubscriptionTopicsSection'
import SubscriptionPurposesSection from './components/SubscriptionPurposesSection'
import CopyableText from '../../components/CopyableText'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import { formatEpochTimestamp } from '../../utils/dateTime'
import { REQUIRED_SCOPES } from '../../utils/scopes'
import useAuthorization from '../auth/useAuthorization'
import DetailGrid from '../catalog/components/DetailGrid'
import SubscriptionDeleteDialog from './components/SubscriptionDeleteDialog'
import SubscriptionDeliveryEventsTable from './components/SubscriptionDeliveryEventsTable'
import {
  useDeleteSubscriptionMutation,
  useSubscriptionDetailQuery,
  useVerifySubscriptionMutation,
} from './hooks/useSubscriptionQueries'
import { getSubscriptionStatusChipColor } from './utils/subscriptionStatusChip'

interface SecretFieldProps {
  secret: string
}

function SecretField({ secret }: SecretFieldProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [isVisible, setIsVisible] = useState(false)
  const [copied, setCopied] = useState(false)
  const resetTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  useEffect(
    () => () => {
      if (resetTimer.current) clearTimeout(resetTimer.current)
    },
    [],
  )

  if (!secret || secret === '-') {
    return <Typography variant="body2">-</Typography>
  }

  const handleCopy = (e: React.MouseEvent) => {
    e.stopPropagation()
    navigator.clipboard?.writeText(secret).then(() => {
      setCopied(true)
      if (resetTimer.current) clearTimeout(resetTimer.current)
      resetTimer.current = setTimeout(() => setCopied(false), 2000)
    })
  }

  const maskedSecret = '•'.repeat(Math.min(secret.length, 24))
  const toggleLabel = isVisible
    ? t('subscriptions.details.hideSecret', 'Hide secret')
    : t('subscriptions.details.showSecret', 'Show secret')

  return (
    <Box
      component="span"
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.5,
        maxWidth: '100%',
        flexWrap: 'wrap',
      }}
    >
      <Typography
        component="span"
        variant="body2"
        sx={{
          fontFamily: 'monospace',
          letterSpacing: isVisible ? undefined : 1.5,
          overflowWrap: 'anywhere',
          wordBreak: 'break-all',
        }}
      >
        {isVisible ? secret : maskedSecret}
      </Typography>
      <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', flexShrink: 0 }}>
        <Tooltip title={toggleLabel}>
          <IconButton
            size="small"
            onClick={() => setIsVisible(!isVisible)}
            aria-label={toggleLabel}
          >
            {isVisible ? <EyeOff size={16} /> : <Eye size={16} />}
          </IconButton>
        </Tooltip>
        <Tooltip title={copied ? t('copyableText.copied') : t('copyableText.copy')}>
          <IconButton size="small" onClick={handleCopy} aria-label={t('copyableText.copy')}>
            {copied ? <Check size={14} /> : <Copy size={14} />}
          </IconButton>
        </Tooltip>
      </Box>
    </Box>
  )
}

export default function SubscriptionDetailsPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const detailQuery = useSubscriptionDetailQuery(id)
  const deleteMutation = useDeleteSubscriptionMutation()
  const verifyMutation = useVerifySubscriptionMutation()

  const [isDeleteOpen, setIsDeleteOpen] = useState(false)
  const [snackbarMessage, setSnackbarMessage] = useState<string | null>(null)

  const { hasScope } = useAuthorization()
  const canWrite = hasScope(REQUIRED_SCOPES.EVENT_SUBSCRIPTIONS_WRITE)

  const sub = detailQuery.data

  if (detailQuery.isLoading) {
    return (
      <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
        <Stack spacing={3}>
          <HeaderBreadcrumbs />
          <Skeleton width={300} height={48} />
          <Skeleton variant="rounded" height={220} />
          <Skeleton variant="rounded" height={320} />
        </Stack>
      </Box>
    )
  }

  if (detailQuery.isError || !sub) {
    return (
      <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
        <Stack spacing={3}>
          <HeaderBreadcrumbs />
          <Alert severity="error">{t('subscriptions.details.loadFailed')}</Alert>
          <Button
            variant="outlined"
            startIcon={<ArrowLeft size={16} />}
            onClick={() => navigate('/events/subscriptions')}
            sx={{ alignSelf: 'flex-start' }}
          >
            {t('subscriptions.actions.backToList')}
          </Button>
        </Stack>
      </Box>
    )
  }

  const statusStr = (sub.status || 'ACTIVE').toUpperCase()
  const isDeleted = statusStr === 'DELETED'
  const deliveryMode = (sub.delivery?.mode || 'webhook').toLowerCase()
  const isWebhook = deliveryMode === 'webhook'
  const filterType = sub.filter?.type || 'all'

  const handleVerify = (): void => {
    if (!sub) return
    verifyMutation.mutate(sub.subscriptionId, {
      onSuccess: () => {
        setSnackbarMessage(
          t('subscriptions.verification.success', 'Verification triggered successfully.'),
        )
      },
      onError: (err) => {
        setSnackbarMessage(
          err.message || t('subscriptions.verification.failed', 'Verification failed.'),
        )
      },
    })
  }

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack spacing={1}>
          <HeaderBreadcrumbs currentLabel={sub.subscriptionId} />
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            justifyContent="space-between"
            alignItems={{ xs: 'flex-start', sm: 'center' }}
            spacing={2}
          >
            <Stack direction="row" spacing={1.5} alignItems="center">
              <Button
                variant="outlined"
                size="small"
                startIcon={<ArrowLeft size={16} />}
                onClick={() => navigate('/events/subscriptions')}
              >
                {t('subscriptions.actions.backToList')}
              </Button>
              <Typography variant="h4" fontWeight={700}>
                {t('subscriptions.topicUi.detailsTitle')}
              </Typography>
              <Chip
                size="small"
                color={getSubscriptionStatusChipColor(statusStr)}
                label={t(`subscriptions.status.${statusStr.toLowerCase()}`, statusStr)}
              />
            </Stack>

            <Stack direction="row" spacing={1}>
              {canWrite && isWebhook && statusStr === 'STALE' ? (
                <Button
                  variant="outlined"
                  color="secondary"
                  startIcon={<RefreshCw size={16} />}
                  disabled={verifyMutation.isPending}
                  onClick={handleVerify}
                >
                  {t('subscriptions.actions.verify')}
                </Button>
              ) : null}

              {canWrite ? (
                <Button
                  variant="outlined"
                  color="error"
                  startIcon={<Trash2 size={16} />}
                  disabled={isDeleted || deleteMutation.isPending}
                  onClick={() => setIsDeleteOpen(true)}
                >
                  {t('subscriptions.actions.delete')}
                </Button>
              ) : null}
            </Stack>
          </Stack>
        </Stack>

        <Card sx={{ border: 1, borderColor: 'divider', boxShadow: 1 }}>
          <CardHeader
            title={
              <Typography variant="h6" fontWeight={700}>
                {t('subscriptions.details.configTitle')}
              </Typography>
            }
          />
          <Divider />
          <CardContent sx={{ p: 3 }}>
            <DetailGrid
              fields={[
                {
                  icon: <Tag size={16} />,
                  label: t('subscriptions.details.name'),
                  value: sub.name || '-',
                },
                {
                  icon: <Tag size={16} />,
                  label: t('subscriptions.table.subscriptionId'),
                  value: <CopyableText value={sub.subscriptionId} monospace />,
                },
                {
                  icon: <Globe size={16} />,
                  label: t('subscriptions.table.deliveryMode'),
                  value: (
                    <Chip
                      size="small"
                      variant="outlined"
                      label={t(`subscriptions.deliveryMode.${deliveryMode}`, deliveryMode)}
                    />
                  ),
                },
                {
                  icon: <Globe size={16} />,
                  label: t('subscriptions.dialog.callbackUrlLabel'),
                  value: sub.delivery?.callbackUrl || '-',
                },
                {
                  icon: <Lock size={16} />,
                  label: t('subscriptions.details.sharedSecret', 'Shared Secret'),
                  value: <SecretField secret={sub.delivery?.sharedSecret || '-'} />,
                },
                {
                  icon: <Clock3 size={16} />,
                  label: t('subscriptions.details.createdAt'),
                  value: formatEpochTimestamp(sub.createdAt),
                },
                {
                  icon: <Clock3 size={16} />,
                  label: t('subscriptions.details.updatedAt'),
                  value: formatEpochTimestamp(sub.updatedAt),
                },
              ]}
            />
          </CardContent>
        </Card>

        <SubscriptionTopicsSection topics={sub.topics ?? (sub.topic ? [sub.topic] : [])} />
        <SubscriptionPurposesSection
          purposes={sub.filter?.purposes ?? []}
          filterType={filterType}
        />
        <Card sx={{ border: 1, borderColor: 'divider', boxShadow: 1 }}>
          <CardHeader
            title={
              <Typography variant="h6" fontWeight={700}>
                {t('subscriptions.details.eventsTitle')}
              </Typography>
            }
          />
          <Divider />
          <CardContent sx={{ p: 3 }}>
            <SubscriptionDeliveryEventsTable subscriptionId={sub.subscriptionId} />
          </CardContent>
        </Card>

        {isDeleteOpen ? (
          <SubscriptionDeleteDialog
            open
            subscription={sub}
            loading={deleteMutation.isPending}
            error={deleteMutation.error?.message}
            onClose={() => {
              setIsDeleteOpen(false)
              deleteMutation.reset()
            }}
            onConfirm={() => {
              deleteMutation.mutate(sub.subscriptionId, {
                onSuccess: () => {
                  setIsDeleteOpen(false)
                  navigate('/events/subscriptions')
                },
              })
            }}
          />
        ) : null}

        <Snackbar
          open={Boolean(snackbarMessage)}
          autoHideDuration={4000}
          onClose={() => setSnackbarMessage(null)}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        >
          <Alert
            onClose={() => setSnackbarMessage(null)}
            severity={verifyMutation.isError ? 'error' : 'success'}
            sx={{ width: '100%' }}
          >
            {snackbarMessage}
          </Alert>
        </Snackbar>
      </Stack>
    </Box>
  )
}
