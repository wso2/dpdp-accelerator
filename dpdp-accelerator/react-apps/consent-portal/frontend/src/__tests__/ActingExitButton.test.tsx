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
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { AcrylicOrangeTheme, CssBaseline, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ActingExitButton from '../features/nominee/actingAs/ActingExitButton'
import type { ActingSession } from '../features/nominee/actingAs/actingAsContext'
import i18n from '../i18n/i18n'

const session = vi.hoisted(() => ({
  value: {
    ownerId: 'owner-1',
    nomineeId: 'nominee-1',
    scope: ['CONSENT_VIEW', 'CONSENT_REVOKE'],
    expiresAt: new Date(Date.now() + 300_000).toISOString(),
  } as ActingSession | null,
}))
const stopActing = vi.hoisted(() => vi.fn())

vi.mock('../features/nominee/actingAs/actingAsContext', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  useActingAs: (): { session: ActingSession | null; stopActing: () => void } => ({
    session: session.value,
    stopActing,
  }),
}))

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  stopActing.mockClear()
  session.value = {
    ownerId: 'owner-1',
    nomineeId: 'nominee-1',
    scope: ['CONSENT_VIEW', 'CONSENT_REVOKE'],
    expiresAt: new Date(Date.now() + 300_000).toISOString(),
  }
})

function renderExit(): void {
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
        <CssBaseline />
        <I18nextProvider i18n={i18n}>
          <MemoryRouter initialEntries={['/consents']}>
            <ActingExitButton />
          </MemoryRouter>
        </I18nextProvider>
      </OxygenUIThemeProvider>
    </QueryClientProvider>,
  )
}

describe('acting exit', () => {
  it('offers a way out while acting', () => {
    renderExit()

    expect(screen.getByRole('button', { name: /exit/i })).toBeTruthy()
  })

  // Acting runs in a tab of its own. Leaving it open after Exit means two
  // windows signed in as two different people, which is how somebody ends up
  // acting for an owner without realising it.
  it('closes the tab on exit, after dropping the session', () => {
    const close = vi.spyOn(window, 'close').mockImplementation(() => undefined)
    renderExit()

    fireEvent.click(screen.getByRole('button', { name: /exit/i }))

    expect(stopActing).toHaveBeenCalledOnce()
    expect(close).toHaveBeenCalledOnce()
  })

  it('renders nothing when not acting', () => {
    session.value = null
    renderExit()

    expect(screen.queryByRole('button', { name: /exit/i })).toBeNull()
  })
})
