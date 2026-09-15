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
  queryOptions,
  type UseMutationResult,
  type UseQueryResult,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import type {
  ComplaintListQueryParamsAPI,
  ComplaintCategoryAPI,
  ComplaintDetail,
  ComplaintPriorityAPI,
  ComplaintQueueStatsAPI,
  ComplaintRecord,
  ComplaintStatus,
} from '../../../types/complaint'
import {
  createMyComplaint,
  getManagedComplaint,
  getManagedComplaintQueueStats,
  getManagedComplaintTimeline,
  getMyComplaint,
  getMyComplaintTimeline,
  listComplaintCategories,
  listManagedComplaints,
  listMyComplaints,
  sendManagedComplaintMessage,
  sendMyComplaintMessage,
  uploadManagedComplaintAttachments,
  uploadMyComplaintAttachments,
} from '../api/complaintsApi'
import { buildComplaintDetail, buildComplaintRecord } from '../utils/complaintMapper'

interface ComplaintListParams {
  status?: ComplaintStatus
  priority?: ComplaintPriorityAPI
  search?: string
  limit?: number
  offset?: number
}

interface ComplaintListResult {
  rows: ComplaintRecord[]
  total: number
}

function toListQueryParams(params: ComplaintListParams): ComplaintListQueryParamsAPI {
  return {
    status: params.status,
    priority: params.priority,
    search: params.search,
    limit: params.limit ?? 100,
    offset: params.offset ?? 0,
  }
}

function myComplaintListQueryOptions(params: ComplaintListParams) {
  const apiParams = toListQueryParams(params)

  return queryOptions({
    queryKey: ['complaints', 'me', apiParams],
    queryFn: async (): Promise<ComplaintListResult> => {
      const response = await listMyComplaints(apiParams)

      return { rows: response.data.map(buildComplaintRecord), total: response.metadata.total }
    },
    placeholderData: keepPreviousData,
  })
}

export function useMyComplaintListQuery(
  params: ComplaintListParams,
): UseQueryResult<ComplaintListResult> {
  return useQuery(myComplaintListQueryOptions(params))
}

function managedComplaintListQueryOptions(params: ComplaintListParams) {
  const apiParams = toListQueryParams(params)

  return queryOptions({
    queryKey: ['complaints', 'managed', apiParams],
    queryFn: async (): Promise<ComplaintListResult> => {
      const response = await listManagedComplaints(apiParams)

      return { rows: response.data.map(buildComplaintRecord), total: response.metadata.total }
    },
    placeholderData: keepPreviousData,
  })
}

export function useManagedComplaintListQuery(
  params: ComplaintListParams,
): UseQueryResult<ComplaintListResult> {
  return useQuery(managedComplaintListQueryOptions(params))
}

export function useManagedComplaintQueueStatsQuery(): UseQueryResult<ComplaintQueueStatsAPI> {
  return useQuery({
    queryKey: ['complaints', 'managed', 'stats'],
    queryFn: getManagedComplaintQueueStats,
  })
}

export function useMyComplaintDetailQuery(id: string | undefined): UseQueryResult<ComplaintDetail> {
  return useQuery<ComplaintDetail>({
    queryKey: ['complaint', 'me', id],
    queryFn: async (): Promise<ComplaintDetail> => {
      const [record, timeline] = await Promise.all([
        getMyComplaint(String(id)),
        getMyComplaintTimeline(String(id)),
      ])

      return buildComplaintDetail(record, timeline.data)
    },
    enabled: Boolean(id),
  })
}

export function useManagedComplaintDetailQuery(
  id: string | undefined,
): UseQueryResult<ComplaintDetail> {
  return useQuery<ComplaintDetail>({
    queryKey: ['complaint', 'managed', id],
    queryFn: async (): Promise<ComplaintDetail> => {
      const [record, timeline] = await Promise.all([
        getManagedComplaint(String(id)),
        getManagedComplaintTimeline(String(id)),
      ])

      return buildComplaintDetail(record, timeline.data)
    },
    enabled: Boolean(id),
  })
}

export function useComplaintCategoriesQuery(): UseQueryResult<ComplaintCategoryAPI[]> {
  return useQuery({
    queryKey: ['complaints', 'categories'],
    queryFn: async (): Promise<ComplaintCategoryAPI[]> => {
      const response = await listComplaintCategories()

      return response.data.map((entry) => entry.category)
    },
  })
}

interface CreateComplaintVariables {
  category: ComplaintCategoryAPI
  description: string
  files: File[]
}

interface CreateComplaintResult {
  id: string
  referenceId: string
  attachmentUploadFailed: boolean
}

export function useCreateComplaintMutation(): UseMutationResult<
  CreateComplaintResult,
  Error,
  CreateComplaintVariables
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({
      category,
      description,
      files,
    }: CreateComplaintVariables): Promise<CreateComplaintResult> => {
      const created = await createMyComplaint({
        subjectCategory: category,
        description,
      })

      // The complaint is already filed at this point. A rejected attachment upload (bad
      // file type, oversize, etc.) must not be reported as if the whole submission failed —
      // that would leave the user unaware their complaint exists and needs a retry.
      let attachmentUploadFailed = false
      if (files.length > 0) {
        try {
          await uploadMyComplaintAttachments(created.id, files)
        } catch {
          attachmentUploadFailed = true
        }
      }

      return { id: created.id, referenceId: created.referenceId, attachmentUploadFailed }
    },
    onSuccess: async (): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['complaints', 'me'] })
    },
  })
}

interface SendMyComplaintMessageVariables {
  complaintId: string
  message: string
  files: File[]
  toStatus?: ComplaintStatus
}

export function useSendMyComplaintMessageMutation(): UseMutationResult<
  void,
  Error,
  SendMyComplaintMessageVariables
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({
      complaintId,
      message,
      files,
      toStatus,
    }: SendMyComplaintMessageVariables): Promise<void> => {
      // Attachments are complaint-level resources (see complaint-server-API.yaml), not bound to
      // this specific comment - the comment itself is only needed to detect toStatus below.
      await sendMyComplaintMessage(complaintId, {
        message,
        toStatus,
      })

      if (files.length > 0) {
        await uploadMyComplaintAttachments(complaintId, files)
      }
    },
    onSuccess: async (_data, variables): Promise<void> => {
      await queryClient.invalidateQueries({ queryKey: ['complaint', 'me', variables.complaintId] })
      if (variables.toStatus) {
        await queryClient.invalidateQueries({ queryKey: ['complaints', 'me'] })
      }
    },
  })
}

interface SendManagedComplaintMessageVariables {
  complaintId: string
  message: string
  isPublic: boolean
  files: File[]
  toStatus?: ComplaintStatus
}

export function useSendManagedComplaintMessageMutation(): UseMutationResult<
  void,
  Error,
  SendManagedComplaintMessageVariables
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({
      complaintId,
      message,
      isPublic,
      files,
      toStatus,
    }: SendManagedComplaintMessageVariables): Promise<void> => {
      // Attachments are complaint-level resources (see complaint-server-API.yaml), not bound to
      // this specific comment; mirror the comment's isPublic so internal evidence stays internal.
      await sendManagedComplaintMessage(complaintId, {
        message,
        isPublic,
        toStatus,
      })

      if (files.length > 0) {
        await uploadManagedComplaintAttachments(complaintId, files, isPublic)
      }
    },
    onSuccess: async (_data, variables): Promise<void> => {
      await queryClient.invalidateQueries({
        queryKey: ['complaint', 'managed', variables.complaintId],
      })
      if (variables.toStatus) {
        await queryClient.invalidateQueries({ queryKey: ['complaints', 'managed'] })
      }
    },
  })
}
