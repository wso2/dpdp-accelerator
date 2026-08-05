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
import { MemoryRouter, useNavigate } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import RouteScrollManager from '../components/layout/main-layout/RouteScrollManager'

function NavigationControls(): React.JSX.Element {
  const navigate = useNavigate()

  return (
    <>
      <button type="button" onClick={() => navigate('/first?page=2')}>
        Change query
      </button>
      <button type="button" onClick={() => navigate('/second')}>
        Change page
      </button>
      <button type="button" onClick={() => navigate('/second#target')}>
        Open hash
      </button>
      <button type="button" onClick={() => navigate(-1)}>
        Go back
      </button>
      <div id="target">Target</div>
    </>
  )
}

function renderManager(initialEntries = ['/first'], initialIndex = 0): void {
  render(
    <MemoryRouter initialEntries={initialEntries} initialIndex={initialIndex}>
      <div data-testid="scroll-container">
        <RouteScrollManager />
        <NavigationControls />
      </div>
    </MemoryRouter>,
  )
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('RouteScrollManager', () => {
  it('scrolls to the top for a new pathname but not a query-only update', async () => {
    renderManager()
    const scrollContainer = screen.getByTestId('scroll-container')
    const scrollTo = vi.fn()
    scrollContainer.scrollTo = scrollTo

    fireEvent.click(screen.getByRole('button', { name: 'Change query' }))
    expect(scrollTo).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'Change page' }))

    await waitFor(() => {
      expect(scrollTo).toHaveBeenCalledWith({ top: 0, left: 0, behavior: 'auto' })
    })
  })

  it('scrolls hash navigation to its target instead of the page top', async () => {
    const scrollIntoView = vi.fn()
    const originalScrollIntoView = HTMLElement.prototype.scrollIntoView
    HTMLElement.prototype.scrollIntoView = scrollIntoView
    renderManager()
    const scrollContainer = screen.getByTestId('scroll-container')
    const scrollTo = vi.fn()
    scrollContainer.scrollTo = scrollTo

    fireEvent.click(screen.getByRole('button', { name: 'Open hash' }))

    await waitFor(() => {
      expect(scrollIntoView).toHaveBeenCalled()
    })
    expect(scrollTo).not.toHaveBeenCalled()
    HTMLElement.prototype.scrollIntoView = originalScrollIntoView
  })

  it('leaves back navigation to browser scroll restoration', async () => {
    renderManager(['/first', '/second'], 1)
    const scrollContainer = screen.getByTestId('scroll-container')
    const scrollTo = vi.fn()
    scrollContainer.scrollTo = scrollTo

    fireEvent.click(screen.getByRole('button', { name: 'Go back' }))

    await waitFor(() => {
      expect(scrollTo).not.toHaveBeenCalled()
    })
  })
})
