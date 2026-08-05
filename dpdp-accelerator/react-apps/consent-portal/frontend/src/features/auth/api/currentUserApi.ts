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

import type { CurrentUser } from '../../../types/auth'
import { apiRequest } from '../../../utils/apiClient'
import { isPortalScope } from '../../../utils/portalScopes'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function parseCurrentUser(value: unknown): CurrentUser {
  if (
    !isRecord(value) ||
    typeof value.userId !== 'string' ||
    !value.userId.trim() ||
    typeof value.organizationId !== 'string' ||
    !value.organizationId.trim() ||
    !Array.isArray(value.scopes) ||
    !value.scopes.every(isPortalScope)
  ) {
    throw new Error('invalid current-user response')
  }

  return {
    userId: value.userId,
    organizationId: value.organizationId,
    scopes: value.scopes,
  }
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  return parseCurrentUser(await apiRequest<unknown>('/me', { method: 'GET' }))
}
