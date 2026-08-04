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

import { AcrylicOrangeTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminConsentFilters from '../features/admin-consents/components/AdminConsentFilters'
import {
  EMPTY_ADMIN_CONSENT_FILTERS,
  getAdminConsentFilters,
  normalizeAdminConsentFilters,
} from '../features/admin-consents/utils/adminConsentFilters'
import i18n from '../i18n/i18n'

function renderFilters(
  filters = EMPTY_ADMIN_CONSENT_FILTERS,
  onFilterChange = vi.fn(),
  onClear = vi.fn(),
): void {
  render(
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <I18nextProvider i18n={i18n}>
        <AdminConsentFilters filters={filters} onFilterChange={onFilterChange} onClear={onClear} />
      </I18nextProvider>
    </OxygenUIThemeProvider>,
  )
}

afterEach(cleanup)

describe('administrative consent filters', () => {
  it('trims the supported filter values', () => {
    expect(
      normalizeAdminConsentFilters({
        state: 'ACTIVE',
        consentId: '  consent-1 ',
        subjectId: ' admin ',
        serviceId: ' dpdp-portal ',
      }),
    ).toEqual({
      state: 'ACTIVE',
      consentId: 'consent-1',
      subjectId: 'admin',
      serviceId: 'dpdp-portal',
    })
  })

  it('reads native consent states from the URL and ignores unknown ones', () => {
    expect(getAdminConsentFilters(new URLSearchParams('state=PENDING')).state).toBe('PENDING')
    expect(getAdminConsentFilters(new URLSearchParams('state=CREATED')).state).toBe('All')
    expect(getAdminConsentFilters(new URLSearchParams('subjectId=admin')).subjectId).toBe('admin')
  })

  it('offers only the subject and service advanced filters', () => {
    const onFilterChange = vi.fn()
    renderFilters(EMPTY_ADMIN_CONSENT_FILTERS, onFilterChange)

    expect(screen.getByPlaceholderText('Search by consent ID')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Advanced filters' }))

    const subjectId = screen.getByRole('textbox', { name: 'User' })
    const serviceId = screen.getByRole('textbox', { name: 'Service' })
    expect(subjectId).toBeEnabled()
    expect(serviceId).toBeEnabled()
    expect(screen.queryByRole('textbox', { name: /element/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: /group/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: /purpose/i })).not.toBeInTheDocument()

    fireEvent.change(subjectId, { target: { value: ' admin ' } })
    fireEvent.change(serviceId, { target: { value: 'dpdp-portal' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }))

    expect(onFilterChange).toHaveBeenCalledWith({
      state: 'All',
      consentId: '',
      subjectId: 'admin',
      serviceId: 'dpdp-portal',
    })
  })

  it('lists every native consent state in the state filter', () => {
    renderFilters()

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'State' }))

    expect(screen.getByRole('option', { name: 'All' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Pending' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Active' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Rejected' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Revoked' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Expired' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Created' })).not.toBeInTheDocument()
  })

  it('disables advanced filters and explains why when a Consent ID filter is active', async () => {
    renderFilters({ ...EMPTY_ADMIN_CONSENT_FILTERS, consentId: 'consent-123' })

    const advancedFiltersButton = screen.getByRole('button', { name: 'Advanced filters' })
    const stateSelect = screen.getByRole('combobox', { name: 'State' })
    expect(advancedFiltersButton).toBeDisabled()
    expect(stateSelect).toHaveAttribute('aria-disabled', 'true')

    fireEvent.mouseOver(advancedFiltersButton.parentElement as HTMLElement)
    expect(
      await screen.findByText('Remove the Consent ID filter to use advanced filters.'),
    ).toBeInTheDocument()

    fireEvent.mouseOver(stateSelect.closest('[aria-label]') as HTMLElement)
    expect(
      await screen.findByText('Remove the Consent ID filter to use the state filter.'),
    ).toBeInTheDocument()
  })
})
