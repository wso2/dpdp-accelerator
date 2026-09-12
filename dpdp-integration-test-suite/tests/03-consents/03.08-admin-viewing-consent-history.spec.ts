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

import { test, expect, loginAsUser, loginAsConsentAdmin } from '../../fixtures/auth.fixtures'
import { ConsentDetailPage } from '../../pages/ConsentDetailPage'
import { ConsentFullHistoryDialogPage } from '../../pages/ConsentFullHistoryDialogPage'
import { env } from '../../utils/env'
import { seedConsent } from '../../utils/consentSetup'

/**
 * The admin surface (/administration/consents/:id) of the same two components covered in
 * 03.07-user-viewing-consent-history.spec.ts - see that file's comment for why `goto()` runs
 * twice and why CREATE is always attributed to the admin persona. `dpdp-consent-admin` gets both
 * ANY and SELF history scopes by default, so no extra setup is needed here either.
 *
 * This file also proves the ANY-scoped endpoints return a *different* user's own history, not
 * just the admin's own actions - something the self-viewing spec's single persona can't show.
 */
test.describe('Admin viewing Consent History (UI)', () => {
  test('03.08.01 - Revoking an Active consent as admin attributes CREATE and REVOKE to the admin, showing only the state transition in the diff', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'ACTIVE',
    )

    const detailPage = new ConsentDetailPage(consentAdminPage, 'admin')
    await detailPage.goto(consentId)
    await detailPage.openActionDialog('revoke')
    await detailPage.confirmAction('revoke')
    await expect(consentAdminPage.getByText('Revoked', { exact: true }).first()).toBeVisible()

    // See the file-level comment above - the history queries need a fresh page load.
    await detailPage.goto(consentId)

    await expect(
      detailPage.lifecycleRow('Consent created', env.consentAdmin.username),
    ).toBeVisible()
    await expect(detailPage.lifecycleRow('Revoked', env.consentAdmin.username)).toBeVisible()

    const rowTexts = await detailPage.lifecycleRows.allTextContents()
    const createdIndex = rowTexts.findIndex((text) => text.includes('Consent created'))
    const revokedIndex = rowTexts.findIndex((text) => text.includes('Revoked by'))
    expect(revokedIndex).toBeGreaterThan(createdIndex)

    await detailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(consentAdminPage)
    await expect(dialog.dialog).toBeVisible()

    // This consent was created directly in ACTIVE state with no authorizations to cascade, so
    // the only change is `state` itself (Active -> Revoked) - no ambiguity like 03.07.03's
    // revoke-after-approve case.
    await dialog.expand('Revoked', env.consentAdmin.username)
    await expect(
      dialog.stateTransition('Revoked', env.consentAdmin.username, 'Active', 'Revoked'),
    ).toBeVisible()

    await dialog.close()
    await consentAdminPage.context().close()
  })

  test("03.08.02 - The admin surface shows the data principal's own approval, not just admin-authored history", async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const userPage = await loginAsUser(browser)
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'PENDING',
    )

    // The data principal, not the admin, performs the approval - on the self surface.
    const selfDetailPage = new ConsentDetailPage(userPage, 'self')
    await selfDetailPage.goto(consentId)
    await selfDetailPage.openActionDialog('approve')
    await selfDetailPage.confirmAction('approve')
    await expect(userPage.getByText('Active', { exact: true }).first()).toBeVisible()

    // First load happens after the approval already landed server-side, so a single navigation
    // is enough here - unlike the "goto() twice" cases elsewhere in this file.
    const adminDetailPage = new ConsentDetailPage(consentAdminPage, 'admin')
    await adminDetailPage.goto(consentId)

    await expect(
      adminDetailPage.lifecycleRow('Consent created', env.consentAdmin.username),
    ).toBeVisible()
    await expect(adminDetailPage.lifecycleRow('Approved', env.user.username)).toBeVisible()

    await adminDetailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(consentAdminPage)
    await expect(dialog.entry('Approved', env.user.username)).toBeVisible()

    await dialog.expand('Approved', env.user.username)
    await expect(dialog.changedTag('Approved', env.user.username)).toBeVisible()

    await dialog.close()
    await userPage.context().close()
    await consentAdminPage.context().close()
  })

  test('03.08.03 - A full multi-actor lifecycle (admin creates, the data principal approves, admin revokes) is captured in order with each actor attributed correctly', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const userPage = await loginAsUser(browser)
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'PENDING',
    )

    const selfDetailPage = new ConsentDetailPage(userPage, 'self')
    await selfDetailPage.goto(consentId)
    await selfDetailPage.openActionDialog('approve')
    await selfDetailPage.confirmAction('approve')
    await expect(userPage.getByText('Active', { exact: true }).first()).toBeVisible()

    const adminDetailPage = new ConsentDetailPage(consentAdminPage, 'admin')
    await adminDetailPage.goto(consentId)
    await adminDetailPage.openActionDialog('revoke')
    await adminDetailPage.confirmAction('revoke')
    await expect(consentAdminPage.getByText('Revoked', { exact: true }).first()).toBeVisible()

    // Second navigation to pick up the REVOKE entry - see the file-level comment.
    await adminDetailPage.goto(consentId)

    // Wait on a visible element rather than reading text straight off goto() - see 03.07's
    // identical comment on the SPA's post-navigation redirect settling.
    await expect(
      adminDetailPage.lifecycleRow('Revoked', env.consentAdmin.username),
    ).toBeVisible()

    const rowTexts = await adminDetailPage.lifecycleRows.allTextContents()
    const createdIndex = rowTexts.findIndex(
      (text) => text.includes('Consent created') && text.includes(env.consentAdmin.username),
    )
    const approvedIndex = rowTexts.findIndex(
      (text) => text.includes('Approved by') && text.includes(env.user.username),
    )
    const revokedIndex = rowTexts.findIndex(
      (text) => text.includes('Revoked by') && text.includes(env.consentAdmin.username),
    )
    expect(createdIndex).toBeGreaterThanOrEqual(0)
    expect(approvedIndex).toBeGreaterThan(createdIndex)
    expect(revokedIndex).toBeGreaterThan(approvedIndex)

    await adminDetailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(consentAdminPage)
    // The dialog's heading renders as soon as it opens, but its history is fetched lazily *on
    // open* - so gating on `dialog.dialog` (which is defined by that heading) proves nothing
    // about the entries. allTextContents() below does not retry, so without a gate on real
    // content it can snapshot an empty accordion list. Gate on the newest entry: it is the last
    // one the fetch can produce, so its presence means the list is fully rendered.
    await expect(dialog.entry('Revoked', env.consentAdmin.username)).toBeVisible()
    // Summary text uses "·", not "by" - see ConsentFullHistoryDialogPage.
    const summaryTexts = await dialog.entrySummaries.allTextContents()
    const dialogCreatedIndex = summaryTexts.findIndex(
      (text) => text.includes('Consent created') && text.includes(env.consentAdmin.username),
    )
    const dialogApprovedIndex = summaryTexts.findIndex(
      (text) => text.includes('Approved') && text.includes(env.user.username),
    )
    const dialogRevokedIndex = summaryTexts.findIndex(
      (text) => text.includes('Revoked') && text.includes(env.consentAdmin.username),
    )
    // Newest-first: REVOKE (admin), then APPROVE (user), then CREATE (admin).
    expect(dialogRevokedIndex).toBeGreaterThanOrEqual(0)
    expect(dialogApprovedIndex).toBeGreaterThan(dialogRevokedIndex)
    expect(dialogCreatedIndex).toBeGreaterThan(dialogApprovedIndex)

    await dialog.close()
    await userPage.context().close()
    await consentAdminPage.context().close()
  })
})
