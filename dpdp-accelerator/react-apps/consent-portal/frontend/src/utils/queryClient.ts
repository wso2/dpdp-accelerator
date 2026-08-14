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

import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import { ACTING_STORAGE_KEY } from '../features/nominee/actingAs/actingAsContext'
import { APIError } from './apiClient'

/**
 * A browser holds one acting session across all its tabs. Opening a second owner
 * replaces the first, leaving the older tab believing it is somewhere it no
 * longer is; the server answers ACTING_OWNER_MISMATCH rather than handing it the
 * newer owner's records.
 *
 * Recovery has to be a reload. Every view in the tab was built for the previous
 * owner - cached lists, an open dialog, a half-filled form - and none of it is
 * about the account this browser is now acting on.
 */
function handleActingOwnerMismatch(error: unknown): void {
  if (!(error instanceof APIError) || error.code !== 'ACTING_OWNER_MISMATCH') {
    return
  }
  if (!window.sessionStorage.getItem(ACTING_STORAGE_KEY)) {
    return
  }
  window.sessionStorage.removeItem(ACTING_STORAGE_KEY)
  window.location.reload()
}

const STALE_TIME_IN_MS = 0.5 * 60 * 1000

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: handleActingOwnerMismatch }),
  mutationCache: new MutationCache({ onError: handleActingOwnerMismatch }),
  defaultOptions: {
    queries: {
      staleTime: STALE_TIME_IN_MS,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})

export default queryClient
