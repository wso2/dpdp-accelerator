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
  Card,
  CardContent,
  CardHeader,
  Chip,
  Divider,
  Stack,
  Tooltip,
  Typography,
} from '@wso2/oxygen-ui'
import {
  CalendarClock,
  CalendarPlus,
  CircleHelp,
  Fingerprint,
  Gauge,
  History,
  RefreshCw,
  Repeat2,
  Users,
} from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import CopyableText from '../../../../components/CopyableText'
import type { ConsentDetailAPI } from '../../../../types/consent'
import { formatEpochTimestamp } from '../../../../utils/dateTime'
import { getConsentStatusChipColor, getConsentStatusLabelKey } from '../../utils/statusChip'

type DurationUnit = 'hour' | 'day' | 'year'

interface DurationDisplayParts {
  value: number
  unit: DurationUnit
}

interface ConsentMetadataCardProps {
  consentId: string
  detail: ConsentDetailAPI
}

interface MetadataLabelProps {
  help: string | undefined
  icon: React.ReactNode
  label: string
}

function MetadataLabel({ help, icon, label }: MetadataLabelProps): React.JSX.Element {
  return (
    <Typography
      variant="caption"
      color="text.secondary"
      fontWeight={700}
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.5,
        mb: 1,
        textTransform: 'uppercase',
        letterSpacing: 0.5,
      }}
    >
      {icon}
      {label}
      {help ? (
        <Tooltip title={help}>
          <Box
            component="span"
            sx={{ display: 'inline-flex', alignItems: 'center', color: 'text.disabled' }}
          >
            <CircleHelp size={14} />
          </Box>
        </Tooltip>
      ) : null}
    </Typography>
  )
}

function getDurationDisplayParts(
  durationInSeconds: number | undefined,
): DurationDisplayParts | null {
  if (!durationInSeconds || durationInSeconds <= 0) {
    return null
  }

  const totalHours = durationInSeconds / 3600

  if (totalHours < 24) {
    return { value: Math.max(1, Math.floor(totalHours)), unit: 'hour' }
  }

  const totalDays = totalHours / 24

  if (totalDays < 365) {
    return { value: Math.floor(totalDays), unit: 'day' }
  }

  return { value: Math.floor(totalDays / 365), unit: 'year' }
}

function getDurationUnitLabelKey(unit: DurationUnit, isSingular: boolean): string {
  if (unit === 'hour') {
    return isSingular
      ? 'consentRegistry.details.durationUnitHourSingular'
      : 'consentRegistry.details.durationUnitHourPlural'
  }

  if (unit === 'day') {
    return isSingular
      ? 'consentRegistry.details.durationUnitDaySingular'
      : 'consentRegistry.details.durationUnitDayPlural'
  }

  return isSingular
    ? 'consentRegistry.details.durationUnitYearSingular'
    : 'consentRegistry.details.durationUnitYearPlural'
}

function ConsentMetadataCard({ consentId, detail }: ConsentMetadataCardProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const hasFrequency = detail.frequency != null && detail.frequency !== 0
  const hasDuration =
    detail.dataAccessValidityDuration != null && detail.dataAccessValidityDuration !== 0
  const hasExpirationTime = detail.expirationTime != null && detail.expirationTime !== 0
  const durationDisplay = getDurationDisplayParts(detail.dataAccessValidityDuration)
  const durationUnitLabel = durationDisplay
    ? t(getDurationUnitLabelKey(durationDisplay.unit, durationDisplay.value === 1))
    : ''

  return (
    <Card sx={{ boxShadow: 1 }}>
      <CardHeader
        title={
          <Stack direction="row" spacing={1} alignItems="center">
            <Fingerprint size={15} />
            <Typography variant="body2" fontWeight={400}>
              {t('consentRegistry.details.consentId', 'Consent ID')}:
            </Typography>
            <CopyableText
              value={consentId}
              monospace
              textAriaLabel={t('copyableText.valueAriaLabel', {
                label: t('consentRegistry.details.consentId'),
                value: consentId,
              })}
              copyTooltip={t('copyableText.copyLabel', {
                label: t('consentRegistry.details.consentId'),
              })}
              copyAriaLabel={t('copyableText.copyValue', {
                label: t('consentRegistry.details.consentId'),
                value: consentId,
              })}
            />
          </Stack>
        }
        action={
          <Stack direction="row" spacing={1}>
            <Chip
              label={t(`consentRegistry.status.${getConsentStatusLabelKey(detail.status)}`)}
              color={getConsentStatusChipColor(detail.status)}
              size="small"
              variant="outlined"
            />
            <Chip label={detail.type} color="default" size="small" variant="outlined" />
          </Stack>
        }
        sx={{ pb: 1 }}
      />
      <Divider />
      <CardContent sx={{ pt: 3 }}>
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(3, 1fr)' },
            gap: { xs: 3, md: 4 },
          }}
        >
          <Box>
            <MetadataLabel
              help={undefined}
              icon={<CalendarPlus size={14} />}
              label={t('consentRegistry.details.created', 'Created')}
            />
            <Typography variant="body2" fontWeight={500}>
              {formatEpochTimestamp(detail.createdTime)}
            </Typography>
          </Box>
          <Box>
            <MetadataLabel
              help={undefined}
              icon={<RefreshCw size={14} />}
              label={t('consentRegistry.details.updated', 'Updated')}
            />
            <Typography variant="body2" fontWeight={500}>
              {formatEpochTimestamp(detail.updatedTime)}
            </Typography>
          </Box>
          <Box>
            <MetadataLabel
              help={undefined}
              icon={<CalendarClock size={14} />}
              label={t('consentRegistry.details.validUntil', 'Valid Until')}
            />
            <Typography variant="body2" fontWeight={500}>
              <Box
                component="span"
                sx={{ color: hasExpirationTime ? 'text.primary' : 'text.disabled' }}
              >
                {hasExpirationTime
                  ? formatEpochTimestamp(detail.expirationTime)
                  : t('consentRegistry.table.notApplicable', 'Not applicable')}
              </Box>
            </Typography>
          </Box>
          <Box>
            <MetadataLabel
              help={undefined}
              icon={<Users size={14} />}
              label={t('consentRegistry.details.groupId', 'Group ID')}
            />
            <Typography variant="body2" fontWeight={500}>
              {detail.groupId}
            </Typography>
          </Box>
          <Box>
            <MetadataLabel
              help={undefined}
              icon={<Repeat2 size={14} />}
              label={t('consentRegistry.details.recurring', 'Recurring')}
            />
            <Typography variant="body2" fontWeight={500}>
              {detail.recurringIndicator
                ? t('consentRegistry.details.values.yes', 'Yes')
                : t('consentRegistry.details.values.no', 'No')}
            </Typography>
          </Box>
          {hasFrequency ? (
            <Box>
              <MetadataLabel
                icon={<Gauge size={14} />}
                label={t('consentRegistry.details.frequency', 'Access Limit')}
                help={t(
                  'consentRegistry.details.frequencyHelp',
                  'This indicates how many times this consent can be accessed per day.',
                )}
              />
              <Typography variant="body2" fontWeight={500}>
                {detail.frequency}{' '}
                {detail.frequency === 1
                  ? t('consentRegistry.details.frequencyUnitSingular', 'time per day')
                  : t('consentRegistry.details.frequencyUnitPlural', 'times per day')}
              </Typography>
            </Box>
          ) : null}
          {hasDuration ? (
            <Box>
              <MetadataLabel
                icon={<History size={14} />}
                label={t('consentRegistry.details.duration', 'Lookback Period')}
                help={t(
                  'consentRegistry.details.durationHelp',
                  'This defines how far back data can be accessed. For example, if set to 6 months, data from up to 6 months ago is accessible.',
                )}
              />
              <Typography variant="body2" fontWeight={500}>
                {durationDisplay ? `${durationDisplay.value} ${durationUnitLabel}` : '-'}
              </Typography>
            </Box>
          ) : null}
        </Box>
      </CardContent>
    </Card>
  )
}

export default ConsentMetadataCard
