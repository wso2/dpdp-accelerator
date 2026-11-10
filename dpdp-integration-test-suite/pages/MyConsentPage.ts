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
import { selectMuiOption } from '../utils/muiSelect'
import { submitFilterValue, waitForFilterQueryParam } from '../utils/filterCommit'
import { ConsentRegistryTable } from './ConsentRegistryTable'

const RELATION_QUERY_VALUES: Record<'All' | 'Personal' | 'Managed', string> = {
  All: '',
  Personal: 'SUBJECT',
  Managed: 'AUTHORIZER',
}

/** A user's own consent list at /consents, labelled "My Consents" in the sidebar. */
export class MyConsentPage extends ConsentRegistryTable {
  readonly serviceSearch: Locator
  readonly stateFilter: Locator
  readonly relationFilter: Locator
  readonly clearFiltersButton: Locator

  constructor(page: Page) {
    super(page)
    this.serviceSearch = page.getByPlaceholder('Search by service')
    this.stateFilter = page.getByRole('combobox', { name: 'State' })
    this.relationFilter = page.getByRole('combobox', { name: 'Relation' })
    this.clearFiltersButton = page.getByRole('button', { name: 'Clear all filters' })
  }

  async goto(): Promise<void> {
    await this.page.goto('consents')
  }

  async gotoPending(): Promise<void> {
    await this.page.goto('consents?view=pending')
  }

  async searchByService(serviceId: string): Promise<void> {
    await submitFilterValue(
      this.page,
      this.serviceSearch,
      async () => {
        await this.serviceSearch.press('Enter')
      },
      'serviceId',
      serviceId,
    )
  }

  async filterByState(stateLabel: string): Promise<void> {
    await selectMuiOption(this.page, 'consent-state', stateLabel)
  }

  async filterByRelation(relationLabel: 'All' | 'Personal' | 'Managed'): Promise<void> {
    await selectMuiOption(this.page, 'consent-relation', relationLabel)
    await waitForFilterQueryParam(this.page, 'relation', RELATION_QUERY_VALUES[relationLabel])
  }

  async clearFilters(): Promise<void> {
    await this.clearFiltersButton.click()
  }
}
