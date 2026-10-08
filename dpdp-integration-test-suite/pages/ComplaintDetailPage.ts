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

import { type Locator, type Page } from '@playwright/test'

/**
 * ComplaintDetailPage.tsx at /complaints/:id - a Data Principal's own complaint, its activity
 * feed, and ComplaintReplyComposer.tsx with canPostInternalNote={false} (no "Internal note"
 * toggle - a Data Principal can only ever post a public reply).
 */
export class ComplaintDetailPage {
  readonly backButton: Locator
  readonly notFoundHeading: Locator
  readonly awaitingInfoBanner: Locator
  readonly resolvedBanner: Locator
  readonly activityTab: Locator
  readonly attachmentsTab: Locator
  readonly replyField: Locator
  readonly sendButton: Locator
  readonly attachButton: Locator
  readonly removeAttachmentButton: Locator
  readonly internalNoteToggle: Locator

  constructor(private readonly page: Page) {
    this.backButton = page.getByRole('button', { name: /^Back/ })
    this.notFoundHeading = page.getByRole('heading', { name: 'Complaint not found' })
    this.awaitingInfoBanner = page.getByText(
      'The complaint officer is waiting on more information from you. Please reply below.',
    )
    this.resolvedBanner = page.getByText(/This complaint has been resolved\./)
    this.activityTab = page.getByRole('tab', { name: 'Activity' })
    this.attachmentsTab = page.getByRole('tab', { name: /Attachments/ })
    this.replyField = page.getByPlaceholder('Write a reply to the user...')
    // Exact: "Send" alone, not the "More send options" chevron button next to it in the same
    // ButtonGroup - the Data Principal composer never renders that second button at all, since
    // statusOptions is always [] for canPostInternalNote={false} (see ComplaintDetailPage.tsx).
    this.sendButton = page.getByRole('button', { name: 'Send', exact: true })
    // exact: true - a substring match on "Attach" also matches "Remove attachment" (case-insensitive
    // substring "attach" appears in "attachment"), causing a strict-mode violation once a file is staged.
    this.attachButton = page.getByRole('button', { name: 'Attach', exact: true })
    this.removeAttachmentButton = page.getByRole('button', { name: 'Remove attachment' })
    this.internalNoteToggle = page.getByRole('button', { name: 'Internal note' })
  }

  async goto(complaintId: string): Promise<void> {
    await this.page.goto(`complaints/${complaintId}`)
  }

  async openAttachmentsTab(): Promise<void> {
    await this.attachmentsTab.click()
  }

  async sendReply(message: string): Promise<void> {
    await this.replyField.fill(message)
    await this.sendButton.click()
  }

  async attachFile(name: string, mimeType: string, buffer: Buffer = Buffer.from('fake-attachment-content')): Promise<void> {
    await this.page.locator('input[type="file"]').setInputFiles({ name, mimeType, buffer })
  }

  stagedAttachmentName(name: string): Locator {
    return this.page.getByText(name, { exact: true })
  }

  /** An attachment tile in the activity feed - AttachmentTile sets `title={fileName}`, which becomes its accessible name (no visible text otherwise). */
  sentAttachmentTile(fileName: string): Locator {
    return this.page.getByRole('button', { name: fileName })
  }

  timelineEntry(text: string | RegExp): Locator {
    return this.page.getByText(text)
  }

  /**
   * Scopes to the status Chip's own label - a plain page.getByText(label, {exact:true}) also
   * matches the activity feed's timeline entries, which routinely mention the same status name in
   * prose, causing a strict-mode violation.
   */
  chipWithLabel(label: string): Locator {
    return this.page.locator('.MuiChip-label').and(this.page.getByText(label, { exact: true }))
  }
}
