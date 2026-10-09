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
  CatalogElement,
  CursorPageParams,
  ElementInput,
  ElementListQueryParams,
  ElementListResponse,
  PurposeDetail,
  PurposeInput,
  PurposeListQueryParams,
  PurposeListResponse,
  PurposeVersionInput,
  PurposeVersionListResponse,
  PurposeVersionSummary,
  PurposeVersionDetail,
} from '../../../types/catalog'
import { apiRequest, apiRequestNoContent } from '../../../utils/apiClient'
import { escapeFilterValue } from '../../../utils/filterGrammar'

/** The Identity Server's consent management (administrative) API. */
const CONSENT_MGT_V2 = '/api/identity/consent-mgt/v2.0'

const jsonHeaders = { 'Content-Type': 'application/json' }

function toCursorQuery(params: CursorPageParams): Record<string, string | number | undefined> {
  return {
    limit: params.limit,
    after: params.after,
    before: params.before,
  }
}

/**
 * Builds a `name co "<term>"` filter for the Elements API. Values are quoted
 * so a search term containing spaces or quotes stays a single value rather
 * than being parsed as separate filter tokens.
 */
export function buildElementNameFilter(term: string): string | undefined {
  const trimmed = term.trim()
  return trimmed ? `name co "${escapeFilterValue(trimmed)}"` : undefined
}

/**
 * Builds a `name co "<name>" and type eq "<type>"` filter for the Purposes
 * API (either clause may be omitted). `type` has no enum on the Identity
 * Server, so it's matched exactly rather than as a substring.
 */
export function buildPurposeFilter(name: string, type: string): string | undefined {
  const clauses: string[] = []
  const trimmedName = name.trim()
  const trimmedType = type.trim()
  if (trimmedName) {
    clauses.push(`name co "${escapeFilterValue(trimmedName)}"`)
  }
  if (trimmedType) {
    clauses.push(`type eq "${escapeFilterValue(trimmedType)}"`)
  }
  return clauses.length > 0 ? clauses.join(' and ') : undefined
}

export function fetchElements(params: ElementListQueryParams): Promise<ElementListResponse> {
  return apiRequest<ElementListResponse>(`${CONSENT_MGT_V2}/elements`, {
    method: 'GET',
    query: { ...toCursorQuery(params), filter: params.filter },
  })
}

export function fetchElement(elementId: string): Promise<CatalogElement> {
  return apiRequest<CatalogElement>(`${CONSENT_MGT_V2}/elements/${encodeURIComponent(elementId)}`, {
    method: 'GET',
  })
}

export function createElement(payload: ElementInput): Promise<CatalogElement> {
  return apiRequest<CatalogElement>(`${CONSENT_MGT_V2}/elements`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(payload),
  })
}

export function deleteElement(elementId: string): Promise<void> {
  return apiRequestNoContent(`${CONSENT_MGT_V2}/elements/${encodeURIComponent(elementId)}`, {
    method: 'DELETE',
  })
}

export function fetchPurposes(params: PurposeListQueryParams): Promise<PurposeListResponse> {
  return apiRequest<PurposeListResponse>(`${CONSENT_MGT_V2}/purposes`, {
    method: 'GET',
    query: { ...toCursorQuery(params), filter: params.filter },
  })
}

export function fetchPurpose(purposeId: string): Promise<PurposeDetail> {
  return apiRequest<PurposeDetail>(`${CONSENT_MGT_V2}/purposes/${encodeURIComponent(purposeId)}`, {
    method: 'GET',
  })
}

export function createPurpose(payload: PurposeInput): Promise<PurposeDetail> {
  return apiRequest<PurposeDetail>(`${CONSENT_MGT_V2}/purposes`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(payload),
  })
}

export function deletePurpose(purposeId: string): Promise<void> {
  return apiRequestNoContent(`${CONSENT_MGT_V2}/purposes/${encodeURIComponent(purposeId)}`, {
    method: 'DELETE',
  })
}

export function fetchPurposeVersions(
  purposeId: string,
  params: CursorPageParams,
): Promise<PurposeVersionListResponse> {
  return apiRequest<PurposeVersionListResponse>(
    `${CONSENT_MGT_V2}/purposes/${encodeURIComponent(purposeId)}/versions`,
    { method: 'GET', query: toCursorQuery(params) },
  )
}

export function createPurposeVersion(
  purposeId: string,
  payload: PurposeVersionInput,
): Promise<PurposeVersionSummary> {
  return apiRequest<PurposeVersionSummary>(
    `${CONSENT_MGT_V2}/purposes/${encodeURIComponent(purposeId)}/versions`,
    { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) },
  )
}

export function setLatestPurposeVersion(purposeId: string, versionId: string): Promise<void> {
  return apiRequestNoContent(
    `${CONSENT_MGT_V2}/purposes/${encodeURIComponent(purposeId)}/versions/latest`,
    { method: 'PUT', headers: jsonHeaders, body: JSON.stringify({ id: versionId }) },
  )
}

export function deletePurposeVersion(purposeId: string, versionId: string): Promise<void> {
  return apiRequestNoContent(
    `${CONSENT_MGT_V2}/purposes/${encodeURIComponent(purposeId)}/versions/${encodeURIComponent(versionId)}`,
    { method: 'DELETE' },
  )
}

export function fetchPurposeVersion(
  purposeId: string,
  versionId: string,
): Promise<PurposeVersionDetail> {
  return apiRequest<PurposeVersionDetail>(
    `${CONSENT_MGT_V2}/purposes/${encodeURIComponent(purposeId)}/versions/${encodeURIComponent(versionId)}`,
    { method: 'GET' },
  )
}
