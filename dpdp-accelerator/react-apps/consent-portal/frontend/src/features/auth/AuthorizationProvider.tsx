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

import { useCallback, useMemo, type ReactNode } from 'react'
import type { CurrentUser } from '../../types/auth'
import type { PortalScope } from '../../utils/portalScopes'
import AuthorizationContext from './authorizationContext'

interface AuthorizationProviderProps {
  currentUser: CurrentUser
  children: ReactNode
}

export function AuthorizationProvider({
  currentUser,
  children,
}: AuthorizationProviderProps): React.JSX.Element {
  const grantedScopes = useMemo(
    () => new Set<PortalScope>(currentUser.scopes),
    [currentUser.scopes],
  )
  const hasScope = useCallback(
    (scope: PortalScope): boolean => grantedScopes.has(scope),
    [grantedScopes],
  )
  const hasAnyScope = useCallback(
    (scopes: readonly PortalScope[]): boolean => scopes.some((scope) => grantedScopes.has(scope)),
    [grantedScopes],
  )
  const hasAllScopes = useCallback(
    (scopes: readonly PortalScope[]): boolean => scopes.every((scope) => grantedScopes.has(scope)),
    [grantedScopes],
  )
  const value = useMemo(
    () => ({ currentUser, hasScope, hasAnyScope, hasAllScopes }),
    [currentUser, hasAllScopes, hasAnyScope, hasScope],
  )

  return <AuthorizationContext.Provider value={value}>{children}</AuthorizationContext.Provider>
}
