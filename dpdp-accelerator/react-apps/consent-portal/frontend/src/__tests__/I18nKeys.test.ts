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
import commonEn from '../../public/i18n/en/common.json'

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

  // i18next runs with skipOnVariables (its default), so a placeholder the call
  // site never passes is not blanked out - it is printed verbatim, and users see
  // "This grants {{nomineeName}} access to ...". The English default string next
  // to the call can disagree with the resource string silently, which is exactly
  // how that shipped once already.
  it('passes every placeholder the English resource string interpolates', () => {
    const flatten = (value: unknown, prefix = ''): [string, string][] => {
      if (!value || typeof value !== 'object' || Array.isArray(value)) {
        return []
      }

      return Object.entries(value as Record<string, unknown>).flatMap(([key, nested]) => {
        const path = prefix ? `${prefix}.${key}` : key
        return typeof nested === 'string'
          ? ([[path, nested]] as [string, string][])
          : flatten(nested, path)
      })
    }

    const resourceStrings = new Map(flatten(commonEn))
    // t('some.key', { a, b, defaultValue: '...' }) - the option object only.
    const interpolatedCallPattern = /\bt\(\s*'([^']+)'\s*,\s*\{([\s\S]{0,600}?)\}\s*\)/g
    const placeholderPattern = /\{\{\s*([A-Za-z_$][\w$]*)/g
    // Property names in the option object, including a trailing shorthand.
    const passedNamePattern = /(?:^|[{,])\s*([A-Za-z_$][\w$]*)\s*(?=[,:}]|$)/g

    const mismatches = Object.entries(sourceFiles).flatMap(([filePath, source]) =>
      Array.from(source.matchAll(interpolatedCallPattern)).flatMap(([, key, optionBody]) => {
        const resourceString = resourceStrings.get(key)

        if (!resourceString) {
          return []
        }

        const passed = new Set(
          Array.from(optionBody.matchAll(passedNamePattern), (match) => match[1]),
        )
        const missing = Array.from(
          new Set(Array.from(resourceString.matchAll(placeholderPattern), (match) => match[1])),
        ).filter((name) => !passed.has(name))

        return missing.length > 0 ? [{ filePath, key, missing }] : []
      }),
    )

    expect(mismatches).toEqual([])
  })
})
