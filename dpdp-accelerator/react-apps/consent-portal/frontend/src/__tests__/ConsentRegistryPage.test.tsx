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
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ConsentRegistryPage from '../features/consent-registry/ConsentRegistryPage'
import i18n from '../i18n/i18n'
import TestAuthorizationProvider from './TestAuthorizationProvider'
import { PORTAL_SCOPES } from '../utils/portalScopes'

const fetchMock = vi.fn()

function PendingConsentsLink(): React.JSX.Element {
  const navigate = useNavigate()

  return (
    <button type="button" onClick={() => navigate('/consents?status=Pending')}>
      Pending Consents
    </button>
  )
}

function CurrentLocation(): React.JSX.Element {
  const location = useLocation()

  return <span data-testid="current-location">{`${location.pathname}${location.search}`}</span>
}

function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  })
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
                      <PendingConsentsLink />
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

afterEach(() => {
  cleanup()
  fetchMock.mockReset()
  vi.unstubAllGlobals()
})

describe('ConsentRegistryPage', () => {
  it('renders page heading, filters, and grouped consent rows', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        data: [
          {
            id: 'CON/8291?draft',
            groupId: 'tesco-bank',
            type: 'Accounts',
            status: 'ACTIVE',
            createdTime: 1702800000,
            updatedTime: 1702800000,
            expirationTime: 0,
            purposes: [
              {
                purposeId: 'purpose-marketing',
                name: 'marketing_preferences',
                version: 'v1',
                displayName: 'Marketing',
                elements: [],
              },
            ],
          },
        ],
        metadata: {
          total: 1,
          offset: 0,
          count: 1,
          limit: 10,
        },
      }),
    })

    const queryClient = createQueryClient()

    renderConsentRegistryPage(queryClient)

    expect(await screen.findByRole('heading', { name: 'All Consents' })).toBeInTheDocument()
    expect(screen.getByLabelText('Consent filters')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Search by purpose name')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Status' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Advanced filters' })).toBeInTheDocument()
    expect(await screen.findByText('Group ID: tesco-bank')).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Consent registry table' })).toBeInTheDocument()
    expect(await screen.findByText('Marketing')).toBeInTheDocument()
    expect(await screen.findByText('Not applicable')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View' })).toHaveAttribute(
      'href',
      '/consents/CON%2F8291%3Fdraft',
    )
    // The consent id is deliberately not a column: it identifies a record to the
    // system, not to the person reading the table.
    expect(screen.queryByText('Consent ID')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Consent ID: CON/8291?draft')).not.toBeInTheDocument()
    expect(screen.queryByText('Type')).not.toBeInTheDocument()
  })

  it('shows an error message when consent fetch fails', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({
        code: 'INTERNAL_SERVER_ERROR',
        message: 'Something went wrong.',
      }),
    })

    const queryClient = createQueryClient()

    renderConsentRegistryPage(queryClient)

    expect(await screen.findByRole('heading', { name: 'All Consents' })).toBeInTheDocument()
    expect(await screen.findByText('Unable to load consents right now.')).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Consent registry table' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('renders consent rows in API order without sorting the current page locally', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        data: [
          {
            id: 'z-consent',
            groupId: 'group-1',
            type: 'accounts',
            status: 'ACTIVE',
            createdTime: 1702800000000,
            updatedTime: 1702800000000,
            purposes: [],
          },
          {
            id: 'a-consent',
            groupId: 'group-1',
            type: 'accounts',
            status: 'ACTIVE',
            createdTime: 1702900000000,
            updatedTime: 1702900000000,
            purposes: [],
          },
        ],
        metadata: { total: 2, offset: 0, count: 2, limit: 10 },
      }),
    })

    renderConsentRegistryPage(createQueryClient())

    // Order is asserted through the View links, which carry the consent id in
    // their href, now that the id is not shown as a column.
    const links = await screen.findAllByRole('link', { name: 'View' })

    expect(links.map((element) => element.getAttribute('href'))).toEqual([
      '/consents/z-consent',
      '/consents/a-consent',
    ])
  })

  it('shows the empty state for an empty v0.3 response', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        data: [],
        metadata: { total: 0, offset: 0, count: 0, limit: 10 },
      }),
    })

    renderConsentRegistryPage(createQueryClient())

    expect(
      await screen.findByText('No consents found for the selected filters.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Consent registry table' })).toBeInTheDocument()
  })

  it('shows the error state when a consent response has an unsupported status', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        data: [
          {
            id: 'CON-INVALID',
            groupId: 'sample-group',
            type: 'Accounts',
            status: 'UNKNOWN',
            createdTime: 1702800000000,
            updatedTime: 1702800000000,
            purposes: [],
          },
        ],
        metadata: { total: 1, offset: 0, count: 1, limit: 10 },
      }),
    })

    renderConsentRegistryPage(createQueryClient())

    expect(await screen.findByText('Unable to load consents right now.')).toBeInTheDocument()
  })

  it('does not render approve action for rejected consents', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        data: [
          {
            id: 'CON-9123',
            groupId: 'sample-group',
            type: 'Accounts',
            status: 'REJECTED',
            createdTime: 1702800000,
            updatedTime: 1702800000,
            purposes: [
              {
                purposeId: 'purpose-marketing',
                name: 'marketing_preferences',
                version: 'v1',
                displayName: 'Marketing',
                elements: [],
              },
            ],
          },
        ],
        metadata: {
          total: 1,
          offset: 0,
          count: 1,
          limit: 10,
        },
      }),
    })

    const queryClient = createQueryClient()

    renderConsentRegistryPage(queryClient)

    expect(await screen.findByRole('table', { name: 'Consent registry table' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Approve')).not.toBeInTheDocument()
  })

  it('uses page and rowsPerPage URL params when fetching consents', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        data: [],
        metadata: {
          total: 0,
          offset: 25,
          count: 0,
          limit: 25,
        },
      }),
    })

    const queryClient = createQueryClient()

    renderConsentRegistryPage(queryClient, '/consents?page=2&rowsPerPage=25')

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled()
    })

    const [requestUrl] = fetchMock.mock.calls[0] ?? []

    expect(String(requestUrl)).toContain('limit=25')
    expect(String(requestUrl)).toContain('offset=25')
    expect(String(requestUrl)).toContain('sort=updatedTime%3Adesc')
  })

  it('sends a valid URL sort to the API', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        data: [],
        metadata: { total: 0, offset: 0, count: 0, limit: 10 },
      }),
    })

    renderConsentRegistryPage(createQueryClient(), '/consents?sort=validityTime%3Aasc')

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled()
    })

    const [requestUrl] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))

    expect(url.searchParams.get('sort')).toBe('validityTime:asc')
  })

  it('falls back to updatedTime descending for an invalid URL sort', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        data: [],
        metadata: { total: 0, offset: 0, count: 0, limit: 10 },
      }),
    })

    renderConsentRegistryPage(createQueryClient(), '/consents?sort=unsupported%3Aasc')

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled()
    })

    const [requestUrl] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))

    expect(url.searchParams.get('sort')).toBe('updatedTime:desc')
  })

  it('requests a newly selected server sort from the first page', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        data: [
          {
            id: 'consent-1',
            groupId: 'group-1',
            type: 'accounts',
            status: 'ACTIVE',
            createdTime: 1702800000000,
            updatedTime: 1702800000000,
            expirationTime: 1702900000000,
            purposes: [],
          },
        ],
        metadata: { total: 1, offset: 10, count: 1, limit: 10 },
      }),
    })

    renderConsentRegistryPage(createQueryClient(), '/consents?page=2')

    expect(await screen.findByRole('button', { name: 'Status' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Status' }))

    await waitFor(() => {
      const requests = fetchMock.mock.calls.map(([requestUrl]) => new URL(String(requestUrl)))

      expect(
        requests.some(
          (url) =>
            url.searchParams.get('sort') === 'status:asc' && url.searchParams.get('offset') === '0',
        ),
      ).toBe(true)
      expect(screen.getByTestId('current-location')).toHaveTextContent(
        '/consents?sort=status%3Aasc',
      )
    })
  })

  it('prefetches exactly one next page when more consents are available', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = new URL(String(input))
      const offset = Number(url.searchParams.get('offset') ?? 0)

      return {
        ok: true,
        status: 200,
        json: async () => ({
          data: [],
          metadata: {
            total: 25,
            offset,
            count: 0,
            limit: 10,
          },
        }),
      }
    })

    renderConsentRegistryPage(createQueryClient())

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(2)
    })

    const requestedOffsets = fetchMock.mock.calls.map(([requestUrl]) => {
      const url = new URL(String(requestUrl))
      return Number(url.searchParams.get('offset'))
    })

    expect(requestedOffsets).toEqual([0, 10])
  })

  it('maps URL filters to v0.3 consent search parameters', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        data: [],
        metadata: { total: 0, offset: 0, count: 0, limit: 10 },
      }),
    })

    renderConsentRegistryPage(
      createQueryClient(),
      '/consents?status=Pending&purposeName=marketing&groupIds=group-1%2Cgroup-2&startDate=2026-01-01&endDate=2026-01-02',
    )

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled()
    })

    const [requestUrl] = fetchMock.mock.calls[0] ?? []
    const url = new URL(String(requestUrl))
    expect(url.searchParams.get('consentStatuses')).toBe('CREATED')
    expect(url.searchParams.get('purposeName')).toBe('marketing')
    expect(url.searchParams.get('groupIds')).toBe('group-1,group-2')
    expect(url.searchParams.get('elementName')).toBeNull()
    expect(url.searchParams.get('elementVersion')).toBeNull()
    expect(Number(url.searchParams.get('fromTime'))).toBeGreaterThan(1_000_000_000_000)
    expect(Number(url.searchParams.get('toTime'))).toBeGreaterThan(
      Number(url.searchParams.get('fromTime')),
    )
  })
})
