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
import { SubscriptionDeliveryHistoryModal } from './SubscriptionDeliveryHistoryModal'

/**
 * SubscriptionDetailsPage.tsx at /events/subscriptions/:id. Config fields render through the
 * shared DetailGrid (a label Typography immediately followed by its value Typography, both
 * children of the same Stack, neither carrying a data attribute) - fieldValue() walks from the
 * exact label text node to that next sibling rather than guessing a selector.
 */
export class SubscriptionDetailsPage {
  readonly backButton: Locator
  readonly loadFailedAlert: Locator
  readonly verifyButton: Locator
  readonly deleteButton: Locator
  readonly deliveryEventsTable: Locator
  readonly topicsSearchInput: Locator
  readonly verificationSuccessToast: Locator
  readonly verificationFailedToast: Locator

  constructor(private readonly page: Page) {
    this.backButton = page.getByRole('button', { name: /^Back/ })
    this.loadFailedAlert = page.getByText('Unable to load subscription details.')
    this.verifyButton = page.getByRole('button', { name: 'Re-verify webhook' })
    this.deleteButton = page.getByRole('button', { name: 'Delete subscription' })
    this.deliveryEventsTable = page.getByRole('table', { name: 'Delivery events log table' })
    this.topicsSearchInput = page.getByLabel('Search associated topics')
    this.verificationSuccessToast = page.getByText('Verification ping dispatched successfully.')
    this.verificationFailedToast = page.getByText('Webhook verification failed. Please check endpoint reachability.')
  }

  async goto(subscriptionId: string): Promise<void> {
    await this.page.goto(`events/subscriptions/${subscriptionId}`)
  }

  async goBack(): Promise<void> {
    await this.backButton.click()
  }

  async verify(): Promise<void> {
    await this.verifyButton.click()
  }

  async openDeleteDialog(): Promise<void> {
    await this.deleteButton.click()
  }

  /**
   * DetailGrid.tsx: a label Typography followed immediately by its value Typography, both
   * children of the same Stack. `.and(':not(th)')` excludes the embedded delivery-events
   * table's own column headers, which can render the exact same label text (e.g. "Topic") as
   * this page's config DetailGrid - an unscoped match resolves to both.
   */
  fieldValue(label: string): Locator {
    return this.page
      .getByText(label, { exact: true })
      .and(this.page.locator(':not(th)'))
      .locator('xpath=following-sibling::*[1]')
  }

  get deliveryEventRows(): Locator {
    return this.deliveryEventsTable.locator('tbody').getByRole('row')
  }

  deliveryEventRowByDeliveryId(deliveryId: string): Locator {
    return this.deliveryEventRows.filter({ has: this.page.locator(`[aria-label="${deliveryId}"]`) })
  }

  async openDeliveryHistory(deliveryId: string): Promise<SubscriptionDeliveryHistoryModal> {
    await this.deliveryEventRowByDeliveryId(deliveryId)
      .getByRole('button', { name: 'View Delivery Attempts' })
      .click()
    return new SubscriptionDeliveryHistoryModal(this.page)
  }

  async expandSubscribedTopics(): Promise<void> {
    const toggle = this.page.getByRole('button', { name: 'Subscribed Topics' })
    if ((await toggle.count()) > 0) {
      const expanded = await toggle.getAttribute('aria-expanded')
      if (expanded !== 'true') {
        await toggle.click()
      }
    }
  }

  topicChip(topicName: string): Locator {
    return this.page.locator('#subscribed-topic-content').getByText(topicName, { exact: true })
  }

  async searchTopics(query: string): Promise<void> {
    await this.topicsSearchInput.fill(query)
  }
}
