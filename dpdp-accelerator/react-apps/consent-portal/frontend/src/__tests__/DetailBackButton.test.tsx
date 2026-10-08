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
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import DetailBackButton from '../components/layout/main-layout/DetailBackButton'
import i18n from '../i18n/i18n'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('DetailBackButton', () => {
  it('renders "Back" label by default', () => {
    render(
      <I18nextProvider i18n={i18n}>
        <MemoryRouter>
          <DetailBackButton to="/events" />
        </MemoryRouter>
      </I18nextProvider>,
    )

    const button = screen.getByRole('button', { name: 'Back' })
    expect(button).toBeInTheDocument()
  })

  it('renders custom label when provided', () => {
    render(
      <I18nextProvider i18n={i18n}>
        <MemoryRouter>
          <DetailBackButton label="Go Previous" />
        </MemoryRouter>
      </I18nextProvider>,
    )

    expect(screen.getByRole('button', { name: 'Go Previous' })).toBeInTheDocument()
  })

  it('navigates to target route when "to" is provided', () => {
    render(
      <I18nextProvider i18n={i18n}>
        <MemoryRouter>
          <DetailBackButton to="/events" />
        </MemoryRouter>
      </I18nextProvider>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Back' }))
    expect(mockNavigate).toHaveBeenCalledWith('/events')
  })

  it('navigates -1 when "to" is omitted', () => {
    render(
      <I18nextProvider i18n={i18n}>
        <MemoryRouter>
          <DetailBackButton />
        </MemoryRouter>
      </I18nextProvider>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Back' }))
    expect(mockNavigate).toHaveBeenCalledWith(-1)
  })

  it('triggers custom onClick handler if provided', () => {
    const handleClick = vi.fn()
    render(
      <I18nextProvider i18n={i18n}>
        <MemoryRouter>
          <DetailBackButton onClick={handleClick} />
        </MemoryRouter>
      </I18nextProvider>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Back' }))
    expect(handleClick).toHaveBeenCalledTimes(1)
    expect(mockNavigate).not.toHaveBeenCalled()
  })
})
