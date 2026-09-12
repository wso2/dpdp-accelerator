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

/** The only rows-per-page values TopicTable.tsx/CursorPaginationFooter accept. */
export const ROWS_PER_PAGE_OPTIONS = [10, 20, 50] as const

/**
 * TopicsPage.tsx at /events/topics - Event Notification Topics list, filters, and pagination.
 * TopicTable.tsx has no data-testid anywhere (confirmed across the whole feature); topicId cells
 * render through CopyableText, which truncates the *visible* text but keeps the full id as the
 * inner span's aria-label - rowByTopicId matches on that, never on the (possibly truncated)
 * visible text.
 */
export class TopicsPage {
  readonly heading: Locator
  readonly registerButton: Locator
  readonly table: Locator
  readonly searchInput: Locator
  readonly statusFilter: Locator
  readonly searchButton: Locator
  readonly clearFiltersButton: Locator
  readonly emptyState: Locator
  readonly loadFailedAlert: Locator
  readonly retryButton: Locator
  readonly rowsPerPageSelect: Locator
  readonly previousPageButton: Locator
  readonly nextPageButton: Locator

  constructor(private readonly page: Page) {
    this.heading = page.getByRole('heading', { name: 'Topics' })
    // exact: true - Playwright's default substring/case-insensitive accessible-name match makes
    // this collide with every row's "Deregister topic" button ("Deregister topic" contains
    // "register Topic" as a substring), which only ever surfaces once the table has rows, i.e.
    // never on a truly empty environment - see 08.01.01's regression history.
    this.registerButton = page.getByRole('button', { name: 'Register Topic', exact: true })
    this.table = page.getByRole('table', { name: 'Topics management table' })
    this.searchInput = page.getByPlaceholder('Search by topic name, ID, or description')
    this.statusFilter = page.getByRole('combobox', { name: 'Status' })
    this.searchButton = page.getByRole('button', { name: 'Search' })
    this.clearFiltersButton = page.getByRole('button', { name: 'Clear filters' })
    this.emptyState = page.getByText('No registered topics found.')
    this.loadFailedAlert = page.getByText('Unable to load topics right now.')
    this.retryButton = page.getByRole('button', { name: 'Try again' })
    this.rowsPerPageSelect = page.getByRole('combobox', { name: 'Rows per page' })
    this.previousPageButton = page.getByRole('button', { name: 'Previous' })
    this.nextPageButton = page.getByRole('button', { name: 'Next' })
  }

  async goto(): Promise<void> {
    // No leading slash - see the comment in MyConsentPage.goto() for why.
    await this.page.goto('events/topics')
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

  /** Fires immediately on selection - TopicFilters.tsx applies status without a separate Search click. */
  async filterByStatus(label: 'All Statuses' | 'Active' | 'Deregistered'): Promise<void> {
    await this.statusFilter.click()
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

  /** Data rows only - scoped to tbody so the header row is never matched. */
  get rows(): Locator {
    return this.table.locator('tbody').getByRole('row')
  }

  /** Matches on the topic's exact, untruncated name - TopicTable.tsx never truncates this column. */
  rowByName(name: string): Locator {
    return this.rows.filter({ has: this.page.getByText(name, { exact: true }) })
  }

  /** Matches on the full topicId via CopyableText's aria-label, independent of visible truncation. */
  rowByTopicId(topicId: string): Locator {
    return this.rows.filter({ has: this.page.locator(`[aria-label="${topicId}"]`) })
  }

  /** aria-label is always "Deregister topic" regardless of state (see TopicTable.tsx) - disabled/tooltip carry the rest. */
  deregisterButton(row: Locator): Locator {
    return row.getByRole('button', { name: 'Deregister topic' })
  }

  async deregisterTopicByName(name: string): Promise<void> {
    await this.deregisterButton(this.rowByName(name)).click()
  }
}
