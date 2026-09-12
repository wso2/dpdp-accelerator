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
 * ComplaintListPage.tsx - a Data Principal's own complaint registry at /complaints. Built
 * directly off the component source and public/i18n/en/common.json's "complaints.list.*" keys
 * (see AGENTS.md for how this suite's ground truth was established).
 */
export class ComplaintListPage {
  readonly heading: Locator
  readonly submitButton: Locator
  readonly table: Locator
  readonly statusFilter: Locator
  readonly clearFiltersButton: Locator
  readonly successAlert: Locator
  readonly emptyState: Locator
  readonly emptyStateFiltered: Locator

  constructor(private readonly page: Page) {
    this.heading = page.getByRole('heading', { name: 'My Complaints' })
    this.submitButton = page.getByRole('button', { name: 'Submit a complaint' })
    this.table = page.getByRole('table', { name: 'My complaints table' })
    // ComplaintListFilters.tsx associates the Select via labelId/label, so it gets a real
    // accessible name (unlike ComplaintSubmitDialog's category select - see ComplaintSubmitDialog.ts).
    this.statusFilter = page.getByRole('combobox', { name: 'Status' })
    this.clearFiltersButton = page.getByRole('button', { name: 'Clear filters' })
    this.successAlert = page.getByRole('alert')
    this.emptyState = page.getByText("You haven't submitted any complaints yet.")
    this.emptyStateFiltered = page.getByText('No complaints match the selected filter.')
  }

  async goto(): Promise<void> {
    // No leading slash - see the comment in MyConsentPage.goto() for why.
    await this.page.goto('complaints')
  }

  async openSubmitDialog(): Promise<void> {
    await this.submitButton.click()
  }

  /** The status filter's option labels are the same t('complaints.status.*') copy as every chip. */
  async filterByStatus(label: 'All' | 'Open' | 'In Progress' | 'Waiting on Client' | 'Waiting on Internal Review' | 'Resolved'): Promise<void> {
    await this.statusFilter.click()
    await this.page.getByRole('option', { name: label, exact: true }).click()
  }

  rowByReferenceId(referenceId: string): Locator {
    return this.table.getByRole('row', { name: new RegExp(referenceId) })
  }

  get rows(): Locator {
    return this.table.locator('tbody').getByRole('row')
  }

  async openByReferenceId(referenceId: string): Promise<void> {
    await this.rowByReferenceId(referenceId).click()
  }

  async setRowsPerPage(count: 5 | 10 | 25): Promise<void> {
    await this.page.getByLabel('Rows per page').click()
    await this.page.getByRole('option', { name: String(count), exact: true }).click()
  }
}
