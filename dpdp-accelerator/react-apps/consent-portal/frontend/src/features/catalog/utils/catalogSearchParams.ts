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

import type { CursorPageParams } from '../../../types/catalog'
import { CATALOG_ROWS_PER_PAGE_OPTIONS, DEFAULT_CATALOG_ROWS_PER_PAGE } from '../constants'

/** Reads the cursor page state of a catalog listing out of the URL. */
export function getCursorPageParams(searchParams: URLSearchParams): CursorPageParams {
  const limit = Number(searchParams.get('limit') ?? String(DEFAULT_CATALOG_ROWS_PER_PAGE))

  return {
    limit: CATALOG_ROWS_PER_PAGE_OPTIONS.includes(
      limit as (typeof CATALOG_ROWS_PER_PAGE_OPTIONS)[number],
    )
      ? limit
      : DEFAULT_CATALOG_ROWS_PER_PAGE,
    after: searchParams.get('after') ?? undefined,
    before: searchParams.get('before') ?? undefined,
  }
}

export function toCatalogSearchParams(params: CursorPageParams): URLSearchParams {
  const searchParams = new URLSearchParams()

  if (params.limit !== DEFAULT_CATALOG_ROWS_PER_PAGE) {
    searchParams.set('limit', String(params.limit))
  }
  if (params.after) {
    searchParams.set('after', params.after)
  }
  if (params.before) {
    searchParams.set('before', params.before)
  }

  return searchParams
}
