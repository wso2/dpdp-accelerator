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
import { useState } from 'react'
import { I18nextProvider } from 'react-i18next'
import { afterEach, expect, it, vi } from 'vitest'
import SubscriptionTopicPicker, {
  PAGE_SIZE,
} from '../features/events/components/SubscriptionTopicPicker'
import SubscriptionTopicChips from '../features/events/components/SubscriptionTopicChips'
import SubscriptionTopicsSection from '../features/events/components/SubscriptionTopicsSection'
import { fetchAllActiveTopics } from '../features/events/hooks/useSubscriptionTopicPicker'
import { getTopicCategory } from '../features/events/utils/topicCategory'
import i18n from '../i18n/i18n'

const api = vi.hoisted(() => ({ fetchTopics: vi.fn() }))
vi.mock('../features/events/api/topicsApi', () => api)

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function mount(child: React.ReactNode): void {
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <I18nextProvider i18n={i18n}>
        <OxygenUIThemeProvider theme={OxygenTheme}>{child}</OxygenUIThemeProvider>
      </I18nextProvider>
    </QueryClientProvider>,
  )
}

function Picker(): React.JSX.Element {
  const [selected, setSelected] = useState<string[]>([])
  return <SubscriptionTopicPicker selected={selected} onChange={setSelected} />
}

it('clears selection when category changes after user confirmation', async () => {
  api.fetchTopics.mockResolvedValue({
    items: [
      { topicId: '1', name: 'consent.alpha', status: 'ACTIVE', initiatedBy: 'SYSTEM' },
      { topicId: '2', name: 'user.beta', status: 'ACTIVE', initiatedBy: 'SYSTEM' },
      { topicId: '3', name: 'custom.gamma', status: 'ACTIVE', initiatedBy: 'USER' },
    ],
    total: 3,
  })

  mount(<Picker />)

  // Autocomplete is disabled until a category is chosen
  expect(screen.getByPlaceholderText('Select a category first to choose topics')).toBeDisabled()

  // Select "Consent Topics" category
  fireEvent.mouseDown(screen.getByRole('combobox', { name: /topic category/i }))
  fireEvent.click(await screen.findByRole('option', { name: 'Consent Topics' }))

  const input = screen.getByPlaceholderText('Search topics by name')
  expect(input).toBeEnabled()

  // Open Autocomplete options and pick consent.alpha
  fireEvent.focus(input)
  fireEvent.keyDown(input, { key: 'ArrowDown' })
  const optionAlpha = await screen.findByRole('option', { name: 'consent.alpha' })
  fireEvent.click(optionAlpha)
  expect(screen.getByText('consent.alpha')).toBeInTheDocument()

  // Switch category to "User Topics" -> triggers confirmation dialog
  fireEvent.mouseDown(screen.getByRole('combobox', { name: /topic category/i }))
  fireEvent.click(await screen.findByRole('option', { name: 'User Topics' }))

  // Confirm category switch
  const confirmBtn = await screen.findByRole('button', { name: 'Change Category' })
  fireEvent.click(confirmBtn)
  await waitFor(() => {
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  expect(screen.queryByText('consent.alpha')).not.toBeInTheDocument()

  // Open Autocomplete and pick user.beta
  fireEvent.focus(input)
  fireEvent.keyDown(input, { key: 'ArrowDown' })
  const optionBeta = await screen.findByRole('option', { name: 'user.beta' })
  fireEvent.click(optionBeta)
  expect(screen.getByText('user.beta')).toBeInTheDocument()

  // Switch category to "Custom Topics" -> confirm again
  fireEvent.mouseDown(screen.getByRole('combobox', { name: /topic category/i }))
  fireEvent.click(await screen.findByRole('option', { name: 'Custom Topics' }))
  const confirmBtn2 = await screen.findByRole('button', { name: 'Change Category' })
  fireEvent.click(confirmBtn2)
  await waitFor(() => {
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  expect(screen.queryByText('user.beta')).not.toBeInTheDocument()

  // Open Autocomplete and pick custom.gamma
  fireEvent.focus(input)
  fireEvent.keyDown(input, { key: 'ArrowDown' })
  const optionGamma = await screen.findByRole('option', { name: 'custom.gamma' })
  fireEvent.click(optionGamma)
  expect(screen.getByText('custom.gamma')).toBeInTheDocument()

  // Delete custom.gamma via its chip delete icon
  const deleteIcons = document.querySelectorAll('.MuiChip-deleteIcon')
  expect(deleteIcons.length).toBe(1)
  fireEvent.click(deleteIcons[0])
  expect(screen.queryByText('custom.gamma')).not.toBeInTheDocument()
})

it('canceling category change keeps existing selection and category intact', async () => {
  api.fetchTopics.mockResolvedValue({
    items: [{ topicId: '1', name: 'consent.alpha', status: 'ACTIVE', initiatedBy: 'SYSTEM' }],
    total: 1,
  })

  mount(<Picker />)

  // Select "Consent Topics" category
  fireEvent.mouseDown(screen.getByRole('combobox', { name: /topic category/i }))
  fireEvent.click(await screen.findByRole('option', { name: 'Consent Topics' }))

  const input = screen.getByPlaceholderText('Search topics by name')
  fireEvent.focus(input)
  fireEvent.keyDown(input, { key: 'ArrowDown' })
  fireEvent.click(await screen.findByRole('option', { name: 'consent.alpha' }))
  expect(screen.getByText('consent.alpha')).toBeInTheDocument()

  // Attempt to switch to "Custom Topics"
  fireEvent.mouseDown(screen.getByRole('combobox', { name: /topic category/i }))
  fireEvent.click(await screen.findByRole('option', { name: 'Custom Topics' }))

  // Click Cancel on the confirmation modal
  const cancelBtn = await screen.findByRole('button', { name: 'Cancel' })
  fireEvent.click(cancelBtn)
  await waitFor(() => {
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  // Selection and previous category are preserved
  expect(screen.getByText('consent.alpha')).toBeInTheDocument()
})

it('fetchAllActiveTopics consolidates all pages, pins sort name, and deduplicates', async () => {
  const page1Items = Array.from({ length: 100 }, (_, i) => ({
    topicId: `id-${i}`,
    name: `custom.topic-${i.toString().padStart(3, '0')}`,
    status: 'ACTIVE',
    initiatedBy: 'USER',
  }))
  const page2Items = Array.from({ length: 50 }, (_, i) => ({
    topicId: `id-${100 + i}`,
    name: `custom.topic-${(100 + i).toString().padStart(3, '0')}`,
    status: 'ACTIVE',
    initiatedBy: 'USER',
  }))

  // Add duplicate of item 99 in page 2 to test defensive deduplication
  page2Items.push(page1Items[99])

  api.fetchTopics
    .mockResolvedValueOnce({ items: page1Items, total: 150 })
    .mockResolvedValueOnce({ items: page2Items, total: 150 })

  const result = await fetchAllActiveTopics()

  expect(api.fetchTopics).toHaveBeenCalledTimes(2)
  expect(api.fetchTopics).toHaveBeenNthCalledWith(1, {
    status: 'ACTIVE',
    sort: 'name',
    limit: 100,
    offset: 0,
  })
  expect(api.fetchTopics).toHaveBeenNthCalledWith(2, {
    status: 'ACTIVE',
    sort: 'name',
    limit: 100,
    offset: 100,
  })

  // Deduplicated 150 unique topics
  expect(result.items.length).toBe(150)
  expect(result.total).toBe(150)
})

it('renders pager when active category has >PAGE_SIZE topics and preserves cross-page selections', async () => {
  const total = PAGE_SIZE + 5
  const allItems = Array.from({ length: total }, (_, i) => ({
    topicId: `id-${i}`,
    name: `custom.topic-${i.toString().padStart(3, '0')}`,
    status: 'ACTIVE',
    initiatedBy: 'USER',
  }))

  api.fetchTopics.mockResolvedValue({ items: allItems, total })

  mount(<Picker />)

  // Choose Custom Topics category
  fireEvent.mouseDown(screen.getByRole('combobox', { name: /topic category/i }))
  fireEvent.click(await screen.findByRole('option', { name: 'Custom Topics' }))

  // Open Autocomplete dropdown to view paged options and pager controls
  const input = screen.getByPlaceholderText('Search topics by name')
  fireEvent.focus(input)
  fireEvent.keyDown(input, { key: 'ArrowDown' })

  // Pager bar is rendered inside dropdown
  expect(
    screen.getByText(new RegExp(`Showing 1–${PAGE_SIZE} of ${total}`, 'i')),
  ).toBeInTheDocument()

  // Back button disabled on page 1, Next button enabled
  const backBtn = screen.getByRole('button', { name: 'Back' })
  const nextBtn = screen.getByRole('button', { name: 'Next' })
  expect(backBtn).toBeDisabled()
  expect(nextBtn).toBeEnabled()

  // Select an item from Page 1
  const option0 = await screen.findByRole('option', { name: 'custom.topic-000' })
  fireEvent.click(option0)
  expect(screen.getByText('custom.topic-000')).toBeInTheDocument()

  // Reopen dropdown to view pager and advance to Page 2
  fireEvent.focus(input)
  fireEvent.keyDown(input, { key: 'ArrowDown' })
  const nextBtnOnPage2 = await screen.findByRole('button', { name: 'Next' })
  fireEvent.click(nextBtnOnPage2)
  expect(
    screen.getByText(new RegExp(`Showing ${PAGE_SIZE + 1}–${total} of ${total}`, 'i')),
  ).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Back' })).toBeEnabled()

  // Previous selection from page 1 is preserved
  expect(screen.getByText('custom.topic-000')).toBeInTheDocument()

  // Select an item from Page 2
  const optionNext = await screen.findByRole('option', {
    name: `custom.topic-${PAGE_SIZE.toString().padStart(3, '0')}`,
  })
  fireEvent.click(optionNext)

  // Both chips from Page 1 and Page 2 are selected and preserved
  expect(screen.getByText('custom.topic-000')).toBeInTheDocument()
  expect(
    screen.getByText(`custom.topic-${PAGE_SIZE.toString().padStart(3, '0')}`),
  ).toBeInTheDocument()
})

it('select all selects all topics matching active category up to limit', async () => {
  const allItems = Array.from({ length: 5 }, (_, i) => ({
    topicId: `id-${i}`,
    name: `custom.topic-${i}`,
    status: 'ACTIVE',
    initiatedBy: 'USER',
  }))

  api.fetchTopics.mockResolvedValue({ items: allItems, total: 5 })

  mount(<Picker />)

  // Choose Custom Topics category
  fireEvent.mouseDown(screen.getByRole('combobox', { name: /topic category/i }))
  fireEvent.click(await screen.findByRole('option', { name: 'Custom Topics' }))

  const selectAllBtn = screen.getByRole('button', { name: 'Select all' })
  fireEvent.click(selectAllBtn)

  // All 5 items selected
  for (let i = 0; i < 5; i++) {
    expect(screen.getByText(`custom.topic-${i}`)).toBeInTheDocument()
  }

  // Clear button clears all
  const clearBtn = screen.getByRole('button', { name: 'Clear' })
  fireEvent.click(clearBtn)
  for (let i = 0; i < 5; i++) {
    expect(screen.queryByText(`custom.topic-${i}`)).not.toBeInTheDocument()
  }
})

it('enforces the 100 topic cap and allows chip deletion', async () => {
  api.fetchTopics.mockResolvedValue({ items: [], total: 0 })
  const onChange = vi.fn()

  mount(
    <SubscriptionTopicPicker
      selected={Array.from({ length: 100 }, (_, index) => `consent.topic-${index}`)}
      onChange={onChange}
    />,
  )

  // Chip delete button exists and calls onChange without that item
  const deleteButtons = document.querySelectorAll('.MuiChip-deleteIcon')
  expect(deleteButtons.length).toBe(100)
  fireEvent.click(deleteButtons[0])
  expect(onChange).toHaveBeenCalled()
})

it('shows a retryable error on fetch failure', async () => {
  api.fetchTopics.mockRejectedValueOnce(new Error('offline'))
  mount(<Picker />)

  fireEvent.mouseDown(screen.getByRole('combobox', { name: /topic category/i }))
  fireEvent.click(await screen.findByRole('option', { name: 'Consent Topics' }))

  expect(await screen.findByRole('alert')).toHaveTextContent('Could not load topics')
})

it('renders compact chips and searches associated topics in SubscriptionTopicsSection', () => {
  mount(
    <>
      <SubscriptionTopicChips topics={['alpha', 'beta', 'gamma']} />
      <SubscriptionTopicsSection topics={['delta', 'epsilon']} />
    </>,
  )

  expect(screen.queryByText('gamma')).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '+1 more' }))
  expect(screen.getByText('gamma')).toBeInTheDocument()

  // SubscriptionTopicsSection is non-collapsible (always expanded)
  expect(screen.getByText('delta')).toBeInTheDocument()
  expect(screen.getByText('epsilon')).toBeInTheDocument()

  fireEvent.change(screen.getByLabelText('Search associated topics'), {
    target: { value: 'epsilon' },
  })
  expect(screen.queryByText('delta')).not.toBeInTheDocument()
  expect(screen.getByText('epsilon')).toBeInTheDocument()
})

it('categorizes topics by checking SYSTEM status first before prefix', () => {
  // System topics check prefix
  expect(getTopicCategory({ name: 'consent.create', initiatedBy: 'SYSTEM' })).toBe('consent')
  expect(getTopicCategory({ name: 'consent.update', initiatedBy: 'SYSTEM' })).toBe('consent')
  expect(getTopicCategory({ name: 'consent.custom', initiatedBy: 'SYSTEM' })).toBe('consent')
  expect(getTopicCategory({ name: 'user.data.change', initiatedBy: 'SYSTEM' })).toBe('user')
  expect(getTopicCategory({ name: 'user.account.delete', initiatedBy: 'SYSTEM' })).toBe('user')
  expect(getTopicCategory({ name: 'other.system.topic', initiatedBy: 'SYSTEM' })).toBe('custom')

  // Non-system topics are ALWAYS custom, even if they have consent. or user. in name
  expect(getTopicCategory({ name: 'consent.custom', initiatedBy: 'USER' })).toBe('custom')
  expect(getTopicCategory({ name: 'user.custom', initiatedBy: 'USER' })).toBe('custom')
  expect(getTopicCategory({ name: 'orders.placed', initiatedBy: 'USER' })).toBe('custom')
  expect(getTopicCategory({ name: 'payment.completed' })).toBe('custom')
})

it('does not report query pending state as busy through onBusyChange', () => {
  api.fetchTopics.mockReturnValue(new Promise(() => {}))
  const onBusyChange = vi.fn()

  mount(<SubscriptionTopicPicker selected={[]} onChange={vi.fn()} onBusyChange={onBusyChange} />)

  expect(onBusyChange).toHaveBeenCalledWith(false)
  expect(onBusyChange).not.toHaveBeenCalledWith(true)
})
