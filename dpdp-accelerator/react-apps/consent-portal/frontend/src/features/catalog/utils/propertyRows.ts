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

export interface PropertyRow {
  key: string
  value: string
}

interface PropertyRowIssues {
  duplicateKey: boolean
  orphanedValue: boolean
}

export const EMPTY_PROPERTY_ROW: PropertyRow = { key: '', value: '' }

/** Converts editor rows into the map the Identity Server expects, dropping unkeyed rows. */
export function toPropertiesRecord(rows: PropertyRow[]): Record<string, string> | undefined {
  const entries = rows
    .map((row) => [row.key.trim(), row.value] as const)
    .filter(([key]) => key.length > 0)

  return entries.length > 0 ? Object.fromEntries(entries) : undefined
}

/** Converts a stored properties map into editable rows, e.g. to pre-populate a form. */
export function fromPropertiesRecord(
  properties: Record<string, string> | undefined,
): PropertyRow[] {
  return Object.entries(properties ?? {}).map(([key, value]) => ({ key, value }))
}

/** Flags rows that would otherwise be silently dropped or overwritten on submit. */
export function getPropertyRowIssues(rows: PropertyRow[]): PropertyRowIssues[] {
  return rows.map((row) => {
    const trimmedKey = row.key.trim()
    const duplicateKey =
      trimmedKey.length > 0 && rows.filter((other) => other.key.trim() === trimmedKey).length > 1
    const orphanedValue = trimmedKey.length === 0 && row.value.trim().length > 0
    return { duplicateKey, orphanedValue }
  })
}

export function hasPropertyIssues(rows: PropertyRow[]): boolean {
  return getPropertyRowIssues(rows).some((issue) => issue.duplicateKey || issue.orphanedValue)
}
