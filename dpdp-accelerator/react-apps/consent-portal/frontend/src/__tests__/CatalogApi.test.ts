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

import { afterEach, describe, expect, it, vi } from 'vitest'
import { createElement } from '../features/catalog/api/catalogApi'

const fetchMock = vi.fn()

afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
})

describe('createElement', () => {
  it('returns the created element from an array response', async () => {
    const element = {
      elementId: '82017ca0-a120-4bf3-ac1c-ea76630d86cf',
      name: 'Test 2',
      namespace: 'default',
      type: 'basic' as const,
      version: 'v1',
      createdTime: 1785447718074,
    }
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [{ status: 'SUCCESS', element }],
    })

    await expect(createElement({ name: 'Test 2', type: 'basic' })).resolves.toEqual(element)
  })

  it('preserves support for the wrapped bulk response', async () => {
    const element = {
      elementId: 'element-1',
      name: 'Email',
      namespace: 'default',
      type: 'basic' as const,
      version: 'v1',
      createdTime: 1785447718074,
    }
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        results: [{ index: 0, status: 'SUCCESS', data: element }],
      }),
    })

    await expect(createElement({ name: 'Email', type: 'basic' })).resolves.toEqual(element)
  })

  it('surfaces a string failure returned for an item', async () => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [{ status: 'FAILED', error: 'Element already exists' }],
    })

    await expect(createElement({ name: 'Email', type: 'basic' })).rejects.toThrow(
      'Element already exists',
    )
  })
})
