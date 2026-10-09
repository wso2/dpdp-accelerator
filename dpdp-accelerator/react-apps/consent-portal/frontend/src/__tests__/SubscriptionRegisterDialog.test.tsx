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
import { afterEach, describe, expect, it, vi } from 'vitest'
import SubscriptionRegisterDialog from '../features/events/components/SubscriptionRegisterDialog'
import i18n from '../i18n/i18n'

const topicsApi = vi.hoisted(() => ({
  fetchTopics: vi.fn(),
}))

const catalogApi = vi.hoisted(() => ({
  fetchPurposes: vi.fn(),
  buildPurposeFilter: vi.fn((name: string, _type: string) =>
    name ? `name co "${name}"` : undefined,
  ),
}))

vi.mock('../features/events/api/topicsApi', () => topicsApi)
vi.mock('../features/catalog/api/catalogApi', () => catalogApi)

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('SubscriptionRegisterDialog', () => {
  it('validates name and required fields before registering', async () => {
    topicsApi.fetchTopics.mockResolvedValue({
      items: [
        { topicId: 'topic-1', name: 'consent.update', status: 'ACTIVE', initiatedBy: 'SYSTEM' },
      ],
      total: 1,
    })
    const onSubmit = vi.fn()
    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <I18nextProvider i18n={i18n}>
          <OxygenUIThemeProvider theme={OxygenTheme}>
            <SubscriptionRegisterDialog
              open
              loading={false}
              onClose={vi.fn()}
              onSubmit={onSubmit}
            />
          </OxygenUIThemeProvider>
        </I18nextProvider>
      </QueryClientProvider>,
    )

    // Register button is disabled initially because no topics are selected
    expect(screen.getByRole('button', { name: 'Create Subscription' })).toBeDisabled()

    // Select category and topic
    fireEvent.mouseDown(screen.getByRole('combobox', { name: /Topic Category/i }))
    fireEvent.click(await screen.findByRole('option', { name: 'Consent Topics' }))

    const topicInput = screen.getByPlaceholderText('Search topics by name')
    fireEvent.focus(topicInput)
    fireEvent.keyDown(topicInput, { key: 'ArrowDown' })
    fireEvent.click(await screen.findByRole('option', { name: 'consent.update' }))

    // Now Register button is enabled, but name is still blank
    expect(screen.getByRole('button', { name: 'Create Subscription' })).toBeEnabled()
    fireEvent.click(screen.getByRole('button', { name: 'Create Subscription' }))
    expect(await screen.findByText('Subscription name is required.')).toBeInTheDocument()

    // Fill in name
    fireEvent.change(screen.getByLabelText(/Subscription Name/), {
      target: { value: 'My Subscription' },
    })

    // Fill in webhook URL
    fireEvent.change(screen.getByRole('textbox', { name: /Webhook Callback URL/ }), {
      target: { value: 'https://example.com/events' },
    })

    // Generate shared secret
    fireEvent.click(screen.getByRole('button', { name: /Generate new secret/i }))

    fireEvent.click(screen.getByRole('button', { name: 'Create Subscription' }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'My Subscription',
        topics: ['consent.update'],
      }),
    )
  })

  it('selects consent topics and submits subscription with filter and delivery configuration', async () => {
    topicsApi.fetchTopics.mockResolvedValue({
      items: [
        { topicId: 'topic-1', name: 'consent.update', status: 'ACTIVE', initiatedBy: 'SYSTEM' },
        { topicId: 'topic-2', name: 'consent.revoke', status: 'ACTIVE', initiatedBy: 'SYSTEM' },
      ],
      total: 2,
    })
    const onSubmit = vi.fn()
    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <I18nextProvider i18n={i18n}>
          <OxygenUIThemeProvider theme={OxygenTheme}>
            <SubscriptionRegisterDialog
              open
              loading={false}
              onClose={vi.fn()}
              onSubmit={onSubmit}
            />
          </OxygenUIThemeProvider>
        </I18nextProvider>
      </QueryClientProvider>,
    )

    fireEvent.change(screen.getByLabelText(/Subscription Name/), {
      target: { value: 'Test Webhook Sub' },
    })

    fireEvent.mouseDown(screen.getByRole('combobox', { name: /Topic Category/i }))
    fireEvent.click(await screen.findByRole('option', { name: 'Consent Topics' }))

    const topicInput = screen.getByPlaceholderText('Search topics by name')
    fireEvent.focus(topicInput)
    fireEvent.keyDown(topicInput, { key: 'ArrowDown' })
    fireEvent.click(await screen.findByRole('option', { name: 'consent.update' }))

    fireEvent.focus(topicInput)
    fireEvent.keyDown(topicInput, { key: 'ArrowDown' })
    fireEvent.click(await screen.findByRole('option', { name: 'consent.revoke' }))

    fireEvent.change(screen.getByRole('textbox', { name: /Webhook Callback URL/ }), {
      target: { value: 'https://receiver.example/callback' },
    })

    fireEvent.click(screen.getByRole('button', { name: /Generate new secret/i }))

    fireEvent.click(screen.getByRole('button', { name: 'Create Subscription' }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Test Webhook Sub',
        topics: ['consent.update', 'consent.revoke'],
        filter: { type: 'all', purposes: undefined },
        delivery: expect.objectContaining({
          mode: 'webhook',
          callbackUrl: 'https://receiver.example/callback',
        }),
      }),
    )
  })

  it.each(['user.account.delete', 'user.data.change'])(
    'hides consent-purpose controls and submits the all filter for %s',
    async (topic) => {
      topicsApi.fetchTopics.mockResolvedValue({
        items: [{ topicId: 'topic-1', name: topic, status: 'ACTIVE', initiatedBy: 'SYSTEM' }],
        total: 1,
      })
      const onSubmit = vi.fn()

      render(
        <QueryClientProvider
          client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
        >
          <I18nextProvider i18n={i18n}>
            <OxygenUIThemeProvider theme={OxygenTheme}>
              <SubscriptionRegisterDialog
                open
                loading={false}
                onClose={vi.fn()}
                onSubmit={onSubmit}
              />
            </OxygenUIThemeProvider>
          </I18nextProvider>
        </QueryClientProvider>,
      )

      fireEvent.change(screen.getByLabelText(/Subscription Name/), {
        target: { value: 'User Event Sub' },
      })

      fireEvent.mouseDown(screen.getByRole('combobox', { name: /Topic Category/i }))
      fireEvent.click(await screen.findByRole('option', { name: 'User Topics' }))

      const topicInput = screen.getByPlaceholderText('Search topics by name')
      fireEvent.focus(topicInput)
      fireEvent.keyDown(topicInput, { key: 'ArrowDown' })
      fireEvent.click(await screen.findByRole('option', { name: topic }))

      expect(screen.queryByLabelText('Consent Purpose Filter Mode')).not.toBeInTheDocument()
      expect(screen.queryByLabelText(/Consent Purposes/)).not.toBeInTheDocument()

      fireEvent.change(screen.getByRole('textbox', { name: /Webhook Callback URL/ }), {
        target: { value: 'https://receiver.example/callback' },
      })
      fireEvent.click(screen.getByRole('button', { name: /Generate new secret/i }))
      fireEvent.click(screen.getByRole('button', { name: 'Create Subscription' }))

      await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'User Event Sub',
          topics: [topic],
          filter: { type: 'all', purposes: undefined },
        }),
      )
    },
  )

  it('shows consent-purpose filter controls for consent topics and submits specific purposes', async () => {
    topicsApi.fetchTopics.mockResolvedValue({
      items: [
        {
          topicId: 'topic-1',
          name: 'consent.status.update',
          status: 'ACTIVE',
          initiatedBy: 'SYSTEM',
        },
      ],
      total: 1,
    })
    catalogApi.fetchPurposes.mockResolvedValue({
      totalResults: 2,
      links: [],
      Purposes: [
        { id: 'p-1', name: 'MARKETING', type: 'CONSENT' },
        { id: 'p-2', name: 'ANALYTICS', type: 'CONSENT' },
      ],
    })
    const onSubmit = vi.fn()

    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <I18nextProvider i18n={i18n}>
          <OxygenUIThemeProvider theme={OxygenTheme}>
            <SubscriptionRegisterDialog
              open
              loading={false}
              onClose={vi.fn()}
              onSubmit={onSubmit}
            />
          </OxygenUIThemeProvider>
        </I18nextProvider>
      </QueryClientProvider>,
    )

    fireEvent.change(screen.getByLabelText(/Subscription Name/), {
      target: { value: 'Specific Consent Sub' },
    })

    fireEvent.mouseDown(screen.getByRole('combobox', { name: /Topic Category/i }))
    fireEvent.click(await screen.findByRole('option', { name: 'Consent Topics' }))

    const topicInput = screen.getByPlaceholderText('Search topics by name')
    fireEvent.focus(topicInput)
    fireEvent.keyDown(topicInput, { key: 'ArrowDown' })
    fireEvent.click(await screen.findByRole('option', { name: 'consent.status.update' }))

    expect(screen.getByLabelText('Consent Purpose Filter Mode')).toBeInTheDocument()

    // Change filter mode to Specific Purposes
    fireEvent.mouseDown(screen.getByRole('combobox', { name: /Consent Purpose Filter Mode/i }))
    fireEvent.click(await screen.findByRole('option', { name: 'Specific Purposes' }))

    // Select purposes from picker
    const pickerInput = await screen.findByLabelText(/Consent Purposes/)
    expect(pickerInput).toBeInTheDocument()
    fireEvent.change(pickerInput, { target: { value: 'MARKETING' } })
    fireEvent.click(await screen.findByRole('option', { name: /MARKETING/ }))
    fireEvent.change(pickerInput, { target: { value: 'ANALYTICS' } })
    fireEvent.click(await screen.findByRole('option', { name: /ANALYTICS/ }))

    // Switch delivery mode to Poll
    fireEvent.mouseDown(screen.getByRole('combobox', { name: /Delivery Mode/i }))
    fireEvent.click(await screen.findByRole('option', { name: 'Poll' }))

    fireEvent.click(screen.getByRole('button', { name: /Generate new secret/i }))

    const submitButton = screen.getByRole('button', { name: 'Create Subscription' })
    fireEvent.submit(submitButton.closest('form')!)

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Specific Consent Sub',
        topics: ['consent.status.update'],
        filter: { type: 'specific', purposes: ['MARKETING', 'ANALYTICS'] },
        delivery: expect.objectContaining({ mode: 'poll' }),
      }),
    )
  })

  it('shows consent-purpose filter controls for custom topics and submits specific purposes', async () => {
    topicsApi.fetchTopics.mockResolvedValue({
      items: [
        {
          topicId: 'topic-custom-1',
          name: 'custom.order.events',
          status: 'ACTIVE',
          initiatedBy: 'USER',
        },
      ],
      total: 1,
    })
    catalogApi.fetchPurposes.mockResolvedValue({
      totalResults: 1,
      links: [],
      Purposes: [{ id: 'p-1', name: 'ORDER_FULFILLMENT', type: 'CONSENT' }],
    })
    const onSubmit = vi.fn()

    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <I18nextProvider i18n={i18n}>
          <OxygenUIThemeProvider theme={OxygenTheme}>
            <SubscriptionRegisterDialog
              open
              loading={false}
              onClose={vi.fn()}
              onSubmit={onSubmit}
            />
          </OxygenUIThemeProvider>
        </I18nextProvider>
      </QueryClientProvider>,
    )

    fireEvent.change(screen.getByLabelText(/Subscription Name/), {
      target: { value: 'Custom Topic Sub' },
    })

    fireEvent.mouseDown(screen.getByRole('combobox', { name: /Topic Category/i }))
    fireEvent.click(await screen.findByRole('option', { name: 'Custom Topics' }))

    const topicInput = screen.getByPlaceholderText('Search topics by name')
    fireEvent.focus(topicInput)
    fireEvent.keyDown(topicInput, { key: 'ArrowDown' })
    fireEvent.click(await screen.findByRole('option', { name: 'custom.order.events' }))

    // Purpose filter mode must be available for custom topics
    expect(screen.getByLabelText('Consent Purpose Filter Mode')).toBeInTheDocument()

    // Select Specific Purposes
    fireEvent.mouseDown(screen.getByRole('combobox', { name: /Consent Purpose Filter Mode/i }))
    fireEvent.click(await screen.findByRole('option', { name: 'Specific Purposes' }))

    // Select purpose from picker
    const pickerInput = await screen.findByLabelText(/Consent Purposes/)
    expect(pickerInput).toBeInTheDocument()
    fireEvent.change(pickerInput, { target: { value: 'ORDER_FULFILLMENT' } })
    fireEvent.click(await screen.findByRole('option', { name: /ORDER_FULFILLMENT/ }))

    fireEvent.change(screen.getByRole('textbox', { name: /Webhook Callback URL/ }), {
      target: { value: 'https://orders.example/callback' },
    })

    fireEvent.click(screen.getByRole('button', { name: /Generate new secret/i }))

    const submitButton = screen.getByRole('button', { name: 'Create Subscription' })
    fireEvent.submit(submitButton.closest('form')!)

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Custom Topic Sub',
        topics: ['custom.order.events'],
        filter: { type: 'specific', purposes: ['ORDER_FULFILLMENT'] },
      }),
    )
  })

  it('initializes shared secret as blank and generates secret when generate button is clicked', () => {
    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <I18nextProvider i18n={i18n}>
          <OxygenUIThemeProvider theme={OxygenTheme}>
            <SubscriptionRegisterDialog open loading={false} onClose={vi.fn()} onSubmit={vi.fn()} />
          </OxygenUIThemeProvider>
        </I18nextProvider>
      </QueryClientProvider>,
    )

    const secretInput = screen.getByLabelText(/Shared Secret/)
    expect(secretInput).toHaveValue('')

    const generateBtn = screen.getByRole('button', { name: /Generate new secret/i })
    fireEvent.click(generateBtn)

    expect(secretInput).not.toHaveValue('')
    expect((secretInput as HTMLInputElement).value).toHaveLength(32)
  })
})
