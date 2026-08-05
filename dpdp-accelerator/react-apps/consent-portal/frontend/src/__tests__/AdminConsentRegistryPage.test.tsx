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
import { AcrylicOrangeTheme, CssBaseline, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminConsentRegistryPage from '../features/admin-consents/AdminConsentRegistryPage'
import i18n from '../i18n/i18n'
import { PORTAL_SCOPES } from '../utils/portalScopes'
import TestAuthorizationProvider from './TestAuthorizationProvider'

const fetchMock = vi.fn()

afterEach(() => {
  cleanup()
  fetchMock.mockReset()
  vi.unstubAllGlobals()
})

function renderAdminPage(initialEntry = '/administration/consents'): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  render(
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <CssBaseline />
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={[initialEntry]}>
            <TestAuthorizationProvider scopes={[PORTAL_SCOPES.CONSENTS_READ_ANY]}>
              <Routes>
                <Route path="/administration/consents" element={<AdminConsentRegistryPage />} />
              </Routes>
            </TestAuthorizationProvider>
          </MemoryRouter>
        </QueryClientProvider>
      </I18nextProvider>
    </OxygenUIThemeProvider>,
  )
}

describe('AdminConsentRegistryPage', () => {
  it('renders the native Consents envelope and labels subjects as users', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        totalResults: 2,
        links: [
          {
            rel: 'next',
            href: 'https://localhost:9443/api/identity/consent-mgt/v2.0/consents?limit=10&after=Mg==',
          },
        ],
        Consents: [
          {
            id: 'db0759de-c098-4f44-b78d-6718226db8b2',
            subjectId: 'admin',
            serviceId: 'dpdp-portal-spike',
            state: 'PENDING',
            timestamp: 1785833928316,
          },
        ],
      }),
    })

    renderAdminPage()

    expect(await screen.findByText('admin')).toBeInTheDocument()
    expect(screen.getByText('dpdp-portal-spike')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'User' })).toBeInTheDocument()
    expect(screen.getByText('Pending')).toBeInTheDocument()

    const [requestUrl] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/consents')
    expect(Object.fromEntries(url.searchParams)).toEqual({ limit: '10' })
  })

  it('pages forward with the after cursor taken from links', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        totalResults: 2,
        links: [
          {
            rel: 'next',
            href: 'https://localhost:9443/api/identity/consent-mgt/v2.0/consents?limit=10&after=Mg==',
          },
        ],
        Consents: [
          {
            id: 'consent-1',
            subjectId: 'admin',
            serviceId: 'dpdp-portal',
            state: 'ACTIVE',
            timestamp: 1785833928316,
          },
        ],
      }),
    })

    renderAdminPage()

    const nextButton = await screen.findByRole('button', { name: 'Next' })
    await waitFor(() => expect(nextButton).toBeEnabled())
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()

    fireEvent.click(nextButton)

    await waitFor(() => {
      const cursors = fetchMock.mock.calls.map(([requestUrl]) =>
        new URL(String(requestUrl)).searchParams.get('after'),
      )
      expect(cursors).toContain('Mg==')
    })
  })

  it('uses the consent details endpoint for a Consent ID search', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        id: 'consent/123',
        subjectId: 'admin',
        serviceId: 'dpdp-portal',
        state: 'ACTIVE',
        timestamp: 1785833928316,
        purposes: [
          {
            id: 'purpose-1',
            name: 'marketing-spike',
            type: 'CONSENT',
            versionId: 'version-1',
            version: '1.0.0',
            elements: [],
          },
        ],
        authorizations: [],
      }),
    })

    renderAdminPage('/administration/consents?consentId=consent%2F123')

    expect(await screen.findByText('marketing-spike')).toBeInTheDocument()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))

    const [requestUrl] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/consents/consent%2F123')
    expect(Object.fromEntries(url.searchParams)).toEqual({})
    expect(screen.getByRole('link', { name: 'View' })).toHaveAttribute(
      'href',
      '/administration/consents/consent%2F123',
    )
  })
})
