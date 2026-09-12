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

import { config } from './config'
import { env } from './env'
import { PROVISIONING_SCOPES } from './provisioningScopes'

/**
 * Mints the `client_credentials` token that administrative SCIM2 calls in this suite authenticate
 * with.
 *
 * Identity Server 7.3 disables HTTP Basic auth on protected resources for any deployment whose
 * root organization was created on or after the cutoff in the product's own
 * repository/conf/compatibility-settings-metadata.json (`basicAuth.disableBasicAuth`,
 * 2026-08-13). The shipped H2 database carries a root organization stamped at WSO2's build time,
 * which is why Basic auth still works there; MySQL and every other backend write
 * CURRENT_TIMESTAMP at install and land past the cutoff. A client_credentials token is the one
 * approach that behaves identically on every database type.
 *
 * The client itself is created once per environment by scripts/bootstrap-provisioning-app.ts.
 */

interface ClientCredentials {
  clientId: string
  clientSecret: string
}

function credentials(): ClientCredentials {
  const configured = config.provisioningClient
  if (!configured?.clientId || !configured.clientSecret) {
    throw new Error(
      'No provisioning client configured. Mint one (once per environment) with:\n' +
        '  npm run bootstrap:provisioning-app',
    )
  }
  return configured
}

// One token per worker process: every caller here is administrative setup/teardown within a
// single run, comfortably inside the token's lifetime, so there is nothing to gain from minting
// one per call.
let token: Promise<string> | undefined

export function provisioningToken(): Promise<string> {
  token ??= (async (): Promise<string> => {
    const { clientId, clientSecret } = credentials()
    const response = await fetch(`${env.identityServerBaseUrl}/oauth2/token`, {
      method: 'POST',
      headers: {
        Authorization: `Basic ${Buffer.from(`${clientId}:${clientSecret}`).toString('base64')}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: new URLSearchParams({
        grant_type: 'client_credentials',
        scope: PROVISIONING_SCOPES.join(' '),
      }),
      signal: AbortSignal.timeout(20_000),
    })

    if (!response.ok) {
      throw new Error(
        `The provisioning client ${clientId} could not obtain a token (status ` +
          `${String(response.status)}): ${await response.text()}. ` +
          'Re-mint it with: npm run bootstrap:provisioning-app',
      )
    }
    const body = (await response.json()) as { access_token?: string }
    if (!body.access_token) {
      throw new Error(`The token response for ${clientId} carried no access_token.`)
    }
    return body.access_token
  })()

  return token
}

/** Headers for an administrative SCIM2 call. */
export async function provisioningHeaders(): Promise<Record<string, string>> {
  return {
    Authorization: `Bearer ${await provisioningToken()}`,
    'Content-Type': 'application/json',
    Accept: 'application/json',
  }
}
