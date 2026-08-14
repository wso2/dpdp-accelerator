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

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AcrylicOrangeTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminConsentFilters from '../features/admin-consents/components/AdminConsentFilters'
import {
  EMPTY_ADMIN_CONSENT_FILTERS,
  normalizeAdminConsentFilters,
} from '../features/admin-consents/utils/adminConsentFilters'
import i18n from '../i18n/i18n'

afterEach(cleanup)

describe('administrative consent filters', () => {
  it('normalizes ID lists and removes dependent versions without their identity', () => {
    expect(
      normalizeAdminConsentFilters({
        ...EMPTY_ADMIN_CONSENT_FILTERS,
        userIds: ' user-1, user-2, user-1, ,',
        groupIds: 'group-1, group-1,group-2',
        purposeVersion: 'v2',
        elementVersion: 'v3',
      }),
    ).toMatchObject({
      userIds: 'user-1,user-2',
      groupIds: 'group-1,group-2',
      purposeVersion: '',
      elementVersion: '',
    })
  })

  it('uses free-text element fields when ELEMENTS_READ is unavailable', () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })

    render(
      <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
        <I18nextProvider i18n={i18n}>
          <QueryClientProvider client={queryClient}>
            <AdminConsentFilters
              filters={{ ...EMPTY_ADMIN_CONSENT_FILTERS }}
              canReadElements={false}
              onFilterChange={vi.fn()}
              onClear={vi.fn()}
            />
          </QueryClientProvider>
        </I18nextProvider>
      </OxygenUIThemeProvider>,
    )

    expect(screen.getByPlaceholderText('Search by consent ID')).toBeInTheDocument()
    expect(screen.queryByPlaceholderText('Search by purpose name')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Advanced filters' }))

    expect(screen.getByRole('textbox', { name: 'Purpose name' })).toBeEnabled()
    const elementName = screen.getByRole('textbox', { name: 'Element name' })
    const elementNamespace = screen.getByRole('textbox', { name: 'Element namespace' })
    const elementVersion = screen.getByRole('textbox', { name: 'Element version' })
    expect(elementName).toBeEnabled()
    expect(elementNamespace).toBeEnabled()
    expect(elementVersion).toBeDisabled()

    fireEvent.change(elementName, { target: { value: 'account-number' } })
    expect(elementVersion).toBeEnabled()
  })

  it('disables advanced filters and explains why when a Consent ID filter is active', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })

    render(
      <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
        <I18nextProvider i18n={i18n}>
          <QueryClientProvider client={queryClient}>
            <AdminConsentFilters
              filters={{ ...EMPTY_ADMIN_CONSENT_FILTERS, consentId: 'consent-123' }}
              canReadElements={false}
              onFilterChange={vi.fn()}
              onClear={vi.fn()}
            />
          </QueryClientProvider>
        </I18nextProvider>
      </OxygenUIThemeProvider>,
    )

    const advancedFiltersButton = screen.getByRole('button', { name: 'Advanced filters' })
    const statusSelect = screen.getByRole('combobox', { name: 'Status' })
    expect(advancedFiltersButton).toBeDisabled()
    expect(statusSelect).toHaveAttribute('aria-disabled', 'true')

    fireEvent.mouseOver(advancedFiltersButton.parentElement as HTMLElement)
    expect(
      await screen.findByText('Remove the Consent ID filter to use advanced filters.'),
    ).toBeInTheDocument()

    fireEvent.mouseOver(statusSelect.closest('[aria-label]') as HTMLElement)
    expect(
      await screen.findByText('Remove the Consent ID filter to use the status filter.'),
    ).toBeInTheDocument()
  })
})
