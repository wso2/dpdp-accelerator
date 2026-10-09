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

import { Chip, Tooltip } from '@wso2/oxygen-ui'
import { CircleAlert, CircleCheckBig, Clock } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import type { ComplaintSlaState, ComplaintStatus } from '../../../types/complaint'
import { formatEpochTimestamp } from '../../../utils/dateTime'
import { getComplaintSlaSummary, SLA_DEADLINE_FORMAT_OPTIONS } from '../utils/complaintDisplay'

interface ComplaintSlaIndicatorProps {
  statutoryDueDate: number
  status: ComplaintStatus
  // null where a tooltip would only repeat what is already on screen.
  tooltip: string | null
}

// Only the states an officer must act on get a colour, so red and amber stay meaningful in a
// long queue.
const SLA_CHIP_COLOR: Record<ComplaintSlaState, 'error' | 'warning' | 'default'> = {
  breached: 'error',
  atRisk: 'warning',
  onTrack: 'default',
  met: 'default',
}

const SLA_ICON: Record<ComplaintSlaState, React.JSX.Element> = {
  breached: <CircleAlert size={14} />,
  atRisk: <Clock size={14} />,
  onTrack: <Clock size={14} />,
  met: <CircleCheckBig size={14} />,
}

// Text and icon carry the state, not colour alone (WCAG 1.4.1).
function ComplaintSlaIndicator({
  statutoryDueDate,
  status,
  tooltip,
}: ComplaintSlaIndicatorProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const { state, labelKey, count } = getComplaintSlaSummary(statutoryDueDate, status)
  const label = t(labelKey, { count })
  const dueDate = t('complaints.sla.dueDate', {
    date: formatEpochTimestamp(statutoryDueDate, SLA_DEADLINE_FORMAT_OPTIONS),
  })

  const chip = (
    <Chip
      size="small"
      variant="outlined"
      color={SLA_CHIP_COLOR[state]}
      icon={SLA_ICON[state]}
      label={label}
      // MUI's outlined small chip gives the icon a 2px left margin but the label 7px of right
      // padding; match them so the content sits centred.
      sx={{ '& .MuiChip-icon': { ml: 0.875 } }}
      // Tooltips are hover-only, so screen readers get the due date in the name instead.
      aria-label={`${label}, ${dueDate}`}
    />
  )
  return tooltip ? <Tooltip title={tooltip}>{chip}</Tooltip> : chip
}

export default ComplaintSlaIndicator
