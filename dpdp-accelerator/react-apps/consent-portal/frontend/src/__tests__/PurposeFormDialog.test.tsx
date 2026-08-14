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
import { createElement, type ComponentProps } from 'react'
import { I18nextProvider } from 'react-i18next'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { OxygenTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import PurposeFormDialog from '../features/catalog/components/PurposeFormDialog'
import i18n from '../i18n/i18n'

vi.mock('../features/catalog/hooks/useCatalogQueries', () => ({
  useElementOptionsQuery: () => ({
    data: {
      data: [
        {
          elementId: 'element-email',
          name: 'email',
          namespace: 'profile',
          version: 'v1',
          type: 'basic',
          displayName: 'Email address',
          createdTime: 1,
        },
      ],
      metadata: { total: 1, offset: 0, count: 1, limit: 100 },
    },
    isLoading: false,
  }),
  useElementVersionsQuery: () => ({
    data: {
      elementId: 'element-email',
      name: 'email',
      namespace: 'profile',
      type: 'basic',
      versions: [{ version: 'v1', createdTime: 1 }],
    },
    isSuccess: true,
    isPending: false,
    isFetching: false,
    isError: false,
    refetch: vi.fn(),
  }),
}))

function renderDialog(overrides: Partial<ComponentProps<typeof PurposeFormDialog>> = {}): void {
  const props: ComponentProps<typeof PurposeFormDialog> = {
    open: true,
    initialValue: undefined,
    organizationId: 'test-org',
    loading: false,
    error: undefined,
    onClose: vi.fn(),
    onCreate: vi.fn(),
    onCreateVersion: undefined,
    ...overrides,
  }

  render(
    <I18nextProvider i18n={i18n}>
      <OxygenUIThemeProvider theme={OxygenTheme}>
        {createElement(PurposeFormDialog, props)}
      </OxygenUIThemeProvider>
    </I18nextProvider>,
  )
}

afterEach(() => {
  cleanup()
})

describe('purpose form dialog', () => {
  it('uses the compact layout and creates an organization-wide purpose without a group ID', () => {
    const onCreate = vi.fn()
    renderDialog({ onCreate })

    expect(screen.getByRole('dialog')).toHaveStyle({ maxWidth: '720px' })
    expect(screen.queryByRole('textbox', { name: /group id/i })).not.toBeInTheDocument()

    fireEvent.change(screen.getByRole('textbox', { name: /^name$/i }), {
      target: { value: 'contact-purpose' },
    })
    fireEvent.click(screen.getByRole('button', { name: /add element/i }))

    const elementInput = screen.getByRole('combobox', { name: /element/i })
    fireEvent.change(elementInput, { target: { value: 'Email' } })
    fireEvent.click(screen.getByRole('option', { name: 'Email address (profile)' }))
    fireEvent.click(screen.getByRole('button', { name: /^create$/i }))

    expect(onCreate).toHaveBeenCalledWith(
      {
        name: 'contact-purpose',
        displayName: undefined,
        description: undefined,
        properties: undefined,
        elements: [{ name: 'email', namespace: 'profile', version: 'v1', mandatory: false }],
      },
      undefined,
    )
  })

  it('shows and requires Group ID for a group-specific purpose', () => {
    renderDialog()

    fireEvent.mouseDown(screen.getByRole('combobox', { name: /purpose scope/i }))
    fireEvent.click(screen.getByRole('option', { name: 'Specific group' }))
    expect(screen.getByRole('textbox', { name: /group id/i })).toBeRequired()

    fireEvent.change(screen.getByRole('textbox', { name: /^name$/i }), {
      target: { value: 'contact-purpose' },
    })
    expect(screen.getByRole('button', { name: /^create$/i })).toBeDisabled()
  })

  it('keeps properties visible and rejects duplicate keys in create-version mode', () => {
    const onCreateVersion = vi.fn()
    renderDialog({
      initialValue: {
        purposeId: 'purpose-contact',
        name: 'contact-purpose',
        groupId: 'test-org',
        version: 'v1',
        elements: [
          {
            elementId: 'element-email',
            name: 'email',
            namespace: 'profile',
            version: 'v1',
            mandatory: false,
          },
        ],
        createdTime: 1,
      },
      onCreateVersion,
    })

    expect(screen.getByRole('button', { name: /add property/i })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /add property/i }))
    fireEvent.click(screen.getByRole('button', { name: /add property/i }))

    screen.getAllByRole('textbox', { name: /key/i }).forEach((input) => {
      fireEvent.change(input, { target: { value: 'retention' } })
    })
    fireEvent.click(screen.getByRole('button', { name: /^create$/i }))

    expect(screen.getByRole('alert')).toHaveTextContent('Property keys must be unique.')
    expect(onCreateVersion).not.toHaveBeenCalled()
  })
})
