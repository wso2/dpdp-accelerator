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
import { submitFilterValue } from '../utils/filterCommit'

export const ROWS_PER_PAGE_OPTIONS = [10, 20, 50] as const

/**
 * SubscriptionsPage.tsx at /events/subscriptions. subscriptionId/groupId cells render through
 * CopyableText (truncated visible text, full value on the inner span's aria-label - see
 * TopicsPage.ts's rowByTopicId comment for the same pattern); the topic column is plain,
 * untruncated text.
 */
export class SubscriptionsPage {
  readonly heading: Locator
  readonly registerButton: Locator
  readonly table: Locator
  readonly searchInput: Locator
  readonly statusFilter: Locator
  readonly deliveryModeFilter: Locator
  readonly searchButton: Locator
  readonly clearFiltersButton: Locator
  readonly emptyState: Locator
  readonly loadFailedAlert: Locator
  readonly retryButton: Locator
  readonly rowsPerPageSelect: Locator
  readonly previousPageButton: Locator
  readonly nextPageButton: Locator

  constructor(private readonly page: Page) {
    this.heading = page.getByRole('heading', { name: 'Subscriptions' })
    this.registerButton = page.getByRole('button', { name: 'Create Subscription' })
    this.table = page.getByRole('table', { name: 'Subscriptions management table' })
    this.searchInput = page.getByPlaceholder('Search by subscription, event, delivery ID, topic, purpose, or URL')
    this.statusFilter = page.getByRole('combobox', { name: 'Status' })
    this.deliveryModeFilter = page.getByRole('combobox', { name: 'Delivery Mode' })
    this.searchButton = page.getByRole('button', { name: 'Search' })
    this.clearFiltersButton = page.getByRole('button', { name: 'Clear filters' })
    this.emptyState = page.getByText('No subscriptions found.')
    this.loadFailedAlert = page.getByText('Unable to load subscriptions right now.')
    this.retryButton = page.getByRole('button', { name: 'Try again' })
    this.rowsPerPageSelect = page.getByRole('combobox', { name: 'Rows per page' })
    this.previousPageButton = page.getByRole('button', { name: 'Previous' })
    this.nextPageButton = page.getByRole('button', { name: 'Next' })
  }

  async goto(): Promise<void> {
    // No leading slash - see the comment in MyConsentPage.goto() for why.
    await this.page.goto('events/subscriptions')
  }

  async openRegisterDialog(): Promise<void> {
    await this.registerButton.click()
  }

  async search(term: string): Promise<void> {
    await submitFilterValue(
      this.page,
      this.searchInput,
      async () => {
        await this.searchButton.click()
      },
      'search',
      term,
    )
  }

  async filterByStatus(label: 'All Statuses' | 'Pending' | 'Active' | 'Stale' | 'Deleted'): Promise<void> {
    await this.statusFilter.click()
    await this.page.getByRole('option', { name: label, exact: true }).click()
  }

  async filterByDeliveryMode(label: 'All Modes' | 'Webhook' | 'Poll'): Promise<void> {
    await this.deliveryModeFilter.click()
    await this.page.getByRole('option', { name: label, exact: true }).click()
  }

  async clearFilters(): Promise<void> {
    await this.clearFiltersButton.click()
  }

  async setRowsPerPage(count: (typeof ROWS_PER_PAGE_OPTIONS)[number]): Promise<void> {
    await this.rowsPerPageSelect.click()
    await this.page.getByRole('option', { name: String(count), exact: true }).click()
  }

  async goToNextPage(): Promise<void> {
    await this.nextPageButton.click()
  }

  async goToPreviousPage(): Promise<void> {
    await this.previousPageButton.click()
  }

  get rows(): Locator {
    return this.table.locator('tbody').getByRole('row')
  }

  rowBySubscriptionId(subscriptionId: string): Locator {
    return this.rows.filter({ has: this.page.locator(`[aria-label="${subscriptionId}"]`) })
  }

  viewDetailsButton(row: Locator): Locator {
    return row.getByRole('button', { name: 'View Details' })
  }

  verifyButton(row: Locator): Locator {
    return row.getByRole('button', { name: 'Re-verify webhook' })
  }

  deleteButton(row: Locator): Locator {
    return row.getByRole('button', { name: 'Delete subscription' })
  }

  topicExpandButton(row: Locator): Locator {
    return row.getByRole('button', { name: /\+\d+ more|Show less/ })
  }

  async openDetailsBySubscriptionId(subscriptionId: string): Promise<void> {
    await this.viewDetailsButton(this.rowBySubscriptionId(subscriptionId)).click()
  }
}
