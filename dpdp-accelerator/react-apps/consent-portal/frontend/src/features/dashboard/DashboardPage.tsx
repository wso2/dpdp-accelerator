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
  LinearProgress,
  Skeleton,
  Stack,
  Typography,
} from '@wso2/oxygen-ui'
import { AlarmClock, ArrowRight, ChartPie, Clock3, ShieldCheck } from '@wso2/oxygen-ui-icons-react'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import type { ConsentDetailAPI } from '../../types/consent'
import { formatEpochTimestamp, toEpochMilliseconds } from '../../utils/dateTime'
import { normalizeConsentStatus } from '../consent-registry/utils/statusChip'
import useDashboardConsentsQuery from './hooks/useDashboardConsentsQuery'

const EXPIRING_SOON_DAYS = 30
const DAY_IN_MILLISECONDS = 24 * 60 * 60 * 1000
const ATTENTION_ITEM_LIMIT = 5
const PURPOSE_ITEM_LIMIT = 5

interface PurposeFrequency {
  id: string
  label: string
  count: number
}

interface DashboardData {
  activeCount: number
  pendingCount: number
  pending: ConsentDetailAPI[]
  expiring: ConsentDetailAPI[]
  purposes: PurposeFrequency[]
  types: PurposeFrequency[]
}

function consentUpdatedTime(consent: ConsentDetailAPI): number {
  return toEpochMilliseconds(consent.updatedTime) ?? 0
}

function consentExpirationTime(consent: ConsentDetailAPI): number {
  return toEpochMilliseconds(consent.expirationTime) ?? 0
}

function summarizePurposes(consent: ConsentDetailAPI): string {
  const labels = consent.purposes.map((purpose) => purpose.displayName ?? purpose.name)

  if (labels.length === 0) return '-'
  if (labels.length === 1) return labels[0]
  return `${labels[0]} +${String(labels.length - 1)}`
}

function AttentionRow({ consent, dateLabel }: { consent: ConsentDetailAPI; dateLabel: string }) {
  const navigate = useNavigate()
  const consentPath = `/consents/${encodeURIComponent(consent.id)}`

  return (
    <Box
      role="link"
      tabIndex={0}
      onClick={() => navigate(consentPath)}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          navigate(consentPath)
        }
      }}
      sx={{
        py: 1.25,
        px: 1,
        mx: -1,
        borderRadius: 1,
        cursor: 'pointer',
        '&:hover': { bgcolor: 'action.hover' },
      }}
    >
      <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={2}>
        <Stack spacing={0.25} minWidth={0}>
          <Typography variant="body2" fontWeight={600} noWrap>
            {consent.type}
          </Typography>
          <Typography variant="caption" color="text.secondary" noWrap>
            {summarizePurposes(consent)}
          </Typography>
        </Stack>
        <Stack direction="row" spacing={1} alignItems="center" flexShrink={0}>
          <Typography variant="caption" color="text.secondary">
            {dateLabel}
          </Typography>
          <ArrowRight size={16} />
        </Stack>
      </Stack>
    </Box>
  )
}

function DashboardPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const consentsQuery = useDashboardConsentsQuery()

  const data = useMemo<DashboardData>(() => {
    const consents = consentsQuery.data ?? []
    const active = consents.filter((consent) => normalizeConsentStatus(consent.status) === 'ACTIVE')
    const pending = consents.filter(
      (consent) => normalizeConsentStatus(consent.status) === 'CREATED',
    )
    const now = consentsQuery.dataUpdatedAt
    const expirationCutoff = now + EXPIRING_SOON_DAYS * DAY_IN_MILLISECONDS
    const expiring = active
      .filter((consent) => {
        const expiration = consentExpirationTime(consent)
        return expiration > now && expiration <= expirationCutoff
      })
      .sort((left, right) => consentExpirationTime(left) - consentExpirationTime(right))

    const purposeCounts = new Map<string, PurposeFrequency>()
    active.forEach((consent) => {
      consent.purposes.forEach((purpose) => {
        const existing = purposeCounts.get(purpose.purposeId)
        purposeCounts.set(purpose.purposeId, {
          id: purpose.purposeId,
          label: purpose.displayName ?? purpose.name,
          count: (existing?.count ?? 0) + 1,
        })
      })
    })

    const typeCounts = new Map<string, PurposeFrequency>()
    consents.forEach((consent) => {
      const existing = typeCounts.get(consent.type)
      typeCounts.set(consent.type, {
        id: consent.type,
        label: consent.type,
        count: (existing?.count ?? 0) + 1,
      })
    })

    return {
      activeCount: active.length,
      pendingCount: pending.length,
      pending: [...pending]
        .sort((left, right) => consentUpdatedTime(right) - consentUpdatedTime(left))
        .slice(0, ATTENTION_ITEM_LIMIT),
      expiring: expiring.slice(0, ATTENTION_ITEM_LIMIT),
      purposes: [...purposeCounts.values()]
        .sort((left, right) => right.count - left.count || left.label.localeCompare(right.label))
        .slice(0, PURPOSE_ITEM_LIMIT),
      types: [...typeCounts.values()].sort(
        (left, right) => right.count - left.count || left.label.localeCompare(right.label),
      ),
    }
  }, [consentsQuery.data, consentsQuery.dataUpdatedAt])

  const maximumPurposeCount = data.purposes[0]?.count ?? 1
  const maximumTypeCount = data.types[0]?.count ?? 1

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack spacing={0.75}>
          <HeaderBreadcrumbs />
          <Typography variant="h4" fontWeight={700}>
            {t('dashboard.title')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('dashboard.subtitle')}
          </Typography>
        </Stack>

        {consentsQuery.isError ? <Alert severity="error">{t('dashboard.loadFailed')}</Alert> : null}

        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' },
            gap: 2,
          }}
        >
          <Card sx={{ boxShadow: 1 }}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Stack spacing={0.5}>
                  <Typography variant="body2" color="text.secondary" fontWeight={600}>
                    {t('dashboard.active')}
                  </Typography>
                  {consentsQuery.isLoading ? (
                    <Skeleton width={64} height={48} />
                  ) : (
                    <Typography variant="h3" fontWeight={700}>
                      {data.activeCount}
                    </Typography>
                  )}
                </Stack>
                <Box
                  sx={{ display: 'inline-flex', p: 1.25, borderRadius: 2, bgcolor: 'action.hover' }}
                >
                  <ShieldCheck size={25} />
                </Box>
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ boxShadow: 1 }}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Stack spacing={0.5}>
                  <Typography variant="body2" color="text.secondary" fontWeight={600}>
                    {t('dashboard.pending')}
                  </Typography>
                  {consentsQuery.isLoading ? (
                    <Skeleton width={64} height={48} />
                  ) : (
                    <Typography variant="h3" fontWeight={700}>
                      {data.pendingCount}
                    </Typography>
                  )}
                </Stack>
                <Box
                  sx={{ display: 'inline-flex', p: 1.25, borderRadius: 2, bgcolor: 'action.hover' }}
                >
                  <Clock3 size={25} />
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Box>

        <Typography variant="h6" fontWeight={700}>
          {t('dashboard.attention')}
        </Typography>

        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', lg: 'repeat(2, minmax(0, 1fr))' },
            gap: 2,
          }}
        >
          <Card sx={{ boxShadow: 1 }}>
            <CardHeader
              avatar={<Clock3 size={20} />}
              title={<Typography fontWeight={600}>{t('dashboard.pendingConsents')}</Typography>}
              action={<Chip size="small" label={data.pendingCount} />}
            />
            <Divider />
            <CardContent>
              {consentsQuery.isLoading ? (
                <Stack spacing={1}>
                  <Skeleton height={40} />
                  <Skeleton height={40} />
                  <Skeleton height={40} />
                </Stack>
              ) : null}
              {!consentsQuery.isLoading && data.pending.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  {t('dashboard.noPending')}
                </Typography>
              ) : null}
              {data.pending.map((consent) => (
                <AttentionRow
                  key={consent.id}
                  consent={consent}
                  dateLabel={formatEpochTimestamp(consent.updatedTime)}
                />
              ))}
              {data.pendingCount > 0 ? (
                <Button
                  component={RouterLink}
                  to="/consents?status=Pending"
                  size="small"
                  endIcon={<ArrowRight size={15} />}
                  sx={{ mt: 1 }}
                >
                  {t('dashboard.viewPending')}
                </Button>
              ) : null}
            </CardContent>
          </Card>

          <Card sx={{ boxShadow: 1 }}>
            <CardHeader
              avatar={<AlarmClock size={20} />}
              title={<Typography fontWeight={600}>{t('dashboard.expiringSoon')}</Typography>}
              action={<Chip size="small" label={data.expiring.length} />}
            />
            <Divider />
            <CardContent>
              {consentsQuery.isLoading ? (
                <Stack spacing={1}>
                  <Skeleton height={40} />
                  <Skeleton height={40} />
                  <Skeleton height={40} />
                </Stack>
              ) : null}
              {!consentsQuery.isLoading && data.expiring.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  {t('dashboard.noExpiring', { days: EXPIRING_SOON_DAYS })}
                </Typography>
              ) : null}
              {data.expiring.map((consent) => (
                <AttentionRow
                  key={consent.id}
                  consent={consent}
                  dateLabel={formatEpochTimestamp(consent.expirationTime)}
                />
              ))}
            </CardContent>
          </Card>
        </Box>

        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', lg: 'repeat(2, minmax(0, 1fr))' },
            gap: 2,
          }}
        >
          <Card sx={{ boxShadow: 1 }}>
            <CardHeader
              title={<Typography fontWeight={600}>{t('dashboard.commonPurposes')}</Typography>}
              subheader={t('dashboard.commonPurposesSubtitle')}
            />
            <Divider />
            <CardContent>
              {consentsQuery.isLoading ? (
                <Stack spacing={2}>
                  <Skeleton height={32} />
                  <Skeleton height={32} />
                  <Skeleton height={32} />
                </Stack>
              ) : null}
              {!consentsQuery.isLoading && data.purposes.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  {t('dashboard.noPurposes')}
                </Typography>
              ) : null}
              <Stack spacing={2}>
                {data.purposes.map((purpose, index) => (
                  <Stack key={purpose.id} spacing={0.75}>
                    <Stack direction="row" justifyContent="space-between" spacing={2}>
                      <Typography variant="body2" fontWeight={600} noWrap>
                        {String(index + 1)}. {purpose.label}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {t('dashboard.consentCount', { count: purpose.count })}
                      </Typography>
                    </Stack>
                    <LinearProgress
                      variant="determinate"
                      value={(purpose.count / maximumPurposeCount) * 100}
                      sx={{ height: 6, borderRadius: 3 }}
                    />
                  </Stack>
                ))}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ boxShadow: 1 }}>
            <CardHeader
              avatar={<ChartPie size={20} />}
              title={<Typography fontWeight={600}>{t('dashboard.typeBreakdown')}</Typography>}
              subheader={t('dashboard.typeBreakdownSubtitle')}
            />
            <Divider />
            <CardContent>
              {consentsQuery.isLoading ? (
                <Stack spacing={2}>
                  <Skeleton height={32} />
                  <Skeleton height={32} />
                  <Skeleton height={32} />
                </Stack>
              ) : null}
              {!consentsQuery.isLoading && data.types.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  {t('dashboard.noTypes')}
                </Typography>
              ) : null}
              <Stack spacing={2}>
                {data.types.map((type) => (
                  <Stack key={type.id} spacing={0.75}>
                    <Stack direction="row" justifyContent="space-between" spacing={2}>
                      <Typography variant="body2" fontWeight={600} noWrap>
                        {type.label}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {t('dashboard.consentCount', { count: type.count })}
                      </Typography>
                    </Stack>
                    <LinearProgress
                      variant="determinate"
                      value={(type.count / maximumTypeCount) * 100}
                      sx={{ height: 6, borderRadius: 3 }}
                    />
                  </Stack>
                ))}
              </Stack>
            </CardContent>
          </Card>
        </Box>
      </Stack>
    </Box>
  )
}

export default DashboardPage
