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

import type { CursorLink } from '../types/catalog'

const CURSOR_PARAMETERS = ['after', 'before'] as const

/**
 * Reads the opaque cursor out of a pagination link.
 *
 * The Identity Server returns absolute upstream URLs in `links`, so only the
 * cursor query parameter is reusable by the portal.
 */
export function getCursorFromLinks(
  links: CursorLink[] | undefined,
  rel: 'next' | 'previous',
): string | undefined {
  const href = links?.find((link) => link.rel === rel)?.href

  if (!href) {
    return undefined
  }

  const queryStart = href.indexOf('?')

  if (queryStart === -1) {
    return undefined
  }

  const searchParams = new URLSearchParams(href.slice(queryStart + 1))

  return (
    CURSOR_PARAMETERS.map((parameter) => searchParams.get(parameter)).find(Boolean) ?? undefined
  )
}

export function getNextCursor(links: CursorLink[] | undefined): string | undefined {
  return getCursorFromLinks(links, 'next')
}

export function getPreviousCursor(links: CursorLink[] | undefined): string | undefined {
  return getCursorFromLinks(links, 'previous')
}
