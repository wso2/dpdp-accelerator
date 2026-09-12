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
 * only, see 07.03-data-principal-searching-complaints.spec.ts).
 *
 * The search box (ComplaintQueuePage.tsx's `rows` memo) filters client-side over whatever page
 * the server already returned for the current status/priority filters and pagination, not via a
 * fresh server query - so every test below sets rowsPerPage to its max (25) first, to maximize the
 * odds this test's own freshly-created complaint is actually present in the page being searched.
 */
test.describe('Complaint Officer searching/filtering the queue (UI)', () => {
  test('07.06.01 - Filtering by status shows a matching complaint and hides a non-matching one', async ({
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

  test('07.06.02 - Filtering by priority shows a matching complaint and hides a non-matching one', async ({
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

  test('07.06.03 - Explicitly filtering by "Resolved" status reveals an otherwise-hidden resolved complaint', async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'queue-filter-resolved')
    await moveComplaintToStatus(officerComplaintApi, seeded.id, 'IN_PROGRESS')
    await moveComplaintToStatus(officerComplaintApi, seeded.id, 'RESOLVED', 'Resolved for this test.')

    const officerPage = await loginAsConsentAdmin(browser)
    const queuePage = new ComplaintQueuePage(officerPage)
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)

    // Complements 07.05.02 (resolved complaints hidden by default) - the same status filter that
    // hides them by default is what surfaces them again once selected explicitly.
    await expect(queuePage.rowByReferenceId(seeded.referenceId)).not.toBeVisible()
    await queuePage.filterByStatus('Resolved')
    await expect(queuePage.rowByReferenceId(seeded.referenceId)).toBeVisible()
    await officerPage.context().close()
  })

  test('07.06.04 - Searching by reference id narrows the queue to that complaint', async ({
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
    // Every surviving row carries the searched reference id - asserted as "no row lacks it"
    // rather than "exactly one row", which would be a count assertion on a shared list.
    await expect(queuePage.rows.filter({ hasNotText: seeded.referenceId })).toHaveCount(0)
    await officerPage.context().close()
  })

  test("07.06.05 - Searching by the Data Principal's name narrows the queue to that principal's complaints", async ({
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
    await expect(queuePage.rowByReferenceId(seeded.referenceId)).toBeVisible()
    // Every visible row belongs to this same Data Principal - other complaints of theirs from
    // other tests may also match, so this is "no row lacks the name" rather than an exact count.
    // One assertion over a filtered locator, never a loop over rows.nth(): the row count can
    // change between the count() and the assertion while the search's own refetch is still
    // narrowing the table (see 08.02.02's identical fix).
    await expect(queuePage.rows.filter({ hasNotText: dataPrincipalName })).toHaveCount(0)
    await officerPage.context().close()
  })

})
