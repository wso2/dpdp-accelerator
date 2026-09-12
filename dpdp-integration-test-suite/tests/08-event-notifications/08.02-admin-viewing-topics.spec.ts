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

import { loginAsConsentAdmin, test, expect } from '../../fixtures/auth.fixtures'
import { TopicsPage } from '../../pages/TopicsPage'
import { seedActiveTopic } from '../../utils/eventNotificationSetup'

/**
 * Listing, searching, and filtering Event Notification topics (TopicsPage.tsx/TopicTable.tsx/
 * TopicFilters.tsx). The rows-per-page control offers [10, 20, 50] - see
 * pages/TopicsPage.ts's ROWS_PER_PAGE_OPTIONS.
 */
test.describe('Admin viewing and searching Topics', () => {
  test('08.02.01 - The Topics list renders and paginates', async ({
    browser,
    consentAdminEventApi,
  }) => {
    await seedActiveTopic(consentAdminEventApi, 'list-visible')
    const page = await loginAsConsentAdmin(browser)
    try {
      const topicsPage = new TopicsPage(page)
      await topicsPage.goto()

      await expect(topicsPage.table).toBeVisible()
      await expect(topicsPage.rows.first()).toBeVisible()
      await expect(topicsPage.previousPageButton).toBeDisabled()

      // 20 is a real rows-per-page option; the control offers [10, 20, 50] only.
      await topicsPage.setRowsPerPage(20)
      await expect(topicsPage.table).toBeVisible()
      await expect(topicsPage.rows.first()).toBeVisible()
    } finally {
      await page.context().close()
    }
  })

  test('08.02.02 - Searching by a partial topic name finds the matching row', async ({
    browser,
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'consent-status-changed')
    const uniqueSuffix = topic.name.split('-').slice(-2).join('-')

    const page = await loginAsConsentAdmin(browser)
    try {
      const topicsPage = new TopicsPage(page)
      await topicsPage.goto()
      await topicsPage.search(uniqueSuffix)

      await expect(topicsPage.rowByName(topic.name)).toBeVisible()
      // One assertion over a filtered locator, never a loop over `rows.all()`: .all() snapshots
      // whatever rows exist at that instant, and the row it hands back can be gone before the
      // assertion on it runs - the search's own refetch is still narrowing the table, and
      // rowByName above cannot gate that, since the sought row is present both before and after.
      // CI failed exactly this way, twice: "rows.nth(1) ... element(s) not found".
      await expect(topicsPage.rows.filter({ hasNotText: uniqueSuffix })).toHaveCount(0)
    } finally {
      await page.context().close()
    }
  })

})
