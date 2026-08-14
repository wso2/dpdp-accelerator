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

import { readFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { parse } from 'yaml'
import { PORTAL_SCOPES } from '../utils/portalScopes'

function asRecord(value: unknown, name: string): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new Error(`${name} must be an object`)
  }
  return value as Record<string, unknown>
}

/*
 * The BFF for this deployment is the Java webapp this frontend is packaged
 * into, so its published contract sits beside the webapp rather than in a
 * sibling backend module as it does in the standalone portal.
 */
function documentedPortalScopes(): string[] {
  const openAPIPath = path.resolve(process.cwd(), '../openapi/portal-backend.yaml')
  const document = asRecord(parse(readFileSync(openAPIPath, 'utf8')) as unknown, 'document')
  const components = asRecord(document.components, 'components')
  const securitySchemes = asRecord(components.securitySchemes, 'securitySchemes')
  const portalOAuth = asRecord(securitySchemes.PortalOAuthDocumentation, 'PortalOAuthDocumentation')
  const flows = asRecord(portalOAuth.flows, 'flows')
  const authorizationCode = asRecord(flows.authorizationCode, 'authorizationCode')
  return Object.keys(asRecord(authorizationCode.scopes, 'scopes'))
}

describe('portal scope registry', () => {
  it('exactly matches the backend OpenAPI scope registry', () => {
    const frontendScopes = Object.values(PORTAL_SCOPES)
    const documentedScopes = documentedPortalScopes()

    expect(frontendScopes.every((scope) => scope.trim().length > 0)).toBe(true)
    expect(new Set(frontendScopes).size).toBe(frontendScopes.length)
    expect(new Set(documentedScopes).size).toBe(documentedScopes.length)
    expect([...frontendScopes].sort()).toEqual([...documentedScopes].sort())
  })
})
