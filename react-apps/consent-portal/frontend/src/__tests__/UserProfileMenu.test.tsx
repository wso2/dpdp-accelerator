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
import { AcrylicOrangeTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { I18nextProvider } from 'react-i18next'
import { afterEach, describe, expect, it, vi } from 'vitest'
import UserProfileMenu from '../components/layout/main-layout/UserProfileMenu'
import i18n from '../i18n/i18n'

const authMocks = vi.hoisted(() => ({
  getUserProfile: vi.fn<() => Record<string, unknown> | undefined>(),
  logout: vi.fn<() => Promise<void>>(),
}))

vi.mock('../utils/authClient', () => authMocks)

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function renderMenu(profile?: Record<string, unknown>): void {
  authMocks.getUserProfile.mockReturnValue(profile)
  authMocks.logout.mockResolvedValue()
  render(
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <I18nextProvider i18n={i18n}>
        <UserProfileMenu />
      </I18nextProvider>
    </OxygenUIThemeProvider>,
  )
  fireEvent.click(screen.getByRole('button', { name: 'Account' }))
}

describe('UserProfileMenu', () => {
  it.each([
    [{ name: 'Name', displayName: 'Display Name' }, 'Name'],
    [{ displayName: 'Display Name' }, 'Display Name'],
    [{ given_name: 'Ada', family_name: 'Lovelace' }, 'Ada Lovelace'],
    [{ preferred_username: 'preferred', username: 'username' }, 'preferred'],
    [{ username: 'username' }, 'username'],
  ])('resolves the display name from claims in priority order', (profile, expectedName) => {
    renderMenu(profile)

    expect(screen.getByText(expectedName)).toBeInTheDocument()
  })

  it('uses email and avatar claims', () => {
    renderMenu({
      name: 'Portal User',
      email: 'user@example.com',
      picture: 'https://example.com/u.png',
    })

    expect(screen.getByText('user@example.com')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Portal User' })).toHaveAttribute(
      'src',
      'https://example.com/u.png',
    )
  })

  it('falls back to subject when email is unavailable', () => {
    renderMenu({ name: 'Portal User', sub: 'user-1' })

    expect(screen.getByText('user-1')).toBeInTheDocument()
  })

  it('shows translated fallbacks when profile claims are unavailable', () => {
    renderMenu()

    expect(screen.getByText('Unknown user')).toBeInTheDocument()
    expect(screen.getByText('No email available')).toBeInTheDocument()
  })

  it('renders untrusted profile claims as text rather than executable markup', () => {
    const payload = '<img src=x onerror=alert(1)>'
    renderMenu({ name: payload, email: '<script>alert(1)</script>' })

    expect(screen.getByText(payload)).toBeInTheDocument()
    expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument()
    expect(document.querySelector('script')).not.toBeInTheDocument()
    expect(document.querySelector('img[src="x"]')).not.toBeInTheDocument()
  })

  it('shows a translated logout error and allows retry', async () => {
    renderMenu({ name: 'Portal User', email: 'user@example.com' })
    authMocks.logout.mockRejectedValueOnce(new Error('logout failed')).mockResolvedValueOnce()

    fireEvent.click(screen.getByRole('menuitem', { name: 'Sign out' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Unable to sign out. Please try again.')
    expect(authMocks.logout).toHaveBeenCalledOnce()

    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))

    await waitFor(() => expect(authMocks.logout).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })

  it('preserves the displayed profile while logout clears token cookies', async () => {
    let completeLogout: (() => void) | undefined
    authMocks.getUserProfile.mockReturnValue({
      name: 'Portal User',
      email: 'user@example.com',
    })
    authMocks.logout.mockReturnValue(
      new Promise<void>((resolve) => {
        completeLogout = resolve
      }),
    )
    render(
      <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
        <I18nextProvider i18n={i18n}>
          <UserProfileMenu />
        </I18nextProvider>
      </OxygenUIThemeProvider>,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Account' }))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Sign out' }))

    authMocks.getUserProfile.mockReturnValue(undefined)
    fireEvent.click(screen.getByRole('button', { name: 'Account' }))

    expect(screen.getByText('Portal User')).toBeInTheDocument()
    expect(screen.queryByText('Unknown user')).not.toBeInTheDocument()

    completeLogout?.()
    await waitFor(() => expect(authMocks.logout).toHaveBeenCalledOnce())
  })
})
