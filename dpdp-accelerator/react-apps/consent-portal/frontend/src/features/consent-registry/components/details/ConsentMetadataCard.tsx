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
  Typography,
} from '@wso2/oxygen-ui'
import {
  CalendarClock,
  CalendarPlus,
  Fingerprint,
  Globe,
  Server,
  UserRound,
} from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import CopyableText from '../../../../components/CopyableText'
import type { ConsentDetail } from '../../../../types/consent'
import { formatEpochTimestamp } from '../../../../utils/dateTime'
import { getConsentStateChipColor, getConsentStateLabelKey } from '../../utils/statusChip'

interface ConsentMetadataCardProps {
  consentId: string
  detail: ConsentDetail
}

interface MetadataFieldProps {
  icon: React.ReactNode
  label: string
  value: React.ReactNode
}

function MetadataField({ icon, label, value }: MetadataFieldProps): React.JSX.Element {
  return (
    <Box>
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
      </Typography>
      <Typography component="div" variant="body2" fontWeight={500}>
        {value}
      </Typography>
    </Box>
  )
}

function ConsentMetadataCard({ consentId, detail }: ConsentMetadataCardProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const hasExpiryTime = detail.expiryTime != null && detail.expiryTime !== 0

  return (
    <Card sx={{ boxShadow: 1 }}>
      <CardHeader
        title={
          <Stack direction="row" spacing={1} alignItems="center">
            <Fingerprint size={15} />
            <Typography variant="body2" fontWeight={400}>
              {t('consentRegistry.details.consentId')}:
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
          <Chip
            label={t(`consentRegistry.status.${getConsentStateLabelKey(detail.state)}`)}
            color={getConsentStateChipColor(detail.state)}
            size="small"
            variant="outlined"
          />
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
          <MetadataField
            icon={<UserRound size={14} />}
            label={t('consentRegistry.details.subject')}
            value={detail.subjectId}
          />
          <MetadataField
            icon={<Server size={14} />}
            label={t('consentRegistry.details.service')}
            value={detail.serviceId}
          />
          <MetadataField
            icon={<CalendarPlus size={14} />}
            label={t('consentRegistry.details.created')}
            value={formatEpochTimestamp(detail.timestamp)}
          />
          <MetadataField
            icon={<Globe size={14} />}
            label={t('consentRegistry.details.language')}
            value={detail.language ?? '-'}
          />
          <MetadataField
            icon={<CalendarClock size={14} />}
            label={t('consentRegistry.details.validUntil')}
            value={
              <Box
                component="span"
                sx={{ color: hasExpiryTime ? 'text.primary' : 'text.disabled' }}
              >
                {hasExpiryTime
                  ? formatEpochTimestamp(detail.expiryTime)
                  : t('consentRegistry.table.notApplicable')}
              </Box>
            }
          />
        </Box>
      </CardContent>
    </Card>
  )
}

export default ConsentMetadataCard
