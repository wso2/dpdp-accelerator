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
import SubscriptionDeliveryHistoryModal from '../features/events/components/SubscriptionDeliveryHistoryModal'
import i18n from '../i18n/i18n'
import type { SubscriptionEventHistoryRecord } from '../types/subscription'

const subscriptionsApi = vi.hoisted(() => ({
  fetchSubscriptionEventHistory: vi.fn(),
  retrySubscriptionDelivery: vi.fn(),
}))

vi.mock('../features/events/api/subscriptionsApi', () => subscriptionsApi)

describe('SubscriptionDeliveryHistoryModal manual retry', () => {
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

  function renderModal(
    props: {
      open?: boolean
      subscriptionId?: string
      deliveryId?: string
      onClose?: () => void
    } = {},
  ): void {
    render(
      <QueryClientProvider client={queryClient}>
        <I18nextProvider i18n={i18n}>
          <OxygenUIThemeProvider theme={OxygenTheme}>
            <SubscriptionDeliveryHistoryModal
              open={props.open ?? true}
              subscriptionId={props.subscriptionId ?? 'sub-1'}
              deliveryId={props.deliveryId ?? 'del-1'}
              onClose={props.onClose ?? vi.fn()}
            />
          </OxygenUIThemeProvider>
        </I18nextProvider>
      </QueryClientProvider>,
    )
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
      {
        attempt: 2,
        status: 'FAILED',
        httpStatus: 503,
        timestamp: 1700000005000,
        error: 'Service Unavailable',
      },
    ],
  }

  it('renders history and displays the retry button when manualRetryAvailable is true', async () => {
    subscriptionsApi.fetchSubscriptionEventHistory.mockResolvedValue(
      failedHistoryWithRetryAvailable,
    )

    renderModal()

    expect(await screen.findByText('#1')).toBeInTheDocument()
    expect(screen.getByText('#2')).toBeInTheDocument()

    const retryButton = screen.getByRole('button', { name: 'Retry' })
    expect(retryButton).toBeInTheDocument()
    expect(retryButton).toBeEnabled()
  })

  it('validates that when retry is pressed and failed it shows retry again', async () => {
    subscriptionsApi.fetchSubscriptionEventHistory.mockResolvedValue(
      failedHistoryWithRetryAvailable,
    )
    subscriptionsApi.retrySubscriptionDelivery.mockRejectedValueOnce(
      new Error('Webhook receiver endpoint still down'),
    )

    renderModal()

    const retryButton = await screen.findByRole('button', { name: 'Retry' })
    expect(retryButton).toBeEnabled()

    // Press retry
    fireEvent.click(retryButton)

    // Mutation fails: error alert is shown
    expect(await screen.findByText('Unable to load delivery history.')).toBeInTheDocument()

    // The retry button remains visible and enabled so the user can retry again
    const retryButtonAfterFailure = screen.getByRole('button', { name: 'Retry' })
    expect(retryButtonAfterFailure).toBeInTheDocument()
    expect(retryButtonAfterFailure).toBeEnabled()
  })

  it('allows retrying again after an initial failure and still shows retry if it fails repeatedly', async () => {
    subscriptionsApi.fetchSubscriptionEventHistory.mockResolvedValue(
      failedHistoryWithRetryAvailable,
    )
    subscriptionsApi.retrySubscriptionDelivery
      .mockRejectedValueOnce(new Error('First retry attempt failed'))
      .mockRejectedValueOnce(new Error('Second retry attempt failed'))

    renderModal()

    const retryButton = await screen.findByRole('button', { name: 'Retry' })

    // First retry attempt
    fireEvent.click(retryButton)
    await screen.findByText('Unable to load delivery history.')
    expect(screen.getByRole('button', { name: 'Retry' })).toBeEnabled()

    // Second retry attempt
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    await waitFor(() => {
      expect(subscriptionsApi.retrySubscriptionDelivery).toHaveBeenCalledTimes(2)
    })

    // Still shows the retry button after repeating failure
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeEnabled()
  })

  it('does not display the retry button when manualRetryAvailable is false', async () => {
    subscriptionsApi.fetchSubscriptionEventHistory.mockResolvedValue({
      ...failedHistoryWithRetryAvailable,
      manualRetryAvailable: false,
    })

    renderModal()

    await screen.findByText('#1')
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()
  })

  it('localizes completionStatus and attempt status correctly when status is err without showing Completion Evidence for poll', async () => {
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
    subscriptionsApi.fetchSubscriptionEventHistory.mockResolvedValue(pollHistory)

    renderModal({ subscriptionId: 'sub-1', deliveryId: 'del-2' })

    const errorElements = await screen.findAllByText('Error')
    expect(errorElements.length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText(/Poll error: invalid payload/)).toBeInTheDocument()
    expect(screen.queryByText('Completion Evidence')).not.toBeInTheDocument()
  })
})
