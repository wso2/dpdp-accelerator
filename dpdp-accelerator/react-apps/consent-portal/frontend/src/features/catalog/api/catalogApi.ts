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
  ElementBulkCreateResponse,
  ElementCreateRequest,
  ElementFilters,
  ElementListResponse,
  ElementVersion,
  ElementVersionCreateRequest,
  ElementVersionList,
  PurposeCreateRequest,
  PurposeFilters,
  PurposeListResponse,
  PurposeVersion,
  PurposeVersionCreateRequest,
  PurposeVersionList,
} from '../../../types/catalog'
import { apiRequest, apiRequestNoContent } from '../../../utils/apiClient'

const jsonHeaders = { 'Content-Type': 'application/json' }

export function fetchElements(
  filters: ElementFilters,
  limit: number,
  offset: number,
): Promise<ElementListResponse> {
  return apiRequest<ElementListResponse>('/api/consent-elements', {
    method: 'GET',
    query: {
      name: filters.name.trim() || undefined,
      namespace: filters.namespace.trim() || undefined,
      type: filters.type === 'All' ? undefined : filters.type,
      version:
        filters.name.trim() || filters.namespace.trim()
          ? filters.version.trim() || undefined
          : undefined,
      limit,
      offset,
    },
  })
}

export function fetchElement(elementId: string): Promise<ElementVersion> {
  return apiRequest<ElementVersion>(`/api/consent-elements/${encodeURIComponent(elementId)}`, {
    method: 'GET',
  })
}

export function fetchElementVersions(elementId: string): Promise<ElementVersionList> {
  return apiRequest<ElementVersionList>(
    `/api/consent-elements/${encodeURIComponent(elementId)}/versions`,
    { method: 'GET' },
  )
}

export async function createElement(payload: ElementCreateRequest): Promise<ElementVersion> {
  const response = await apiRequest<
    ElementBulkCreateResponse | ElementBulkCreateResponse['results']
  >('/api/consent-elements', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify([payload]),
  })
  const result = (Array.isArray(response) ? response : response.results)[0]
  const element = result?.element ?? result?.data
  const error = result?.error

  if (!result || result.status === 'FAILED' || !element) {
    throw new Error(
      (typeof error === 'string' ? error : (error?.description ?? error?.message)) ??
        'Element creation failed',
    )
  }

  return element
}

export function createElementVersion(
  elementId: string,
  payload: ElementVersionCreateRequest,
): Promise<ElementVersion> {
  return apiRequest<ElementVersion>(
    `/api/consent-elements/${encodeURIComponent(elementId)}/versions`,
    { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) },
  )
}

export function deleteElementVersion(elementId: string, version: string): Promise<void> {
  return apiRequestNoContent(
    `/api/consent-elements/${encodeURIComponent(elementId)}/versions/${encodeURIComponent(version)}`,
    { method: 'DELETE' },
  )
}

export function fetchPurposes(
  filters: PurposeFilters,
  limit: number,
  offset: number,
): Promise<PurposeListResponse> {
  return apiRequest<PurposeListResponse>('/api/consent-purposes', {
    method: 'GET',
    query: {
      purposeName: filters.purposeName.trim() || undefined,
      elementName: filters.elementName.trim() || undefined,
      elementNamespace: filters.elementNamespace.trim() || undefined,
      elementVersion:
        filters.elementName.trim() || filters.elementNamespace.trim()
          ? filters.elementVersion.trim() || undefined
          : undefined,
      groupIds: filters.groupIds.trim() || undefined,
      limit,
      offset,
    },
  })
}

export function fetchPurpose(purposeId: string): Promise<PurposeVersion> {
  return apiRequest<PurposeVersion>(`/api/consent-purposes/${encodeURIComponent(purposeId)}`, {
    method: 'GET',
  })
}

export function fetchPurposeVersions(purposeId: string): Promise<PurposeVersionList> {
  return apiRequest<PurposeVersionList>(
    `/api/consent-purposes/${encodeURIComponent(purposeId)}/versions`,
    { method: 'GET' },
  )
}

export function createPurpose(
  payload: PurposeCreateRequest,
  groupId?: string,
): Promise<PurposeVersion> {
  return apiRequest<PurposeVersion>('/api/consent-purposes', {
    method: 'POST',
    headers: { ...jsonHeaders, ...(groupId ? { 'group-id': groupId } : {}) },
    body: JSON.stringify(payload),
  })
}

export function createPurposeVersion(
  purposeId: string,
  payload: PurposeVersionCreateRequest,
): Promise<PurposeVersion> {
  return apiRequest<PurposeVersion>(
    `/api/consent-purposes/${encodeURIComponent(purposeId)}/versions`,
    { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) },
  )
}

export function deletePurposeVersion(purposeId: string, version: string): Promise<void> {
  return apiRequestNoContent(
    `/api/consent-purposes/${encodeURIComponent(purposeId)}/versions/${encodeURIComponent(version)}`,
    { method: 'DELETE' },
  )
}
