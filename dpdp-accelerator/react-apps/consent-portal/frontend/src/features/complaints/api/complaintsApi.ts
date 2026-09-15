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
  ComplaintQueueStatsAPI,
  ComplaintRecordAPI,
  ComplaintTimelineListResponseAPI,
} from '../../../types/complaint'
import { apiRequest } from '../../../utils/apiClient'

const COMPLAINT_MGT_V1 = '/api/dpdp/complaints/v1'

const jsonHeaders = { 'Content-Type': 'application/json' }

function listQuery(params: ComplaintListQueryParamsAPI) {
  return {
    status: params.status,
    priority: params.priority,
    userId: params.userId,
    search: params.search,
    limit: params.limit,
    offset: params.offset,
    sort: params.sort,
  }
}

function uploadFilesFormData(files: File[], isPublic?: boolean): FormData {
  const formData = new FormData()
  files.forEach((file) => formData.append('file', file))
  if (isPublic !== undefined) {
    formData.append('isPublic', String(isPublic))
  }
  return formData
}

// -- Data Principal surface (/me/complaints/*, complaints:{read,write}:self) -------------

export async function listMyComplaints(
  params: ComplaintListQueryParamsAPI,
): Promise<ComplaintListResponseAPI> {
  return apiRequest<ComplaintListResponseAPI>(`${COMPLAINT_MGT_V1}/me/complaints`, {
    method: 'GET',
    query: listQuery(params),
  })
}

export async function listComplaintCategories(): Promise<ComplaintCategoriesResponseAPI> {
  return apiRequest<ComplaintCategoriesResponseAPI>(
    `${COMPLAINT_MGT_V1}/me/complaints/categories`,
    {
      method: 'GET',
    },
  )
}

export async function createMyComplaint(
  body: ComplaintCreateRequestAPI,
): Promise<ComplaintCreateResponseAPI> {
  return apiRequest<ComplaintCreateResponseAPI>(`${COMPLAINT_MGT_V1}/me/complaints`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ subjectCategory: body.subjectCategory, description: body.description }),
  })
}

export async function getMyComplaint(complaintId: string): Promise<ComplaintRecordAPI> {
  return apiRequest<ComplaintRecordAPI>(
    `${COMPLAINT_MGT_V1}/me/complaints/${encodeURIComponent(complaintId)}`,
    { method: 'GET' },
  )
}

export async function getMyComplaintTimeline(
  complaintId: string,
): Promise<ComplaintTimelineListResponseAPI> {
  return apiRequest<ComplaintTimelineListResponseAPI>(
    `${COMPLAINT_MGT_V1}/me/complaints/${encodeURIComponent(complaintId)}/timeline`,
    { method: 'GET' },
  )
}

export async function sendMyComplaintMessage(
  complaintId: string,
  body: ComplaintMessageRequestAPI,
): Promise<ComplaintCommentCreateResponseAPI> {
  return apiRequest<ComplaintCommentCreateResponseAPI>(
    `${COMPLAINT_MGT_V1}/me/complaints/${encodeURIComponent(complaintId)}/comments`,
    {
      method: 'POST',
      headers: jsonHeaders,
      body: JSON.stringify({ message: body.message, toStatus: body.toStatus }),
    },
  )
}

/** Always isPublic=true - the /me upload endpoint has no isPublic field. */
export async function uploadMyComplaintAttachments(
  complaintId: string,
  files: File[],
): Promise<ComplaintAttachmentAPI[]> {
  return apiRequest<ComplaintAttachmentAPI[]>(
    `${COMPLAINT_MGT_V1}/me/complaints/${encodeURIComponent(complaintId)}/attachments`,
    { method: 'POST', body: uploadFilesFormData(files) },
  )
}

export async function downloadMyComplaintAttachment(
  complaintId: string,
  attachmentId: string,
): Promise<ComplaintAttachmentDownloadAPI> {
  return apiRequest<ComplaintAttachmentDownloadAPI>(
    `${COMPLAINT_MGT_V1}/me/complaints/${encodeURIComponent(complaintId)}/attachments/${encodeURIComponent(attachmentId)}`,
    { method: 'GET' },
  )
}

// -- Complaint Officer surface (/complaints/*, complaints:{read,write}:any) ---------------

export async function listManagedComplaints(
  params: ComplaintListQueryParamsAPI,
): Promise<ComplaintListResponseAPI> {
  return apiRequest<ComplaintListResponseAPI>(`${COMPLAINT_MGT_V1}/complaints`, {
    method: 'GET',
    query: listQuery(params),
  })
}

export async function getManagedComplaintQueueStats(): Promise<ComplaintQueueStatsAPI> {
  return apiRequest<ComplaintQueueStatsAPI>(`${COMPLAINT_MGT_V1}/complaints/stats`, {
    method: 'GET',
  })
}

export async function getManagedComplaint(complaintId: string): Promise<ComplaintRecordAPI> {
  return apiRequest<ComplaintRecordAPI>(
    `${COMPLAINT_MGT_V1}/complaints/${encodeURIComponent(complaintId)}`,
    { method: 'GET' },
  )
}

export async function getManagedComplaintTimeline(
  complaintId: string,
): Promise<ComplaintTimelineListResponseAPI> {
  return apiRequest<ComplaintTimelineListResponseAPI>(
    `${COMPLAINT_MGT_V1}/complaints/${encodeURIComponent(complaintId)}/timeline`,
    { method: 'GET' },
  )
}

export async function sendManagedComplaintMessage(
  complaintId: string,
  body: ComplaintMessageRequestAPI,
): Promise<ComplaintCommentCreateResponseAPI> {
  return apiRequest<ComplaintCommentCreateResponseAPI>(
    `${COMPLAINT_MGT_V1}/complaints/${encodeURIComponent(complaintId)}/comments`,
    {
      method: 'POST',
      headers: jsonHeaders,
      body: JSON.stringify({
        message: body.message,
        isPublic: body.isPublic ?? true,
        toStatus: body.toStatus,
      }),
    },
  )
}

export async function uploadManagedComplaintAttachments(
  complaintId: string,
  files: File[],
  isPublic?: boolean,
): Promise<ComplaintAttachmentAPI[]> {
  return apiRequest<ComplaintAttachmentAPI[]>(
    `${COMPLAINT_MGT_V1}/complaints/${encodeURIComponent(complaintId)}/attachments`,
    { method: 'POST', body: uploadFilesFormData(files, isPublic) },
  )
}

export async function downloadManagedComplaintAttachment(
  complaintId: string,
  attachmentId: string,
): Promise<ComplaintAttachmentDownloadAPI> {
  return apiRequest<ComplaintAttachmentDownloadAPI>(
    `${COMPLAINT_MGT_V1}/complaints/${encodeURIComponent(complaintId)}/attachments/${encodeURIComponent(attachmentId)}`,
    { method: 'GET' },
  )
}
