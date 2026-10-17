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
 * SubscriptionRegisterDialog.tsx. Its Topic <Select> is populated by a live
 * `fetchTopics({limit:100, offset:0, status:'ACTIVE'})` call on open - only ACTIVE topics ever
 * appear as options, so a test must ensure its topic is ACTIVE (system topics qualify too)
 * before opening this dialog. Shared Secret is pre-filled with a random 32-hex-char value the
 * moment the dialog mounts - `sharedSecretValue()` reads whatever the field currently holds
 * rather than assuming a test-supplied one.
 */
export class SubscriptionRegisterDialog {
  readonly root: Locator
  readonly nameField: Locator
  readonly categorySelect: Locator
  readonly topicsInput: Locator
  readonly filterModeSelect: Locator
  readonly purposesField: Locator
  readonly deliveryModeSelect: Locator
  readonly callbackUrlField: Locator
  readonly sharedSecretField: Locator
  readonly generateSecretButton: Locator
  readonly submitButton: Locator
  readonly cancelButton: Locator
  readonly selectAllTopicsButton: Locator
  readonly clearTopicsButton: Locator
  readonly topicsPagerBack: Locator
  readonly topicsPagerNext: Locator
  readonly categoryConfirmDialog: Locator
  readonly confirmCategoryChangeButton: Locator
  readonly cancelCategoryChangeButton: Locator
  readonly nameRequiredError: Locator
  readonly topicRequiredError: Locator
  readonly purposesRequiredError: Locator
  readonly callbackUrlRequiredError: Locator
  readonly callbackUrlInvalidError: Locator
  readonly secretRequiredError: Locator

  constructor(private readonly page: Page) {
    this.root = page.getByRole('dialog', { name: 'Create Subscription' })
    this.nameField = this.root.getByLabel('Subscription Name')
    this.categorySelect = this.root.getByRole('combobox', { name: 'Topic Category' })
    this.topicsInput = this.root.getByLabel('Topics')
    this.selectAllTopicsButton = this.root.getByRole('button', { name: 'Select all' })
    this.clearTopicsButton = this.root.getByRole('button', { name: 'Clear' })
    this.topicsPagerBack = page.getByRole('button', { name: 'Back' })
    this.topicsPagerNext = page.getByRole('button', { name: 'Next' })
    this.categoryConfirmDialog = page.getByRole('dialog', { name: 'Change topic category?' })
    this.confirmCategoryChangeButton = this.categoryConfirmDialog.getByRole('button', { name: 'Change Category' })
    this.cancelCategoryChangeButton = this.categoryConfirmDialog.getByRole('button', { name: 'Cancel' })
    this.filterModeSelect = this.root.getByRole('combobox', { name: 'Consent Purpose Filter Mode' })
    this.purposesField = this.root.getByLabel('Consent Purposes (comma-separated)')
    this.deliveryModeSelect = this.root.getByRole('combobox', { name: 'Delivery Mode' })
    this.callbackUrlField = this.root.getByLabel('Webhook Callback URL')
    this.sharedSecretField = this.root.getByLabel('Shared Secret')
    this.generateSecretButton = this.root.getByRole('button', { name: 'Generate new secret' })
    this.submitButton = this.root.getByRole('button', { name: /^Create/ })
    this.cancelButton = this.root.getByRole('button', { name: 'Cancel' })
    this.nameRequiredError = this.root.getByText('Subscription name is required.')
    this.topicRequiredError = this.root.getByText('Topic is required.')
    this.purposesRequiredError = this.root.getByText(
      'Purposes are required when filtering by specific or all-except.',
    )
    this.callbackUrlRequiredError = this.root.getByText('Callback URL is required for webhook subscriptions.')
    this.callbackUrlInvalidError = this.root.getByText('Please provide a valid absolute URL (http:// or https://).')
    this.secretRequiredError = this.root.getByText('Shared secret is required.')
  }

  async fillName(name: string): Promise<void> {
    await this.nameField.fill(name)
  }

  async selectCategory(label: 'Consent Topics' | 'User Topics' | 'Custom Topics'): Promise<void> {
    await this.categorySelect.click()
    await this.page.getByRole('option', { name: label, exact: true }).click()
  }

  async confirmCategoryChange(): Promise<void> {
    await this.confirmCategoryChangeButton.click()
  }

  async cancelCategoryChange(): Promise<void> {
    await this.cancelCategoryChangeButton.click()
  }

  async clickSelectAllTopics(): Promise<void> {
    await this.selectAllTopicsButton.click()
  }

  async clickClearTopics(): Promise<void> {
    await this.clearTopicsButton.click()
  }

  async selectTopic(name: string): Promise<void> {
    await this.topicsInput.click()
    await this.page.getByRole('option', { name, exact: true }).click()
  }

  topicChip(name: string): Locator {
    return this.root.locator('.MuiChip-root', { hasText: name })
  }

  async deleteTopic(name: string): Promise<void> {
    await this.topicChip(name).locator('.MuiChip-deleteIcon').click()
  }

  async selectFilterMode(label: 'All Purposes' | 'Specific Purposes' | 'All Except Purposes'): Promise<void> {
    await this.filterModeSelect.click()
    await this.page.getByRole('option', { name: label, exact: true }).click()
  }

  async fillPurposes(commaSeparated: string): Promise<void> {
    await this.purposesField.fill(commaSeparated)
  }

  async selectDeliveryMode(label: 'Webhook' | 'Poll'): Promise<void> {
    await this.deliveryModeSelect.click()
    await this.page.getByRole('option', { name: label, exact: true }).click()
  }

  async fillCallbackUrl(url: string): Promise<void> {
    await this.callbackUrlField.fill(url)
  }

  async fillSharedSecret(secret: string): Promise<void> {
    await this.sharedSecretField.fill(secret)
  }

  async clearSharedSecret(): Promise<void> {
    await this.sharedSecretField.fill('')
  }

  sharedSecretValue(): Promise<string> {
    return this.sharedSecretField.inputValue()
  }

  async submit(): Promise<void> {
    await this.submitButton.click()
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click()
  }
}
