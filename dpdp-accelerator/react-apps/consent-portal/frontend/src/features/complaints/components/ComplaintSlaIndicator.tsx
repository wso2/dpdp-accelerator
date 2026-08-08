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

import { Box, Stack, Tooltip, Typography } from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'
import type { ComplaintStatus } from '../../../types/complaint'
import { formatIsoDateTime } from '../../../utils/dateTime'
import {
  getComplaintSlaDaysRemaining,
  getComplaintSlaState,
  getComplaintStatusLabelKey,
} from '../utils/complaintDisplay'

interface ComplaintSlaIndicatorProps {
  statutoryDueDate: string
  status: ComplaintStatus
}

const SLA_DATE_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  month: 'short',
  day: '2-digit',
  year: 'numeric',
}

const SLA_DOT_COLOR = {
  onTrack: 'success.main',
  atRisk: 'warning.main',
  breached: 'error.main',
  met: 'text.disabled',
} as const

function ComplaintSlaIndicator({
  statutoryDueDate,
  status,
}: ComplaintSlaIndicatorProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const slaState = getComplaintSlaState(statutoryDueDate, status)
  const daysRemaining = getComplaintSlaDaysRemaining(statutoryDueDate)

  let label: string

  if (status === 'RESOLVED') {
    label = t(`complaints.status.${getComplaintStatusLabelKey(status)}`)
  } else if (daysRemaining < 0) {
    const overdueDays = Math.abs(daysRemaining)
    label = t(
      overdueDays === 1 ? 'complaints.sla.overdueSingular' : 'complaints.sla.overduePlural',
      { count: overdueDays },
    )
  } else if (daysRemaining === 0) {
    label = t('complaints.sla.dueToday')
  } else {
    label = t(
      daysRemaining === 1 ? 'complaints.sla.daysLeftSingular' : 'complaints.sla.daysLeftPlural',
      { count: daysRemaining },
    )
  }

  return (
    <Tooltip
      title={t('complaints.sla.dueDate', {
        date: formatIsoDateTime(statutoryDueDate, SLA_DATE_FORMAT_OPTIONS),
      })}
    >
      <Stack direction="row" spacing={0.75} alignItems="center">
        <Box
          sx={{
            width: 10,
            height: 10,
            borderRadius: '50%',
            bgcolor: SLA_DOT_COLOR[slaState],
            flexShrink: 0,
          }}
        />
        <Typography variant="body2">{label}</Typography>
      </Stack>
    </Tooltip>
  )
}

export default ComplaintSlaIndicator
