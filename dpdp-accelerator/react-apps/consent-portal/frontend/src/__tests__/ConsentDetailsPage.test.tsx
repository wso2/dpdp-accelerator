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
import { cleanup, render, screen, within } from '@testing-library/react'
import { AcrylicOrangeTheme, CssBaseline, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ConsentDetailsPage from '../features/consent-registry/ConsentDetailsPage'
import i18n from '../i18n/i18n'
import type { ConsentStatusAuditItem } from '../types/consent'
import type { PortalScope } from '../utils/portalScopes'
import TestAuthorizationProvider from './TestAuthorizationProvider'
import { PORTAL_SCOPES } from '../utils/portalScopes'

const fetchMock = vi.fn()

function renderConsentDetailsPage(
  status: string,
  statusHistory: ConsentStatusAuditItem[] = [],
  scopes: PortalScope[] = Object.values(PORTAL_SCOPES),
): void {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({
      id: '00000000-0000-4000-8000-000000000001',
      groupId: 'GROUP-001',
      type: 'accounts',
      status,
      createdTime: 1702800000000,
      updatedTime: 1702800000000,
      purposes: [],
      attributes: {},
      authorizations: [],
      statusHistory,
    }),
  })

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  })

  render(
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <CssBaseline />
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/consents/00000000-0000-4000-8000-000000000001']}>
            <TestAuthorizationProvider scopes={scopes}>
              <Routes>
                <Route path="/consents/:id" element={<ConsentDetailsPage />} />
              </Routes>
            </TestAuthorizationProvider>
          </MemoryRouter>
        </QueryClientProvider>
      </I18nextProvider>
    </OxygenUIThemeProvider>,
  )
}

afterEach(() => {
  cleanup()
  fetchMock.mockReset()
  vi.unstubAllGlobals()
})

describe('ConsentDetailsPage lifecycle actions', () => {
  it('shows approve and reject for created consents without revoke', async () => {
    renderConsentDetailsPage('CREATED')

    expect(await screen.findByRole('button', { name: 'Approve' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reject' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Revoke' })).not.toBeInTheDocument()
  })

  it('shows revoke only for active consents', async () => {
    renderConsentDetailsPage('ACTIVE')

    expect(await screen.findByRole('button', { name: 'Revoke' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
  })

  it('hides lifecycle actions without the consent write scope', async () => {
    renderConsentDetailsPage('CREATED', [], [PORTAL_SCOPES.CONSENTS_READ_SELF])

    expect(await screen.findByRole('heading', { name: 'Consent Details' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Revoke' })).not.toBeInTheDocument()
  })

  it.each(['REJECTED', 'REVOKED', 'EXPIRED'])(
    'shows no lifecycle action for %s consents',
    async (status) => {
      renderConsentDetailsPage(status)

      expect(await screen.findByRole('heading', { name: 'Consent Details' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Revoke' })).not.toBeInTheDocument()
    },
  )

  it('renders actual status history in chronological order', async () => {
    renderConsentDetailsPage('ACTIVE', [
      {
        statusAuditId: 'audit-active',
        previousStatus: 'CREATED',
        currentStatus: 'ACTIVE',
        actionTime: 1702800001000,
        actionBy: 'user@example.com',
        reason: 'All authorizations approved',
      },
      {
        statusAuditId: 'audit-created',
        currentStatus: 'CREATED',
        actionTime: 1702800000000,
        actionBy: 'client-app',
        reason: 'Consent created',
      },
    ])

    const lifecycleTable = await screen.findByRole('table', { name: 'Consent lifecycle' })
    const rows = within(lifecycleTable).getAllByRole('row')

    expect(within(rows[1]).getByText('Pending')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Consent created')).toBeInTheDocument()
    expect(within(rows[1]).queryByText('client-app')).not.toBeInTheDocument()
    expect(within(rows[2]).getByText('Active')).toBeInTheDocument()
    expect(within(rows[2]).getByText('All authorizations approved')).toBeInTheDocument()
  })

  it('renders a lifecycle empty state when status history is unavailable', async () => {
    renderConsentDetailsPage('CREATED')

    const lifecycleTable = await screen.findByRole('table', { name: 'Consent lifecycle' })

    expect(
      within(lifecycleTable).getByText('No lifecycle events are available.'),
    ).toBeInTheDocument()
  })
})
