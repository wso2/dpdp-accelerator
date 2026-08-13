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
import { fetchCurrentUser } from '../features/auth/api/currentUserApi'
import { PORTAL_SCOPES } from '../utils/portalScopes'

const apiClientMocks = vi.hoisted(() => ({
  apiRequest: vi.fn(),
}))

vi.mock('../utils/apiClient', () => apiClientMocks)

afterEach(() => {
  vi.clearAllMocks()
})

describe('current-user API', () => {
  it('returns a typed current user from GET /me', async () => {
    apiClientMocks.apiRequest.mockResolvedValue({
      userId: 'user-1',
      organizationId: 'org-1',
      username: 'jdoe',
      scopes: [PORTAL_SCOPES.CONSENTS_READ_SELF],
    })

    await expect(fetchCurrentUser()).resolves.toEqual({
      userId: 'user-1',
      organizationId: 'org-1',
      username: 'jdoe',
      scopes: [PORTAL_SCOPES.CONSENTS_READ_SELF],
    })
    expect(apiClientMocks.apiRequest).toHaveBeenCalledWith('/me', { method: 'GET' })
  })

  it('rejects unknown scopes and malformed identity data', async () => {
    apiClientMocks.apiRequest.mockResolvedValue({
      userId: 'user-1',
      organizationId: '',
      username: 'jdoe',
      scopes: ['portal:unknown'],
    })

    await expect(fetchCurrentUser()).rejects.toThrow('invalid current-user response')
  })
})
