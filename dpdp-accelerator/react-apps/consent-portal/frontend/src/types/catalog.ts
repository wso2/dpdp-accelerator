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

export type ElementType = 'basic' | 'json' | 'xml'

export interface PaginationMetadata {
  total: number
  offset: number
  count: number
  limit: number
}

export interface ElementVersion {
  elementId: string
  name: string
  namespace: string
  version: string
  type: ElementType
  displayName?: string
  description?: string
  schema?: string
  properties?: Record<string, string>
  createdTime: number
}

export type ElementSummary = ElementVersion

export interface ElementVersionItem {
  version: string
  displayName?: string
  description?: string
  schema?: string
  properties?: Record<string, string>
  createdTime: number
}

export interface ElementVersionList {
  elementId: string
  name: string
  namespace: string
  type: ElementType
  versions: ElementVersionItem[]
}

export interface ElementListResponse {
  data: ElementSummary[]
  metadata: PaginationMetadata
}

export interface ElementFilters {
  name: string
  namespace: string
  type: ElementType | 'All'
  version: string
}

export interface ElementCreateRequest {
  name: string
  namespace?: string
  type: ElementType
  displayName?: string
  description?: string
  schema?: string
  properties?: Record<string, string>
}

export interface ElementVersionCreateRequest {
  displayName?: string
  description?: string
  schema?: string
  properties?: Record<string, string>
}

export interface ElementBulkCreateResult {
  index?: number
  status: 'SUCCESS' | 'FAILED'
  data?: ElementVersion
  element?: ElementVersion
  error?:
    | string
    | {
        code?: string
        message?: string
        description?: string
      }
}

export interface ElementBulkCreateResponse {
  metadata?: {
    traceId: string
    total: number
    succeeded: number
    failed: number
  }
  results: ElementBulkCreateResult[]
}

export interface PurposeElement {
  elementId: string
  name: string
  namespace: string
  version: string
  displayName?: string | null
  description?: string | null
  mandatory: boolean
}

export interface PurposeVersion {
  purposeId: string
  name: string
  groupId: string
  version: string
  displayName?: string
  description?: string
  properties?: Record<string, string>
  elements: PurposeElement[]
  createdTime: number
}

export interface PurposeSummary {
  purposeId: string
  name: string
  groupId: string
  version: string
  displayName?: string
  description?: string
  createdTime: number
  elements?: PurposeElement[]
  properties?: Record<string, string>
}

export interface PurposeVersionItem {
  version: string
  displayName?: string
  description?: string
  properties?: Record<string, string>
  elements: PurposeElement[]
  createdTime: number
}

export interface PurposeVersionList {
  purposeId: string
  name: string
  groupId: string
  versions: PurposeVersionItem[]
}

export interface PurposeListResponse {
  data: PurposeSummary[]
  metadata: PaginationMetadata
}

export interface PurposeFilters {
  purposeName: string
  elementName: string
  elementNamespace: string
  elementVersion: string
  groupIds: string
}

export interface PurposeElementRequest {
  name: string
  namespace?: string
  version?: string
  mandatory: boolean
}

export interface PurposeCreateRequest {
  name: string
  displayName?: string
  description?: string
  properties?: Record<string, string>
  elements: PurposeElementRequest[]
}

export interface PurposeVersionCreateRequest {
  displayName?: string
  description?: string
  properties?: Record<string, string>
  elements: PurposeElementRequest[]
}
