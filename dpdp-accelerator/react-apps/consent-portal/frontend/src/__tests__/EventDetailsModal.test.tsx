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
import { OxygenTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { I18nextProvider } from 'react-i18next'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import EventDetailsModal from '../features/events/components/EventDetailsModal'
import i18n from '../i18n/i18n'
import type { EventRecord } from '../types/event'
import type { SubscriptionEventHistoryRecord } from '../types/subscription'

const eventsApi = vi.hoisted(() => ({
  fetchEventDeliveryHistory: vi.fn(),
}))

const subscriptionsApi = vi.hoisted(() => ({
  retrySubscriptionDelivery: vi.fn(),
}))

vi.mock('../features/events/api/eventsApi', () => eventsApi)
vi.mock('../features/events/api/subscriptionsApi', () => subscriptionsApi)

describe('EventDetailsModal manual retry', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    })
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  const mockEvent: EventRecord = {
    eventId: 'evt-1',
    deliveryId: 'del-1',
    subscriptionId: 'sub-1',
    topic: 'user.events',
    deliveryMode: 'WEBHOOK',
    currentStatus: 'FAILED',
    occurredAt: 1700000000000,
  }

  const failedHistoryWithRetryAvailable: SubscriptionEventHistoryRecord = {
    deliveryId: 'del-1',
    eventId: 'evt-1',
    topic: 'user.events',
    deliveryMode: 'WEBHOOK',
    currentStatus: 'FAILED',
    occurredAt: 1700000000000,
    manualRetryAvailable: true,
    history: [
      {
        attempt: 1,
        status: 'FAILED',
        httpStatus: 503,
        timestamp: 1700000001000,
        error: 'Service Unavailable',
      },
    ],
  }

  function renderModal(event: EventRecord = mockEvent): void {
    render(
      <QueryClientProvider client={queryClient}>
        <I18nextProvider i18n={i18n}>
          <OxygenUIThemeProvider theme={OxygenTheme}>
            <EventDetailsModal open event={event} onClose={vi.fn()} />
          </OxygenUIThemeProvider>
        </I18nextProvider>
      </QueryClientProvider>,
    )
  }

  it('validates that when retry is pressed and failed it shows retry again in EventDetailsModal', async () => {
    eventsApi.fetchEventDeliveryHistory.mockResolvedValue(failedHistoryWithRetryAvailable)
    subscriptionsApi.retrySubscriptionDelivery.mockRejectedValueOnce(
      new Error('Webhook dispatch failed'),
    )

    renderModal()

    const retryButton = await screen.findByRole('button', { name: 'Retry' })
    expect(retryButton).toBeEnabled()

    // Press retry
    fireEvent.click(retryButton)

    // Verify error notification is displayed
    expect(await screen.findByText('Unable to load delivery history.')).toBeInTheDocument()

    // Verify the retry button remains available to retry again
    const retryButtonAfterFailure = screen.getByRole('button', { name: 'Retry' })
    expect(retryButtonAfterFailure).toBeInTheDocument()
    expect(retryButtonAfterFailure).toBeEnabled()
  })

  it('allows retrying again after failure and invokes retry API each time', async () => {
    eventsApi.fetchEventDeliveryHistory.mockResolvedValue(failedHistoryWithRetryAvailable)
    subscriptionsApi.retrySubscriptionDelivery
      .mockRejectedValueOnce(new Error('First failure'))
      .mockResolvedValueOnce({ ...failedHistoryWithRetryAvailable, manualRetryAvailable: false })

    renderModal()

    const retryButton = await screen.findByRole('button', { name: 'Retry' })
    fireEvent.click(retryButton)

    await screen.findByText('Unable to load delivery history.')
    expect(screen.getByRole('button', { name: 'Retry' })).toBeEnabled()

    // Press retry again
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))

    await waitFor(() => {
      expect(subscriptionsApi.retrySubscriptionDelivery).toHaveBeenCalledTimes(2)
      expect(subscriptionsApi.retrySubscriptionDelivery).toHaveBeenCalledWith('sub-1', 'del-1')
    })
  })

  it('renders delivery mode in Title Case and maps err status to Error', async () => {
    const pollHistory: SubscriptionEventHistoryRecord = {
      deliveryId: 'del-2',
      eventId: 'evt-2',
      topic: 'user.events',
      deliveryMode: 'poll',
      currentStatus: 'err',
      completionStatus: 'err',
      completionEvidence: 'Poll error: invalid payload',
      occurredAt: 1700000000000,
      manualRetryAvailable: false,
      history: [
        {
          attempt: 1,
          status: 'err',
          timestamp: 1700000001000,
          error: 'Poll error: invalid payload',
        },
      ],
    }
    eventsApi.fetchEventDeliveryHistory.mockResolvedValue(pollHistory)

    renderModal({
      ...mockEvent,
      deliveryId: 'del-2',
      eventId: 'evt-2',
      deliveryMode: 'poll',
      currentStatus: 'err',
    })

    // Wait for history to load
    expect(await screen.findByText(/Poll error: invalid payload/)).toBeInTheDocument()

    // Mode is rendered in Title Case
    expect(screen.getByText('Poll')).toBeInTheDocument()

    // Completion Evidence is NOT rendered for poll
    expect(screen.queryByText('Completion Evidence')).not.toBeInTheDocument()

    // Status 'err' is localized as 'Error' (header chip, attempt row)
    const errorElements = screen.getAllByText('Error')
    expect(errorElements.length).toBeGreaterThanOrEqual(1)
  })
})
