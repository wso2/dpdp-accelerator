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

/** ElementDetailsPage.tsx - a single Element's fields (id, name, display name, description). */
export class ElementDetailPage {
  readonly propertiesTable: Locator
  readonly loadFailedMessage: Locator
  readonly backButton: Locator
  readonly deleteButton: Locator

  constructor(private readonly page: Page) {
    this.propertiesTable = page
      .locator('.MuiCard-root')
      .filter({ has: page.getByRole('heading', { name: 'Properties' }) })
      .getByRole('table')
    this.loadFailedMessage = page.getByText('Unable to load elements right now.')
    this.backButton = page.getByRole('button', { name: /^Back/ })
    // Only rendered for a persona holding ELEMENTS_WRITE - see ElementDetailsPage.tsx's
    // `canWrite` check. "Delete", not "Delete Element" - that text belongs to the confirmation
    // dialog's own title/confirm button, see ElementDeleteDialog.
    this.deleteButton = page.getByRole('button', { name: 'Delete', exact: true })
  }

  async goto(elementId: string): Promise<void> {
    // No leading slash - see the comment in MyConsentPage.goto() for why.
    await this.page.goto(`elements/${elementId}`)
  }

  heading(name: string): Locator {
    return this.page.getByRole('heading', { name })
  }

  /** A property row, matched by its key - see the identical pattern in PurposeDetailPage.elementRow. */
  propertyRow(key: string): Locator {
    return this.propertiesTable.getByRole('row', { name: new RegExp(key) })
  }

  /**
   * The machine `name` field's rendered value - scoped to a <code> element since the same text
   * also appears (as the current-page breadcrumb) elsewhere on this page, which would make a
   * bare page.getByText(name) ambiguous.
   */
  nameValue(name: string): Locator {
    return this.page.locator('code').filter({ hasText: name })
  }

  /** The Element ID shown (and copyable) in the card header above the name/displayName/description fields. */
  elementIdValue(id: string): Locator {
    return this.page.getByText(id, { exact: true })
  }

  /**
   * A DetailGrid field's rendered value, found via its label (e.g. "Display name", "Description" -
   * see ElementDetailsPage.tsx's `fields` array). DetailGrid.tsx renders the label and value as
   * two sibling Typography elements inside one Stack, with no other structure to hook into, so
   * this locates the label text and takes its next sibling rather than guessing at a class name.
   * Not used for `name` - see nameValue, which needs the `<code>` scoping this doesn't have.
   */
  fieldValue(label: string): Locator {
    return this.page.getByText(label, { exact: true }).locator('xpath=following-sibling::*[1]')
  }
}
