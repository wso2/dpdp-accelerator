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
import { ComplaintSubmitDialog } from '../../pages/ComplaintSubmitDialog'
import { uniqueMarker } from '../../utils/testData'

/**
 * Submitting a new complaint through ComplaintSubmitDialog.tsx, opened from ComplaintListPage.tsx.
 * There is no equivalent "officer creating a complaint" UI - ComplaintQueuePage.tsx has no create
 * action, and officer-assisted intake (POST /complaints) has no frontend of its own. It has no
 * automated coverage either; see TEST-SCENARIOS.md, "Known gaps".
 */
test.describe('Data Principal creating complaints (UI)', () => {
  test('07.01.01 - Submitting a complaint with a category and description shows a success banner with a reference id', async ({
    browser,
  }) => {
    const dataPrincipalPage = await loginAsUser(browser)
    const listPage = new ComplaintListPage(dataPrincipalPage)
    await listPage.goto()
    await listPage.openSubmitDialog()

    const dialog = new ComplaintSubmitDialog(dataPrincipalPage)
    await dialog.selectCategory('Data breach')
    await dialog.fillDescription(`Automated UI test: ${uniqueMarker('data-breach')}`)
    await dialog.submit()

    await expect(listPage.successAlert).toContainText(/has been submitted\.$/)
    await expect(dataPrincipalPage.getByRole('dialog')).toHaveCount(0)
    await dataPrincipalPage.context().close()
  })

  test('07.01.02 - Submitting without selecting a category shows a validation error and does not submit', async ({
    browser,
  }) => {
    const dataPrincipalPage = await loginAsUser(browser)
    const listPage = new ComplaintListPage(dataPrincipalPage)
    await listPage.goto()
    await listPage.openSubmitDialog()

    const dialog = new ComplaintSubmitDialog(dataPrincipalPage)
    await dialog.fillDescription('Missing a category on purpose.')
    await dialog.submit()

    await expect(dialog.categoryRequiredError).toBeVisible()
    await expect(dialog.root).toBeVisible() // still open - never submitted
    await dataPrincipalPage.context().close()
  })

  test('07.01.03 - Submitting without a description shows a validation error and does not submit', async ({
    browser,
  }) => {
    const dataPrincipalPage = await loginAsUser(browser)
    const listPage = new ComplaintListPage(dataPrincipalPage)
    await listPage.goto()
    await listPage.openSubmitDialog()

    const dialog = new ComplaintSubmitDialog(dataPrincipalPage)
    await dialog.selectCategory('Other')
    await dialog.submit()

    await expect(dialog.descriptionRequiredError).toBeVisible()
    await expect(dialog.root).toBeVisible()
    await dataPrincipalPage.context().close()
  })

  test('07.01.04 - Attaching a file before submitting carries it through to the created complaint', async ({
    browser,
  }) => {
    const dataPrincipalPage = await loginAsUser(browser)
    const listPage = new ComplaintListPage(dataPrincipalPage)
    await listPage.goto()
    await listPage.openSubmitDialog()

    const dialog = new ComplaintSubmitDialog(dataPrincipalPage)
    await dialog.selectCategory('Other')
    await dialog.fillDescription(`Automated UI test with an attachment: ${uniqueMarker('with-attachment')}`)
    await dialog.attachFile('evidence.pdf', 'application/pdf')
    await expect(dialog.stagedAttachmentName('evidence.pdf')).toBeVisible()
    await dialog.submit()

    const alertText = await listPage.successAlert.textContent()
    const referenceId = /complaint\s+(\S+)\s+has been submitted/.exec(alertText ?? '')?.[1]
    if (!referenceId) {
      throw new Error(`Could not read a reference id out of the success banner: "${alertText}"`)
    }
    // Not the "...but the attachments failed to upload" variant - a real, valid PDF upload succeeds.
    expect(alertText).not.toMatch(/failed to upload/)

    await listPage.openByReferenceId(referenceId)
    await expect(dataPrincipalPage.getByRole('heading', { name: referenceId })).toBeVisible()
    const detailPage = new ComplaintDetailPage(dataPrincipalPage)
    await detailPage.openAttachmentsTab()
    await expect(dataPrincipalPage.getByText('evidence.pdf')).toBeVisible()
    await dataPrincipalPage.context().close()
  })

  test('07.01.05 - "Upload files" is disabled while a file is staged, and removing it lets a different file be attached', async ({
    browser,
  }) => {
    const dataPrincipalPage = await loginAsUser(browser)
    const listPage = new ComplaintListPage(dataPrincipalPage)
    await listPage.goto()
    await listPage.openSubmitDialog()

    const dialog = new ComplaintSubmitDialog(dataPrincipalPage)
    await dialog.attachFile('first.pdf', 'application/pdf')
    await expect(dialog.stagedAttachmentName('first.pdf')).toBeVisible()
    await expect(dialog.uploadButton).toBeDisabled()

    await dialog.removeAttachmentButton.click()
    await expect(dialog.uploadButton).toBeEnabled()

    await dialog.attachFile('second.png', 'image/png')
    await expect(dialog.stagedAttachmentName('second.png')).toBeVisible()
    await expect(dialog.stagedAttachmentName('first.pdf')).not.toBeVisible()
    await dataPrincipalPage.context().close()
  })

  test('07.01.06 - Cancelling the dialog discards the draft without creating a complaint', async ({ browser }) => {
    const dataPrincipalPage = await loginAsUser(browser)
    const listPage = new ComplaintListPage(dataPrincipalPage)
    await listPage.goto()
    await listPage.openSubmitDialog()

    const dialog = new ComplaintSubmitDialog(dataPrincipalPage)
    const description = `Automated UI test: should never be created (${uniqueMarker('cancelled')})`
    await dialog.selectCategory('Other')
    await dialog.fillDescription(description)
    await dialog.cancel()

    await expect(dialog.root).toHaveCount(0)
    await listPage.openSubmitDialog()
    // Reopening starts from a clean draft - the cancelled description was discarded, not just hidden.
    await expect(dialog.descriptionField).toHaveValue('')
    await dataPrincipalPage.context().close()
  })
})
