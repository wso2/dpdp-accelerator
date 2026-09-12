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
import { ComplaintCaseDetailPage } from '../../pages/ComplaintCaseDetailPage'
import { ComplaintQueuePage } from '../../pages/ComplaintQueuePage'
import { moveComplaintToStatus, seedComplaint } from '../../utils/complaintSetup'

/**
 * A Complaint Officer viewing the org-wide queue and one case's detail -
 * ComplaintQueuePage.tsx / ComplaintCaseDetailPage.tsx at /complaint-management and
 * /complaint-management/:id. Reached with the `dpdp-consent-admin` persona ("Consent Admin") -
 * see AGENTS.md for why there's no distinct Complaint Officer persona.
 */
test.describe('Complaint Officer viewing the queue and a case (UI)', () => {
  test('07.05.01 - The queue table shows reference id, user, category, priority, status, SLA and updated columns', async ({
    browser,
    userComplaintApi,
  }) => {
    await seedComplaint(userComplaintApi, 'OTHER', 'queue-columns')
    const officerPage = await loginAsConsentAdmin(browser)
    const queuePage = new ComplaintQueuePage(officerPage)
    await queuePage.goto()

    await expect(queuePage.table).toBeVisible()
    for (const header of ['Reference ID', 'User', 'Category', 'Priority', 'Status', 'SLA', 'Updated']) {
      await expect(queuePage.table.getByRole('columnheader', { name: header })).toBeVisible()
    }
    await expect(queuePage.rows.first()).toBeVisible()
    await officerPage.context().close()
  })

  test('07.05.02 - A resolved complaint is hidden from the default (status=All) queue view', async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'resolved-hidden')
    await moveComplaintToStatus(officerComplaintApi, seeded.id, 'IN_PROGRESS')
    await moveComplaintToStatus(officerComplaintApi, seeded.id, 'RESOLVED', 'Resolved for this test.')

    const officerPage = await loginAsConsentAdmin(browser)
    const queuePage = new ComplaintQueuePage(officerPage)
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)

    // ComplaintQueuePage.tsx's own `rows` memo filters out CLOSED_OUT_STATUSES (RESOLVED)
    // whenever filters.status === 'All' - visible again only once that filter is explicitly set
    // to "Resolved" (covered by 07.06.03). Asserting on this specific row, not on the word
    // "Resolved" being absent anywhere on the page - the "Resolved" stat tile's own label makes
    // that word always present regardless of this filtering behavior.
    await expect(queuePage.rowByReferenceId(seeded.referenceId)).not.toBeVisible()
    await officerPage.context().close()
  })

  test('07.05.03 - Opening a case from the queue navigates to its detail page showing the same reference id', async ({
    browser,
    userComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'queue-open-case')
    const officerPage = await loginAsConsentAdmin(browser)
    const queuePage = new ComplaintQueuePage(officerPage)
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)

    await queuePage.openByReferenceId(seeded.referenceId)
    await expect(officerPage).toHaveURL(/\/complaint-management\/[^/]+$/)
    await expect(officerPage.getByRole('heading', { name: seeded.referenceId })).toBeVisible()
    await officerPage.context().close()
  })

  test('07.05.04 - Navigating to an unknown case id shows the not-found state with a way back to the queue', async ({
    browser,
  }) => {
    const officerPage = await loginAsConsentAdmin(browser)
    const caseDetailPage = new ComplaintCaseDetailPage(officerPage)
    await caseDetailPage.goto('00000000-0000-0000-0000-000000000000')

    await expect(caseDetailPage.notFoundHeading).toBeVisible()
    await caseDetailPage.backButton.click()
    await expect(officerPage).toHaveURL(/\/complaint-management$/)
    await officerPage.context().close()
  })
})
