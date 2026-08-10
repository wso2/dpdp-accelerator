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
  ComplaintAttachmentAPI,
  ComplaintAttachmentDownloadAPI,
  ComplaintCategoriesResponseAPI,
  ComplaintCommentCreateResponseAPI,
  ComplaintCreateRequestAPI,
  ComplaintCreateResponseAPI,
  ComplaintListQueryParamsAPI,
  ComplaintListResponseAPI,
  ComplaintMessageRequestAPI,
  ComplaintRecordAPI,
  ComplaintStatusUpdateRequestAPI,
  ComplaintStatusUpdateResponseAPI,
  ComplaintTimelineListResponseAPI,
} from '../../../types/complaint'
import { apiRequest } from '../../../utils/apiClient'

// The BFF's /api/complaints route group mirrors the complaint-server's own API contract exactly.
// Which capability of the caller's token a request exercises — Data Principal ("self") or
// Complaint Officer ("management") — is selected via this header, not by a distinct URL path.
// The BFF still verifies the caller actually holds the corresponding scope and always resolves
// the acting identity itself; this header only picks which of the token's own scopes applies.
type PortalSurface = 'self' | 'management'

function surfaceHeaders(surface: PortalSurface, extra?: Record<string, string>): HeadersInit {
  return { 'X-Portal-Surface': surface, ...extra }
}

function listQuery(params: ComplaintListQueryParamsAPI) {
  return {
    status: params.status,
    priority: params.priority,
    limit: params.limit,
    offset: params.offset,
    sort: params.sort,
  }
}

function uploadFilesFormData(files: File[]): FormData {
  const formData = new FormData()
  files.forEach((file) => formData.append('file', file))
  return formData
}

async function listComplaints(
  surface: PortalSurface,
  params: ComplaintListQueryParamsAPI,
): Promise<ComplaintListResponseAPI> {
  return apiRequest<ComplaintListResponseAPI>('/api/complaints', {
    method: 'GET',
    query: listQuery(params),
    headers: surfaceHeaders(surface),
  })
}

async function getComplaint(
  surface: PortalSurface,
  complaintId: string,
): Promise<ComplaintRecordAPI> {
  return apiRequest<ComplaintRecordAPI>(`/api/complaints/${encodeURIComponent(complaintId)}`, {
    method: 'GET',
    headers: surfaceHeaders(surface),
  })
}

async function getComplaintTimeline(
  surface: PortalSurface,
  complaintId: string,
): Promise<ComplaintTimelineListResponseAPI> {
  return apiRequest<ComplaintTimelineListResponseAPI>(
    `/api/complaints/${encodeURIComponent(complaintId)}/timeline`,
    { method: 'GET', headers: surfaceHeaders(surface) },
  )
}

async function sendComplaintMessage(
  surface: PortalSurface,
  complaintId: string,
  body: ComplaintMessageRequestAPI,
): Promise<ComplaintCommentCreateResponseAPI> {
  return apiRequest<ComplaintCommentCreateResponseAPI>(
    `/api/complaints/${encodeURIComponent(complaintId)}/comments`,
    {
      method: 'POST',
      headers: surfaceHeaders(surface, { 'Content-Type': 'application/json' }),
      body: JSON.stringify(body),
    },
  )
}

async function uploadComplaintAttachments(
  surface: PortalSurface,
  complaintId: string,
  files: File[],
): Promise<ComplaintAttachmentAPI[]> {
  return apiRequest<ComplaintAttachmentAPI[]>(
    `/api/complaints/${encodeURIComponent(complaintId)}/attachments`,
    { method: 'POST', headers: surfaceHeaders(surface), body: uploadFilesFormData(files) },
  )
}

async function uploadCommentAttachments(
  surface: PortalSurface,
  complaintId: string,
  commentId: string,
  files: File[],
): Promise<ComplaintAttachmentAPI[]> {
  return apiRequest<ComplaintAttachmentAPI[]>(
    `/api/complaints/${encodeURIComponent(complaintId)}/comments/${encodeURIComponent(commentId)}/attachments`,
    { method: 'POST', headers: surfaceHeaders(surface), body: uploadFilesFormData(files) },
  )
}

async function downloadComplaintAttachment(
  surface: PortalSurface,
  complaintId: string,
  attachmentId: string,
): Promise<ComplaintAttachmentDownloadAPI> {
  return apiRequest<ComplaintAttachmentDownloadAPI>(
    `/api/complaints/${encodeURIComponent(complaintId)}/attachments/${encodeURIComponent(attachmentId)}`,
    { method: 'GET', headers: surfaceHeaders(surface) },
  )
}

// -- Data Principal surface -------------------------------------------------------------------

export async function listMyComplaints(
  params: ComplaintListQueryParamsAPI,
): Promise<ComplaintListResponseAPI> {
  return listComplaints('self', params)
}

export async function listComplaintCategories(): Promise<ComplaintCategoriesResponseAPI> {
  return apiRequest<ComplaintCategoriesResponseAPI>('/api/complaints/categories', {
    method: 'GET',
    headers: surfaceHeaders('self'),
  })
}

export async function createMyComplaint(
  body: ComplaintCreateRequestAPI,
): Promise<ComplaintCreateResponseAPI> {
  return apiRequest<ComplaintCreateResponseAPI>('/api/complaints', {
    method: 'POST',
    headers: surfaceHeaders('self', { 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  })
}

export async function getMyComplaint(complaintId: string): Promise<ComplaintRecordAPI> {
  return getComplaint('self', complaintId)
}

export async function getMyComplaintTimeline(
  complaintId: string,
): Promise<ComplaintTimelineListResponseAPI> {
  return getComplaintTimeline('self', complaintId)
}

export async function sendMyComplaintMessage(
  complaintId: string,
  body: ComplaintMessageRequestAPI,
): Promise<ComplaintCommentCreateResponseAPI> {
  return sendComplaintMessage('self', complaintId, body)
}

export async function uploadMyComplaintAttachments(
  complaintId: string,
  files: File[],
): Promise<ComplaintAttachmentAPI[]> {
  return uploadComplaintAttachments('self', complaintId, files)
}

export async function uploadMyCommentAttachments(
  complaintId: string,
  commentId: string,
  files: File[],
): Promise<ComplaintAttachmentAPI[]> {
  return uploadCommentAttachments('self', complaintId, commentId, files)
}

export async function downloadMyComplaintAttachment(
  complaintId: string,
  attachmentId: string,
): Promise<ComplaintAttachmentDownloadAPI> {
  return downloadComplaintAttachment('self', complaintId, attachmentId)
}

// -- Complaint Officer surface ------------------------------------------------------------------

export async function listManagedComplaints(
  params: ComplaintListQueryParamsAPI,
): Promise<ComplaintListResponseAPI> {
  return listComplaints('management', params)
}

export async function getManagedComplaint(complaintId: string): Promise<ComplaintRecordAPI> {
  return getComplaint('management', complaintId)
}

export async function getManagedComplaintTimeline(
  complaintId: string,
): Promise<ComplaintTimelineListResponseAPI> {
  return getComplaintTimeline('management', complaintId)
}

export async function sendManagedComplaintMessage(
  complaintId: string,
  body: ComplaintMessageRequestAPI,
): Promise<ComplaintCommentCreateResponseAPI> {
  return sendComplaintMessage('management', complaintId, body)
}

export async function updateManagedComplaintStatus(
  complaintId: string,
  body: ComplaintStatusUpdateRequestAPI,
): Promise<ComplaintStatusUpdateResponseAPI> {
  return apiRequest<ComplaintStatusUpdateResponseAPI>(
    `/api/complaints/${encodeURIComponent(complaintId)}/status`,
    {
      method: 'POST',
      headers: surfaceHeaders('management', { 'Content-Type': 'application/json' }),
      body: JSON.stringify(body),
    },
  )
}

export async function uploadManagedComplaintAttachments(
  complaintId: string,
  files: File[],
): Promise<ComplaintAttachmentAPI[]> {
  return uploadComplaintAttachments('management', complaintId, files)
}

export async function uploadManagedCommentAttachments(
  complaintId: string,
  commentId: string,
  files: File[],
): Promise<ComplaintAttachmentAPI[]> {
  return uploadCommentAttachments('management', complaintId, commentId, files)
}

export async function downloadManagedComplaintAttachment(
  complaintId: string,
  attachmentId: string,
): Promise<ComplaintAttachmentDownloadAPI> {
  return downloadComplaintAttachment('management', complaintId, attachmentId)
}
