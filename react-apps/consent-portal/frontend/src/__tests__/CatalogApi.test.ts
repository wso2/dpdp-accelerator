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
import * as catalogApi from '../features/catalog/api/catalogApi'

const fetchMock = vi.fn()

afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
})

function mockJSONResponse(payload: unknown): void {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => payload,
  })
}

function requestedUrl(): URL {
  const [requestUrl] = fetchMock.mock.calls[0] ?? []
  return new URL(String(requestUrl))
}

describe('catalog API', () => {
  it('reads elements with cursor parameters and returns the Elements envelope', async () => {
    const elements = [
      {
        id: '415976b9-85b3-409c-b195-35a2733b0afb',
        name: 'email-spike',
        displayName: 'Email Address',
        description: 'User email address',
        tenantDomain: 'carbon.super',
      },
    ]
    mockJSONResponse({ totalResults: 1, links: [], Elements: elements })

    await expect(catalogApi.fetchElements({ limit: 10, after: 'Mg==' })).resolves.toEqual({
      totalResults: 1,
      links: [],
      Elements: elements,
    })
    expect(requestedUrl().pathname).toBe('/api/consent-elements')
    expect(Object.fromEntries(requestedUrl().searchParams)).toEqual({
      limit: '10',
      after: 'Mg==',
    })
  })

  it('reads a single element by encoded id', async () => {
    mockJSONResponse({ id: 'element/1', name: 'email-spike' })

    await catalogApi.fetchElement('element/1')

    expect(requestedUrl().pathname).toBe('/api/consent-elements/element%2F1')
  })

  it('reads purposes with a before cursor', async () => {
    mockJSONResponse({ totalResults: 0, links: [], Purposes: [] })

    await catalogApi.fetchPurposes({ limit: 25, before: 'MQ==' })

    expect(requestedUrl().pathname).toBe('/api/consent-purposes')
    expect(Object.fromEntries(requestedUrl().searchParams)).toEqual({
      limit: '25',
      before: 'MQ==',
    })
  })

  it('reads a purpose with its mandatory element flags', async () => {
    mockJSONResponse({
      id: '690eb7ef',
      name: 'marketing-spike',
      type: 'CONSENT',
      latestVersion: { id: 'cc689174', version: '1.0.0' },
      elements: [{ id: '415976b9', name: 'email-spike', mandatory: true }],
      properties: {},
      tenantDomain: 'carbon.super',
    })

    const purpose = await catalogApi.fetchPurpose('690eb7ef')

    expect(requestedUrl().pathname).toBe('/api/consent-purposes/690eb7ef')
    expect(purpose.elements[0].mandatory).toBe(true)
  })

  it('reads purpose versions read-only', async () => {
    mockJSONResponse({
      totalResults: 1,
      links: [],
      Versions: [{ id: 'cc689174', version: '1.0.0', description: 'Marketing comms' }],
    })

    const versions = await catalogApi.fetchPurposeVersions('690eb7ef', { limit: 50 })

    expect(requestedUrl().pathname).toBe('/api/consent-purposes/690eb7ef/versions')
    expect(versions.Versions).toHaveLength(1)
  })

  it('exposes no element or purpose write operations', () => {
    expect(Object.keys(catalogApi).sort()).toEqual([
      'fetchElement',
      'fetchElements',
      'fetchPurpose',
      'fetchPurposeVersions',
      'fetchPurposes',
    ])
  })
})
