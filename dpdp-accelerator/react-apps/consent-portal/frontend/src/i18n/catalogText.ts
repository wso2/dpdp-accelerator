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

import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'

/**
 * Purposes and elements are created by administrators at run time, so their
 * wording cannot live in `common.json` the way our own UI text does. Instead each
 * language carries a `catalog.json` keyed by the item's `name`, and this hook
 * resolves an item to the wording for the active language.
 *
 * English is deliberately absent from those files: the server already returns
 * English in `displayName` and `description`, so an untranslated item falls
 * back to it. `en/catalog.json` exists only as the work list translators copy.
 */

export type CatalogKind = 'purposes' | 'elements'

/** The fields every purpose and element carries on both the catalog and consent APIs. */
export interface CatalogItem {
  name: string
  version?: string
  displayName?: string | null
  description?: string | null
}

export interface CatalogTextResult {
  displayName: string
  description: string | undefined
}

interface CatalogEntry {
  displayName?: string
  description?: string
}

type CatalogBundle = Partial<Record<CatalogKind, Record<string, CatalogEntry>>>

/** Blank entries are what the sync script writes for anything not yet translated. */
function usable(value: string | undefined): string | undefined {
  return value?.trim() || undefined
}

export function useCatalogText(): (kind: CatalogKind, item: CatalogItem) => CatalogTextResult {
  // Subscribing to the catalog namespace is what re-renders call sites when the
  // language changes; the bundle itself is read directly below.
  const { i18n } = useTranslation('catalog')
  const { language } = i18n

  return useCallback(
    (kind: CatalogKind, item: CatalogItem): CatalogTextResult => {
      const bundle = i18n.getResourceBundle(language, 'catalog') as CatalogBundle | undefined
      const entries = bundle?.[kind]

      // A version-specific entry wins, so re-wording an item in a new version
      // cannot keep showing the previous version's translation. Without one the
      // shared entry applies, which is the normal case.
      const entry =
        (item.version ? entries?.[`${item.name}@${item.version}`] : undefined) ??
        entries?.[item.name]

      return {
        displayName: usable(entry?.displayName) ?? item.displayName ?? item.name,
        description: usable(entry?.description) ?? item.description ?? undefined,
      }
    },
    [i18n, language],
  )
}
