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

/** TopicRegisterDialog.tsx, opened from TopicsPage's "Create Topic" button. */
export class TopicRegisterDialog {
  readonly root: Locator
  readonly nameField: Locator
  readonly descriptionField: Locator
  readonly submitButton: Locator
  readonly cancelButton: Locator
  readonly nameRequiredError: Locator

  constructor(page: Page) {
    this.root = page.getByRole('dialog')
    this.nameField = this.root.getByLabel('Topic Name')
    this.descriptionField = this.root.getByLabel('Description')
    // Matches both "Create Topic" (idle) and "Creating..." (loading) states via the stable prefix.
    this.submitButton = this.root.getByRole('button', { name: /^Create/ })
    this.cancelButton = this.root.getByRole('button', { name: 'Cancel' })
    this.nameRequiredError = this.root.getByText('Topic name is required.')
  }

  async fillName(name: string): Promise<void> {
    await this.nameField.fill(name)
  }

  async fillDescription(description: string): Promise<void> {
    await this.descriptionField.fill(description)
  }

  async submit(): Promise<void> {
    await this.submitButton.click()
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click()
  }

  async register(name: string, description?: string): Promise<void> {
    await this.fillName(name)
    if (description !== undefined) {
      await this.fillDescription(description)
    }
    await this.submit()
  }
}
