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
import { ComplaintCaseDetailPage } from '../../pages/ComplaintCaseDetailPage'
import { ComplaintDetailPage } from '../../pages/ComplaintDetailPage'
import { ComplaintListPage } from '../../pages/ComplaintListPage'
import { ComplaintQueuePage } from '../../pages/ComplaintQueuePage'
import { ComplaintSubmitDialog } from '../../pages/ComplaintSubmitDialog'
import { seedComplaint } from '../../utils/complaintSetup'
import { uniqueMarker } from '../../utils/testData'

/**
 * End-to-end scenarios driving both surfaces together through the real UI, in the order an actual
 * grievance-redressal case plays out - as opposed to every other file in this directory, which
 * exercises one surface/action in isolation. See tests/06-complaints-api/06.06 for the same
 * scenarios' API-only counterparts (officer-assisted phone intake, category/priority sweep,
 * concurrent replies) - not repeated here since they have no UI of their own to exercise.
 */
test.describe('Real-world complaint scenarios (UI)', () => {
  test('05.09.02 - A citizen replying to a Resolved complaint posts the message but does not reopen it', async ({
    browser,
  }) => {
    // ComplaintDetailPage.tsx's onSend only attaches a toStatus when the complaint is currently
    // WAITING_ON_CLIENT (see the file header comment in 05.04) - RESOLVED isn't handled, even
    // though StatusTransitionValidator.java's own backend rule explicitly allows and documents
    // RESOLVED -> AWAITING_INTERNAL_REVIEW as how a citizen reopens a resolved complaint. As the
    // frontend behaves today, replying to a resolved complaint posts the message but leaves the
    // complaint RESOLVED and hidden from the officer's default queue - this test asserts that
    // actual, current behavior rather than the reopen flow the backend alone would support.
    // Several full-page navigations/reloads happen below, each forcing a silent OIDC re-auth
    // round trip (see the matching comment on 05.05.01) - the default 30s test timeout is too
    // tight once that compounds across this test's many sequential steps.
    test.setTimeout(60_000)
    const dataPrincipalPage = await loginAsUser(browser)
    const officerPage = await loginAsConsentAdmin(browser)

    const listPage = new ComplaintListPage(dataPrincipalPage)
    await listPage.goto()
    await listPage.openSubmitDialog()
    const submitDialog = new ComplaintSubmitDialog(dataPrincipalPage)
    await submitDialog.selectCategory('Other')
    await submitDialog.fillDescription(`Automated UI test: reopen flow ${uniqueMarker('reopen')}`)
    await submitDialog.submit()
    const alertText = await listPage.successAlert.textContent()
    const referenceId = /complaint\s+(\S+)\s+has been submitted/.exec(alertText ?? '')?.[1]
    if (!referenceId) {
      throw new Error(`Could not read a reference id out of the success banner: "${alertText}"`)
    }

    const queuePage = new ComplaintQueuePage(officerPage)
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)
    await queuePage.openByReferenceId(referenceId)
    // Confirms the navigation itself actually landed before asserting on page content - see the
    // matching comment in 05.09.01.
    await expect(officerPage).toHaveURL(/\/complaint-management\/[^/]+$/, { timeout: 15_000 })
    const caseDetailPage = new ComplaintCaseDetailPage(officerPage)
    await caseDetailPage.selectNextStatusBeforeSending('In Progress')
    await caseDetailPage.sendReply(`Reviewing: ${uniqueMarker('reopen-review')}`)
    await caseDetailPage.selectNextStatusBeforeSending('Resolved')
    await caseDetailPage.sendAndConfirmResolve(`Believed resolved: ${uniqueMarker('reopen-resolve')}.`)
    await expect(caseDetailPage.resolvedLockedBanner).toBeVisible()

    // Resolved complaints are hidden from the officer's default queue view.
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)
    await expect(queuePage.rowByReferenceId(referenceId)).not.toBeVisible()

    // Citizen isn't satisfied and replies again. The citizen never left the /complaints list
    // after submitting - openByReferenceId navigates into the complaint's detail page for the
    // first time (see the matching comment in 05.09.01).
    await listPage.openByReferenceId(referenceId)
    await expect(dataPrincipalPage).toHaveURL(/\/complaints\/[^/]+$/, { timeout: 15_000 })
    const detailPage = new ComplaintDetailPage(dataPrincipalPage)
    await expect(detailPage.resolvedBanner).toBeVisible()
    const reopenMessage = `This is still happening, please look again: ${uniqueMarker('reopen-message')}`
    await detailPage.sendReply(reopenMessage)
    await expect(dataPrincipalPage.getByText(reopenMessage)).toBeVisible()
    await expect(detailPage.chipWithLabel('Resolved')).toBeVisible()

    // Still hidden from the officer's default queue view - the reply above left it RESOLVED.
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)
    await expect(queuePage.rowByReferenceId(referenceId)).not.toBeVisible()

    await dataPrincipalPage.context().close()
    await officerPage.context().close()
  })

  test('05.09.03 - A multi-round officer/citizen exchange leaves the whole thread visible to both sides', async ({
    browser,
    userComplaintApi,
  }) => {
    // Regression cover for the stale-timeline bug. Every DPDP DB connection is handed out with
    // autocommit off (JDBCPersistenceManager.getDBConnection), and read paths never commit, so
    // before DatabaseUtils.closeConnection started ending the transaction they returned their
    // connection to the pool mid-transaction. On MySQL - REPEATABLE READ, unlike the H2/Postgres
    // deployments - that pins a snapshot, and a GET timeline landing on such a connection served
    // rows from before the comment that had just been posted. The message appeared to vanish
    // until some later request happened to reuse a connection with a newer snapshot.
    //
    // What makes this catch it where 05.07.01's single reply does not: several rounds, each read
    // back from BOTH surfaces, and every read asserting the entire thread so far rather than only
    // the newest line. A single reply read back once can land on the very connection that just
    // committed it and pass while the bug is present. It is still probabilistic - which pooled
    // connection serves a given read is not controllable from here - but every extra round and
    // every extra full-thread assertion multiplies the chance of hitting a pinned snapshot.
    //
    // Status is asserted alongside the messages because the same stale read corrupted the
    // complaint row itself: a transition recorded FROM_STATUS=OPEN long after the complaint had
    // moved on, because requireComplaint read it from a pinned snapshot.
    //
    // Many full-page navigations, each forcing a silent OIDC re-auth round trip (see 05.05.01) -
    // the default 30s timeout is nowhere near enough once that compounds over five rounds.
    test.setTimeout(180_000)

    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'thread-roundtrip')
    const dataPrincipalPage = await loginAsUser(browser)
    const officerPage = await loginAsConsentAdmin(browser)
    const detailPage = new ComplaintDetailPage(dataPrincipalPage)
    const caseDetailPage = new ComplaintCaseDetailPage(officerPage)

    // Everything said so far, oldest first. Each read-back asserts all of it, not just the tail.
    const thread: string[] = []
    const expectWholeThread = async (
      surface: ComplaintDetailPage | ComplaintCaseDetailPage,
    ): Promise<void> => {
      for (const message of thread) {
        await expect(surface.timelineEntry(message)).toBeVisible()
      }
    }

    // Round 1 - the officer acknowledges. The assertion right after sendReply is the sender's own
    // post-write refetch (useSendManagedComplaintMessageMutation invalidates the detail query),
    // which is itself a read-after-write the bug broke.
    await caseDetailPage.goto(seeded.id)
    await expect(officerPage).toHaveURL(/\/complaint-management\/[^/]+$/, { timeout: 15_000 })
    const officerAck = `Officer ack: ${uniqueMarker('rt-officer-ack')}`
    await caseDetailPage.sendReply(officerAck)
    thread.push(officerAck)
    await expectWholeThread(caseDetailPage)

    await detailPage.goto(seeded.id)
    await expect(dataPrincipalPage).toHaveURL(/\/complaints\/[^/]+$/, { timeout: 15_000 })
    await expectWholeThread(detailPage)

    // Round 2 - the citizen answers.
    const citizenDetail = `Citizen detail: ${uniqueMarker('rt-citizen-detail')}`
    await detailPage.sendReply(citizenDetail)
    thread.push(citizenDetail)
    await expectWholeThread(detailPage)

    await caseDetailPage.goto(seeded.id)
    await expectWholeThread(caseDetailPage)

    // Round 3 - the officer asks for more information, moving the case to Waiting on Client.
    const officerQuestion = `Officer question: ${uniqueMarker('rt-officer-question')}`
    await caseDetailPage.selectNextStatusBeforeSending('Waiting on Client')
    await caseDetailPage.sendReply(officerQuestion)
    thread.push(officerQuestion)
    await expectWholeThread(caseDetailPage)
    await expect(caseDetailPage.chipWithLabel('Waiting on Client')).toBeVisible()

    await detailPage.goto(seeded.id)
    await expectWholeThread(detailPage)
    await expect(detailPage.chipWithLabel('Waiting on Client')).toBeVisible()
    await expect(detailPage.awaitingInfoBanner).toBeVisible()

    // Round 4 - the citizen answers the question. ComplaintDetailPage.tsx attaches
    // toStatus=AWAITING_INTERNAL_REVIEW precisely because the case is WAITING_ON_CLIENT, so this
    // round moves the status back off the citizen's own reply (see 05.04's file header).
    const citizenAnswer = `Citizen answer: ${uniqueMarker('rt-citizen-answer')}`
    await detailPage.sendReply(citizenAnswer)
    thread.push(citizenAnswer)
    await expectWholeThread(detailPage)
    await expect(detailPage.chipWithLabel('Waiting on Internal Review')).toBeVisible()

    await caseDetailPage.goto(seeded.id)
    await expectWholeThread(caseDetailPage)
    await expect(caseDetailPage.chipWithLabel('Waiting on Internal Review')).toBeVisible()

    // Round 5 - the officer closes the loop. Read back one last time from both surfaces, so the
    // final assertion covers the full five-message thread from each side independently.
    const officerFollowUp = `Officer follow-up: ${uniqueMarker('rt-officer-followup')}`
    await caseDetailPage.sendReply(officerFollowUp)
    thread.push(officerFollowUp)
    await expectWholeThread(caseDetailPage)

    await detailPage.goto(seeded.id)
    await expectWholeThread(detailPage)

    // One last read straight off the API, bypassing the SPA's own query cache entirely - the UI
    // assertions above can only prove what the client had already fetched.
    const citizenTimeline = await (await userComplaintApi.getMyTimeline(seeded.id)).json()
    const citizenMessages = citizenTimeline.data.map((entry: { message: string }) => entry.message)
    for (const message of thread) {
      expect(citizenMessages).toContain(message)
    }

    await dataPrincipalPage.context().close()
    await officerPage.context().close()
  })
})
