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
import {
  createElement,
  createElementVersion,
  createPurpose,
  createPurposeVersion,
  deleteElementVersion,
  deletePurposeVersion,
  fetchElement,
  fetchElements,
  fetchElementVersions,
  fetchPurpose,
  fetchPurposes,
  fetchPurposeVersions,
} from '../api/catalogApi'

interface CreatePurposeVariables {
  payload: PurposeCreateRequest
  groupId?: string
}

interface CreateElementVersionVariables {
  elementId: string
  payload: ElementVersionCreateRequest
}

interface CreatePurposeVersionVariables {
  purposeId: string
  payload: PurposeVersionCreateRequest
}

interface DeleteVersionVariables {
  id: string
  version: string
}

export function useElementsQuery(
  filters: ElementFilters,
  page: number,
  rowsPerPage: number,
): UseQueryResult<ElementListResponse> {
  return useQuery({
    queryKey: ['elements', filters, page, rowsPerPage],
    queryFn: () => fetchElements(filters, rowsPerPage, page * rowsPerPage),
    placeholderData: keepPreviousData,
  })
}

export function useElementOptionsQuery(enabled = true): UseQueryResult<ElementListResponse> {
  return useQuery({
    queryKey: ['elements', 'options'],
    queryFn: () => fetchElements({ name: '', namespace: '', type: 'All', version: '' }, 100, 0),
    enabled,
  })
}

export function useElementQuery(elementId?: string): UseQueryResult<ElementVersion> {
  return useQuery({
    queryKey: ['element', elementId],
    queryFn: () => fetchElement(String(elementId)),
    enabled: Boolean(elementId),
  })
}

export function useElementVersionsQuery(elementId?: string): UseQueryResult<ElementVersionList> {
  return useQuery({
    queryKey: ['element', elementId, 'versions'],
    queryFn: () => fetchElementVersions(String(elementId)),
    enabled: Boolean(elementId),
  })
}

export function useCreateElementMutation(): UseMutationResult<
  ElementVersion,
  Error,
  ElementCreateRequest
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: createElement,
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ['elements'] }),
  })
}

export function useCreateElementVersionMutation(): UseMutationResult<
  ElementVersion,
  Error,
  CreateElementVersionVariables
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ elementId, payload }) => createElementVersion(elementId, payload),
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ['elements'] })
      await queryClient.invalidateQueries({ queryKey: ['element', variables.elementId] })
    },
  })
}

export function useDeleteElementVersionMutation(): UseMutationResult<
  void,
  Error,
  DeleteVersionVariables
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, version }) => deleteElementVersion(id, version),
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ['elements'] })
      await queryClient.invalidateQueries({ queryKey: ['element', variables.id] })
    },
  })
}

export function usePurposesQuery(
  filters: PurposeFilters,
  page: number,
  rowsPerPage: number,
): UseQueryResult<PurposeListResponse> {
  return useQuery({
    queryKey: ['purposes', filters, page, rowsPerPage],
    queryFn: () => fetchPurposes(filters, rowsPerPage, page * rowsPerPage),
    placeholderData: keepPreviousData,
  })
}

export function usePurposeQuery(purposeId?: string): UseQueryResult<PurposeVersion> {
  return useQuery({
    queryKey: ['purpose', purposeId],
    queryFn: () => fetchPurpose(String(purposeId)),
    enabled: Boolean(purposeId),
  })
}

export function usePurposeVersionsQuery(purposeId?: string): UseQueryResult<PurposeVersionList> {
  return useQuery({
    queryKey: ['purpose', purposeId, 'versions'],
    queryFn: () => fetchPurposeVersions(String(purposeId)),
    enabled: Boolean(purposeId),
  })
}

export function useCreatePurposeMutation(): UseMutationResult<
  PurposeVersion,
  Error,
  CreatePurposeVariables
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ payload, groupId }) => createPurpose(payload, groupId),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ['purposes'] }),
  })
}

export function useCreatePurposeVersionMutation(): UseMutationResult<
  PurposeVersion,
  Error,
  CreatePurposeVersionVariables
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ purposeId, payload }) => createPurposeVersion(purposeId, payload),
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ['purposes'] })
      await queryClient.invalidateQueries({ queryKey: ['purpose', variables.purposeId] })
    },
  })
}

export function useDeletePurposeVersionMutation(): UseMutationResult<
  void,
  Error,
  DeleteVersionVariables
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, version }) => deletePurposeVersion(id, version),
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ['purposes'] })
      await queryClient.invalidateQueries({ queryKey: ['purpose', variables.id] })
    },
  })
}
