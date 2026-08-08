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

import type { ComplaintStatus } from '../../types/complaint'

export const COMPLAINT_STATE_MACHINE_ORDER: ComplaintStatus[] = [
  'OPEN',
  'IN_PROGRESS',
  'WAITING_ON_CLIENT',
  'AWAITING_INTERNAL_REVIEW',
  'RESOLVED',
]

export const COMPLAINT_NEXT_STATUSES: Record<ComplaintStatus, ComplaintStatus[]> = {
  OPEN: ['IN_PROGRESS', 'WAITING_ON_CLIENT'],
  IN_PROGRESS: ['WAITING_ON_CLIENT', 'RESOLVED'],
  WAITING_ON_CLIENT: ['AWAITING_INTERNAL_REVIEW'],
  AWAITING_INTERNAL_REVIEW: ['IN_PROGRESS', 'WAITING_ON_CLIENT', 'RESOLVED'],
  RESOLVED: [],
}

export const MAX_ATTACHMENT_SIZE_BYTES = 10 * 1024 * 1024

export const MAX_ATTACHMENT_SIZE_LABEL = '10 MB'

export const COMPLAINT_LIST_ROWS_PER_PAGE_OPTIONS = [5, 10, 25] as const

export const COMPLAINT_QUEUE_ROWS_PER_PAGE_OPTIONS = [5, 10, 25] as const
