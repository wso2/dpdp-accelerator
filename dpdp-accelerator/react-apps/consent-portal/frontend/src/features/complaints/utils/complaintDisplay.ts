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

import type {
  ComplaintActorRole,
  ComplaintPriorityAPI,
  ComplaintSlaState,
  ComplaintStatus,
} from '../../../types/complaint'

type ChipColor = 'success' | 'warning' | 'error' | 'info' | 'default'

export function getComplaintPriorityChipColor(priority: ComplaintPriorityAPI): ChipColor {
  switch (priority) {
    case 'CRITICAL':
      return 'error'
    case 'HIGH':
      return 'warning'
    case 'MEDIUM':
      return 'info'
    case 'LOW':
    default:
      return 'default'
  }
}

export function getComplaintStatusChipColor(
  status: ComplaintStatus,
  viewerRole: Extract<ComplaintActorRole, 'DataPrincipal' | 'ComplaintOfficer'>,
): ChipColor {
  switch (status) {
    case 'OPEN':
      return 'info'
    case 'IN_PROGRESS':
      return 'warning'
    case 'AWAITING_INTERNAL_REVIEW':
      return viewerRole === 'DataPrincipal' ? 'default' : 'error'
    case 'WAITING_ON_CLIENT':
      return viewerRole === 'DataPrincipal' ? 'error' : 'default'
    case 'RESOLVED':
      return 'success'
    default:
      return 'default'
  }
}

const STATUS_LABEL_KEYS: Record<ComplaintStatus, string> = {
  OPEN: 'open',
  IN_PROGRESS: 'investigation',
  WAITING_ON_CLIENT: 'awaitingInfo',
  AWAITING_INTERNAL_REVIEW: 'waitingOnDpo',
  RESOLVED: 'resolved',
}

export function getComplaintStatusLabelKey(status: ComplaintStatus): string {
  return STATUS_LABEL_KEYS[status]
}

const CHIP_COLOR_TO_SX_PATH: Record<ChipColor, string> = {
  success: 'success.main',
  warning: 'warning.main',
  error: 'error.main',
  info: 'info.main',
  default: 'text.disabled',
}

export function getComplaintStatusAccentColor(
  status: ComplaintStatus,
  viewerRole: Extract<ComplaintActorRole, 'DataPrincipal' | 'ComplaintOfficer'>,
): string {
  return CHIP_COLOR_TO_SX_PATH[getComplaintStatusChipColor(status, viewerRole)]
}

const SLA_AT_RISK_THRESHOLD_HOURS = 24 * 14
const DAY_IN_MS = 1000 * 60 * 60 * 24

export function getComplaintSlaState(
  statutoryDueDate: string,
  status: ComplaintStatus,
): ComplaintSlaState {
  if (status === 'RESOLVED') {
    return 'met'
  }

  const hoursRemaining = (new Date(statutoryDueDate).getTime() - Date.now()) / (1000 * 60 * 60)

  if (hoursRemaining < 0) {
    return 'breached'
  }

  if (hoursRemaining <= SLA_AT_RISK_THRESHOLD_HOURS) {
    return 'atRisk'
  }

  return 'onTrack'
}

export function getComplaintSlaDaysRemaining(statutoryDueDate: string): number {
  return Math.ceil((new Date(statutoryDueDate).getTime() - Date.now()) / DAY_IN_MS)
}
