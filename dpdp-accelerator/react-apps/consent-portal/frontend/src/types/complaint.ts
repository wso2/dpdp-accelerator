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

// Types for the complaint-mgt endpoint (see complaint-server-API.yaml, v2.5.0). *API suffixed
// types mirror the wire shapes exactly; the rest are UI-facing shapes the mapper functions in
// utils/complaintMapper.ts build from them.
//
// The API distinguishes a "me" (Data Principal, self) request body from a "cm" (officer/admin,
// any) request body for create/message/status operations - e.g. only the officer body carries
// userId/actorUserId/actorRole. The *RequestAPI types below carry the union of fields either
// namespace's api function might send; each function in api/complaintsApi.ts only populates the
// subset its namespace's endpoint actually accepts.

// ---- Enums / literal unions ----

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

export type ComplaintPriorityAPI = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'

export const COMPLAINT_PRIORITIES: ComplaintPriorityAPI[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

// Note: complaint-server-API.yaml's ComplaintStatus enum names this state AWAITING_COMPLAINT_INFO;
// the DAO/service layer (DAOConstants, mysql.sql's CHK_COMPLAINT_STATUS) and this frontend both
// predate that spec revision and consistently use WAITING_ON_CLIENT instead. Kept as-is rather
// than renamed across the DB/enum layer for this pass - see the integration notes for this gap.
export const COMPLAINT_STATUSES = [
  'OPEN',
  'IN_PROGRESS',
  'WAITING_ON_CLIENT',
  'AWAITING_INTERNAL_REVIEW',
  'RESOLVED',
] as const

export type ComplaintStatus = (typeof COMPLAINT_STATUSES)[number]

export type ComplaintActorRoleAPI = 'USER' | 'COMPLAINT_OFFICER' | 'SYSTEM'
export type ComplaintActorRole = 'DataPrincipal' | 'ComplaintOfficer' | 'System'

export type ComplaintTimelineEntryTypeAPI = 'STATUS_CHANGE' | 'COMMENT' | 'INTERNAL_NOTE'
export type ComplaintTimelineVisibility = 'shared' | 'internal'

export type ComplaintSlaState = 'onTrack' | 'atRisk' | 'breached' | 'met'

// ---- Raw wire (*API) shapes ----

export interface ComplaintAttachmentAPI {
  attachmentId: string
  fileName: string
  contentType: string
  sizeBytes: number
  isPublic: boolean
  uploadedTime: number
}

export interface ComplaintAttachmentDownloadAPI {
  attachmentId: string
  fileName: string
  contentType: string
  uploadedTime: number
  content: string
}

interface PageMetadataAPI {
  total: number
  offset: number
  count: number
  limit: number
}

export interface ComplaintRecordAPI {
  id: string
  referenceId: string
  subjectCategory: ComplaintCategoryAPI
  priority: ComplaintPriorityAPI
  status: ComplaintStatus
  userId: string
  /** Human-readable display name for userId, when the identity provider resolved one. */
  userName?: string
  description: string
  attachments: ComplaintAttachmentAPI[]
  submittedAt: number
  updatedAt: number
  statutoryDueDate: number
}

export interface ComplaintCreateResponseAPI {
  id: string
  referenceId: string
  subjectCategory: ComplaintCategoryAPI
  priority: ComplaintPriorityAPI
  status: ComplaintStatus
  userId: string
  description: string
  submittedAt: number
  updatedAt: number
  statutoryDueDate: number
}

export interface ComplaintListResponseAPI {
  data: ComplaintRecordAPI[]
  metadata: PageMetadataAPI
}

/** Org-wide counts for the officer/admin queue's summary tiles - always unfiltered. */
export interface ComplaintQueueStatsAPI {
  openCount: number
  awaitingInternalReviewCount: number
  resolvedCount: number
  slaBreachedCount: number
}

export interface ComplaintListQueryParamsAPI {
  status?: ComplaintStatus
  priority?: ComplaintPriorityAPI
  userId?: string
  limit?: number
  offset?: number
  sort?: string
}

export interface ComplaintTimelineEntryAPI {
  id: string
  type: ComplaintTimelineEntryTypeAPI
  isPublic: boolean
  actorUserId?: string
  /** Human-readable display name for actorUserId, when the identity provider resolved one. */
  actorUserName?: string
  actorRole: ComplaintActorRoleAPI
  message: string
  fromStatus?: ComplaintStatus
  toStatus?: ComplaintStatus
  createdTime: number
  /** Attachments uploaded alongside this entry - empty for entries that are not an upload. */
  attachments: ComplaintAttachmentAPI[]
}

export interface ComplaintTimelineListResponseAPI {
  data: ComplaintTimelineEntryAPI[]
  metadata: PageMetadataAPI
}

export interface ComplaintCommentCreateResponseAPI {
  id: string
  actorUserId: string
  actorRole: ComplaintActorRoleAPI
  message: string
  isPublic: boolean
  fromStatus?: ComplaintStatus
  toStatus?: ComplaintStatus
  createdTime: number
}

export interface ComplaintCategoriesResponseAPI {
  data: Array<{ category: ComplaintCategoryAPI; priority: ComplaintPriorityAPI }>
}

// ---- Request bodies ----

export interface ComplaintCreateRequestAPI {
  subjectCategory: ComplaintCategoryAPI
  description: string
  /** Officer/admin (cm) only - the Data Principal on whose behalf the complaint is lodged. */
  userId?: string
  /** Officer/admin (cm) only - identity of the officer/system performing the intake. */
  actorUserId?: string
  actorRole?: 'COMPLAINT_OFFICER' | 'SYSTEM'
}

export interface ComplaintMessageRequestAPI {
  message: string
  toStatus?: ComplaintStatus
  /** Officer/admin (cm) only - true for a shared reply, false for an internal note. */
  isPublic?: boolean
}

// ---- UI-facing (mapped) shapes - see utils/complaintMapper.ts ----

export interface ComplaintAttachment {
  id: string
  fileName: string
  fileSizeLabel: string
}

export interface ComplaintTimelineEntry {
  id: string
  type: 'statusChange' | 'resolution' | 'note' | 'communication' | 'systemAcknowledgement'
  actorName: string
  actorRole: ComplaintActorRole
  message: string
  timestamp: number
  visibility: ComplaintTimelineVisibility
  fromStatus?: ComplaintStatus
  toStatus?: ComplaintStatus
  /** Attachments uploaded alongside this entry - empty for entries that are not an upload. */
  attachments?: ComplaintAttachment[]
}

export interface ComplaintRecord {
  id: string
  referenceId: string
  category: ComplaintCategoryAPI
  priority: ComplaintPriorityAPI
  status: ComplaintStatus
  dataPrincipalName: string
  submittedAt: number
  updatedAt: number
  statutoryDueDate: number
}

export interface ComplaintDetail extends ComplaintRecord {
  description: string
  attachments: ComplaintAttachment[]
  timeline: ComplaintTimelineEntry[]
}
