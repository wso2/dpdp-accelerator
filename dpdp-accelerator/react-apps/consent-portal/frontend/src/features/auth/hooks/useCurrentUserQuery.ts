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

import { type UseQueryResult, useQuery } from '@tanstack/react-query'
import type { CurrentUser } from '../../../types/auth'
import { fetchCurrentUser } from '../api/currentUserApi'

export const CURRENT_USER_QUERY_KEY = ['current-user'] as const

export default function useCurrentUserQuery(enabled: boolean): UseQueryResult<CurrentUser> {
  return useQuery({
    queryKey: CURRENT_USER_QUERY_KEY,
    queryFn: fetchCurrentUser,
    enabled,
    retry: false,
  })
}
