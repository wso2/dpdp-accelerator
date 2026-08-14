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

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AcrylicOrangeTheme, CssBaseline, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ConsentRegistryPage from '../features/consent-registry/ConsentRegistryPage'
import i18n from '../i18n/i18n'
import TestAuthorizationProvider from './TestAuthorizationProvider'
import { PORTAL_SCOPES } from '../utils/portalScopes'

const fetchMock = vi.fn()

function CurrentLocation(): React.JSX.Element {
  const location = useLocation()

  return <span data-testid="current-location">{`${location.pathname}${location.search}`}</span>
}

function createQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

function buildConsent(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    id: 'db1f6e7a-2107-438c-a4cf-b62588c50259',
    subjectId: 'admin',
    serviceId: 'dpdp-portal',
    state: 'ACTIVE',
    language: 'en',
    timestamp: 1785833979893,
    purposes: [
      {
        id: '690eb7ef-3a32-4439-b006-2d47f2fb6885',
        name: 'marketing-spike',
        type: 'CONSENT',
        versionId: 'cc689174-c91a-449d-ae85-05c33cab1721',
        version: '1.0.0',
        elements: [],
        properties: {},
      },
    ],
    authorizations: [],
    properties: {},
    ...overrides,
  }
}

function renderConsentRegistryPage(queryClient: QueryClient, initialEntry = '/consents'): void {
  render(
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <CssBaseline />
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={[initialEntry]}>
            <TestAuthorizationProvider scopes={Object.values(PORTAL_SCOPES)}>
              <Routes>
                <Route
                  path="*"
                  element={
                    <>
                      <CurrentLocation />
                      <ConsentRegistryPage />
                    </>
                  }
                />
              </Routes>
            </TestAuthorizationProvider>
          </MemoryRouter>
        </QueryClientProvider>
      </I18nextProvider>
    </OxygenUIThemeProvider>,
  )
}

function mockConsentSearch(data: unknown[], limit = 10): void {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({
      data,
      metadata: { total: data.length, offset: 0, count: data.length, limit },
    }),
  })
}

afterEach(() => {
  cleanup()
  fetchMock.mockReset()
  vi.unstubAllGlobals()
})

describe('ConsentRegistryPage', () => {
  it('renders heading, filters and native consent rows', async () => {
    mockConsentSearch([buildConsent()])

    renderConsentRegistryPage(createQueryClient())

    expect(await screen.findByRole('heading', { name: 'All Consents' })).toBeInTheDocument()
    expect(screen.getByLabelText('Consent filters')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Search by service')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'State' })).toBeInTheDocument()
    expect(await screen.findByText('marketing-spike')).toBeInTheDocument()
    expect(screen.getByText('dpdp-portal')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View' })).toHaveAttribute(
      'href',
      '/consents/db1f6e7a-2107-438c-a4cf-b62588c50259',
    )
    expect(
      screen.getByLabelText('Consent ID: db1f6e7a-2107-438c-a4cf-b62588c50259'),
    ).toHaveTextContent('db1f6e7a…')
  })

  it('never renders a group column or an exact result total', async () => {
    mockConsentSearch([buildConsent()])

    renderConsentRegistryPage(createQueryClient())

    expect(await screen.findByText('marketing-spike')).toBeInTheDocument()
    expect(screen.queryByText(/group id/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/1–1 of/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: 'Expiration' })).not.toBeInTheDocument()
  })

  it('shows an error message when consent fetch fails', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({ code: 'INTERNAL_SERVER_ERROR', message: 'Something went wrong.' }),
    })

    renderConsentRegistryPage(createQueryClient())

    expect(await screen.findByText('Unable to load consents right now.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('renders consent rows in API order', async () => {
    mockConsentSearch([buildConsent({ id: 'z-consent' }), buildConsent({ id: 'a-consent' })])

    renderConsentRegistryPage(createQueryClient())

    const consentIDs = await screen.findAllByLabelText(/^Consent ID: /)

    expect(consentIDs.map((element) => element.getAttribute('aria-label'))).toEqual([
      'Consent ID: z-consent',
      'Consent ID: a-consent',
    ])
  })

  it('shows the empty state for an empty response', async () => {
    mockConsentSearch([])

    renderConsentRegistryPage(createQueryClient())

    expect(
      await screen.findByText('No consents found for the selected filters.'),
    ).toBeInTheDocument()
  })

  it('shows the error state when a consent response has an unsupported state', async () => {
    mockConsentSearch([buildConsent({ state: 'CREATED' })])

    renderConsentRegistryPage(createQueryClient())

    expect(await screen.findByText('Unable to load consents right now.')).toBeInTheDocument()
  })

  it('does not render approve action for rejected consents', async () => {
    mockConsentSearch([buildConsent({ state: 'REJECTED' })])

    renderConsentRegistryPage(createQueryClient())

    expect(await screen.findByText('marketing-spike')).toBeInTheDocument()
    expect(screen.queryByLabelText('Approve')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Revoke')).not.toBeInTheDocument()
  })

  it('renders approve and revoke actions from the consent state alone', async () => {
    mockConsentSearch([
      buildConsent({ id: 'pending-consent', state: 'PENDING' }),
      buildConsent({ id: 'active-consent', state: 'ACTIVE' }),
    ])

    renderConsentRegistryPage(createQueryClient())

    expect((await screen.findAllByLabelText('Approve')).length).toBeGreaterThan(0)
    expect(screen.getAllByLabelText('Revoke').length).toBeGreaterThan(0)
  })

  it('maps URL filters to the supported self-service query parameters', async () => {
    mockConsentSearch([])

    renderConsentRegistryPage(
      createQueryClient(),
      '/consents?state=PENDING&serviceId=dpdp-portal&page=3&rowsPerPage=25',
    )

    await waitFor(() => expect(fetchMock).toHaveBeenCalled())

    const [requestUrl] = fetchMock.mock.calls[0] ?? []
    expect(Object.fromEntries(new URL(String(requestUrl)).searchParams)).toEqual({
      consentStatuses: 'PENDING',
      serviceId: 'dpdp-portal',
      limit: '25',
      offset: '50',
    })
  })

  it('ignores the removed CREATED status in the URL', async () => {
    mockConsentSearch([])

    renderConsentRegistryPage(createQueryClient(), '/consents?state=CREATED')

    await waitFor(() => expect(fetchMock).toHaveBeenCalled())

    const [requestUrl] = fetchMock.mock.calls[0] ?? []
    expect(new URL(String(requestUrl)).searchParams.get('consentStatuses')).toBeNull()
  })

  it('pages with next and previous instead of numbered pages', async () => {
    mockConsentSearch(
      Array.from({ length: 10 }, (_, index) => buildConsent({ id: `consent-${String(index)}` })),
    )

    renderConsentRegistryPage(createQueryClient())

    const nextButton = await screen.findByRole('button', { name: 'Next' })
    await waitFor(() => expect(nextButton).toBeEnabled())
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()

    fireEvent.click(nextButton)

    await waitFor(() => {
      expect(screen.getByTestId('current-location')).toHaveTextContent('/consents?page=2')
    })
    await waitFor(() => {
      const offsets = fetchMock.mock.calls.map(([requestUrl]) =>
        new URL(String(requestUrl)).searchParams.get('offset'),
      )
      expect(offsets).toContain('10')
    })
  })

  it('disables next when a short page indicates the end of the results', async () => {
    mockConsentSearch([buildConsent()])

    renderConsentRegistryPage(createQueryClient())

    expect(await screen.findByText('marketing-spike')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
  })
})
