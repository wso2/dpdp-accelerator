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

export type ComplaintStatusLabel =
  | 'Open'
  | 'In Progress'
  | 'Waiting on Client'
  | 'Waiting on Internal Review'
  | 'Resolved'

/**
 * ComplaintQueuePage.tsx at /complaint-management - the Complaint Officer's org-wide queue.
 * Reached with the same `dpdp-consent-admin` persona as the rest of this suite's admin surface -
 * see AGENTS.md: there is no distinct Complaint Officer role, "the officer" is
 * defined as any dpdp-consent-admin member (COMPLAINT-NOTIFICATION-DESIGN.md section 4).
 */
export class ComplaintQueuePage {
  readonly heading: Locator
  readonly table: Locator
  readonly statusFilter: Locator
  readonly priorityFilter: Locator
  readonly searchBox: Locator
  readonly clearFiltersButton: Locator
  readonly emptyState: Locator
  readonly openStatTile: Locator
  readonly awaitingResponseStatTile: Locator
  readonly resolvedStatTile: Locator
  readonly slaBreachedStatTile: Locator

  constructor(private readonly page: Page) {
    this.heading = page.getByRole('heading', { name: 'Complaints' })
    this.table = page.getByRole('table', { name: 'Complaint queue table' })
    this.statusFilter = page.getByRole('combobox', { name: 'Status' })
    this.priorityFilter = page.getByRole('combobox', { name: 'Priority' })
    this.searchBox = page.getByPlaceholder('Search by reference ID or user')
    this.clearFiltersButton = page.getByRole('button', { name: 'Clear filters' })
    this.emptyState = page.getByText('No complaints match the selected filters.')
    // Each StatCard's label renders as a <p> (ARIA role "paragraph") - scoped to that role since a
    // plain getByText(label, {exact:true}) also matches the queue table's status/priority Chips
    // (rendered as <span>) whenever a row happens to share the same label text.
    this.openStatTile = page.getByRole('paragraph', { name: 'Open', exact: true })
    this.awaitingResponseStatTile = page.getByRole('paragraph', { name: 'Awaiting response', exact: true })
    this.resolvedStatTile = page.getByRole('paragraph', { name: 'Resolved', exact: true })
    this.slaBreachedStatTile = page.getByRole('paragraph', { name: 'SLA breached', exact: true })
  }

  async goto(): Promise<void> {
    await this.page.goto('complaint-management')
  }

  async filterByStatus(label: 'All' | ComplaintStatusLabel): Promise<void> {
    await this.statusFilter.click()
    await this.page.getByRole('option', { name: label, exact: true }).click()
  }

  async filterByPriority(label: 'All' | 'Critical' | 'High' | 'Medium' | 'Low'): Promise<void> {
    await this.priorityFilter.click()
    await this.page.getByRole('option', { name: label, exact: true }).click()
  }

  async searchByReferenceOrName(text: string): Promise<void> {
    await this.searchBox.fill(text)
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
