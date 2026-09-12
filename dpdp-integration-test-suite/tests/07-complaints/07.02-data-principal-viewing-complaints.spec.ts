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
import { ComplaintDetailPage } from '../../pages/ComplaintDetailPage'
import { ComplaintListPage } from '../../pages/ComplaintListPage'
import { seedComplaint } from '../../utils/complaintSetup'

/**
 * A Data Principal viewing their own complaint list and one complaint's detail -
 * ComplaintListPage.tsx / ComplaintDetailPage.tsx at /complaints and /complaints/:id. Complaints
 * are seeded through the REST API (seedComplaint) rather than assumed to already exist from a
 * prior test run - see this suite's root README's "Tests run in parallel by default" and
 * "The environment never resets" operating principles.
 *
 * Not covered here: the list's true empty state (complaints.list.empty) - this suite's shared
 * "user" persona (personas.user) is used by every complaint test in this run and by every
 * prior run against this environment, so there is no way to observe it with a known-empty
 * account without a dedicated, never-otherwise-used env var this suite doesn't currently define.
 */
test.describe('Data Principal viewing complaints (UI)', () => {
  test('07.02.01 - The complaint list shows reference id, category, status, submitted and updated columns', async ({
    browser,
    userComplaintApi,
  }) => {
    await seedComplaint(userComplaintApi, 'OTHER', 'list-columns')
    const dataPrincipalPage = await loginAsUser(browser)
    const listPage = new ComplaintListPage(dataPrincipalPage)
    await listPage.goto()

    await expect(listPage.table).toBeVisible()
    for (const header of ['Reference ID', 'Category', 'Status', 'Submitted', 'Updated']) {
      await expect(listPage.table.getByRole('columnheader', { name: header })).toBeVisible()
    }
    await expect(listPage.rows.first()).toBeVisible()
    await dataPrincipalPage.context().close()
  })

  test('07.02.02 - A freshly submitted complaint appears in the list with its category and Open status', async ({
    browser,
    userComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'DATA_BREACH', 'appears-in-list')
    const dataPrincipalPage = await loginAsUser(browser)
    const listPage = new ComplaintListPage(dataPrincipalPage)
    await listPage.goto()
    // Raise the odds this test's own freshly-created row is on the first (only) page fetched -
    // the list has no explicit sort control, but the API defaults to newest-updated-first.
    await listPage.setRowsPerPage(25)

    const row = listPage.rowByReferenceId(seeded.referenceId)
    await expect(row).toBeVisible()
    await expect(row).toContainText('Data breach')
    await expect(row).toContainText('Open')
    await dataPrincipalPage.context().close()
  })

  test('07.02.03 - Opening a complaint from the list navigates to its detail page showing the same reference id', async ({
    browser,
    userComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'open-from-list')
    const dataPrincipalPage = await loginAsUser(browser)
    const listPage = new ComplaintListPage(dataPrincipalPage)
    await listPage.goto()
    await listPage.setRowsPerPage(25)

    await listPage.openByReferenceId(seeded.referenceId)
    await expect(dataPrincipalPage).toHaveURL(/\/complaints\/[^/]+$/)
    await expect(dataPrincipalPage.getByRole('heading', { name: seeded.referenceId })).toBeVisible()
    await dataPrincipalPage.context().close()
  })

  test("07.02.04 - A complaint's detail page shows its category, description, submitted date, and an empty attachments tab", async ({
    browser,
    userComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'PURPOSE_VIOLATION', 'detail-fields')
    const dataPrincipalPage = await loginAsUser(browser)
    const detailPage = new ComplaintDetailPage(dataPrincipalPage)
    await detailPage.goto(seeded.id)

    await expect(dataPrincipalPage.getByRole('heading', { name: seeded.referenceId })).toBeVisible()
    await expect(dataPrincipalPage.getByText('Purpose violation')).toBeVisible()
    await expect(dataPrincipalPage.getByText(seeded.description)).toBeVisible()

    await detailPage.openAttachmentsTab()
    await expect(dataPrincipalPage.getByText('No attachments have been added to this complaint.')).toBeVisible()
    await dataPrincipalPage.context().close()
  })

  test('07.02.05 - Navigating to an unknown complaint id shows the not-found state with a way back to the list', async ({
    browser,
  }) => {
    const dataPrincipalPage = await loginAsUser(browser)
    const detailPage = new ComplaintDetailPage(dataPrincipalPage)
    await detailPage.goto('00000000-0000-0000-0000-000000000000')

    await expect(detailPage.notFoundHeading).toBeVisible()
    await detailPage.backButton.click()
    await expect(dataPrincipalPage).toHaveURL(/\/complaints$/)
    await dataPrincipalPage.context().close()
  })
})
