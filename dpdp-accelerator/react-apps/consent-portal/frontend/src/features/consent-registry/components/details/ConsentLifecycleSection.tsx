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
  CardHeader,
  Divider,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@wso2/oxygen-ui'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import type { ConsentStatusAuditItem } from '../../../../types/consent'
import { formatEpochTimestamp } from '../../../../utils/dateTime'
import { getConsentStatusLabelKey, normalizeConsentStatus } from '../../utils/statusChip'

interface ConsentLifecycleSectionProps {
  statusHistory: ConsentStatusAuditItem[]
}

const DATE_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
}

const TIME_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
}

function getStatusDotColor(status: string): string {
  switch (normalizeConsentStatus(status)) {
    case 'ACTIVE':
      return 'success.main'
    case 'CREATED':
      return 'warning.main'
    case 'REJECTED':
    case 'REVOKED':
      return 'error.main'
    case 'EXPIRED':
    default:
      return 'action.disabled'
  }
}

function ConsentLifecycleSection({
  statusHistory,
}: ConsentLifecycleSectionProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const events = useMemo(
    () => [...statusHistory].sort((first, second) => first.actionTime - second.actionTime),
    [statusHistory],
  )

  return (
    <Card sx={{ boxShadow: 1 }}>
      <CardHeader
        title={
          <Typography variant="h5" fontWeight={600}>
            {t('consentRegistry.details.section.lifecycle')}
          </Typography>
        }
        sx={{ pb: 1 }}
      />
      <Divider />
      <TableContainer>
        <Table
          aria-label={t('consentRegistry.details.lifecycle.tableAriaLabel')}
          sx={{
            tableLayout: 'fixed',
            '& tbody tr:hover': { bgcolor: 'action.hover' },
          }}
        >
          <TableHead>
            <TableRow sx={{ bgcolor: 'action.default' }}>
              <TableCell sx={{ width: '16%', fontWeight: 700 }}>
                {t('consentRegistry.details.table.eventType')}
              </TableCell>
              <TableCell sx={{ width: '15%', fontWeight: 700 }}>
                {t('consentRegistry.details.table.date')}
              </TableCell>
              <TableCell sx={{ width: '15%', fontWeight: 700 }}>
                {t('consentRegistry.details.table.time')}
              </TableCell>
              <TableCell sx={{ width: '56%', fontWeight: 700 }}>
                {t('consentRegistry.details.table.description')}
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {events.length === 0 ? (
              <TableRow>
                <TableCell colSpan={4}>
                  <Typography variant="body2" color="text.secondary" align="center">
                    {t('consentRegistry.details.lifecycle.empty')}
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              events.map((event) => {
                const normalizedStatus = normalizeConsentStatus(event.currentStatus)
                const statusLabel = t(
                  `consentRegistry.status.${getConsentStatusLabelKey(normalizedStatus)}`,
                  normalizedStatus,
                )

                return (
                  <TableRow key={event.statusAuditId}>
                    <TableCell>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Box
                          sx={{
                            width: 8,
                            height: 8,
                            borderRadius: '50%',
                            bgcolor: getStatusDotColor(normalizedStatus),
                            flexShrink: 0,
                          }}
                        />
                        <Typography variant="body2" fontWeight={600}>
                          {statusLabel}
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {formatEpochTimestamp(event.actionTime, DATE_FORMAT_OPTIONS)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {formatEpochTimestamp(event.actionTime, TIME_FORMAT_OPTIONS)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {event.reason ?? t('consentRegistry.details.lifecycle.noDescription')}
                      </Typography>
                    </TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Card>
  )
}

export default ConsentLifecycleSection
