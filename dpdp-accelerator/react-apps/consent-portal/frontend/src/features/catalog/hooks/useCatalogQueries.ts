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

import {
  keepPreviousData,
  type UseMutationResult,
  type UseQueryResult,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import type {
  CatalogElement,
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
} from '../../../types/catalog'
import {
  createElement,
  createPurpose,
  createPurposeVersion,
  deleteElement,
  deletePurpose,
  deletePurposeVersion,
  fetchElement,
  fetchElements,
  fetchPurpose,
  fetchPurposes,
  fetchPurposeVersions,
  setLatestPurposeVersion,
} from '../api/catalogApi'

export const CATALOG_VERSIONS_PAGE_SIZE = 50

export function useElementsQuery(
  params: ElementListQueryParams,
): UseQueryResult<ElementListResponse> {
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

export function useCreateElementMutation(): UseMutationResult<CatalogElement, Error, ElementInput> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: ElementInput) => createElement(payload),
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['elements'] })
    },
  })
}

export function useDeleteElementMutation(): UseMutationResult<void, Error, string> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (elementId: string) => deleteElement(elementId),
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['elements'] })
    },
  })
}

export function usePurposesQuery(
  params: PurposeListQueryParams,
): UseQueryResult<PurposeListResponse> {
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

export function useCreatePurposeMutation(): UseMutationResult<PurposeDetail, Error, PurposeInput> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: PurposeInput) => createPurpose(payload),
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['purposes'] })
    },
  })
}

export function useDeletePurposeMutation(): UseMutationResult<void, Error, string> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (purposeId: string) => deletePurpose(purposeId),
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['purposes'] })
    },
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

interface CreatePurposeVersionArgs {
  purposeId: string
  payload: PurposeVersionInput
}

export function useCreatePurposeVersionMutation(): UseMutationResult<
  PurposeVersionSummary,
  Error,
  CreatePurposeVersionArgs
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ purposeId, payload }: CreatePurposeVersionArgs) =>
      createPurposeVersion(purposeId, payload),
    onSuccess: async (_data, { purposeId }): Promise<void> => {
      // Prefix match invalidates both the detail and versions queries.
      await queryClient.invalidateQueries({ queryKey: ['purpose', purposeId] })
      await queryClient.invalidateQueries({ queryKey: ['purposes'] })
    },
  })
}

interface SetLatestPurposeVersionArgs {
  purposeId: string
  versionId: string
}

export function useSetLatestPurposeVersionMutation(): UseMutationResult<
  void,
  Error,
  SetLatestPurposeVersionArgs
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ purposeId, versionId }: SetLatestPurposeVersionArgs) =>
      setLatestPurposeVersion(purposeId, versionId),
    onSuccess: async (_data, { purposeId }): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['purpose', purposeId] })
      await queryClient.invalidateQueries({ queryKey: ['purposes'] })
    },
  })
}

interface DeletePurposeVersionArgs {
  purposeId: string
  versionId: string
}

export function useDeletePurposeVersionMutation(): UseMutationResult<
  void,
  Error,
  DeletePurposeVersionArgs
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ purposeId, versionId }: DeletePurposeVersionArgs) =>
      deletePurposeVersion(purposeId, versionId),
    onSuccess: async (_data, { purposeId }): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['purpose', purposeId] })
      await queryClient.invalidateQueries({ queryKey: ['purposes'] })
    },
  })
}
