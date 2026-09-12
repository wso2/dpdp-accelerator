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

/** PurposeFormDialog.tsx, opened from PurposeListPage's "Add Purpose" button. */
export class PurposeFormDialog {
  readonly root: Locator
  readonly title: Locator
  readonly nameField: Locator
  readonly typeField: Locator
  readonly versionField: Locator
  readonly descriptionField: Locator
  readonly elementsPicker: Locator
  readonly createButton: Locator
  readonly cancelButton: Locator
  readonly errorAlert: Locator

  constructor(private readonly page: Page) {
    this.root = page.getByRole('dialog')
    this.title = this.root.getByRole('heading', { name: 'Add Purpose' })
    // Required fields render their label as "<Name> *" (MUI's asterisk indicator is part of the
    // <label>'s text content, which is what Playwright's getByLabel matches against, even
    // though the asterisk span itself is aria-hidden) - confirmed empirically, not guessed.
    this.nameField = this.root.getByLabel('Name *', { exact: true })
    this.typeField = this.root.getByLabel('Type *', { exact: true })
    this.versionField = this.root.getByLabel('Version *', { exact: true })
    this.descriptionField = this.root.getByLabel('Description', { exact: true })
    this.elementsPicker = this.root.getByLabel('Elements', { exact: true })
    this.createButton = this.root.getByRole('button', { name: 'Create' })
    this.cancelButton = this.root.getByRole('button', { name: 'Cancel' })
    this.errorAlert = this.root.getByRole('alert')
  }

  async fill(fields: { name?: string; type?: string; version?: string; description?: string }): Promise<void> {
    if (fields.name !== undefined) {
      await this.nameField.fill(fields.name)
    }
    if (fields.type !== undefined) {
      await this.typeField.fill(fields.type)
    }
    if (fields.version !== undefined) {
      await this.versionField.fill(fields.version)
    }
    if (fields.description !== undefined) {
      await this.descriptionField.fill(fields.description)
    }
  }

  /**
   * Selects whichever elements the picker lists first (one per entry in `mandatoryFlags`,
   * in order) and toggles each one's Mandatory checkbox accordingly - deliberately not
   * searching for specific just-created elements by name. The picker only fetches its first
   * 200 elements (oldest first, see PurposeElementPicker.tsx), so a freshly created one is not
   * guaranteed to be among them once the shared environment has accumulated more than that -
   * the same class of gap as the catalog lists' own pagination.
   * Returns the selected elements' label text, in selection order, for the caller to assert
   * against. A single-element purpose is just `addElements([true])`.
   */
  async addElements(mandatoryFlags: boolean[]): Promise<string[]> {
    const labels: string[] = []
    for (let index = 0; index < mandatoryFlags.length; index += 1) {
      await this.elementsPicker.click()
      const option = this.page.getByRole('option').nth(index)
      labels.push((await option.textContent())?.trim() ?? '')
      await option.click()
    }
    // The popup is already closed after the last selection (see above); nothing further to
    // dismiss before the mandatory checkboxes below the field become clickable.
    for (const [index, mandatory] of mandatoryFlags.entries()) {
      if (mandatory) {
        // Checkbox order mirrors selection order.
        await this.root.getByRole('checkbox').nth(index).check()
      }
    }
    return labels
  }

  async addProperty(key: string, value: string): Promise<void> {
    await this.root.getByRole('button', { name: 'Add property' }).click()
    const rows = this.root.getByLabel('Key', { exact: true })
    const valueRows = this.root.getByLabel('Value', { exact: true })
    await rows.last().fill(key)
    await valueRows.last().fill(value)
  }

  async submit(): Promise<void> {
    await this.createButton.click()
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click()
  }

  /**
   * Clicks into a field and back out to the dialog title, to trigger its touched-state
   * validation via a real blur event rather than programmatic focus()/blur() calls, which
   * proved unreliable against MUI's Autocomplete (the "Type" field).
   */
  async blur(field: 'name' | 'type' | 'version'): Promise<void> {
    const locator = { name: this.nameField, type: this.typeField, version: this.versionField }[field]
    await locator.click()
    await this.title.click()
  }
}
