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

import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import useAuthorization from '../features/auth/useAuthorization'
import ScopeGuard from '../features/auth/ScopeGuard'
import { PORTAL_SCOPES } from '../utils/portalScopes'
import TestAuthorizationProvider from './TestAuthorizationProvider'
import firstAuthorizedPath from '../features/auth/authorizationRoutes'

function AuthorizationProbe(): React.JSX.Element {
  const { hasScope, hasAnyScope, hasAllScopes } = useAuthorization()
  return (
    <>
      <span>{String(hasScope(PORTAL_SCOPES.ELEMENTS_READ))}</span>
      <span>
        {String(hasAnyScope([PORTAL_SCOPES.ELEMENTS_WRITE, PORTAL_SCOPES.CONSENTS_READ_SELF]))}
      </span>
      <span>
        {String(hasAllScopes([PORTAL_SCOPES.ELEMENTS_READ, PORTAL_SCOPES.CONSENTS_READ_SELF]))}
      </span>
    </>
  )
}

describe('frontend authorization', () => {
  it('selects the first authorized destination in stable navigation order', () => {
    expect(firstAuthorizedPath([PORTAL_SCOPES.ELEMENTS_READ, PORTAL_SCOPES.PURPOSES_READ])).toBe(
      '/purposes',
    )
    expect(firstAuthorizedPath([PORTAL_SCOPES.CONSENTS_READ_SELF])).toBe('/dashboard')
    expect(firstAuthorizedPath([PORTAL_SCOPES.CONSENTS_READ_ANY])).toBe('/administration/consents')
    expect(firstAuthorizedPath([])).toBeUndefined()
  })

  it('provides typed single, any, and all scope checks', () => {
    render(
      <TestAuthorizationProvider
        scopes={[PORTAL_SCOPES.ELEMENTS_READ, PORTAL_SCOPES.CONSENTS_READ_SELF]}
      >
        <AuthorizationProbe />
      </TestAuthorizationProvider>,
    )

    expect(screen.getAllByText('true')).toHaveLength(3)
  })

  it('renders guarded content only with the required scope', () => {
    const { rerender } = render(
      <TestAuthorizationProvider scopes={[PORTAL_SCOPES.ELEMENTS_READ]}>
        <ScopeGuard scope={PORTAL_SCOPES.ELEMENTS_WRITE}>
          <span>Write control</span>
        </ScopeGuard>
      </TestAuthorizationProvider>,
    )
    expect(screen.queryByText('Write control')).not.toBeInTheDocument()

    rerender(
      <TestAuthorizationProvider
        scopes={[PORTAL_SCOPES.ELEMENTS_READ, PORTAL_SCOPES.ELEMENTS_WRITE]}
      >
        <ScopeGuard scope={PORTAL_SCOPES.ELEMENTS_WRITE}>
          <span>Write control</span>
        </ScopeGuard>
      </TestAuthorizationProvider>,
    )
    expect(screen.getByText('Write control')).toBeInTheDocument()
  })
})
