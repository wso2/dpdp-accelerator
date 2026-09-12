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

import {
  test,
  expect,
  getPersonaState,
  hasSecondUser,
  loginAsUser,
  loginAsConsentAdmin,
} from '../../fixtures/auth.fixtures'
import { ConsentApiClient } from '../../clients/ConsentApiClient'
import { ConsentDetailPage } from '../../pages/ConsentDetailPage'
import { ConsentFullHistoryDialogPage } from '../../pages/ConsentFullHistoryDialogPage'
import { authHeadersFromPersonaState } from '../../utils/authStorage'
import { env } from '../../utils/env'
import { seedConsent } from '../../utils/consentSetup'

/**
 * A user's own consent history: the lifecycle timeline and full-history dialog on the self
 * detail page (/consents/:id). See 03.08-admin-viewing-consent-history.spec.ts for the admin
 * surface and cross-persona checks. `dpdp-consent-user` gets *_VIEW_SELF scopes by default, so
 * no extra setup is needed here.
 *
 * `seedConsent` always creates via the admin API, so every "Consent created by ..." entry below
 * is attributed to `env.consentAdmin.username`, even in these self-service tests.
 *
 * `detailPage.goto(consentId)` is called again after each action that should appear in history:
 * the approve/reject/revoke mutations don't invalidate the history query keys, so the lifecycle
 * card and dialog would otherwise keep showing stale data - a real product gap, not a test quirk.
 */
test.describe('User viewing Consent History (UI)', () => {
  test('03.07.01 - Approving a Pending consent records CREATE then AUTHORIZE_APPROVE, oldest-first in the table and newest-first in the dialog', async ({
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

    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto(consentId)
    await detailPage.openActionDialog('approve')
    await detailPage.confirmAction('approve')
    await expect(userPage.getByText('Active', { exact: true }).first()).toBeVisible()

    // See the file-level comment above - the history queries need a fresh page load.
    await detailPage.goto(consentId)

    await expect(detailPage.lifecycleSection).toBeVisible()
    await expect(
      detailPage.lifecycleRow('Consent created', env.consentAdmin.username),
    ).toBeVisible()
    await expect(detailPage.lifecycleRow('Approved', env.user.username)).toBeVisible()

    // Oldest-first in the table: CREATE row precedes the AUTHORIZE_APPROVE row.
    const rowTexts = await detailPage.lifecycleRows.allTextContents()
    const createdIndex = rowTexts.findIndex((text) => text.includes('Consent created'))
    const approvedIndex = rowTexts.findIndex((text) => text.includes('Approved by'))
    expect(createdIndex).toBeGreaterThanOrEqual(0)
    expect(approvedIndex).toBeGreaterThan(createdIndex)

    await detailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(userPage)
    // The dialog's heading renders as soon as it opens, but its history is fetched lazily *on
    // open* - so gating on `dialog.dialog` (which is defined by that heading) proves nothing
    // about the entries. allTextContents() below does not retry, so without a gate on real
    // content it can snapshot an empty accordion list. Gate on the newest entry: it is the last
    // one the fetch can produce, so its presence means the list is fully rendered.
    await expect(dialog.entry('Approved', env.user.username)).toBeVisible()

    // Newest-first in the dialog, reversed relative to the table above. Summary text uses "·",
    // not "by" - see ConsentFullHistoryDialogPage - so these checks drop "by".
    const summaryTexts = await dialog.entrySummaries.allTextContents()
    const dialogApprovedIndex = summaryTexts.findIndex((text) => text.includes('Approved'))
    const dialogCreatedIndex = summaryTexts.findIndex((text) => text.includes('Consent created'))
    expect(dialogApprovedIndex).toBeGreaterThanOrEqual(0)
    expect(dialogCreatedIndex).toBeGreaterThan(dialogApprovedIndex)

    await dialog.expand('Consent created', env.consentAdmin.username)
    await expect(
      dialog.initialSnapshotChip('Consent created', env.consentAdmin.username),
    ).toBeVisible()

    // A real diff against real server data: the subject's authorization moves to APPROVED.
    await dialog.expand('Approved', env.user.username)
    await expect(dialog.changedTag('Approved', env.user.username)).toBeVisible()

    await dialog.close()
    await userPage.context().close()
    await consentAdminPage.context().close()
  })

  test('03.07.02 - Rejecting a Pending consent records AUTHORIZE_REJECT with a diffed authorization', async ({
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

    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto(consentId)
    await detailPage.openActionDialog('reject')
    await detailPage.confirmAction('reject')
    // .first(): the metadata card's state chip and the authorizations table's own state chip
    // both render the literal state text (see 03.01.02's identical comment).
    await expect(userPage.getByText('Rejected', { exact: true }).first()).toBeVisible()

    await detailPage.goto(consentId)

    await expect(detailPage.lifecycleRow('Rejected', env.user.username)).toBeVisible()

    await detailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(userPage)
    await dialog.expand('Rejected', env.user.username)
    await expect(dialog.changedTag('Rejected', env.user.username)).toBeVisible()

    await dialog.close()
    await userPage.context().close()
    await consentAdminPage.context().close()
  })

  test('03.07.03 - A full self-service lifecycle (created, approved, then revoked) is captured in order end to end', async ({
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

    const detailPage = new ConsentDetailPage(userPage, 'self')
    await detailPage.goto(consentId)
    await detailPage.openActionDialog('approve')
    await detailPage.confirmAction('approve')
    await expect(userPage.getByText('Active', { exact: true }).first()).toBeVisible()

    // Chained without a reload - Revoke becomes available reactively once Active is reflected.
    await detailPage.openActionDialog('revoke')
    await detailPage.confirmAction('revoke')
    await expect(userPage.getByText('Revoked', { exact: true }).first()).toBeVisible()

    // See the file-level comment above - the history queries need a fresh page load.
    await detailPage.goto(consentId)

    // Wait on a visible element rather than reading text straight off goto() - every full page
    // load re-drives the SPA's silent sign-in redirect, which can otherwise hit a destroyed
    // execution context (see fixtures/auth.fixtures.ts).
    await expect(detailPage.lifecycleRow('Revoked', env.user.username)).toBeVisible()

    const rowTexts = await detailPage.lifecycleRows.allTextContents()
    const createdIndex = rowTexts.findIndex((text) => text.includes('Consent created'))
    const approvedIndex = rowTexts.findIndex((text) => text.includes('Approved by'))
    const revokedIndex = rowTexts.findIndex((text) => text.includes('Revoked by'))
    expect(createdIndex).toBeGreaterThanOrEqual(0)
    expect(approvedIndex).toBeGreaterThan(createdIndex)
    expect(revokedIndex).toBeGreaterThan(approvedIndex)

    await detailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(userPage)
    // The dialog's heading renders as soon as it opens, but its history is fetched lazily *on
    // open* - so gating on `dialog.dialog` (which is defined by that heading) proves nothing
    // about the entries. allTextContents() below does not retry, so without a gate on real
    // content it can snapshot an empty accordion list. Gate on the newest entry: it is the last
    // one the fetch can produce, so its presence means the list is fully rendered.
    await expect(dialog.entry('Revoked', env.user.username)).toBeVisible()
    const summaryTexts = await dialog.entrySummaries.allTextContents()
    const dialogCreatedIndex = summaryTexts.findIndex((text) => text.includes('Consent created'))
    const dialogApprovedIndex = summaryTexts.findIndex((text) => text.includes('Approved'))
    const dialogRevokedIndex = summaryTexts.findIndex((text) => text.includes('Revoked'))
    // Newest-first: REVOKE, then APPROVE, then CREATE.
    expect(dialogRevokedIndex).toBeGreaterThanOrEqual(0)
    expect(dialogApprovedIndex).toBeGreaterThan(dialogRevokedIndex)
    expect(dialogCreatedIndex).toBeGreaterThan(dialogApprovedIndex)

    // Only proves the diff rendered a real result - see diffRendered's own comment.
    await dialog.expand('Revoked', env.user.username)
    await expect(dialog.diffRendered('Revoked', env.user.username)).toBeVisible()

    await dialog.close()
    await userPage.context().close()
    await consentAdminPage.context().close()
  })

  test('03.07.04 - A delegated consent (parent approving on behalf of a child) attributes the approval to the parent, not the subject', async ({
    browser,
    request,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    // No dedicated "parent"/"child" persona exists - the second, generic user account stands in
    // for the parent, and env.user (this file's usual subject) stands in for the child.
    test.skip(!hasSecondUser(), 'personas.user2 is not configured')
    const parent = env.secondUser()
    if (!parent) {
      throw new Error('Unreachable: hasSecondUser() already checked this above.')
    }

    const consentAdminPage = await loginAsConsentAdmin(browser)
    // authorizations lists only the parent, never the child - carbon-consent-mgt-core's model has
    // no separate "subject" field on an authorization; delegation is expressed purely by the
    // subjectId (child) and authorizations[].userId (parent) not matching.
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'PENDING',
      undefined,
      undefined,
      [{ userId: parent.username, type: 'PARENT' }],
    )

    // The parent approves on the child's behalf through the same self-service endpoint a subject
    // would use - IS's own consent-mgt resolves authorization by matching the caller against the
    // receipt's authorizations list, not by requiring caller === subject, which is exactly what
    // makes a delegated/guardian approval possible at all. Called directly via the API (not the
    // UI) since this test's point is the resulting history attribution, not the approve form.
    const parentPersonaState = await getPersonaState(browser, 'user-2', parent)
    const parentConsentApi = new ConsentApiClient(request, authHeadersFromPersonaState(parentPersonaState))
    const authorizeResponse = await parentConsentApi.authorizeMyConsent(consentId, 'APPROVED')
    expect(authorizeResponse.ok()).toBe(true)

    // The child - the consent's subject - sees their own consent's history with the approval
    // attributed to the PARENT, not to themselves: actionBy records who actually performed the
    // action (DPDPConsentHistoryListener.getActionBy(), the caller's own PrivilegedCarbonContext
    // username), which is a different thing from whose consent this is.
    const childPage = await loginAsUser(browser)
    const detailPage = new ConsentDetailPage(childPage, 'self')
    await detailPage.goto(consentId)
    // The page's own metadata card renders the subject as plain text (see 03.02.01) - confirms
    // the child, not the parent, is who this consent is about.
    await expect(childPage.getByText(env.user.username)).toBeVisible()
    await expect(detailPage.lifecycleRow('Approved', parent.username)).toBeVisible()
    await expect(detailPage.lifecycleRow('Approved', env.user.username)).toHaveCount(0)

    await detailPage.openFullHistoryDialog()
    const dialog = new ConsentFullHistoryDialogPage(childPage)
    await dialog.expand('Approved', parent.username)
    await expect(dialog.changedTag('Approved', parent.username)).toBeVisible()
    await dialog.close()

    await childPage.context().close()
    await consentAdminPage.context().close()
  })
})
