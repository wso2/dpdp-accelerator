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

import { keepPreviousData, type UseQueryResult, useQuery } from '@tanstack/react-query'
import type {
  CatalogElement,
  CursorPageParams,
  ElementListResponse,
  PurposeDetail,
  PurposeListResponse,
  PurposeVersionListResponse,
} from '../../../types/catalog'
import {
  fetchElement,
  fetchElements,
  fetchPurpose,
  fetchPurposes,
  fetchPurposeVersions,
} from '../api/catalogApi'

export const CATALOG_VERSIONS_PAGE_SIZE = 50

export function useElementsQuery(params: CursorPageParams): UseQueryResult<ElementListResponse> {
  return useQuery({
    queryKey: ['elements', params],
    queryFn: () => fetchElements(params),
    placeholderData: keepPreviousData,
  })
}

export function useElementQuery(elementId?: string): UseQueryResult<CatalogElement> {
  return useQuery({
    queryKey: ['element', elementId],
    queryFn: () => fetchElement(String(elementId)),
    enabled: Boolean(elementId),
  })
}

export function usePurposesQuery(params: CursorPageParams): UseQueryResult<PurposeListResponse> {
  return useQuery({
    queryKey: ['purposes', params],
    queryFn: () => fetchPurposes(params),
    placeholderData: keepPreviousData,
  })
}

export function usePurposeQuery(purposeId?: string): UseQueryResult<PurposeDetail> {
  return useQuery({
    queryKey: ['purpose', purposeId],
    queryFn: () => fetchPurpose(String(purposeId)),
    enabled: Boolean(purposeId),
  })
}

export function usePurposeVersionsQuery(
  purposeId?: string,
): UseQueryResult<PurposeVersionListResponse> {
  return useQuery({
    queryKey: ['purpose', purposeId, 'versions'],
    queryFn: () => fetchPurposeVersions(String(purposeId), { limit: CATALOG_VERSIONS_PAGE_SIZE }),
    enabled: Boolean(purposeId),
  })
}
