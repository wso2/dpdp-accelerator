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

import { test, expect, loginAsConsentAdmin } from '../../fixtures/auth.fixtures'
import { ComplaintQueuePage } from '../../pages/ComplaintQueuePage'
import { moveComplaintToStatus, seedComplaint } from '../../utils/complaintSetup'

/**
 * Narrowing the officer's org-wide queue - ComplaintQueueFilters.tsx offers a status filter, a
 * priority filter, and a free-text search box, unlike the Data Principal's list (status filter
 * only, see 05.03-data-principal-searching-complaints.spec.ts).
 *
 * The search box (ComplaintQueuePage.tsx's `rows` memo) filters client-side over whatever page
 * the server already returned for the current status/priority filters and pagination, not via a
 * fresh server query - so every test below sets rowsPerPage to its max (25) first, to maximize the
 * odds this test's own freshly-created complaint is actually present in the page being searched.
 */
test.describe('Complaint Officer searching/filtering the queue (UI)', () => {
  test('05.06.01 - Filtering by status shows a matching complaint and hides a non-matching one', async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const openComplaint = await seedComplaint(userComplaintApi, 'OTHER', 'queue-filter-open')
    const inProgressComplaint = await seedComplaint(userComplaintApi, 'OTHER', 'queue-filter-in-progress')
    await moveComplaintToStatus(officerComplaintApi, inProgressComplaint.id, 'IN_PROGRESS')

    const officerPage = await loginAsConsentAdmin(browser)
    const queuePage = new ComplaintQueuePage(officerPage)
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)
    await queuePage.filterByStatus('Open')

    await expect(queuePage.rowByReferenceId(openComplaint.referenceId)).toBeVisible()
    await expect(queuePage.rowByReferenceId(inProgressComplaint.referenceId)).not.toBeVisible()
    await officerPage.context().close()
  })

  test('05.06.02 - Filtering by priority shows a matching complaint and hides a non-matching one', async ({
    browser,
    userComplaintApi,
  }) => {
    const criticalComplaint = await seedComplaint(userComplaintApi, 'DATA_BREACH', 'queue-filter-critical')
    const lowComplaint = await seedComplaint(userComplaintApi, 'OTHER', 'queue-filter-low')

    const officerPage = await loginAsConsentAdmin(browser)
    const queuePage = new ComplaintQueuePage(officerPage)
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)
    await queuePage.filterByPriority('Critical')

    await expect(queuePage.rowByReferenceId(criticalComplaint.referenceId)).toBeVisible()
    await expect(queuePage.rowByReferenceId(lowComplaint.referenceId)).not.toBeVisible()
    await officerPage.context().close()
  })

  test('05.06.03 - Filtering by "Resolved" status narrows the queue to resolved complaints', async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    // Two complaints so the filter has something to exclude: 05.05.03 covers both being visible
    // under "All", and this asserts the OPEN one drops out once "Resolved" is selected. The
    // status filter is applied server-side, so this exercises the query, not a client-side memo.
    const resolved = await seedComplaint(userComplaintApi, 'OTHER', 'queue-filter-resolved')
    await moveComplaintToStatus(officerComplaintApi, resolved.id, 'IN_PROGRESS')
    await moveComplaintToStatus(officerComplaintApi, resolved.id, 'RESOLVED', 'Resolved for this test.')
    const stillOpen = await seedComplaint(userComplaintApi, 'OTHER', 'queue-filter-open')

    const officerPage = await loginAsConsentAdmin(browser)
    const queuePage = new ComplaintQueuePage(officerPage)
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)

    await expect(queuePage.rowByReferenceId(resolved.referenceId)).toBeVisible()
    await expect(queuePage.rowByReferenceId(stillOpen.referenceId)).toBeVisible()

    await queuePage.filterByStatus('Resolved')
    await expect(queuePage.rowByReferenceId(resolved.referenceId)).toBeVisible()
    await expect(queuePage.rowByReferenceId(stillOpen.referenceId)).not.toBeVisible()
    await officerPage.context().close()
  })

  test('05.06.04 - Searching by reference id narrows the queue to that complaint', async ({
    browser,
    userComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'queue-search-reference')
    const officerPage = await loginAsConsentAdmin(browser)
    const queuePage = new ComplaintQueuePage(officerPage)
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)

    await queuePage.searchByReferenceOrName(seeded.referenceId)
    await expect(queuePage.rowByReferenceId(seeded.referenceId)).toBeVisible()
    await expect(queuePage.rows).toHaveCount(1)
    await officerPage.context().close()
  })

  test("05.06.05 - Searching by the Data Principal's name narrows the queue to that principal's complaints", async ({
    browser,
    userComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'queue-search-name')
    const record = await (await userComplaintApi.getMyComplaint(seeded.id)).json()
    const dataPrincipalName = (record.userName as string | null) ?? (record.userId as string)

    const officerPage = await loginAsConsentAdmin(browser)
    const queuePage = new ComplaintQueuePage(officerPage)
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)

    await queuePage.searchByReferenceOrName(dataPrincipalName)
    // Every visible row belongs to this same Data Principal (other complaints of theirs from
    // other tests may also match - not asserting an exact count) - not just that ours is present.
    await expect(queuePage.rowByReferenceId(seeded.referenceId)).toBeVisible()
    const rowCount = await queuePage.rows.count()
    for (let index = 0; index < rowCount; index += 1) {
      await expect(queuePage.rows.nth(index)).toContainText(dataPrincipalName)
    }
    await officerPage.context().close()
  })

})
