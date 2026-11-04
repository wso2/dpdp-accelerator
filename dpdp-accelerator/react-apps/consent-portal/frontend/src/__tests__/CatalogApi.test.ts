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
import { getNextCursor, getPreviousCursor } from '../utils/cursorPagination'

const transport = vi.hoisted(() => ({
  httpRequest: vi.fn(),
  login: vi.fn(),
}))

vi.mock('../utils/authClient', () => ({
  httpRequest: transport.httpRequest,
  login: transport.login,
}))

const CONSENT_MGT_V2 = '/api/identity/consent-mgt/v2.0'

afterEach(() => {
  vi.clearAllMocks()
})

function respondWith(payload: unknown, status = 200): void {
  transport.httpRequest.mockResolvedValue({ status, data: payload })
}

/** The request config the api handed to the auth SDK. */
function sentRequest(): { url: string; method?: string; data?: unknown } {
  return transport.httpRequest.mock.calls[0]?.[0] as ReturnType<typeof sentRequest>
}

function requestedUrl(): URL {
  return new URL(sentRequest().url)
}

function requestBody(): unknown {
  return JSON.parse(String(sentRequest().data))
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
    respondWith({ totalResults: 1, links: [], Elements: elements })

    await expect(catalogApi.fetchElements({ limit: 10, after: 'Mg==' })).resolves.toEqual({
      totalResults: 1,
      links: [],
      Elements: elements,
    })
    expect(requestedUrl().pathname).toBe(`${CONSENT_MGT_V2}/elements`)
    expect(Object.fromEntries(requestedUrl().searchParams)).toEqual({
      limit: '10',
      after: 'Mg==',
    })
  })

  it('reads next and previous cursors out of an Elements response', async () => {
    respondWith({
      totalResults: 2,
      links: [
        {
          rel: 'next',
          href: 'https://localhost:9443/api/identity/consent-mgt/v2.0/elements?limit=1&after=MQ==',
        },
        {
          rel: 'previous',
          href: 'https://localhost:9443/api/identity/consent-mgt/v2.0/elements?limit=1&before=MA==',
        },
      ],
      Elements: [],
    })

    const page = await catalogApi.fetchElements({ limit: 1 })

    expect(getNextCursor(page.links)).toBe('MQ==')
    expect(getPreviousCursor(page.links)).toBe('MA==')
  })

  it('quotes and escapes a name search into the filter grammar', () => {
    expect(catalogApi.buildElementNameFilter('email')).toBe('name co "email"')
    expect(catalogApi.buildElementNameFilter('  email address  ')).toBe('name co "email address"')
    expect(catalogApi.buildElementNameFilter('say "hi"')).toBe('name co "say \\"hi\\""')
    expect(catalogApi.buildElementNameFilter('   ')).toBeUndefined()
  })

  it('forwards the name filter as a query parameter when searching elements', async () => {
    respondWith({ totalResults: 0, links: [], Elements: [] })

    await catalogApi.fetchElements({ limit: 10, filter: 'name co "email"' })

    expect(Object.fromEntries(requestedUrl().searchParams)).toEqual({
      limit: '10',
      filter: 'name co "email"',
    })
  })

  it('reads a single element by encoded id', async () => {
    respondWith({ id: 'element/1', name: 'email-spike' })

    await catalogApi.fetchElement('element/1')

    expect(requestedUrl().pathname).toBe(`${CONSENT_MGT_V2}/elements/element%2F1`)
  })

  it('reads purposes with a before cursor', async () => {
    respondWith({ totalResults: 0, links: [], Purposes: [] })

    await catalogApi.fetchPurposes({ limit: 25, before: 'MQ==' })

    expect(requestedUrl().pathname).toBe(`${CONSENT_MGT_V2}/purposes`)
    expect(Object.fromEntries(requestedUrl().searchParams)).toEqual({
      limit: '25',
      before: 'MQ==',
    })
  })

  it('reads a purpose with its mandatory element flags', async () => {
    respondWith({
      id: '690eb7ef',
      name: 'marketing-spike',
      type: 'CONSENT',
      latestVersion: { id: 'cc689174', version: '1.0.0' },
      elements: [{ id: '415976b9', name: 'email-spike', mandatory: true }],
      properties: {},
      tenantDomain: 'carbon.super',
    })

    const purpose = await catalogApi.fetchPurpose('690eb7ef')

    expect(requestedUrl().pathname).toBe(`${CONSENT_MGT_V2}/purposes/690eb7ef`)
    expect(purpose.elements[0].mandatory).toBe(true)
  })

  it('reads purpose versions read-only', async () => {
    respondWith({
      totalResults: 1,
      links: [],
      Versions: [{ id: 'cc689174', version: '1.0.0', description: 'Marketing comms' }],
    })

    const versions = await catalogApi.fetchPurposeVersions('690eb7ef', { limit: 50 })

    expect(requestedUrl().pathname).toBe(`${CONSENT_MGT_V2}/purposes/690eb7ef/versions`)
    expect(versions.Versions).toHaveLength(1)
  })

  it('reads a specific purpose version including elements', async () => {
    respondWith({
      id: 'cc689174',
      version: '1.0.0',
      description: 'Marketing comms',
      elements: [
        {
          id: 'e123',
          name: 'email',
          displayName: 'Email',
          mandatory: true,
        },
      ],
      properties: { retention: '30' },
    })

    const version = await catalogApi.fetchPurposeVersion('690eb7ef', 'cc689174')

    expect(requestedUrl().pathname).toBe(`${CONSENT_MGT_V2}/purposes/690eb7ef/versions/cc689174`)
    expect(version.version).toBe('1.0.0')
    expect(version.elements).toHaveLength(1)
    expect(version.elements?.[0].mandatory).toBe(true)
    expect(version.properties).toEqual({ retention: '30' })
  })

  it('combines name and type into the purpose filter grammar', () => {
    expect(catalogApi.buildPurposeFilter('ui', '')).toBe('name co "ui"')
    expect(catalogApi.buildPurposeFilter('', 'Marketing')).toBe('type eq "Marketing"')
    expect(catalogApi.buildPurposeFilter('ui', 'Marketing')).toBe(
      'name co "ui" and type eq "Marketing"',
    )
    expect(catalogApi.buildPurposeFilter('', '')).toBeUndefined()
  })

  it('forwards the combined filter when searching purposes', async () => {
    respondWith({ totalResults: 0, links: [], Purposes: [] })

    await catalogApi.fetchPurposes({ limit: 10, filter: 'name co "ui" and type eq "Marketing"' })

    expect(Object.fromEntries(requestedUrl().searchParams)).toEqual({
      limit: '10',
      filter: 'name co "ui" and type eq "Marketing"',
    })
  })

  it('creates a purpose with elements and properties', async () => {
    respondWith({
      id: 'bfc68e5e',
      name: 'ui-verify-purpose',
      type: 'Marketing',
      latestVersion: { id: 'e8d303e4', version: 'v1' },
      elements: [],
    })

    await catalogApi.createPurpose({
      name: 'ui-verify-purpose',
      type: 'Marketing',
      version: 'v1',
      elements: [{ id: 'e12b', mandatory: true }],
      properties: { lawfulBasis: 'consent' },
    })

    expect(requestedUrl().pathname).toBe(`${CONSENT_MGT_V2}/purposes`)
    expect(sentRequest().method).toBe('POST')
    expect(requestBody()).toEqual({
      name: 'ui-verify-purpose',
      type: 'Marketing',
      version: 'v1',
      elements: [{ id: 'e12b', mandatory: true }],
      properties: { lawfulBasis: 'consent' },
    })
  })

  it('deletes a purpose by encoded id and expects no content', async () => {
    respondWith(undefined, 204)

    await catalogApi.deletePurpose('purpose/1')

    expect(requestedUrl().pathname).toBe(`${CONSENT_MGT_V2}/purposes/purpose%2F1`)
    expect(sentRequest().method).toBe('DELETE')
  })

  it('creates a purpose version, not inheriting anything by default', async () => {
    respondWith({ id: '3efd4b26', version: 'v2', elements: [] })

    await catalogApi.createPurposeVersion('bfc68e5e', { version: 'v2', setAsLatest: true })

    expect(requestedUrl().pathname).toBe(`${CONSENT_MGT_V2}/purposes/bfc68e5e/versions`)
    expect(sentRequest().method).toBe('POST')
    expect(requestBody()).toEqual({ version: 'v2', setAsLatest: true })
  })

  it('sets a version as latest via PUT with the version id in the body', async () => {
    respondWith(undefined, 204)

    await catalogApi.setLatestPurposeVersion('bfc68e5e', 'e8d303e4')

    expect(requestedUrl().pathname).toBe(`${CONSENT_MGT_V2}/purposes/bfc68e5e/versions/latest`)
    expect(sentRequest().method).toBe('PUT')
    expect(requestBody()).toEqual({ id: 'e8d303e4' })
  })

  it('deletes a purpose version by encoded ids and expects no content', async () => {
    respondWith(undefined, 204)

    await catalogApi.deletePurposeVersion('purpose/1', 'version/2')

    expect(requestedUrl().pathname).toBe(
      `${CONSENT_MGT_V2}/purposes/purpose%2F1/versions/version%2F2`,
    )
    expect(sentRequest().method).toBe('DELETE')
  })

  it('creates an element with the given fields', async () => {
    respondWith({
      id: 'e12b',
      name: 'gap-check-el',
      displayName: 'Gap Check',
      description: 'probe',
      properties: { pii: 'true' },
    })

    const created = await catalogApi.createElement({
      name: 'gap-check-el',
      displayName: 'Gap Check',
      description: 'probe',
      properties: { pii: 'true' },
    })

    expect(requestedUrl().pathname).toBe(`${CONSENT_MGT_V2}/elements`)
    expect(sentRequest().method).toBe('POST')
    expect(requestBody()).toEqual({
      name: 'gap-check-el',
      displayName: 'Gap Check',
      description: 'probe',
      properties: { pii: 'true' },
    })
    expect(created.id).toBe('e12b')
  })

  it('deletes an element by encoded id and expects no content', async () => {
    respondWith(undefined, 204)

    await catalogApi.deleteElement('element/1')

    expect(requestedUrl().pathname).toBe(`${CONSENT_MGT_V2}/elements/element%2F1`)
    expect(sentRequest().method).toBe('DELETE')
  })

  it('exposes exactly the element and purpose operations the catalog UI uses', () => {
    expect(Object.keys(catalogApi).sort()).toEqual([
      'buildElementNameFilter',
      'buildPurposeFilter',
      'createElement',
      'createPurpose',
      'createPurposeVersion',
      'deleteElement',
      'deletePurpose',
      'deletePurposeVersion',
      'fetchElement',
      'fetchElements',
      'fetchPurpose',
      'fetchPurposeVersion',
      'fetchPurposeVersions',
      'fetchPurposes',
      'setLatestPurposeVersion',
    ])
  })
})
