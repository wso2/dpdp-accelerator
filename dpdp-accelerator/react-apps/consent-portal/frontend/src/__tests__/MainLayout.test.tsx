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

import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { AcrylicOrangeTheme, CssBaseline, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import HeaderBreadcrumbs from '../components/layout/main-layout/HeaderBreadcrumbs'
import MainLayout from '../components/layout/main-layout/MainLayout'
import i18n from '../i18n/i18n'

interface MockSidebarProps {
  collapsed: boolean
}

vi.mock('../components/layout/sidebar/AppSidebar', () => ({
  default: ({ collapsed }: MockSidebarProps): React.JSX.Element => (
    <div data-testid="app-sidebar" data-collapsed={String(collapsed)} />
  ),
}))

afterEach(() => {
  cleanup()
})

function renderMainLayout(initialRoute = '/'): void {
  render(
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <CssBaseline />
      <I18nextProvider i18n={i18n}>
        <MemoryRouter initialEntries={[initialRoute]}>
          <Routes>
            <Route path="/" element={<MainLayout />}>
              <Route index element={<h1>Nested route content</h1>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </I18nextProvider>
    </OxygenUIThemeProvider>,
  )
}

function renderHeaderBreadcrumbs(initialRoute: string): void {
  render(
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <CssBaseline />
      <I18nextProvider i18n={i18n}>
        <MemoryRouter initialEntries={[initialRoute]}>
          <Routes>
            <Route path="*" element={<HeaderBreadcrumbs />} />
          </Routes>
        </MemoryRouter>
      </I18nextProvider>
    </OxygenUIThemeProvider>,
  )
}

describe('MainLayout', () => {
  it('renders translated header title and account menu trigger', () => {
    renderMainLayout()

    expect(screen.getByText('Consent Portal')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Account' })).toBeInTheDocument()
  })

  it('toggles sidebar collapsed state when header toggle is clicked', () => {
    renderMainLayout()

    const sidebar = screen.getByTestId('app-sidebar')
    const toggleButton = screen.getAllByRole('button')[0]

    expect(sidebar).toHaveAttribute('data-collapsed', 'false')

    fireEvent.click(toggleButton)
    expect(sidebar).toHaveAttribute('data-collapsed', 'true')

    fireEvent.click(toggleButton)
    expect(sidebar).toHaveAttribute('data-collapsed', 'false')
  })

  it('renders nested route content through Outlet', () => {
    renderMainLayout()

    expect(screen.getByRole('heading', { name: 'Nested route content' })).toBeInTheDocument()
  })

  it('decodes encoded consent IDs in breadcrumbs', () => {
    renderHeaderBreadcrumbs('/consents/consent%2F123%3Fdraft')

    expect(screen.getByText('consent/123?draft')).toBeInTheDocument()
  })

  it('falls back to the raw consent ID when breadcrumb decoding fails', () => {
    renderHeaderBreadcrumbs('/consents/consent%')

    expect(screen.getByText('consent%')).toBeInTheDocument()
  })

  it('renders the administration hierarchy for the administrative consent registry', () => {
    renderHeaderBreadcrumbs('/administration/consents')

    const breadcrumbs = screen.getByRole('navigation', { name: 'Breadcrumb' })
    expect(within(breadcrumbs).getByText('Home')).toBeInTheDocument()
    expect(within(breadcrumbs).getByRole('link', { name: 'Administration' })).toHaveAttribute(
      'href',
      '/administration/consents',
    )
    expect(within(breadcrumbs).getByText('Consents')).toHaveAttribute('aria-current', 'page')
  })

  it('decodes administrative consent IDs in breadcrumbs', () => {
    renderHeaderBreadcrumbs('/administration/consents/consent%2F123%3Fdraft')

    const breadcrumbs = screen.getByRole('navigation', { name: 'Breadcrumb' })
    expect(within(breadcrumbs).getByRole('link', { name: 'Administration' })).toBeInTheDocument()
    expect(within(breadcrumbs).getByRole('link', { name: 'Consents' })).toHaveAttribute(
      'href',
      '/administration/consents',
    )
    expect(within(breadcrumbs).getByText('consent/123?draft')).toHaveAttribute(
      'aria-current',
      'page',
    )
  })
})
