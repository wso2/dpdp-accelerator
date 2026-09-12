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
import { moveComplaintToStatus, seedComplaint } from '../../utils/complaintSetup'
import { uniqueMarker } from '../../utils/testData'

/**
 * A Complaint Officer replying on a case's activity thread - ComplaintReplyComposer.tsx as
 * rendered by ComplaintCaseDetailPage.tsx, with canPostInternalNote=true and statusOptions from
 * COMPLAINT_NEXT_STATUSES[complaint.status] (unlike the Data Principal composer in
 * 07.04-data-principal-replying-in-thread.spec.ts, which has neither).
 *
 * COMPLAINT_NEXT_STATUSES.ts is deliberately narrower than StatusTransitionValidator.java allows
 * (e.g. it never offers OPEN -> AWAITING_INTERNAL_REVIEW as a menu option, and offers nothing at
 * all once RESOLVED) - not a bug, a UI choice to expose a curated subset of the backend's real
 * transition graph. 07.07.03 asserts that curated menu, not the backend's full one; the
 * backend's own rules live in StatusTransitionValidator.java and have no E2E coverage of their
 * own (see TEST-SCENARIOS.md, "Known gaps").
 */
test.describe("Complaint Officer replying in a case thread (UI)", () => {
  test('07.07.01 - Sending a public reply appends it to the activity feed', async ({
    browser,
    userComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'officer-reply-basic')
    const officerPage = await loginAsConsentAdmin(browser)
    const caseDetailPage = new ComplaintCaseDetailPage(officerPage)
    await caseDetailPage.goto(seeded.id)

    const message = `Automated UI test: acknowledging this complaint (${uniqueMarker('officer-reply')}).`
    await caseDetailPage.sendReply(message)
    await expect(officerPage.getByText(message)).toBeVisible()
    await officerPage.context().close()
  })

  test('07.07.02 - Sending a reply with a status change transitions the complaint and records the transition', async ({
    browser,
    userComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'officer-reply-with-transition')
    const officerPage = await loginAsConsentAdmin(browser)
    const caseDetailPage = new ComplaintCaseDetailPage(officerPage)
    await caseDetailPage.goto(seeded.id)

    await caseDetailPage.selectNextStatusBeforeSending('In Progress')
    await caseDetailPage.sendReply(`Picking this up for review: ${uniqueMarker('pickup')}`)
    await expect(caseDetailPage.chipWithLabel('In Progress')).toBeVisible()
    await officerPage.context().close()
  })

  test("07.07.03 - Only OPEN's curated next statuses (In Progress, Waiting on Client) appear in the status menu", async ({
    browser,
    userComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'officer-status-menu')
    const officerPage = await loginAsConsentAdmin(browser)
    const caseDetailPage = new ComplaintCaseDetailPage(officerPage)
    await caseDetailPage.goto(seeded.id)

    await caseDetailPage.sendOptionsButton.click()
    await expect(officerPage.getByRole('menuitem', { name: 'In Progress', exact: true })).toBeVisible()
    await expect(officerPage.getByRole('menuitem', { name: 'Waiting on Client', exact: true })).toBeVisible()
    await expect(officerPage.getByRole('menuitem', { name: 'Resolved', exact: true })).toHaveCount(0)
    await expect(
      officerPage.getByRole('menuitem', { name: 'Waiting on Internal Review', exact: true }),
    ).toHaveCount(0)
    await officerPage.context().close()
  })

  test('07.07.04 - Switching to "Internal note" posts a note the Data Principal never sees', async ({
    browser,
    userComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'officer-internal-note')
    const officerPage = await loginAsConsentAdmin(browser)
    const caseDetailPage = new ComplaintCaseDetailPage(officerPage)
    await caseDetailPage.goto(seeded.id)

    const note = `Internal-only note: ${uniqueMarker('internal-note')} - should never reach the Data Principal.`
    await caseDetailPage.switchToInternalNote()
    await caseDetailPage.sendReply(note)
    await expect(officerPage.getByText(note)).toBeVisible()

    const citizenTimeline = await (await userComplaintApi.getMyTimeline(seeded.id)).json()
    const citizenMessages = citizenTimeline.data.map((entry: { message: string }) => entry.message)
    expect(citizenMessages).not.toContain(note)
    await officerPage.context().close()
  })

  test('07.07.05 - Resolving requires confirmation, and cancelling leaves the complaint open and the draft intact', async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'officer-resolve-cancel')
    await moveComplaintToStatus(officerComplaintApi, seeded.id, 'IN_PROGRESS')

    const officerPage = await loginAsConsentAdmin(browser)
    const caseDetailPage = new ComplaintCaseDetailPage(officerPage)
    await caseDetailPage.goto(seeded.id)

    const message = `Confirmed and resolved - thanks for the details (${uniqueMarker('resolve-cancel')}).`
    await caseDetailPage.selectNextStatusBeforeSending('Resolved')
    await caseDetailPage.replyField.fill(message)
    await caseDetailPage.sendButton.click()
    await expect(caseDetailPage.resolveDialog).toBeVisible()

    await caseDetailPage.resolveCancelButton.click()
    await expect(caseDetailPage.resolveDialog).toHaveCount(0)
    // ComplaintCaseDetailPage.tsx's onClose only clears pendingResolve state, not the composer's
    // own draft (see ComplaintReplyComposer.tsx's onSent contract) - the message the officer typed
    // is still there to resend or edit.
    await expect(caseDetailPage.replyField).toHaveValue(message)
    await expect(caseDetailPage.chipWithLabel('In Progress')).toBeVisible()

    const complaint = await (await officerComplaintApi.getComplaint(seeded.id)).json()
    expect(complaint.status).toBe('IN_PROGRESS')
    await officerPage.context().close()
  })

  test('07.07.06 - Confirming the resolve dialog resolves the complaint and locks the composer', async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'officer-resolve-confirm')
    await moveComplaintToStatus(officerComplaintApi, seeded.id, 'IN_PROGRESS')

    const officerPage = await loginAsConsentAdmin(browser)
    const caseDetailPage = new ComplaintCaseDetailPage(officerPage)
    await caseDetailPage.goto(seeded.id)

    await caseDetailPage.selectNextStatusBeforeSending('Resolved')
    await caseDetailPage.sendAndConfirmResolve(`Resolved after review: ${uniqueMarker('resolve-confirm')}.`)

    await expect(caseDetailPage.resolvedLockedBanner).toBeVisible()
    await expect(caseDetailPage.replyField).toHaveCount(0)
    await officerPage.context().close()
  })

  test('07.07.07 - Sending a reply with a status change and an attachment transitions the complaint and uploads the file', async ({
    browser,
    userComplaintApi,
  }) => {
    // Exercises message + nextStatus + attachment together, the way a real
    // resolving-with-evidence workflow does - 07.07.01/07.07.02 cover message/nextStatus alone,
    // 07.04.07 covers attachment staging alone on the self surface.
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'officer-reply-with-evidence')
    const officerPage = await loginAsConsentAdmin(browser)
    const caseDetailPage = new ComplaintCaseDetailPage(officerPage)
    await caseDetailPage.goto(seeded.id)

    await caseDetailPage.attachFile('evidence.pdf', 'application/pdf')
    await expect(caseDetailPage.stagedAttachmentName('evidence.pdf')).toBeVisible()

    await caseDetailPage.selectNextStatusBeforeSending('In Progress')
    const message = `Picking this up, evidence attached: ${uniqueMarker('evidence')}.`
    await caseDetailPage.sendReply(message)

    await expect(officerPage.getByText(message)).toBeVisible()
    await expect(caseDetailPage.chipWithLabel('In Progress')).toBeVisible()
    await expect(caseDetailPage.sentAttachmentTile('evidence.pdf')).toBeVisible()

    // The composer clears its own draft state after a successful send - a stray leftover draft
    // would indicate onSend's reset (setDraft/setDraftFiles/setPendingStatus) silently failed.
    await expect(caseDetailPage.replyField).toHaveValue('')
    await expect(caseDetailPage.stagedAttachmentName('evidence.pdf')).not.toBeVisible()
    await officerPage.context().close()
  })
})
