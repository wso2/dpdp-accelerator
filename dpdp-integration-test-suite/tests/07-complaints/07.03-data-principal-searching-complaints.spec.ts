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

import { test, expect, loginAsUser } from '../../fixtures/auth.fixtures'
import { ComplaintListPage } from '../../pages/ComplaintListPage'
import { moveComplaintToStatus, seedComplaint } from '../../utils/complaintSetup'

/**
 * Narrowing a Data Principal's own complaint list - ComplaintListPage.tsx only offers a status
 * filter (no free-text search field, unlike the officer queue's - see
 * 07.06-officer-searching-complaints.spec.ts). This environment never resets (see the suite root
 * README), so every assertion below is "my complaint is/isn't in this filtered view", never "the
 * filtered view has exactly N rows" or "is empty" - either could be false purely from other
 * accounts' or prior runs' history sharing the same status.
 */
test.describe('Data Principal searching/filtering complaints (UI)', () => {
  test('07.03.01 - Filtering to "Open" shows an Open complaint and hides an In Progress one', async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const openComplaint = await seedComplaint(userComplaintApi, 'OTHER', 'filter-open')
    const inProgressComplaint = await seedComplaint(userComplaintApi, 'OTHER', 'filter-in-progress')
    await moveComplaintToStatus(officerComplaintApi, inProgressComplaint.id, 'IN_PROGRESS')

    const dataPrincipalPage = await loginAsUser(browser)
    const listPage = new ComplaintListPage(dataPrincipalPage)
    await listPage.goto()
    await listPage.setRowsPerPage(25)
    await listPage.filterByStatus('Open')

    await expect(listPage.rowByReferenceId(openComplaint.referenceId)).toBeVisible()
    await expect(listPage.rowByReferenceId(inProgressComplaint.referenceId)).not.toBeVisible()
    await dataPrincipalPage.context().close()
  })

  test('07.03.02 - Filtering to "In Progress" shows the In Progress complaint and hides the Open one', async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const openComplaint = await seedComplaint(userComplaintApi, 'OTHER', 'filter-open-2')
    const inProgressComplaint = await seedComplaint(userComplaintApi, 'OTHER', 'filter-in-progress-2')
    await moveComplaintToStatus(officerComplaintApi, inProgressComplaint.id, 'IN_PROGRESS')

    const dataPrincipalPage = await loginAsUser(browser)
    const listPage = new ComplaintListPage(dataPrincipalPage)
    await listPage.goto()
    await listPage.setRowsPerPage(25)
    await listPage.filterByStatus('In Progress')

    await expect(listPage.rowByReferenceId(inProgressComplaint.referenceId)).toBeVisible()
    await expect(listPage.rowByReferenceId(openComplaint.referenceId)).not.toBeVisible()
    await dataPrincipalPage.context().close()
  })

  test('07.03.03 - Clearing the status filter (back to "All") restores complaints of every status', async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const openComplaint = await seedComplaint(userComplaintApi, 'OTHER', 'filter-clear-open')
    const inProgressComplaint = await seedComplaint(userComplaintApi, 'OTHER', 'filter-clear-in-progress')
    await moveComplaintToStatus(officerComplaintApi, inProgressComplaint.id, 'IN_PROGRESS')

    const dataPrincipalPage = await loginAsUser(browser)
    const listPage = new ComplaintListPage(dataPrincipalPage)
    await listPage.goto()
    await listPage.setRowsPerPage(25)
    await listPage.filterByStatus('Open')
    await expect(listPage.rowByReferenceId(inProgressComplaint.referenceId)).not.toBeVisible()

    await listPage.filterByStatus('All')
    await expect(listPage.rowByReferenceId(openComplaint.referenceId)).toBeVisible()
    await expect(listPage.rowByReferenceId(inProgressComplaint.referenceId)).toBeVisible()
    await dataPrincipalPage.context().close()
  })
})
