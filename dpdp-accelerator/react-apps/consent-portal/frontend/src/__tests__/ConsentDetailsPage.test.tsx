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
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { AcrylicOrangeTheme, CssBaseline, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ConsentDetailsPage from '../features/consent-registry/ConsentDetailsPage'
import i18n from '../i18n/i18n'
import type { ConsentDetail } from '../types/consent'
import { PORTAL_SCOPES, type PortalScope } from '../utils/portalScopes'
import TestAuthorizationProvider from './TestAuthorizationProvider'

const fetchMock = vi.fn()

const CONSENT_ID = '06168ee0-f82a-4b0f-87ea-2a37600ec3f2'

function buildConsent(state: string, overrides: Partial<ConsentDetail> = {}): ConsentDetail {
  return {
    id: CONSENT_ID,
    subjectId: 'admin',
    serviceId: 'dpdp-portal',
    state,
    language: 'en',
    timestamp: 1785835726132,
    purposes: [
      {
        id: '690eb7ef-3a32-4439-b006-2d47f2fb6885',
        name: 'marketing-spike',
        type: 'CONSENT',
        versionId: 'cc689174-c91a-449d-ae85-05c33cab1721',
        version: '1.0.0',
        elements: [
          {
            id: '415976b9-85b3-409c-b195-35a2733b0afb',
            name: 'email-spike',
            displayName: 'Email Address',
          },
        ],
        properties: {},
      },
    ],
    authorizations: [{ userId: 'admin', state: 'APPROVED', updatedTime: 1785835726345 }],
    properties: {},
    ...overrides,
  }
}

function renderConsentDetailsPage(
  state: string,
  scopes: PortalScope[] = Object.values(PORTAL_SCOPES),
  overrides: Partial<ConsentDetail> = {},
): void {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => buildConsent(state, overrides),
  })

  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  render(
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <CssBaseline />
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={[`/consents/${CONSENT_ID}`]}>
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
  it('shows approve and reject for pending consents without revoke', async () => {
    renderConsentDetailsPage('PENDING')

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
    renderConsentDetailsPage('PENDING', [PORTAL_SCOPES.CONSENTS_READ_SELF])

    expect(await screen.findByRole('heading', { name: 'Consent Details' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Revoke' })).not.toBeInTheDocument()
  })

  it.each(['REJECTED', 'REVOKED', 'EXPIRED'])(
    'shows no lifecycle action for %s consents',
    async (state) => {
      renderConsentDetailsPage(state)

      expect(await screen.findByRole('heading', { name: 'Consent Details' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Revoke' })).not.toBeInTheDocument()
    },
  )
})

describe('ConsentDetailsPage content', () => {
  it('renders subject, service and purposes from the native payload', async () => {
    renderConsentDetailsPage('ACTIVE')

    expect(await screen.findByRole('heading', { name: 'Consent Details' })).toBeInTheDocument()
    expect(screen.getByText('marketing-spike')).toBeInTheDocument()
    expect(screen.getByText('1.0.0')).toBeInTheDocument()
    expect(screen.getAllByText('admin').length).toBeGreaterThan(0)
    expect(screen.getByText('dpdp-portal')).toBeInTheDocument()
  })

  it('lists authorizations by username with their state', async () => {
    renderConsentDetailsPage('ACTIVE')

    const authorizationsTable = await screen.findByRole('table', { name: 'Authorizations' })
    const rows = within(authorizationsTable).getAllByRole('row')

    expect(within(rows[1]).getByText('admin')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Approved')).toBeInTheDocument()
  })

  it('renders no consent lifecycle history section', async () => {
    renderConsentDetailsPage('ACTIVE')

    expect(await screen.findByRole('heading', { name: 'Consent Details' })).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Consent lifecycle' })).not.toBeInTheDocument()
    expect(screen.queryByText('View Resources')).not.toBeInTheDocument()
  })

  it('surfaces the BFF message when approving a consent that is not PENDING', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockImplementation(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'POST') {
        return {
          ok: false,
          status: 409,
          json: async () => ({
            code: 'INVALID_CONSENT_STATE',
            message: 'Consent is not in PENDING state.',
          }),
        }
      }

      return { ok: true, status: 200, json: async () => buildConsent('PENDING') }
    })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(
      <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
        <CssBaseline />
        <I18nextProvider i18n={i18n}>
          <QueryClientProvider client={queryClient}>
            <MemoryRouter initialEntries={[`/consents/${CONSENT_ID}`]}>
              <TestAuthorizationProvider scopes={Object.values(PORTAL_SCOPES)}>
                <Routes>
                  <Route path="/consents/:id" element={<ConsentDetailsPage />} />
                </Routes>
              </TestAuthorizationProvider>
            </MemoryRouter>
          </QueryClientProvider>
        </I18nextProvider>
      </OxygenUIThemeProvider>,
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Approve' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Approve Consent' }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Consent is not in PENDING state.')
    })
  })
})
