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

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { OxygenTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import i18n from '../i18n/i18n'
import ConsentApprovalDialog from '../features/consent-registry/components/ConsentApprovalDialog'
import ConsentRejectionDialog from '../features/consent-registry/components/ConsentRejectionDialog'
import ConsentRevocationDialog from '../features/consent-registry/components/ConsentRevocationDialog'

function renderWithProviders(component: React.JSX.Element): void {
  render(
    <I18nextProvider i18n={i18n}>
      <OxygenUIThemeProvider theme={OxygenTheme}>{component}</OxygenUIThemeProvider>
    </I18nextProvider>,
  )
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  cleanup()
  vi.runOnlyPendingTimers()
  vi.useRealTimers()
})

describe('consent registry dialogs', () => {
  it('shows loading text instead of empty states while approval details load', () => {
    renderWithProviders(
      <ConsentApprovalDialog
        open
        consentId="consent-123"
        loading
        purposes={[]}
        onClose={vi.fn()}
        onConfirm={vi.fn()}
      />,
    )

    expect(screen.getByText('Loading consent details...')).toBeInTheDocument()
    expect(
      screen.queryByText('No mandatory requirements for this consent.'),
    ).not.toBeInTheDocument()
  })

  it('submits selected optional permissions from approval dialog', () => {
    const onConfirm = vi.fn()

    renderWithProviders(
      <ConsentApprovalDialog
        open
        consentId="consent-123"
        loading={false}
        purposes={[
          {
            purposeId: 'purpose-accounts',
            name: 'Accounts',
            version: 'v2',
            displayName: 'Accounts',
            elements: [
              {
                elementId: 'element-account-number',
                name: 'account_number',
                namespace: 'accounts',
                version: 'v1',
                displayName: 'Account Number',
                approved: true,
                mandatory: true,
              },
              {
                elementId: 'element-transactions',
                name: 'transaction_history',
                namespace: 'accounts',
                version: 'v3',
                displayName: 'Transaction History',
                approved: true,
                mandatory: false,
              },
              {
                elementId: 'element-marketing',
                name: 'marketing_messages',
                namespace: 'accounts',
                version: 'v2',
                displayName: 'Marketing Messages',
                approved: false,
                mandatory: false,
              },
            ],
          },
        ]}
        onClose={vi.fn()}
        onConfirm={onConfirm}
      />,
    )

    const toggles = screen.getAllByRole('checkbox', { name: /toggle permission/i })
    fireEvent.click(toggles[1])

    fireEvent.click(screen.getByRole('button', { name: /approve & continue/i }))

    expect(onConfirm).toHaveBeenCalledWith([
      {
        purposeId: 'purpose-accounts',
        purposeVersion: 'v2',
        elementId: 'element-transactions',
        elementVersion: 'v3',
      },
      {
        purposeId: 'purpose-accounts',
        purposeVersion: 'v2',
        elementId: 'element-marketing',
        elementVersion: 'v2',
      },
    ])
  })

  it('calls revocation handlers from confirmation dialog', () => {
    const onConfirm = vi.fn()
    const onClose = vi.fn()

    renderWithProviders(
      <ConsentRevocationDialog
        open
        consentId="consent-456"
        loading={false}
        onClose={onClose}
        onConfirm={onConfirm}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: /revoke consents/i }))
    expect(onConfirm).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('calls rejection handlers from confirmation dialog', () => {
    const onConfirm = vi.fn()
    const onClose = vi.fn()

    renderWithProviders(
      <ConsentRejectionDialog
        open
        consentId="consent-789"
        loading={false}
        onClose={onClose}
        onConfirm={onConfirm}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: /reject consent/i }))
    expect(onConfirm).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('disables rejection actions while processing', () => {
    renderWithProviders(
      <ConsentRejectionDialog
        open
        consentId="consent-789"
        loading
        onClose={vi.fn()}
        onConfirm={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: /processing/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /cancel/i })).toBeDisabled()
  })
})
