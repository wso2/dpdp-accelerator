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

import { expect, type Locator, type Page } from '@playwright/test'

/**
 * Fills a filter's text input and submits it, then waits for the URL to actually carry the value.
 *
 * Every filtered list page (SubscriptionsPage.tsx, TopicsPage.tsx, EventsPage.tsx, both consent
 * registries) renders its filter bar with `key={searchParams.toString()}`, and the filter component
 * seeds its own input from `useState(filters.<field>)`. So each committed submit REMOUNTS the whole
 * filter bar and resets every input back to whatever the URL says. A `fill()` that lands in the
 * window between a previous submit and its remount is silently discarded, and the submit that
 * follows sends the OLD term - the list then looks filtered, just by the wrong value.
 *
 * Asserting on the resulting rows does not close that window: the row a test is looking for is
 * usually in the unfiltered list too, so the assertion passes instantly against stale content. The
 * URL is the only signal that the submit really committed - and because the remount key IS the
 * query string, it is also the only way to know the remount is done.
 *
 * So the whole interaction retries as a unit: fill, re-read the input to catch a remount that wiped
 * it, submit, and require the URL to carry the value. Re-filling is harmless - a committed submit
 * leaves the input holding exactly this value anyway. Always route a fill-then-submit filter
 * interaction through this.
 */
/**
 * Waits until the page URL's `param` query parameter equals `expectedValue` (treating an omitted
 * parameter as `''`), providing a deterministic commit checkpoint after a filter change.
 */
export async function waitForFilterQueryParam(
  page: Page,
  param: string,
  expectedValue: string,
  timeoutMs = 3_000,
): Promise<void> {
  await page.waitForURL(
    (url) => (new URL(url.toString()).searchParams.get(param) ?? '') === expectedValue,
    { timeout: timeoutMs },
  )
}

export async function submitFilterValue(
  page: Page,
  input: Locator,
  submit: () => Promise<void>,
  param: string,
  value: string,
): Promise<void> {
  const expected = value.trim()

  await expect(async () => {
    await input.fill(value)
    expect(await input.inputValue(), 'a remount reset the filter input before it was submitted')
      .toBe(value)
    await submit()
    await waitForFilterQueryParam(page, param, expected)
  }).toPass({ intervals: [250, 500, 1_000], timeout: 20_000 })
}
