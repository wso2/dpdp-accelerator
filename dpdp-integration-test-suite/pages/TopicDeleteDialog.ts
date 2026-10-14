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

/** TopicDeleteDialog.tsx - a thin wrapper over the shared ConsentActionDialog (see ComplaintSubmitDialog.ts's sibling pattern). */
export class TopicDeleteDialog {
  readonly root: Locator
  readonly confirmButton: Locator
  readonly cancelButton: Locator

  constructor(page: Page) {
    this.root = page.getByRole('dialog')
    this.confirmButton = this.root.getByRole('button', { name: 'Delete Topic' })
    this.cancelButton = this.root.getByRole('button', { name: 'Cancel' })
  }

  async confirm(): Promise<void> {
    await this.confirmButton.click()
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click()
  }
}
