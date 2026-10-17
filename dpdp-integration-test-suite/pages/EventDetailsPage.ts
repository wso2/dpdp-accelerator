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
import { EventDetailsModal } from './EventDetailsModal'

/**
 * EventDetailsPage.tsx at /events/:id. Metadata fields render through the shared DetailGrid
 * (see SubscriptionDetailsPage.ts's fieldValue comment for the exact DOM shape this walks).
 * The deliveries table paginates with MUI's stock `TablePagination` (not the
 * CursorPaginationFooter every other list in this feature uses) - its prev/next controls carry
 * MUI's own default aria-labels, "Go to previous page"/"Go to next page", never overridden here.
 */
export class EventDetailsPage {
  readonly backButton: Locator
  readonly loadFailedAlert: Locator
  readonly deliveriesTable: Locator
  readonly noDeliveriesHeading: Locator
  readonly noDeliveriesMessage: Locator
  readonly deliveriesPreviousPageButton: Locator
  readonly deliveriesNextPageButton: Locator

  constructor(private readonly page: Page) {
    this.backButton = page.getByRole('button', { name: 'Back to Events' })
    this.loadFailedAlert = page.getByText('Unable to load event details.')
    this.deliveriesTable = page.getByRole('table', { name: 'Downstream Subscriber Deliveries' })
    this.noDeliveriesHeading = page.getByText('No Deliveries Generated')
    this.noDeliveriesMessage = page.getByText('No active subscriptions matched this topic when the event occurred.')
    this.deliveriesPreviousPageButton = page.getByRole('button', { name: 'Go to previous page' })
    this.deliveriesNextPageButton = page.getByRole('button', { name: 'Go to next page' })
  }

  async goto(eventId: string): Promise<void> {
    await this.page.goto(`events/${eventId}`)
  }

  async goBack(): Promise<void> {
    await this.backButton.click()
  }

  /** DetailGrid.tsx: a label Typography followed immediately by its value Typography, both children of the same Stack. */
  /** `.and(':not(th)')` excludes the embedded deliveries table's own column headers - see SubscriptionDetailsPage.ts's fieldValue comment for the live collision this guards against. */
  fieldValue(label: string): Locator {
    return this.page
      .getByText(label, { exact: true })
      .and(this.page.locator(':not(th)'))
      .locator('xpath=following-sibling::*[1]')
  }

  get deliveryRows(): Locator {
    return this.deliveriesTable.locator('tbody').getByRole('row')
  }

  deliveryRowByDeliveryId(deliveryId: string): Locator {
    return this.deliveryRows.filter({ has: this.page.locator(`[aria-label="${deliveryId}"]`) })
  }

  async openDeliveryDetails(deliveryId: string): Promise<EventDetailsModal> {
    await this.deliveryRowByDeliveryId(deliveryId).getByRole('button', { name: 'View Details' }).click()
    return new EventDetailsModal(this.page)
  }
}
