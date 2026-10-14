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
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { OxygenTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import i18n from '../i18n/i18n'
import TopicDeleteDialog from '../features/events/components/TopicDeleteDialog'
import type { TopicRecord } from '../types/topic'

function renderWithProviders(component: React.JSX.Element): void {
  render(
    <I18nextProvider i18n={i18n}>
      <OxygenUIThemeProvider theme={OxygenTheme}>{component}</OxygenUIThemeProvider>
    </I18nextProvider>,
  )
}

describe('TopicDeleteDialog', () => {
  const mockTopic: TopicRecord = {
    topicId: 'topic-123',
    name: 'user.consent.revoked',
    description: 'Triggered when consent is revoked',
    status: 'ACTIVE',
  }

  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    cleanup()
    vi.runOnlyPendingTimers()
    vi.useRealTimers()
  })

  it('renders modal without consent ID caption and highlights topic name in bold', () => {
    const onConfirm = vi.fn()
    const onClose = vi.fn()

    renderWithProviders(
      <TopicDeleteDialog
        open
        topic={mockTopic}
        loading={false}
        onClose={onClose}
        onConfirm={onConfirm}
      />,
    )

    // Accessible dialog name is strictly the title (does not conflate description)
    const dialog = screen.getByRole('dialog', { name: 'Confirm Topic Deletion' })
    expect(dialog).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 2, name: 'Confirm Topic Deletion' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('heading', { level: 6 })).not.toBeInTheDocument()

    // Confirmation question is associated with the dialog via aria-describedby
    const describedById = dialog.getAttribute('aria-describedby')
    expect(describedById).toBeTruthy()
    expect(document.getElementById(describedById ?? '')).toHaveTextContent(
      /Are you sure you want to delete topic/i,
    )

    // Confirm Topic Deletion title is present
    expect(screen.getByText('Confirm Topic Deletion')).toBeInTheDocument()

    // "Consent ID:" must NOT be rendered
    expect(screen.queryByText(/Consent ID/i)).not.toBeInTheDocument()

    // Topic name is rendered and bolded
    const nameElement = screen.getByText('user.consent.revoked')
    expect(nameElement).toBeInTheDocument()
    expect(nameElement).toHaveStyle({ fontWeight: 700 })

    // Note says deletion prevents new subscriptions and publication
    expect(
      screen.getByText(
        'Deleting a topic prevents new subscriptions and publication of events to this topic.',
      ),
    ).toBeInTheDocument()

    // Confirm button triggers onConfirm
    fireEvent.click(screen.getByRole('button', { name: /delete topic/i }))
    expect(onConfirm).toHaveBeenCalledTimes(1)

    // Cancel button triggers onClose
    fireEvent.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
