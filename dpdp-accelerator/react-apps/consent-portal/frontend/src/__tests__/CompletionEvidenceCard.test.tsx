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
import { afterEach, describe, expect, it, vi } from 'vitest'
import CompletionEvidenceCard from '../features/events/components/CompletionEvidenceCard'

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, defaultValue?: string) => {
      if (key === 'subscriptions.deliveryHistory.completion') return 'Completion Evidence'
      if (key === 'subscriptions.deliveryHistory.collapseEvidence')
        return 'Collapse completion evidence'
      if (key === 'subscriptions.deliveryHistory.expandEvidence')
        return 'Expand completion evidence'
      if (key === 'subscriptions.deliveryHistory.attemptStatus') return 'Status'
      if (key === 'events.status.completed') return 'Completed'
      if (key === 'events.status.failed') return 'Failed'
      if (key === 'copyableText.copy') return 'Copy'
      return defaultValue ?? key
    },
  }),
}))

describe('CompletionEvidenceCard', () => {
  it('renders nothing when both completionStatus and completionEvidence are missing', () => {
    const { container } = render(<CompletionEvidenceCard />)
    expect(container).toBeEmptyDOMElement()
  })

  it('renders nothing when deliveryMode is poll', () => {
    const { container } = render(
      <CompletionEvidenceCard
        completionStatus="completed"
        completionEvidence="https://cloudengage.example/evidence/receipt.pdf"
        deliveryMode="poll"
      />,
    )
    expect(container).toBeEmptyDOMElement()
  })

  it('renders status chip and external link when evidence is a URL', () => {
    render(
      <CompletionEvidenceCard
        completionStatus="completed"
        completionEvidence="https://cloudengage.example/evidence/receipt.pdf"
      />,
    )

    expect(screen.getByText('Completion Evidence')).toBeInTheDocument()
    expect(screen.getByText('Completed')).toBeInTheDocument()

    const link = screen.getByRole('link', { name: /receipt\.pdf/ })
    expect(link).toHaveAttribute('href', 'https://cloudengage.example/evidence/receipt.pdf')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('toggles collapse when header is clicked', () => {
    render(
      <CompletionEvidenceCard
        completionStatus="completed"
        completionEvidence="https://cloudengage.example/evidence/receipt.pdf"
      />,
    )

    const toggleButton = screen.getByLabelText('Collapse completion evidence')
    expect(toggleButton).toBeInTheDocument()

    // Collapse
    fireEvent.click(toggleButton)
    expect(screen.getByLabelText('Expand completion evidence')).toBeInTheDocument()

    // Expand again
    fireEvent.click(screen.getByLabelText('Expand completion evidence'))
    expect(screen.getByLabelText('Collapse completion evidence')).toBeInTheDocument()
  })

  it('toggles collapse on Enter or Space key press', () => {
    render(
      <CompletionEvidenceCard
        completionStatus="completed"
        completionEvidence="https://cloudengage.example/evidence/receipt.pdf"
      />,
    )

    const toggleButton = screen.getByRole('button', { name: 'Collapse completion evidence' })
    expect(toggleButton).toHaveAttribute('aria-expanded', 'true')

    // Collapse via Enter key
    fireEvent.keyDown(toggleButton, { key: 'Enter' })
    expect(screen.getByRole('button', { name: 'Expand completion evidence' })).toHaveAttribute(
      'aria-expanded',
      'false',
    )

    // Expand via Space key
    fireEvent.keyDown(toggleButton, { key: ' ' })
    expect(screen.getByRole('button', { name: 'Collapse completion evidence' })).toHaveAttribute(
      'aria-expanded',
      'true',
    )
  })

  it('copies evidence to clipboard when copy button is clicked', () => {
    const writeTextMock = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, {
      clipboard: {
        writeText: writeTextMock,
      },
    })

    render(
      <CompletionEvidenceCard
        completionStatus="completed"
        completionEvidence="https://cloudengage.example/evidence/receipt.pdf"
      />,
    )

    const copyBtn = screen.getByLabelText('Copy')
    fireEvent.click(copyBtn)

    expect(writeTextMock).toHaveBeenCalledWith('https://cloudengage.example/evidence/receipt.pdf')
  })

  it('renders text evidence properly when evidence is not a URL', () => {
    render(
      <CompletionEvidenceCard
        completionStatus="err"
        completionEvidence="Worker processing failed: malformed payload"
      />,
    )

    expect(screen.getByText('Worker processing failed: malformed payload')).toBeInTheDocument()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
  })
})
