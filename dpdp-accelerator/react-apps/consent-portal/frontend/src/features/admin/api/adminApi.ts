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

import type { AdminUserSummary } from '../../../types/admin'
import type { NominationResponse } from '../../../types/nominee'
import { apiRequest } from '../../../utils/apiClient'

/** Nominee Service is called directly - never through the BFF - per its own auth. */
const NOMINEE_SERVICE_BASE_URL = import.meta.env.VITE_NOMINEE_SERVICE_BASE_URL

function nomineeServiceRequest<T>(
  path: string,
  options: Parameters<typeof apiRequest>[1] = {},
): Promise<T> {
  return apiRequest<T>(path, { ...options, baseURL: NOMINEE_SERVICE_BASE_URL })
}

/** Directory search, unrelated to nominee state - stays on the BFF. */
export async function searchUsers(query: string): Promise<AdminUserSummary[]> {
  return apiRequest<AdminUserSummary[]>('/admin/users/search', {
    method: 'GET',
    query: { q: query },
  })
}

/** Every nomination this owner has made. An owner may appoint more than one. */
export async function fetchNominationsByOwner(ownerId: string): Promise<NominationResponse[]> {
  return nomineeServiceRequest<NominationResponse[]>('/admin/nominations', {
    method: 'GET',
    query: { ownerId },
  })
}

/** Nominations accepted by the nominee but not yet activated - the admin review queue. */
export async function fetchPendingNominations(): Promise<NominationResponse[]> {
  return nomineeServiceRequest<NominationResponse[]>('/admin/nominations/pending', {
    method: 'GET',
  })
}

export async function activateNomination(
  nominationId: string,
  ticketReference: string,
): Promise<NominationResponse> {
  return nomineeServiceRequest<NominationResponse>(
    `/admin/nominations/${encodeURIComponent(nominationId)}/activate`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ticketReference }),
    },
  )
}

export async function deactivateNomination(
  nominationId: string,
  reason: string,
): Promise<NominationResponse> {
  return nomineeServiceRequest<NominationResponse>(
    `/admin/nominations/${encodeURIComponent(nominationId)}/deactivate`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason }),
    },
  )
}
