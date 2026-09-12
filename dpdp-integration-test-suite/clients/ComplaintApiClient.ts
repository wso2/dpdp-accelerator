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

import type { APIRequestContext, APIResponse } from '@playwright/test'
import { complaintsApiUrl, meComplaintsApiUrl } from '../utils/env'
import type { AuthHeaders } from '../utils/authStorage'

/**
 * Ground truth for these shapes is the actual Java source under
 * dpdp-accelerator/{components,internal-webapps}/org.wso2.dpdp.accelerator.complaint.mgt.*, not
 * complaint-server-API.yaml alone - the two have drifted in a couple of places. Types below match
 * what the server actually does.
 */
export type ComplaintCategory =
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

// ComplaintStatus.java, not complaint-server-API.yaml's ComplaintStatus enum: the yaml says
// AWAITING_COMPLAINT_INFO, the DB/service/API actually says WAITING_ON_CLIENT. Verified by reading
// StatusTransitionValidator.java and ComplaintServiceConstants.NOTE_REQUIRED_FOR_RESOLVED_ERROR's
// call site directly.
export type ComplaintStatus = 'OPEN' | 'IN_PROGRESS' | 'WAITING_ON_CLIENT' | 'AWAITING_INTERNAL_REVIEW' | 'RESOLVED'

export type ComplaintPriority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'

export interface UploadFile {
  name: string
  mimeType: string
  buffer: Buffer
}

export interface MeComplaintListParams {
  status?: ComplaintStatus
  limit?: number
  offset?: number
  sort?: string
}

export interface ComplaintListParams extends MeComplaintListParams {
  priority?: ComplaintPriority
  userId?: string
}

export interface TimelineParams {
  fromTime?: number
  toTime?: number
  order?: 'asc' | 'desc'
  limit?: number
  offset?: number
}

/**
 * Wraps the complaint-server's two REST namespaces (see complaintServerApiUrl in utils/env.ts for
 * how its base URL is derived) - `/me/complaints/*` for the authenticated Data Principal acting on
 * their own complaints, and `/complaints/*` for a COMPLAINT_OFFICER/SYSTEM actor managing every
 * complaint in the org. One class serves both, same rationale as ConsentApiClient: auth is just a
 * header pair resolved from whichever persona's captured auth state the caller passes in, and
 * scope-isolation tests deliberately construct this with the "wrong" persona's headers to prove the
 * other surface rejects them.
 */
export class ComplaintApiClient {
  constructor(
    private readonly request: APIRequestContext,
    private readonly auth: AuthHeaders,
  ) {}

  private headers(extra?: Record<string, string>): Record<string, string> {
    return { ...this.auth, ...extra }
  }

  private jsonHeaders(): Record<string, string> {
    return this.headers({ 'Content-Type': 'application/json' })
  }

  private toFormData(files: UploadFile[], extra?: Record<string, string>): FormData {
    const form = new FormData()
    for (const file of files) {
      // Copy into a plain Uint8Array: Node's Buffer is backed by ArrayBufferLike, which
      // BlobPart does not accept.
      form.append('file', new Blob([new Uint8Array(file.buffer)], { type: file.mimeType }), file.name)
    }
    if (extra) {
      for (const [key, value] of Object.entries(extra)) {
        form.append(key, value)
      }
    }
    return form
  }

  // Typed as `object`, not `Record<string, ...>`, so the specific param interfaces below (which
  // have no index signature of their own) can be passed straight through without a cast at every
  // call site.
  private queryParams(params: object): Record<string, string> {
    return Object.fromEntries(
      Object.entries(params as Record<string, string | number | undefined>)
        .filter(([, value]) => value !== undefined)
        .map(([key, value]) => [key, String(value)]),
    )
  }

  // --------------------------------------------------------------------- Me (Data Principal)

  async createMyComplaint(body: { subjectCategory: ComplaintCategory; description: string }): Promise<APIResponse> {
    return this.request.post(meComplaintsApiUrl(''), { headers: this.jsonHeaders(), data: body })
  }

  async listMyComplaints(params: MeComplaintListParams = {}): Promise<APIResponse> {
    return this.request.get(meComplaintsApiUrl(''), {
      headers: this.headers(),
      params: this.queryParams(params),
    })
  }

  async getMyComplaint(complaintId: string): Promise<APIResponse> {
    return this.request.get(meComplaintsApiUrl(`/${complaintId}`), { headers: this.headers() })
  }

  async getMyCategories(): Promise<APIResponse> {
    return this.request.get(meComplaintsApiUrl('/categories'), { headers: this.headers() })
  }

  async getMyTimeline(complaintId: string, params: TimelineParams = {}): Promise<APIResponse> {
    return this.request.get(meComplaintsApiUrl(`/${complaintId}/timeline`), {
      headers: this.headers(),
      params: this.queryParams(params),
    })
  }

  /** actorRole is implicitly USER, isPublic implicitly true - a Data Principal can never post an internal note. */
  async addMyComment(complaintId: string, body: { message: string; toStatus?: ComplaintStatus }): Promise<APIResponse> {
    return this.request.post(meComplaintsApiUrl(`/${complaintId}/comments`), { headers: this.jsonHeaders(), data: body })
  }

  /** No `note` field exists on this request at all - see MeComplaintStatusUpdateRequest in the spec. */
  async updateMyStatus(complaintId: string, toStatus: ComplaintStatus): Promise<APIResponse> {
    return this.request.post(meComplaintsApiUrl(`/${complaintId}/status`), {
      headers: this.jsonHeaders(),
      data: { toStatus },
    })
  }

  /** Uploads always land isPublic=true - there is no isPublic parameter on this endpoint. */
  async uploadMyAttachments(complaintId: string, files: UploadFile[]): Promise<APIResponse> {
    return this.request.post(meComplaintsApiUrl(`/${complaintId}/attachments`), {
      headers: this.headers(),
      multipart: this.toFormData(files),
    })
  }

  async downloadMyAttachment(complaintId: string, attachmentId: string): Promise<APIResponse> {
    return this.request.get(meComplaintsApiUrl(`/${complaintId}/attachments/${attachmentId}`), {
      headers: this.headers(),
    })
  }

  // ---------------------------------------------------------------------- Officer/admin

  /** Officer-assisted intake: userId is the Data Principal on whose behalf this is lodged, never the caller. */
  async createComplaintForUser(body: {
    userId: string
    subjectCategory: ComplaintCategory
    description: string
  }): Promise<APIResponse> {
    return this.request.post(complaintsApiUrl(''), { headers: this.jsonHeaders(), data: body })
  }

  async listComplaints(params: ComplaintListParams = {}): Promise<APIResponse> {
    return this.request.get(complaintsApiUrl(''), {
      headers: this.headers(),
      params: this.queryParams(params),
    })
  }

  async getComplaint(complaintId: string): Promise<APIResponse> {
    return this.request.get(complaintsApiUrl(`/${complaintId}`), { headers: this.headers() })
  }

  async getCategories(): Promise<APIResponse> {
    return this.request.get(complaintsApiUrl('/categories'), { headers: this.headers() })
  }

  async getTimeline(complaintId: string, params: TimelineParams = {}): Promise<APIResponse> {
    return this.request.get(complaintsApiUrl(`/${complaintId}/timeline`), {
      headers: this.headers(),
      params: this.queryParams(params),
    })
  }

  /** isPublic=false posts an internal note the Data Principal never sees - only an officer may do this. */
  async addComment(
    complaintId: string,
    body: { message: string; isPublic: boolean; toStatus?: ComplaintStatus },
  ): Promise<APIResponse> {
    return this.request.post(complaintsApiUrl(`/${complaintId}/comments`), { headers: this.jsonHeaders(), data: body })
  }

  /** Status-only transition, no comment. `note` is required by the server when toStatus is RESOLVED. */
  async updateStatus(complaintId: string, body: { toStatus: ComplaintStatus; note?: string }): Promise<APIResponse> {
    return this.request.post(complaintsApiUrl(`/${complaintId}/status`), { headers: this.jsonHeaders(), data: body })
  }

  /** isPublic defaults to true server-side when omitted. */
  async uploadAttachments(complaintId: string, files: UploadFile[], isPublic?: boolean): Promise<APIResponse> {
    return this.request.post(complaintsApiUrl(`/${complaintId}/attachments`), {
      headers: this.headers(),
      multipart: this.toFormData(files, isPublic === undefined ? undefined : { isPublic: String(isPublic) }),
    })
  }

  /** Officers can download regardless of isPublic - unlike downloadMyAttachment. */
  async downloadAttachment(complaintId: string, attachmentId: string): Promise<APIResponse> {
    return this.request.get(complaintsApiUrl(`/${complaintId}/attachments/${attachmentId}`), {
      headers: this.headers(),
    })
  }
}
