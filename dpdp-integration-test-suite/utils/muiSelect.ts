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
 * The portal's filter bars and dialogs use MUI's <Select>, which renders as a div with
 * role="combobox" (not a native <select>), so it must be opened and its menu item clicked
 * rather than driven with Playwright's selectOption. The open menu portals to <body>, outside
 * any dialog/section the trigger lives in, so the option is always looked up on the page itself.
 */
export async function selectMuiOption(
  page: Page,
  selectId: string,
  optionName: string,
  scope?: Locator,
): Promise<void> {
  const trigger = scope ? scope.locator(`#${selectId}`) : page.locator(`#${selectId}`)
  const option = page.getByRole('option', { name: optionName, exact: true })

  await expect(async () => {
    if (!(await option.isVisible())) {
      await trigger.click()
    }
    await option.click({ timeout: 2_000 })
  }).toPass({ intervals: [250, 500, 1_000], timeout: 10_000 })
}
