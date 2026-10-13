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
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { AcrylicOrangeTheme, OxygenUIThemeProvider, Table, TableBody } from '@wso2/oxygen-ui'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PurposeVersionRow from '../features/catalog/components/PurposeVersionRow'
import i18n from '../i18n/i18n'
import type { PurposeVersionSummary } from '../types/catalog'

const catalogApi = vi.hoisted(() => ({
  fetchPurposeVersion: vi.fn(),
}))

vi.mock('../features/catalog/api/catalogApi', () => catalogApi)

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function renderRow(versionSummary: PurposeVersionSummary, isLatest = false) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>
        <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
          <MemoryRouter>
            <Table>
              <TableBody>
                <PurposeVersionRow
                  purposeId="purpose-1"
                  purposeName="Marketing"
                  version={versionSummary}
                  isLatest={isLatest}
                  canWrite={true}
                  isSettingLatest={false}
                  onSetLatest={vi.fn()}
                  onDelete={vi.fn()}
                />
              </TableBody>
            </Table>
          </MemoryRouter>
        </OxygenUIThemeProvider>
      </I18nextProvider>
    </QueryClientProvider>,
  )
}

describe('PurposeVersionRow', () => {
  const version: PurposeVersionSummary = {
    id: 'ver-1',
    version: '1.0.0',
    description: 'Initial version',
  }

  it('renders collapsed by default and does not fetch version details until expanded', () => {
    renderRow(version)

    expect(screen.getByText('1.0.0')).toBeInTheDocument()
    expect(screen.getByText('Initial version')).toBeInTheDocument()

    const toggleButton = screen.getByRole('button', {
      name: 'Show elements for version 1.0.0',
    })
    expect(toggleButton).toHaveAttribute('aria-expanded', 'false')
    expect(catalogApi.fetchPurposeVersion).not.toHaveBeenCalled()
  })

  it('fetches and renders that specific version elements when expanded', async () => {
    catalogApi.fetchPurposeVersion.mockResolvedValueOnce({
      id: 'ver-1',
      version: '1.0.0',
      description: 'Initial version',
      elements: [
        {
          id: 'elem-1',
          name: 'email-address',
          displayName: 'Email Address',
          description: 'User email',
          mandatory: true,
        },
      ],
    })

    renderRow(version)

    const toggleButton = screen.getByRole('button', {
      name: 'Show elements for version 1.0.0',
    })
    fireEvent.click(toggleButton)

    expect(catalogApi.fetchPurposeVersion).toHaveBeenCalledWith('purpose-1', 'ver-1')

    await waitFor(() => {
      expect(screen.getByText('Email Address')).toBeInTheDocument()
    })

    expect(screen.getByText('email-address')).toBeInTheDocument()
    expect(screen.getByText('Mandatory')).toBeInTheDocument()
    expect(
      screen.getByRole('button', {
        name: 'Hide elements for version 1.0.0',
      }),
    ).toHaveAttribute('aria-expanded', 'true')
  })

  it('displays empty state when the version has no elements configured', async () => {
    catalogApi.fetchPurposeVersion.mockResolvedValueOnce({
      id: 'ver-1',
      version: '1.0.0',
      description: 'Initial version',
      elements: [],
    })

    renderRow(version)

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Show elements for version 1.0.0',
      }),
    )

    await waitFor(() => {
      expect(screen.getByText('No elements are configured for this version.')).toBeInTheDocument()
    })
  })

  it('displays an error alert when fetching version details fails', async () => {
    catalogApi.fetchPurposeVersion.mockRejectedValueOnce(new Error('Network error'))

    renderRow(version)

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Show elements for version 1.0.0',
      }),
    )

    await waitFor(() => {
      expect(
        screen.getByText('Unable to load elements for this version right now.'),
      ).toBeInTheDocument()
    })
  })
})
