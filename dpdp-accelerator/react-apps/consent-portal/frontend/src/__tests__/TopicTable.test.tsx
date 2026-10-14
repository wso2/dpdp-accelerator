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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { AcrylicOrangeTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TopicTable from '../features/events/components/TopicTable'
import i18n from '../i18n/i18n'
import type { TopicRecord } from '../types/topic'

afterEach(() => {
  cleanup()
})

describe('TopicTable', () => {
  it('identifies system topics and prevents their deletion', () => {
    const rows: TopicRecord[] = [
      {
        topicId: 'system-topic',
        name: 'consent.update',
        description: 'System topic',
        status: 'ACTIVE',
        initiatedBy: 'system',
      },
      {
        topicId: 'user-topic',
        name: 'custom.event',
        description: 'User topic',
        status: 'ACTIVE',
        initiatedBy: 'user',
      },
    ]
    const onDelete = vi.fn()

    render(
      <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
        <I18nextProvider i18n={i18n}>
          <TopicTable
            rows={rows}
            isLoading={false}
            isError={false}
            rowsPerPage={10}
            hasPreviousPage={false}
            hasNextPage={false}
            canWrite
            isMutating={false}
            onPreviousPage={vi.fn()}
            onNextPage={vi.fn()}
            onRowsPerPageChange={vi.fn()}
            onRetry={vi.fn()}
            onDelete={onDelete}
          />
        </I18nextProvider>
      </OxygenUIThemeProvider>,
    )

    expect(screen.getByText('SYSTEM')).toBeInTheDocument()
    expect(screen.getByText('USER')).toBeInTheDocument()

    const deleteButtons = screen.getAllByRole('button', { name: 'Delete topic' })
    expect(deleteButtons[0]).toBeDisabled()
    expect(deleteButtons[1]).toBeEnabled()

    fireEvent.click(deleteButtons[0])
    expect(onDelete).not.toHaveBeenCalled()

    fireEvent.click(deleteButtons[1])
    expect(onDelete).toHaveBeenCalledWith(rows[1])
  })
})
