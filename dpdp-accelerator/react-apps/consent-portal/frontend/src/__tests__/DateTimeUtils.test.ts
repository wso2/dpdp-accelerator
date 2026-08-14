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
import {
  formatEpochTimestamp,
  formatIsoDateTime,
  toEndOfDayEpochMilliseconds,
  toEpochMilliseconds,
  toStartOfDayEpochMilliseconds,
} from '../utils/dateTime'

const DATE_TIME_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
  timeZone: 'UTC',
}

describe('toEpochMilliseconds', () => {
  it('returns null for undefined, null, and non-finite values', () => {
    expect(toEpochMilliseconds(undefined)).toBeNull()
    expect(toEpochMilliseconds(null)).toBeNull()
    expect(toEpochMilliseconds(Number.NaN)).toBeNull()
    expect(toEpochMilliseconds(Number.POSITIVE_INFINITY)).toBeNull()
  })

  it('converts epoch seconds to milliseconds', () => {
    expect(toEpochMilliseconds(1710000000)).toBe(1710000000000)
  })

  it('leaves epoch milliseconds unchanged', () => {
    expect(toEpochMilliseconds(1710000000000)).toBe(1710000000000)
  })

  it('documents the seconds-to-milliseconds cutoff boundary', () => {
    expect(toEpochMilliseconds(99_999_999_999)).toBe(99_999_999_999_000)
    expect(toEpochMilliseconds(100_000_000_001)).toBe(100_000_000_001)
  })
})

describe('formatEpochTimestamp', () => {
  it('returns placeholder for undefined, null, and non-finite values', () => {
    expect(formatEpochTimestamp(undefined)).toBe('-')
    expect(formatEpochTimestamp(null)).toBe('-')
    expect(formatEpochTimestamp(Number.NaN)).toBe('-')
    expect(formatEpochTimestamp(Number.POSITIVE_INFINITY)).toBe('-')
  })

  it('formats epoch seconds using locale and options', () => {
    const epochInSeconds = 1710000000
    const expected = new Date(epochInSeconds * 1000).toLocaleString(
      'en-US',
      DATE_TIME_FORMAT_OPTIONS,
    )

    expect(formatEpochTimestamp(epochInSeconds, DATE_TIME_FORMAT_OPTIONS, 'en-US')).toBe(expected)
  })

  it('formats epoch milliseconds using locale and options', () => {
    const epochInMilliseconds = 1710000000000
    const expected = new Date(epochInMilliseconds).toLocaleString('en-US', DATE_TIME_FORMAT_OPTIONS)

    expect(formatEpochTimestamp(epochInMilliseconds, DATE_TIME_FORMAT_OPTIONS, 'en-US')).toBe(
      expected,
    )
  })
})

describe('toStartOfDayEpochMilliseconds', () => {
  it('returns undefined for empty or invalid values', () => {
    expect(toStartOfDayEpochMilliseconds('')).toBeUndefined()
    expect(toStartOfDayEpochMilliseconds('not-a-date')).toBeUndefined()
  })

  it('returns epoch milliseconds for the start of the selected day', () => {
    const dateText = '2026-05-15'
    const expected = Date.UTC(2026, 4, 15, 0, 0, 0, 0)

    expect(toStartOfDayEpochMilliseconds(dateText)).toBe(expected)
  })
})

describe('toEndOfDayEpochMilliseconds', () => {
  it('returns undefined for empty or invalid values', () => {
    expect(toEndOfDayEpochMilliseconds('')).toBeUndefined()
    expect(toEndOfDayEpochMilliseconds('not-a-date')).toBeUndefined()
  })

  it('returns epoch milliseconds for the end of the selected day', () => {
    const dateText = '2026-05-15'
    const expected = Date.UTC(2026, 4, 15, 23, 59, 59, 999)

    expect(toEndOfDayEpochMilliseconds(dateText)).toBe(expected)
  })
})

describe('formatIsoDateTime', () => {
  it('returns placeholder for empty or invalid values', () => {
    expect(formatIsoDateTime(undefined)).toBe('-')
    expect(formatIsoDateTime(null)).toBe('-')
    expect(formatIsoDateTime('')).toBe('-')
    expect(formatIsoDateTime('not-a-date')).toBe('-')
  })

  it('formats valid ISO date-time using locale and options', () => {
    const isoDateTime = '2026-03-02T15:29:57Z'
    const expected = new Date(isoDateTime).toLocaleString('en-US', DATE_TIME_FORMAT_OPTIONS)

    expect(formatIsoDateTime(isoDateTime, DATE_TIME_FORMAT_OPTIONS, 'en-US')).toBe(expected)
  })
})
