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

export const COMPLAINT_STATUSES = [
  'OPEN',
  'IN_PROGRESS',
  'WAITING_ON_CLIENT',
  'AWAITING_INTERNAL_REVIEW',
  'RESOLVED',
] as const

export type ComplaintStatus = (typeof COMPLAINT_STATUSES)[number]

export type ComplaintActorRole = 'DataPrincipal' | 'ComplaintOfficer' | 'System'

export type ComplaintTimelineVisibility = 'internal' | 'shared'

export type ComplaintSlaState = 'met' | 'breached' | 'atRisk' | 'onTrack'

export type ComplaintTimelineEntryType =
  | 'resolution'
  | 'statusChange'
  | 'note'
  | 'communication'
  | 'systemAcknowledgement'

export interface ComplaintAttachment {
  id: string
  fileName: string
  fileSizeLabel: string
}

export interface ComplaintTimelineEntry {
  id: string
  type: ComplaintTimelineEntryType
  actorName: string
  actorRole: ComplaintActorRole
  message: string
  timestamp: string
  visibility: ComplaintTimelineVisibility
  fromStatus?: ComplaintStatus
  toStatus?: ComplaintStatus
  attachments?: ComplaintAttachment[]
}

/** Row model shared by the Data Principal and Complaint Officer complaint tables. */
export interface ComplaintRecord {
  id: string
  referenceId: string
  category: ComplaintCategoryAPI
  priority: ComplaintPriorityAPI
  status: ComplaintStatus
  dataPrincipalName: string
  submittedAt: string
  updatedAt: string
  statutoryDueDate: string
}

export interface ComplaintDetail extends ComplaintRecord {
  description: string
  attachments: ComplaintAttachment[]
  timeline: ComplaintTimelineEntry[]
}

// -- BFF wire types -----------------------------------------------------------------------------

// These wire values are used directly as both the UI's internal representation and
// the i18n key fragment (e.g. `complaints.priority.CRITICAL`) - no separate UI vocabulary.
export type ComplaintStatusAPI = ComplaintStatus

export type ComplaintPriorityAPI = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'

/** Severity order, used for sorting - not the API's declaration order. */
export const COMPLAINT_PRIORITIES: readonly ComplaintPriorityAPI[] = [
  'CRITICAL',
  'HIGH',
  'MEDIUM',
  'LOW',
]

export type ComplaintCategoryAPI =
  | 'DATA_BREACH'
  | 'UNAUTHORIZED_DATA_SHARING'
  | 'CONSENT_WITHDRAWN_DATA_STILL_USED'
  | 'PURPOSE_VIOLATION'
  | 'DATA_ERASURE_NOT_COMPLETED'
  | 'DATA_CORRECTION_NOT_COMPLETED'
  | 'CONSENT_LIFECYCLE_ISSUE'
  | 'DATA_ACCESS_DENIED'
  | 'EXCESSIVE_DATA_COLLECTION'
  | 'OTHER'

export type ComplaintActorRoleAPI = 'USER' | 'COMPLAINT_OFFICER' | 'SYSTEM'

export type ComplaintTimelineEntryTypeAPI = 'STATUS_CHANGE' | 'INTERNAL_NOTE' | 'COMMENT'

export interface ComplaintAttachmentAPI {
  attachmentId: string
  fileName: string
  sizeBytes: number
}

export interface ComplaintAttachmentDownloadAPI {
  contentType: string
  content: string
}

export interface ComplaintRecordAPI {
  id: string
  referenceId: string
  userId: string
  subjectCategory: ComplaintCategoryAPI
  priority: ComplaintPriorityAPI
  status: ComplaintStatusAPI
  description: string
  submittedAt: string
  updatedAt: string
  statutoryDueDate: string
  attachments: ComplaintAttachmentAPI[]
}

export interface ComplaintTimelineEntryAPI {
  id: string
  type: ComplaintTimelineEntryTypeAPI
  actorUserId?: string
  actorRole: ComplaintActorRoleAPI
  message: string
  createdTime: string
  isPublic: boolean
  fromStatus?: ComplaintStatusAPI
  toStatus?: ComplaintStatusAPI
  attachments: ComplaintAttachmentAPI[]
}

export interface ComplaintTimelineListResponseAPI {
  data: ComplaintTimelineEntryAPI[]
}

export interface ComplaintListQueryParamsAPI {
  status?: ComplaintStatusAPI
  priority?: ComplaintPriorityAPI
  limit: number
  offset: number
  sort?: string
}

export interface ComplaintListResponseAPI {
  data: ComplaintRecordAPI[]
  metadata: {
    total: number
  }
}

/** The set of categories an organization currently accepts complaints under. */
export interface ComplaintCategoriesResponseAPI {
  data: ComplaintCategoryAPI[]
}

export interface ComplaintCreateRequestAPI {
  subjectCategory: ComplaintCategoryAPI
  description: string
}

export interface ComplaintCreateResponseAPI {
  id: string
  referenceId: string
}

export interface ComplaintMessageRequestAPI {
  message: string
  isPublic?: boolean
  toStatus?: ComplaintStatusAPI
}

export interface ComplaintCommentCreateResponseAPI {
  id: string
}

export interface ComplaintStatusUpdateRequestAPI {
  toStatus: ComplaintStatusAPI
  note: string
}

export interface ComplaintStatusUpdateResponseAPI {
  status: ComplaintStatusAPI
}
