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
  ConsentApprovalSelection,
  ConsentDetailAPI,
  ConsentListQueryParams,
  ConsentSearchResponse,
} from '../../../types/consent'
import type {
  ActivateNominationRequest,
  CreateNominationRequest,
  DeactivateNominationRequest,
  NominationResponse,
} from '../../../types/nominee'
import { apiRequest, apiRequestNoContent } from '../../../utils/apiClient'

/** Nominee Service is called directly - never through the BFF - per its own auth. */
const NOMINEE_SERVICE_BASE_URL = import.meta.env.VITE_NOMINEE_SERVICE_BASE_URL

function nomineeServiceRequest<T>(
  path: string,
  options: Parameters<typeof apiRequest>[1] = {},
): Promise<T> {
  return apiRequest<T>(path, { ...options, baseURL: NOMINEE_SERVICE_BASE_URL })
}

/** Resolves a nominee candidate's email to their user ID (BFF, not Nominee Service). */
export async function lookupUserByEmail(email: string): Promise<{ id: string; email: string }> {
  return apiRequest<{ id: string; email: string }>('/nominees/lookup', {
    method: 'GET',
    query: { email },
  })
}

interface UserSummary {
  id: string
  name: string
  email: string
}

/**
 * Resolves an owner/nominee ID to a display name (BFF, not Nominee Service -
 * Nominee Service has no directory access of its own).
 */
export async function lookupUserByID(id: string): Promise<UserSummary> {
  return apiRequest<UserSummary>(`/users/${encodeURIComponent(id)}`, { method: 'GET' })
}

/**
 * Every nomination this owner has made.
 *
 * An owner may nominate any number of people (DPDP Rule 14(4)), each with its
 * own permissions and status, so this is always a list - an empty one when the
 * owner has nominated nobody.
 */
export async function fetchMyNominations(): Promise<NominationResponse[]> {
  return nomineeServiceRequest<NominationResponse[]>('/me/nominees', { method: 'GET' })
}

/** Adds one nominee. Additive - existing nominations are untouched. */
export async function addMyNomination(
  request: CreateNominationRequest,
): Promise<NominationResponse> {
  return nomineeServiceRequest<NominationResponse>('/me/nominees', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}

/** Changes what one nominee may do, leaving the owner's others alone. */
export async function updateMyNominationPermissions(
  nominationId: string,
  permissions: CreateNominationRequest['permissions'],
): Promise<NominationResponse> {
  return nomineeServiceRequest<NominationResponse>(
    `/me/nominees/${encodeURIComponent(nominationId)}`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ permissions }),
    },
  )
}

/** Removes one nominee by id. */
export async function removeMyNomination(nominationId: string): Promise<void> {
  return apiRequestNoContent(`/me/nominees/${encodeURIComponent(nominationId)}`, {
    method: 'DELETE',
    baseURL: NOMINEE_SERVICE_BASE_URL,
  })
}

export async function fetchNominatedFor(): Promise<NominationResponse[]> {
  return nomineeServiceRequest<NominationResponse[]>('/nominated-for', { method: 'GET' })
}

export async function acceptNomination(nominationId: string): Promise<NominationResponse> {
  return nomineeServiceRequest<NominationResponse>(
    `/nominations/${encodeURIComponent(nominationId)}/accept`,
    { method: 'POST' },
  )
}

/** Declining is final - the owner must nominate again rather than reviving this. */
export async function rejectNomination(nominationId: string): Promise<NominationResponse> {
  return nomineeServiceRequest<NominationResponse>(
    `/nominations/${encodeURIComponent(nominationId)}/reject`,
    { method: 'POST' },
  )
}

export async function activateNomination(
  nominationId: string,
  request: ActivateNominationRequest,
): Promise<NominationResponse> {
  return nomineeServiceRequest<NominationResponse>(
    `/admin/nominations/${encodeURIComponent(nominationId)}/activate`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    },
  )
}

export async function deactivateNomination(
  nominationId: string,
  request: DeactivateNominationRequest,
): Promise<NominationResponse> {
  return nomineeServiceRequest<NominationResponse>(
    `/admin/nominations/${encodeURIComponent(nominationId)}/deactivate`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    },
  )
}

/**
 * Acting-mode consent APIs, served by the BFF's /acting/* routes.
 *
 * The nominee is identified by the impersonation token the BFF holds in an
 * HttpOnly cookie, so no owner or session id is passed: nothing the browser
 * sends decides whose data is returned.
 */
export async function fetchActingConsents(
  params: ConsentListQueryParams,
): Promise<ConsentSearchResponse> {
  return apiRequest<ConsentSearchResponse>('/acting-api/consents', {
    method: 'GET',
    query: {
      consentStatuses: params.consentStatuses,
      purposeName: params.purposeName,
      groupIds: params.groupIds,
      fromTime: params.fromTime,
      toTime: params.toTime,
      limit: params.limit,
      offset: params.offset,
    },
  })
}

export async function fetchActingConsentByID(consentID: string): Promise<ConsentDetailAPI> {
  return apiRequest<ConsentDetailAPI>(`/acting-api/consents/${encodeURIComponent(consentID)}`, {
    method: 'GET',
  })
}

export async function revokeActingConsent(consentID: string): Promise<void> {
  return apiRequestNoContent(`/acting-api/consents/${encodeURIComponent(consentID)}/revoke`, {
    method: 'POST',
  })
}

export async function approveActingConsent(
  consentID: string,
  selectedOptionalElements: ConsentApprovalSelection[],
): Promise<void> {
  return apiRequestNoContent(`/acting-api/consents/${encodeURIComponent(consentID)}/approve`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(selectedOptionalElements),
  })
}
