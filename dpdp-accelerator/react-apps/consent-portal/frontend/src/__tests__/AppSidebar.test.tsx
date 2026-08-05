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
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { AcrylicOrangeTheme, CssBaseline, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import AppSidebar from '../components/layout/sidebar/AppSidebar'
import i18n from '../i18n/i18n'
import TestAuthorizationProvider from './TestAuthorizationProvider'
import { PORTAL_SCOPES } from '../utils/portalScopes'

function LocationProbe(): React.JSX.Element {
  const location = useLocation()

  return <p>{`${location.pathname}${location.search}`}</p>
}

afterEach(() => {
  cleanup()
})

describe('AppSidebar', () => {
  it('renders navigation items and navigates to dashboard and pending consents', () => {
    render(
      <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
        <CssBaseline />
        <I18nextProvider i18n={i18n}>
          <MemoryRouter initialEntries={['/consents']}>
            <TestAuthorizationProvider scopes={Object.values(PORTAL_SCOPES)}>
              <Routes>
                <Route
                  path="*"
                  element={
                    <>
                      <AppSidebar collapsed={false} />
                      <LocationProbe />
                    </>
                  }
                />
              </Routes>
            </TestAuthorizationProvider>
          </MemoryRouter>
        </I18nextProvider>
      </OxygenUIThemeProvider>,
    )

    expect(screen.getByRole('complementary')).toBeInTheDocument()
    expect(screen.getByRole('navigation')).toBeInTheDocument()
    expect(screen.getByText('Consent')).toBeInTheDocument()
    expect(screen.getByText('/consents')).toBeInTheDocument()
    const navigationText = screen.getByRole('navigation').textContent ?? ''
    expect(navigationText.indexOf('Administration')).toBeLessThan(
      navigationText.indexOf('Definitions'),
    )

    fireEvent.click(screen.getByText('Pending Consents'))

    expect(screen.getByText('/consents?state=PENDING')).toBeInTheDocument()

    fireEvent.click(screen.getByText('Dashboard'))

    expect(screen.getByText('/dashboard')).toBeInTheDocument()
  })

  it('hides unauthorized items and empty categories', () => {
    render(
      <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
        <I18nextProvider i18n={i18n}>
          <MemoryRouter initialEntries={['/purposes']}>
            <TestAuthorizationProvider scopes={[PORTAL_SCOPES.PURPOSES_READ]}>
              <AppSidebar collapsed={false} />
            </TestAuthorizationProvider>
          </MemoryRouter>
        </I18nextProvider>
      </OxygenUIThemeProvider>,
    )

    expect(screen.getByText('Purposes')).toBeInTheDocument()
    expect(screen.queryByText('Consent')).not.toBeInTheDocument()
    expect(screen.queryByText('Dashboard')).not.toBeInTheDocument()
    expect(screen.queryByText('Elements')).not.toBeInTheDocument()
    expect(screen.queryByText('Administration')).not.toBeInTheDocument()
  })

  it('shows administration consents independently from self-service consents', () => {
    render(
      <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
        <I18nextProvider i18n={i18n}>
          <MemoryRouter initialEntries={['/administration/consents']}>
            <TestAuthorizationProvider scopes={[PORTAL_SCOPES.CONSENTS_READ_ANY]}>
              <Routes>
                <Route
                  path="*"
                  element={
                    <>
                      <AppSidebar collapsed={false} />
                      <LocationProbe />
                    </>
                  }
                />
              </Routes>
            </TestAuthorizationProvider>
          </MemoryRouter>
        </I18nextProvider>
      </OxygenUIThemeProvider>,
    )

    expect(screen.getByText('Administration')).toBeInTheDocument()
    expect(screen.getByText('Consents')).toBeInTheDocument()
    expect(screen.queryByText('Consent')).not.toBeInTheDocument()
    expect(screen.queryByText('All Consents')).not.toBeInTheDocument()
    expect(screen.queryByText('Dashboard')).not.toBeInTheDocument()
    expect(screen.getByText('/administration/consents')).toBeInTheDocument()
  })
})
