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

import { Chip } from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'
import type { ComplaintActorRole, ComplaintStatus } from '../../../types/complaint'
import { getComplaintStatusChipColor, getComplaintStatusLabelKey } from '../utils/complaintDisplay'

interface ComplaintStatusChipProps {
  status: ComplaintStatus
  viewerRole: Extract<ComplaintActorRole, 'DataPrincipal' | 'ComplaintOfficer'>
}

function ComplaintStatusChip({ status, viewerRole }: ComplaintStatusChipProps): React.JSX.Element {
  const { t } = useTranslation('common')

  return (
    <Chip
      size="small"
      color={getComplaintStatusChipColor(status, viewerRole)}
      label={t(`complaints.status.${getComplaintStatusLabelKey(status)}`)}
    />
  )
}

export default ComplaintStatusChip
