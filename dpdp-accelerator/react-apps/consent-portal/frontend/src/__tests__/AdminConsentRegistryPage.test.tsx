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
import { cleanup, render, screen, waitFor } from '@testing-library/react'
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

describe('AdminConsentRegistryPage', () => {
  it('uses the consent details endpoint and lists its result for a Consent ID search', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        id: 'consent/123',
        groupId: 'group-1',
        type: 'accounts',
        status: 'ACTIVE',
        updatedTime: 1_780_000_000_000,
        expirationTime: 0,
        purposes: [
          {
            purposeId: 'purpose-1',
            name: 'account_access',
            displayName: 'Account access',
            version: 'v1',
            elements: [],
          },
        ],
      }),
    })
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })

    render(
      <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
        <CssBaseline />
        <I18nextProvider i18n={i18n}>
          <QueryClientProvider client={queryClient}>
            <MemoryRouter initialEntries={['/administration/consents?consentId=consent%2F123']}>
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

    expect(await screen.findByText('Account access')).toBeInTheDocument()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    const [requestUrl] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.pathname).toBe('/api/consents/consent%2F123')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      details: 'true',
      includeStatusHistory: 'true',
    })
    expect(screen.getByRole('link', { name: 'View' })).toHaveAttribute(
      'href',
      '/administration/consents/consent%2F123',
    )
  })
})
