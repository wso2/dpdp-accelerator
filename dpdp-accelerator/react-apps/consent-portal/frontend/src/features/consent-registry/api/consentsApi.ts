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
  ConsentDetail,
  ConsentListQueryParams,
  ConsentSearchResponse,
} from '../../../types/consent'
import { apiRequest } from '../../../utils/apiClient'

const jsonHeaders = { 'Content-Type': 'application/json' }

export async function fetchMyConsents(
  params: ConsentListQueryParams,
): Promise<ConsentSearchResponse> {
  return apiRequest<ConsentSearchResponse>('/me/consents', {
    method: 'GET',
    query: {
      consentStatuses: params.consentStatuses,
      serviceId: params.serviceId,
      limit: params.limit,
      offset: params.offset,
    },
  })
}

export async function fetchMyConsentByID(consentID: string): Promise<ConsentDetail> {
  return apiRequest<ConsentDetail>(`/me/consents/${encodeURIComponent(consentID)}`, {
    method: 'GET',
  })
}

/** Approves the whole consent. Per element selection no longer exists. */
export async function approveMyConsent(consentID: string): Promise<unknown> {
  return apiRequest<unknown>(`/me/consents/${encodeURIComponent(consentID)}/approve`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({}),
  })
}

export async function rejectMyConsent(consentID: string): Promise<unknown> {
  return apiRequest<unknown>(`/me/consents/${encodeURIComponent(consentID)}/reject`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({}),
  })
}

export async function revokeMyConsent(consentID: string): Promise<unknown> {
  return apiRequest<unknown>(`/me/consents/${encodeURIComponent(consentID)}/revoke`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({}),
  })
}
