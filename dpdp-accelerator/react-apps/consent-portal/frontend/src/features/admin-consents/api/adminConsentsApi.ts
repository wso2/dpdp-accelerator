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
  ConsentDetailAPI,
  ConsentListQueryParams,
  ConsentSearchResponse,
} from '../../../types/consent'
import { apiRequest } from '../../../utils/apiClient'

export async function fetchAdminConsents(
  params: ConsentListQueryParams,
): Promise<ConsentSearchResponse> {
  return apiRequest<ConsentSearchResponse>('/api/consents', {
    method: 'GET',
    query: {
      consentStatuses: params.consentStatuses,
      userIds: params.userIds,
      groupIds: params.groupIds,
      purposeName: params.purposeName,
      purposeVersion: params.purposeVersion,
      elementName: params.elementName,
      elementNamespace: params.elementNamespace,
      elementVersion: params.elementVersion,
      sort: params.sort,
      fromTime: params.fromTime,
      toTime: params.toTime,
      limit: params.limit,
      offset: params.offset,
      details: true,
    },
  })
}

export async function fetchAdminConsentByID(consentID: string): Promise<ConsentDetailAPI> {
  return apiRequest<ConsentDetailAPI>(`/api/consents/${encodeURIComponent(consentID)}`, {
    method: 'GET',
    query: { details: true, includeStatusHistory: true },
  })
}

export async function revokeAdminConsent(consentID: string, actionBy: string): Promise<unknown> {
  return apiRequest<unknown>(`/api/consents/${encodeURIComponent(consentID)}/revoke`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ actionBy }),
  })
}
