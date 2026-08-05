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

/** Pagination link returned by the Identity Server catalog and consent APIs. */
export interface CursorLink {
  rel: 'next' | 'previous' | string
  href: string
}

/** Cursor page envelope shared by every administrative and catalog listing. */
export interface CursorPage {
  totalResults: number
  links: CursorLink[]
}

export interface CursorPageParams {
  limit: number
  after?: string
  before?: string
}

export interface PurposeVersionRef {
  id: string
  version: string
}

export interface PurposeSummary {
  id: string
  name: string
  description?: string
  type: string
  latestVersion?: PurposeVersionRef
  tenantDomain?: string
}

/** Purpose definition elements DO carry a `mandatory` flag. */
export interface PurposeElement {
  id: string
  name: string
  displayName?: string
  description?: string
  mandatory: boolean
}

export interface PurposeDetail extends PurposeSummary {
  elements: PurposeElement[]
  properties?: Record<string, string>
}

export interface PurposeVersionSummary {
  id: string
  version: string
  description?: string
}

export interface PurposeListResponse extends CursorPage {
  Purposes: PurposeSummary[]
}

export interface PurposeVersionListResponse extends CursorPage {
  Versions: PurposeVersionSummary[]
}

export interface CatalogElement {
  id: string
  name: string
  displayName?: string
  description?: string
  tenantDomain?: string
}

export interface ElementListResponse extends CursorPage {
  Elements: CatalogElement[]
}
