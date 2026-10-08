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
import type { ComplaintStatusLabel } from './ComplaintQueuePage'

/**
 * ComplaintCaseDetailPage.tsx at /complaint-management/:id - ComplaintReplyComposer.tsx with
 * canPostInternalNote={true} and statusOptions from COMPLAINT_NEXT_STATUSES[complaint.status]
 * (unlike ComplaintDetailPage.ts, which has neither).
 */
export class ComplaintCaseDetailPage {
  readonly backButton: Locator
  readonly notFoundHeading: Locator
  readonly resolvedLockedBanner: Locator
  readonly activityTab: Locator
  readonly attachmentsTab: Locator
  readonly replyToggle: Locator
  readonly internalNoteToggle: Locator
  readonly replyField: Locator
  /** Matches "Send" or the dynamic "Send and mark as <status>" - excludes the separate "More send options" chevron button, which never starts with "Send". */
  readonly sendButton: Locator
  readonly sendOptionsButton: Locator
  readonly attachButton: Locator
  readonly removeAttachmentButton: Locator
  readonly resolveDialog: Locator
  readonly resolveConfirmButton: Locator
  readonly resolveCancelButton: Locator

  constructor(private readonly page: Page) {
    this.backButton = page.getByRole('button', { name: /^Back/ })
    this.notFoundHeading = page.getByRole('heading', { name: 'Complaint not found' })
    this.resolvedLockedBanner = page.getByText(/This complaint has been resolved and is now locked\./)
    this.activityTab = page.getByRole('tab', { name: 'Activity' })
    this.attachmentsTab = page.getByRole('tab', { name: /Attachments/ })
    this.replyToggle = page.getByRole('button', { name: 'Reply', exact: true })
    this.internalNoteToggle = page.getByRole('button', { name: 'Internal note' })
    // i18n: composerPlaceholderPublic is "Write a reply to the user...", composerPlaceholderInternal
    // is "Write an internal note...". The indefinite article changes ("a" vs "an") - a naive
    // "Write a (reply to the user|internal note)" pattern never matches the internal placeholder.
    this.replyField = page.getByPlaceholder(/Write (a reply to the user|an internal note)/)
    this.sendButton = page.getByRole('button', { name: /^Send/ })
    this.sendOptionsButton = page.getByRole('button', { name: 'More send options' })
    // exact: true - a substring match on "Attach" also matches "Remove attachment" (case-insensitive
    // substring "attach" appears in "attachment"), causing a strict-mode violation once a file is staged.
    this.attachButton = page.getByRole('button', { name: 'Attach', exact: true })
    this.removeAttachmentButton = page.getByRole('button', { name: 'Remove attachment' })
    this.resolveDialog = page.getByRole('dialog')
    this.resolveConfirmButton = this.resolveDialog.getByRole('button', { name: 'Resolve Complaint' })
    this.resolveCancelButton = this.resolveDialog.getByRole('button', { name: 'Cancel' })
  }

  async goto(complaintId: string): Promise<void> {
    await this.page.goto(`complaint-management/${complaintId}`)
  }

  async openAttachmentsTab(): Promise<void> {
    await this.attachmentsTab.click()
  }

  async switchToInternalNote(): Promise<void> {
    await this.internalNoteToggle.click()
  }

  async switchToPublicReply(): Promise<void> {
    await this.replyToggle.click()
  }

  /** Opens the status menu and picks one target status - "Resolved" still requires confirmResolve() afterwards. */
  async selectNextStatusBeforeSending(label: ComplaintStatusLabel): Promise<void> {
    await this.sendOptionsButton.click()
    await this.page.getByRole('menuitem', { name: label, exact: true }).click()
  }

  async clearPendingStatus(): Promise<void> {
    await this.sendOptionsButton.click()
    await this.page.getByRole('menuitem', { name: 'Send only' }).click()
  }

  async sendReply(message: string): Promise<void> {
    await this.replyField.fill(message)
    await this.sendButton.click()
  }

  /** Sends with a queued "Resolved" transition and confirms the ComplaintResolveConfirmDialog that opens instead of sending immediately. */
  async sendAndConfirmResolve(message: string): Promise<void> {
    await this.replyField.fill(message)
    await this.sendButton.click()
    await this.resolveConfirmButton.click()
  }

  async attachFile(name: string, mimeType: string, buffer: Buffer = Buffer.from('fake-attachment-content')): Promise<void> {
    await this.page.locator('input[type="file"]').setInputFiles({ name, mimeType, buffer })
  }

  stagedAttachmentName(name: string): Locator {
    return this.page.getByText(name, { exact: true })
  }

  sentAttachmentTile(fileName: string): Locator {
    return this.page.getByRole('button', { name: fileName })
  }

  timelineEntry(text: string | RegExp): Locator {
    return this.page.getByText(text)
  }

  /**
   * Scopes to a status/priority Chip's own label - a plain page.getByText(label, {exact:true})
   * also matches the activity feed's timeline entries, which routinely mention the same status
   * name in prose (e.g. "Status changed to In Progress"), causing a strict-mode violation.
   */
  chipWithLabel(label: string): Locator {
    return this.page.locator('.MuiChip-label').and(this.page.getByText(label, { exact: true }))
  }
}
