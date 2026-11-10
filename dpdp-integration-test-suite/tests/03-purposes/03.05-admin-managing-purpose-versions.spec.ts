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

import type { Page } from '@playwright/test'
import { test, expect, loginAsConsentAdmin } from '../../fixtures/auth.fixtures'
import { PurposeDetailPage } from '../../pages/PurposeDetailPage'
import { PurposeFormDialog } from '../../pages/PurposeFormDialog'
import { PurposeListPage } from '../../pages/PurposeListPage'
import { PurposeVersionDeleteDialog } from '../../pages/PurposeVersionDeleteDialog'
import { PurposeVersionFormDialog } from '../../pages/PurposeVersionFormDialog'
import { seedConsentViaApi } from '../../utils/consentSetup'
import { uniqueElementName, uniquePurposeName } from '../../utils/testData'

/**
 * Version history on a Purpose's detail page: adding a version, the duplicate-version-name
 * validation, promoting a version to latest, and deleting a non-latest version. The current
 * latest version's own delete action is disabled, not hidden - see PurposeDetailPage's
 * versionDeleteButton and PurposeVersionDeleteDialog.
 */

/** Creates a purpose with a single "v1" version (its only version, so also its latest). */
async function createPurposeWithV1(page: Page): Promise<string> {
  const listPage = new PurposeListPage(page)
  await listPage.goto()
  await listPage.openCreateDialog()
  const dialog = new PurposeFormDialog(page)
  await dialog.fill({ name: uniquePurposeName(), type: 'Policy', version: 'v1' })
  await dialog.submit()
  await expect(page).toHaveURL(/\/purposes\/[^/]+$/)
  const match = /\/purposes\/([^/]+)$/.exec(page.url())
  if (!match) {
    throw new Error(`Could not read a purpose id out of the detail URL: ${page.url()}`)
  }
  return match[1]
}

test.describe('Admin managing Purpose versions (UI)', () => {
  test('03.05.01 - Adding a new version does not change which version is latest unless "Set as latest" is checked', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const purposeId = await createPurposeWithV1(consentAdminPage)

    const detailPage = new PurposeDetailPage(consentAdminPage)
    await detailPage.goto(purposeId)
    await detailPage.addVersionButton.click()
    const versionDialog = new PurposeVersionFormDialog(consentAdminPage)
    await versionDialog.fill({ version: 'v2' })
    await versionDialog.setAsLatest(false)
    await versionDialog.submit()

    await expect(detailPage.versionRow('v2')).toBeVisible()
    await expect(detailPage.versionRow('v2')).not.toContainText('Latest')
    await expect(detailPage.versionRow('v1')).toContainText('Latest')
    await expect(detailPage.fieldValue('Latest version')).toHaveText('v1')
    await consentAdminPage.context().close()
  })

  test('03.05.02 - Adding a version with a name that already exists shows the duplicate-version validation error and blocks submission', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const purposeId = await createPurposeWithV1(consentAdminPage)

    const detailPage = new PurposeDetailPage(consentAdminPage)
    await detailPage.goto(purposeId)
    await detailPage.addVersionButton.click()
    const versionDialog = new PurposeVersionFormDialog(consentAdminPage)
    await versionDialog.fill({ version: 'v1' })
    await versionDialog.blurVersion()

    await expect(
      versionDialog.root.getByText('This purpose already has a version with this name.'),
    ).toBeVisible()
    // The Create button itself stays enabled here - unlike the properties-with-no-key case,
    // this validation is enforced in the submit handler (a no-op return), not by disabling the
    // button - so the real proof is that clicking it does not close the dialog. (Not checking
    // versionRows here too: the modal renders with the rest of the page aria-hidden while open,
    // so a role-based query against the background table would misreport regardless.)
    await versionDialog.submit()
    await expect(versionDialog.root).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('03.05.03 - Setting a version as latest moves the "Latest" label to it, and its own delete action becomes enabled', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const purposeId = await createPurposeWithV1(consentAdminPage)

    const detailPage = new PurposeDetailPage(consentAdminPage)
    await detailPage.goto(purposeId)
    await detailPage.addVersionButton.click()
    const versionDialog = new PurposeVersionFormDialog(consentAdminPage)
    await versionDialog.fill({ version: 'v2' })
    await versionDialog.setAsLatest(false)
    await versionDialog.submit()
    await expect(detailPage.versionRow('v2')).toBeVisible()

    const v1Row = detailPage.versionRow('v1')
    const v2Row = detailPage.versionRow('v2')
    // v1 is the current latest: its own delete is disabled, and it has no "Set as latest"
    // button (nothing to promote it to - it already is latest).
    await expect(detailPage.versionDeleteButton(v1Row)).toBeDisabled()
    await expect(detailPage.setLatestButton(v2Row)).toBeVisible()

    await detailPage.setLatestButton(v2Row).click()

    await expect(v2Row).toContainText('Latest')
    await expect(v1Row).not.toContainText('Latest')
    await expect(detailPage.fieldValue('Latest version')).toHaveText('v2')
    // The promotion reverses which version's delete is disabled.
    await expect(detailPage.versionDeleteButton(v2Row)).toBeDisabled()
    await expect(detailPage.versionDeleteButton(v1Row)).toBeEnabled()
    await consentAdminPage.context().close()
  })

  test('03.05.04 - Deleting a non-latest version removes it from the version history', async ({
    browser,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const purposeId = await createPurposeWithV1(consentAdminPage)

    const detailPage = new PurposeDetailPage(consentAdminPage)
    await detailPage.goto(purposeId)
    await detailPage.addVersionButton.click()
    const versionDialog = new PurposeVersionFormDialog(consentAdminPage)
    await versionDialog.fill({ version: 'v2' })
    await versionDialog.setAsLatest(false)
    await versionDialog.submit()
    await expect(detailPage.versionRow('v2')).toBeVisible()

    await detailPage.versionDeleteButton(detailPage.versionRow('v2')).click()
    const deleteDialog = new PurposeVersionDeleteDialog(consentAdminPage)
    await deleteDialog.confirm()

    await expect(detailPage.versionRow('v2')).not.toBeVisible()
    // v1, the latest, is unaffected and still resolves.
    await expect(detailPage.versionRow('v1')).toContainText('Latest')
    await consentAdminPage.context().close()
  })

  test('03.05.05 - A version referenced by a consent cannot be deleted', async ({
    browser,
    target,
    consentAdminConsentApi,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // seedConsentViaApi creates its own Purpose (and Element) - that Purpose's v1 is what this
    // test needs referenced by a real, permanent Consent (Consents can never be deleted, see
    // AGENTS.md), so it can't be seeded any other way.
    const { purposeName } = await seedConsentViaApi(consentAdminConsentApi, target.personas.user.username, 'ACTIVE')

    // Searched by name, not just goto() + openByName: the unfiltered list only shows its first
    // page, and this shared environment accumulates far more purposes than that.
    const listPage = new PurposeListPage(consentAdminPage)
    await listPage.goto()
    await listPage.search({ name: purposeName })
    await listPage.openByName(purposeName)
    await expect(consentAdminPage).toHaveURL(/\/purposes\/[^/]+$/)

    const detailPage = new PurposeDetailPage(consentAdminPage)
    await detailPage.addVersionButton.click()
    const versionDialog = new PurposeVersionFormDialog(consentAdminPage)
    await versionDialog.fill({ version: 'v2' })
    await versionDialog.submit()
    await expect(detailPage.versionRow('v2')).toBeVisible()

    // v1 is no longer latest, so its delete action is enabled - but the consent seeded above
    // still references it.
    const v1Row = detailPage.versionRow('v1')
    await detailPage.versionDeleteButton(v1Row).click()
    const deleteDialog = new PurposeVersionDeleteDialog(consentAdminPage)
    await deleteDialog.confirm()

    // The server does reject this, but PurposeDetailsPage.tsx's
    // deleteVersionErrorMessage treats every failure as unexpected and shows this generic text -
    // unlike the whole-Purpose delete, there's no version-specific "still referenced" message.
    await expect(deleteDialog.errorAlert).toContainText(
      'Something went wrong and the version could not be deleted. Please try again.',
    )
    await expect(deleteDialog.root).toBeVisible()
    await deleteDialog.cancel()
    await expect(v1Row).toBeVisible()
    await consentAdminPage.context().close()
  })

  test('03.05.06 - Expanding each version row fetches and shows that version’s own elements and properties', async ({
    browser,
    consentAdminConsentApi,
  }) => {
    const elementOneName = uniqueElementName()
    const elementTwoName = uniqueElementName()

    const elOneResp = await consentAdminConsentApi.createElement({
      name: elementOneName,
      displayName: elementOneName,
      description: 'First version element',
    })
    expect(elOneResp.status()).toBe(201)
    const elementOneId = ((await elOneResp.json()) as { id: string }).id

    const elTwoResp = await consentAdminConsentApi.createElement({
      name: elementTwoName,
      displayName: elementTwoName,
      description: 'Second version element',
    })
    expect(elTwoResp.status()).toBe(201)
    const elementTwoId = ((await elTwoResp.json()) as { id: string }).id

    const purposeResp = await consentAdminConsentApi.createPurpose({
      name: uniquePurposeName(),
      type: 'Policy',
      version: 'v1',
      description: 'Version 1 description',
      elements: [{ id: elementOneId, mandatory: true }],
      properties: { retention: '30d', region: 'IN' },
    })
    expect(purposeResp.status()).toBe(201)
    const purposeId = ((await purposeResp.json()) as { id: string }).id

    const v2Resp = await consentAdminConsentApi.createPurposeVersion(purposeId, {
      version: 'v2',
      setAsLatest: true,
      description: 'Version 2 description',
      elements: [{ id: elementTwoId, mandatory: false }],
      properties: { retention: '90d', lawfulBasis: 'consent' },
    })
    expect(v2Resp.status()).toBe(201)

    const consentAdminPage = await loginAsConsentAdmin(browser)
    const detailPage = new PurposeDetailPage(consentAdminPage)
    await detailPage.goto(purposeId)

    await detailPage.expandVersionButton('v1').click()
    await detailPage.expandVersionButton('v2').click()

    const v1Details = detailPage.versionDetailsRow('v1')
    const v2Details = detailPage.versionDetailsRow('v2')

    await expect(v1Details).toContainText(elementOneName)
    await expect(v1Details).toContainText('Mandatory')
    await expect(v1Details).toContainText('retention')
    await expect(v1Details).toContainText('30d')
    await expect(v1Details).toContainText('region')
    await expect(v1Details).not.toContainText(elementTwoName)
    await expect(v1Details).not.toContainText('90d')

    await expect(v2Details).toContainText(elementTwoName)
    await expect(v2Details).toContainText('Optional')
    await expect(v2Details).toContainText('retention')
    await expect(v2Details).toContainText('90d')
    await expect(v2Details).toContainText('lawfulBasis')
    await expect(v2Details).not.toContainText(elementOneName)
    await expect(v2Details).not.toContainText('30d')

    await consentAdminPage.context().close()
  })
})
