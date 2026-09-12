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
import { moveComplaintToStatus, seedComplaint } from '../../utils/complaintSetup'
import { uniqueMarker } from '../../utils/testData'

/**
 * A Data Principal replying on their own complaint's activity thread - ComplaintReplyComposer.tsx
 * as rendered by ComplaintDetailPage.tsx, with canPostInternalNote=false (no "Internal note"
 * toggle - a Data Principal can only ever post a public reply, unlike the officer surface in
 * 07.07-officer-replying-in-thread.spec.ts).
 *
 * ComplaintDetailPage.tsx's onSend attaches a toStatus of AWAITING_INTERNAL_REVIEW in the two
 * cases StatusTransitionValidator.java allows a reply to auto-advance - WAITING_ON_CLIENT (the
 * officer asked for information, see 07.04.05) and RESOLVED (a reply is the only way a closed
 * complaint reopens, see 07.04.08). Every other status (OPEN, IN_PROGRESS) posts the reply with
 * no toStatus at all and is left unchanged - see 07.04.04 and 07.04.06.
 */
test.describe('Data Principal replying in a complaint thread (UI)', () => {
  test('07.04.01 - Sending a reply appends it to the activity feed', async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    // Seeded straight into AWAITING_INTERNAL_REVIEW so this reply carries no implicit status
    // transition (see the file header comment) - isolates "does the message show up" from any
    // status-transition side effect, covered separately below.
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'reply-basic')
    await moveComplaintToStatus(officerComplaintApi, seeded.id, 'AWAITING_INTERNAL_REVIEW')

    const dataPrincipalPage = await loginAsUser(browser)
    const detailPage = new ComplaintDetailPage(dataPrincipalPage)
    await detailPage.goto(seeded.id)

    const message = `Automated UI test reply: ${uniqueMarker('reply-basic')}`
    await detailPage.sendReply(message)
    await expect(dataPrincipalPage.getByText(message)).toBeVisible()
    await dataPrincipalPage.context().close()
  })

  test("07.04.02 - Sending a reply clears the composer's text field", async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'reply-clears')
    await moveComplaintToStatus(officerComplaintApi, seeded.id, 'AWAITING_INTERNAL_REVIEW')

    const dataPrincipalPage = await loginAsUser(browser)
    const detailPage = new ComplaintDetailPage(dataPrincipalPage)
    await detailPage.goto(seeded.id)

    await detailPage.sendReply(`Automated UI test reply: ${uniqueMarker('reply-clears')}`)
    await expect(detailPage.replyField).toHaveValue('')
    await dataPrincipalPage.context().close()
  })

  test('07.04.03 - The composer has no "Internal note" toggle for a Data Principal', async ({
    browser,
    userComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'no-internal-toggle')
    const dataPrincipalPage = await loginAsUser(browser)
    const detailPage = new ComplaintDetailPage(dataPrincipalPage)
    await detailPage.goto(seeded.id)

    // ComplaintDetailPage.tsx passes canPostInternalNote={false} to ComplaintReplyComposer, which
    // then never renders the ToggleButtonGroup at all - not merely disabled.
    await expect(detailPage.internalNoteToggle).toHaveCount(0)
    await dataPrincipalPage.context().close()
  })

  test('07.04.04 - Replying to a freshly-OPEN complaint posts the message without changing its status', async ({
    browser,
    userComplaintApi,
  }) => {
    // OPEN isn't WAITING_ON_CLIENT, so ComplaintDetailPage.tsx's onSend attaches no toStatus at
    // all here (see the file header comment) - the officer hasn't asked the citizen for anything,
    // so an unprompted reply shouldn't move the complaint into internal review on its own.
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'reply-from-open')
    const dataPrincipalPage = await loginAsUser(browser)
    const detailPage = new ComplaintDetailPage(dataPrincipalPage)
    await detailPage.goto(seeded.id)

    const message = `Adding more context up front: ${uniqueMarker('reply-from-open')}`
    await detailPage.sendReply(message)
    await expect(dataPrincipalPage.getByText(message)).toBeVisible()
    await expect(detailPage.chipWithLabel('Open')).toBeVisible()
    await dataPrincipalPage.context().close()
  })

  test('07.04.05 - Replying to a complaint the officer asked for more information on routes it back for internal review', async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'reply-from-waiting-on-client')
    await moveComplaintToStatus(officerComplaintApi, seeded.id, 'WAITING_ON_CLIENT')

    const dataPrincipalPage = await loginAsUser(browser)
    const detailPage = new ComplaintDetailPage(dataPrincipalPage)
    await detailPage.goto(seeded.id)
    await expect(detailPage.awaitingInfoBanner).toBeVisible()

    await detailPage.sendReply(`Here is the additional information you requested: ${uniqueMarker('client-reply')}`)
    await expect(detailPage.chipWithLabel('Waiting on Internal Review')).toBeVisible()
    await dataPrincipalPage.context().close()
  })

  test('07.04.06 - Replying while the complaint is In Progress posts the message without changing its status', async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    // Same "no toStatus unless WAITING_ON_CLIENT" rule as 07.04.04 - IN_PROGRESS is not
    // WAITING_ON_CLIENT, so this reply carries no implicit transition either.
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'reply-from-in-progress')
    await moveComplaintToStatus(officerComplaintApi, seeded.id, 'IN_PROGRESS')

    const dataPrincipalPage = await loginAsUser(browser)
    const detailPage = new ComplaintDetailPage(dataPrincipalPage)
    await detailPage.goto(seeded.id)

    const message = `This reply should post normally: ${uniqueMarker('in-progress-reply')}`
    await detailPage.replyField.fill(message)
    const [response] = await Promise.all([
      dataPrincipalPage.waitForResponse(
        (candidate) => candidate.url().includes('/comments') && candidate.request().method() === 'POST',
      ),
      detailPage.sendButton.click(),
    ])
    // MeComplaintCommentEndpoint.java returns Response.ok(...) for a posted comment - 200, not
    // 201 (only complaint creation and attachment upload return 201 in this API).
    expect(response.status()).toBe(200)

    await expect(dataPrincipalPage.getByText(message)).toBeVisible()
    await expect(detailPage.chipWithLabel('In Progress')).toBeVisible()
    await dataPrincipalPage.context().close()
  })

  test('07.04.07 - "Attach" is disabled while a file is staged, and removing it lets a different file be attached', async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'reply-attachment')
    await moveComplaintToStatus(officerComplaintApi, seeded.id, 'AWAITING_INTERNAL_REVIEW')

    const dataPrincipalPage = await loginAsUser(browser)
    const detailPage = new ComplaintDetailPage(dataPrincipalPage)
    await detailPage.goto(seeded.id)

    await detailPage.attachFile('first.pdf', 'application/pdf')
    await expect(detailPage.stagedAttachmentName('first.pdf')).toBeVisible()
    await expect(detailPage.attachButton).toBeDisabled()

    await detailPage.removeAttachmentButton.click()
    await expect(detailPage.attachButton).toBeEnabled()

    await detailPage.attachFile('second.png', 'image/png')
    await expect(detailPage.stagedAttachmentName('second.png')).toBeVisible()
    await expect(detailPage.stagedAttachmentName('first.pdf')).not.toBeVisible()
    await dataPrincipalPage.context().close()
  })

  test('07.04.08 - Replying to a resolved complaint reopens it for internal review', async ({
    browser,
    userComplaintApi,
    officerComplaintApi,
  }) => {
    // RESOLVED is the one status an officer cannot manually transition out of
    // (StatusTransitionValidator.java allows only RESOLVED -> AWAITING_INTERNAL_REVIEW), so a
    // reply here is the sole way a closed complaint reopens. OPEN -> RESOLVED is not a direct
    // transition either, hence the explicit IN_PROGRESS hop that moveComplaintToStatus only
    // automates for AWAITING_INTERNAL_REVIEW.
    const seeded = await seedComplaint(userComplaintApi, 'OTHER', 'reply-from-resolved')
    await moveComplaintToStatus(officerComplaintApi, seeded.id, 'IN_PROGRESS')
    await moveComplaintToStatus(officerComplaintApi, seeded.id, 'RESOLVED')

    const dataPrincipalPage = await loginAsUser(browser)
    const detailPage = new ComplaintDetailPage(dataPrincipalPage)
    await detailPage.goto(seeded.id)
    await expect(detailPage.resolvedBanner).toBeVisible()

    await detailPage.sendReply(`This was not actually resolved: ${uniqueMarker('resolved-reply')}`)
    await expect(detailPage.chipWithLabel('Waiting on Internal Review')).toBeVisible()
    await dataPrincipalPage.context().close()
  })
})
