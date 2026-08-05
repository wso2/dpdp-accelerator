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

import { describe, expect, it } from 'vitest'
import commonEn from '../i18n/resources/en/common'

const sourceFiles = import.meta.glob<string>(
  ['../**/*.{ts,tsx}', '!../**/*.test.{ts,tsx}', '!../__tests__/**'],
  {
    eager: true,
    import: 'default',
    query: '?raw',
  },
)

// This intentionally checks only statically declared keys like t('app.title').
// Dynamic keys such as t(`consentRegistry.status.${status}`) need targeted tests.
const STATIC_TRANSLATION_KEY_PATTERN = /\bt\s*\(\s*(['"])([^'"`]+)\1/g

function flattenTranslationKeys(value: unknown, prefix = ''): string[] {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return []
  }

  return Object.entries(value as Record<string, unknown>).flatMap(([key, nestedValue]) => {
    const path = prefix ? `${prefix}.${key}` : key

    if (typeof nestedValue === 'string') {
      return [path]
    }

    return flattenTranslationKeys(nestedValue, path)
  })
}

function getStaticTranslationKeys(source: string): string[] {
  return Array.from(source.matchAll(STATIC_TRANSLATION_KEY_PATTERN), (match) => match[2])
}

describe('i18n resources', () => {
  it('defines all statically referenced translation keys in English resources', () => {
    const resourceKeys = new Set(flattenTranslationKeys(commonEn))
    const missingKeys = Object.entries(sourceFiles)
      .flatMap(([filePath, source]) =>
        getStaticTranslationKeys(source).map((translationKey) => ({ filePath, translationKey })),
      )
      .filter(({ translationKey }) => !resourceKeys.has(translationKey))

    expect(missingKeys).toEqual([])
  })
})
