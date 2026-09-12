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

/**
 * Mints the machine-to-machine OAuth2 client that scripts/provision-test-users.sh authenticates
 * with, and records its credentials under `provisioningClient` in e2e-config.local.json.
 *
 * WHY THIS EXISTS. Identity Server 7.3 disables HTTP Basic auth on protected resources for any
 * deployment whose root organization was created on or after the cutoff in
 * repository/conf/compatibility-settings-metadata.json (`basicAuth.disableBasicAuth`,
 * 2026-08-13). The shipped H2 database carries a root org stamped at WSO2's own build time
 * (2026-04-23), which is why Basic auth still works there; every other backend writes
 * CURRENT_TIMESTAMP at install (dbscripts/mysql.sql, the UM_ORG seed row) and so lands past the
 * cutoff. Provisioning therefore cannot use Basic auth on MySQL today, or on H2 once WSO2 ships
 * a pack built after the cutoff - a client_credentials token is the one approach that behaves
 * identically on every database type.
 *
 * Creating that client is itself an authenticated call, so the bootstrap uses the one credential
 * path unaffected by the above: a real browser sign-in to the Console, exactly as the suite's own
 * personas sign in. That happens ONCE per environment - a cached credential that still mints a
 * token short-circuits before any browser is launched.
 *
 * Deliberately does NOT import utils/env.ts: that module requires the test-account credentials
 * at import time, and those accounts are precisely what does not exist yet when this runs. It
 * reads utils/config.ts directly instead, which validates nothing until asked.
 *
 * Reads e2e-config.json (see utils/config.ts) for the server URL and the super admin it signs in
 * as - the same single configuration every other part of the suite uses.
 *
 * Usage:
 *   npm run bootstrap:provisioning-app
 */

import { chromium, type APIRequestContext, type Browser } from '@playwright/test'
import { LoginPage } from '../pages/LoginPage'
import { config, requireConfigured, trimTrailingSlash, updateLocalConfig } from '../utils/config'
import { PROVISIONING_APIS, PROVISIONING_SCOPES } from '../utils/provisioningScopes'

const APPLICATION_NAME = 'DPDP E2E Provisioning'

/**
 * The Identity Server's own management APIs reject "NO POLICY" (APP-60512), so authorization goes
 * through RBAC - the same policy the accelerator's own DPDPApiResourceProvisioningUtil uses for
 * its equally user-less DPDP Consent API Invoker client.
 */
const API_POLICY = 'RBAC'

const isBaseUrl = trimTrailingSlash(config.identityServer.baseUrl)
const adminUsername = requireConfigured(config.superAdmin.username, 'superAdmin.username')
const adminPassword = requireConfigured(config.superAdmin.password, 'superAdmin.password')
const ignoreHttpsErrors = config.identityServer.ignoreHttpsErrors

interface ClientCredentials {
  clientId: string
  clientSecret: string
}


function fail(message: string): never {
  console.error(`ERROR: ${message}`)
  process.exit(1)
}

/** Mints a token, or returns undefined if these credentials no longer work. */
async function tryMintToken(credentials: ClientCredentials): Promise<string | undefined> {
  const basic = Buffer.from(`${credentials.clientId}:${credentials.clientSecret}`).toString('base64')
  const response = await fetch(`${isBaseUrl}/oauth2/token`, {
    method: 'POST',
    headers: {
      Authorization: `Basic ${basic}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({ grant_type: 'client_credentials', scope: PROVISIONING_SCOPES.join(' ') }),
  }).catch(() => undefined)

  if (!response?.ok) {
    return undefined
  }
  const body = (await response.json()) as { access_token?: string; scope?: string }
  if (!body.access_token) {
    return undefined
  }
  // A token that came back short of the scopes it asked for means the app exists but is not
  // authorized the way this script expects - treat it as unusable so the run below repairs it,
  // rather than letting provisioning fail later with an opaque 403.
  const granted = new Set((body.scope ?? '').split(' '))
  const missing = PROVISIONING_SCOPES.filter((scope) => !granted.has(scope))
  if (missing.length > 0) {
    console.log(`  cached client is missing scope(s): ${missing.join(', ')} - re-authorizing`)
    return undefined
  }
  return body.access_token
}

function readCachedCredentials(): ClientCredentials | undefined {
  const configured = config.provisioningClient
  return configured?.clientId && configured.clientSecret ? configured : undefined
}

/**
 * Signs in to the Console and returns a request context authenticated as that session.
 *
 * Two things make this more than "grab a token". The Console keeps its token in its auth SDK's
 * web worker, never anywhere page script or a cookie can reach, so the only way to read it is to
 * intercept an outgoing request - the same approach fixtures/auth.fixtures.ts uses for the
 * portal. And the token alone is not enough: it is bound to the browser session's `atbv` cookie,
 * so replaying it from a plain fetch() is rejected with 401 no matter how valid it looks (the
 * same constraint that stops this suite from having a browser-less API layer). Every call below
 * therefore goes through the browser context's own request object, which carries that cookie.
 */
async function openConsoleSession(): Promise<{ browser: Browser; request: APIRequestContext; token: string }> {
  const browser = await chromium.launch()
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: ignoreHttpsErrors })
    const page = await context.newPage()

    let resolveToken: (token: string) => void
    const token = new Promise<string>((resolve) => {
      resolveToken = resolve
    })
    page.on('request', (request) => {
      if (!request.url().includes('/api/server/v1/')) {
        return
      }
      const authorization = request.headers()['authorization']
      if (authorization?.toLowerCase().startsWith('bearer ')) {
        resolveToken(authorization.slice('bearer '.length))
      }
    })

    await page.goto(`${isBaseUrl}/console`, { waitUntil: 'domcontentloaded' })
    const loginPage = new LoginPage(page)
    await loginPage.signIn({ username: adminUsername, password: adminPassword })

    const failed = loginPage.errorMessage
      .waitFor({ state: 'visible', timeout: 60_000 })
      .then(async () => {
        const message = (await loginPage.errorMessage.textContent())?.trim()
        throw new Error(
          `Console sign-in failed for ${adminUsername}: ${message ?? 'the login form reported an error'}. ` +
            'Check superAdmin.username / superAdmin.password in e2e-config.json.',
        )
      })

    let timer: ReturnType<typeof setTimeout> | undefined
    const timeout = new Promise<never>((_, reject) => {
      timer = setTimeout(
        () =>
          reject(
            new Error(
              'Signed in, but no Console management-API call carried a bearer token within 60s. ' +
                'The Console may have failed to load - re-run with PWDEBUG=1 to watch it.',
            ),
          ),
        60_000,
      )
    })

    try {
      return { browser, request: context.request, token: await Promise.race([token, failed, timeout]) }
    } finally {
      // A pending timer keeps Node's event loop alive, so without this the command sits idle for
      // the rest of the 60s after the token has already arrived - measured at 64s end to end
      // against 2s for the cached path.
      clearTimeout(timer)
    }
  } catch (error) {
    await browser.close()
    throw error
  }
}

// Set once the Console session is open; every managementApi() call below goes through it.
let session: { request: APIRequestContext; token: string } | undefined

async function managementApi(
  method: 'GET' | 'POST',
  apiPath: string,
  body?: unknown,
): Promise<{ status: number; location: string | null; json: unknown }> {
  if (!session) {
    fail('internal: the Console session was not opened before a management API call')
  }
  const url = `${isBaseUrl}/api/server/v1${apiPath}`
  const options = {
    headers: {
      Authorization: `Bearer ${session.token}`,
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
    ...(body === undefined ? {} : { data: body }),
    timeout: 30_000,
  }
  const response = method === 'GET' ? await session.request.get(url, options) : await session.request.post(url, options)

  const text = await response.text()
  let json: unknown = undefined
  if (text.length > 0) {
    try {
      json = JSON.parse(text)
    } catch {
      json = text
    }
  }
  if (!response.ok()) {
    fail(`${method} /api/server/v1${apiPath} returned HTTP ${String(response.status())}: ${text}`)
  }
  return { status: response.status(), location: response.headers()['location'] ?? null, json }
}

/** Resolves an API resource's id by its identifier, e.g. "/scim2/Users". */
async function apiResourceId(identifier: string): Promise<string> {
  const { json } = await managementApi(
    'GET',
    `/api-resources?filter=${encodeURIComponent(`identifier eq ${identifier}`)}`,
  )
  const resources = (json as { apiResources?: { id: string; identifier: string }[] }).apiResources ?? []
  // The filter is server-side, but match exactly here too: "/scim2/Users" and "/o/scim2/Users"
  // are different resources and an over-broad match would authorize the organization-level one.
  const match = resources.find((resource) => resource.identifier === identifier)
  if (!match) {
    fail(`the Identity Server has no API resource with identifier ${identifier}`)
  }
  return match.id
}

async function findApplicationId(): Promise<string | undefined> {
  const { json } = await managementApi(
    'GET',
    `/applications?filter=${encodeURIComponent(`name eq "${APPLICATION_NAME}"`)}`,
  )
  const applications = (json as { applications?: { id: string; name: string }[] }).applications ?? []
  return applications.find((application) => application.name === APPLICATION_NAME)?.id
}

async function createApplication(): Promise<string> {
  const { location } = await managementApi('POST', '/applications', {
    name: APPLICATION_NAME,
    description:
      'Machine-to-machine client used by the DPDP integration test suite to provision its test ' +
      'accounts. Created by scripts/bootstrap-provisioning-app.ts - not part of the accelerator.',
    templateId: 'm2m-application',
    inboundProtocolConfiguration: { oidc: { grantTypes: ['client_credentials'] } },
  })
  const id = location?.split('/').pop()
  if (!id) {
    fail('the Identity Server accepted the application but returned no Location header to read its id from')
  }
  return id
}

/** Authorizes the SCIM2 resources the provisioning script needs. Safe to re-run. */
async function authorizeApis(applicationId: string): Promise<void> {
  const { json } = await managementApi('GET', `/applications/${applicationId}/authorized-apis`)
  const existing = (json as { id: string; identifier?: string }[] | undefined) ?? []

  for (const api of PROVISIONING_APIS) {
    const id = await apiResourceId(api.identifier)
    if (existing.some((authorized) => authorized.id === id)) {
      console.log(`  ${api.identifier}: already authorized`)
      continue
    }
    await managementApi('POST', `/applications/${applicationId}/authorized-apis`, {
      id,
      policyIdentifier: API_POLICY,
      scopes: api.scopes,
    })
    console.log(`  ${api.identifier}: authorized (${api.scopes.join(', ')})`)
  }
}

async function readCredentials(applicationId: string): Promise<ClientCredentials> {
  const { json } = await managementApi('GET', `/applications/${applicationId}/inbound-protocols/oidc`)
  const oidc = json as { clientId?: string; clientSecret?: string }
  if (!oidc.clientId || !oidc.clientSecret) {
    fail('the application has no OIDC client id/secret - was it created without an inbound protocol?')
  }
  return { clientId: oidc.clientId, clientSecret: oidc.clientSecret }
}

function persist(credentials: ClientCredentials): void {
  // Stored in e2e-config.local.json, not under .auth/ - global-teardown.ts removes that whole
  // directory after every run, which would discard this and force a browser sign-in each time.
  updateLocalConfig({ provisioningClient: credentials })

  // Inside Actions this registers the secret for masking; anywhere else it would just print it
  // in the clear, which is why it is guarded rather than emitted unconditionally.
  if (process.env.GITHUB_ACTIONS === 'true') {
    console.log(`::add-mask::${credentials.clientSecret}`)
  }
}

async function main(): Promise<void> {
  const cached = readCachedCredentials()
  if (cached && (await tryMintToken(cached))) {
    console.log(`Provisioning client ${cached.clientId} is already usable - no sign-in needed.`)
    persist(cached)
    return
  }

  console.log(`Bootstrapping the provisioning client on ${isBaseUrl} (signing in as ${adminUsername})`)
  const opened = await openConsoleSession()
  session = { request: opened.request, token: opened.token }

  let credentials: ClientCredentials
  try {
    let applicationId = await findApplicationId()
    if (applicationId) {
      console.log(`  ${APPLICATION_NAME}: already exists (${applicationId})`)
    } else {
      applicationId = await createApplication()
      console.log(`  ${APPLICATION_NAME}: created (${applicationId})`)
    }

    await authorizeApis(applicationId)
    credentials = await readCredentials(applicationId)
  } finally {
    await opened.browser.close()
    session = undefined
  }

  // Client authentication at the token endpoint is not cookie-bound the way the Console session
  // is, so this one runs fine outside the browser - and has to, since that is exactly how
  // provisioning will use these credentials later.
  const minted = await tryMintToken(credentials)
  if (!minted) {
    fail(
      `the application was set up but ${credentials.clientId} could not mint a token carrying ` +
        `${PROVISIONING_SCOPES.join(', ')}. Check the authorized API policy - a client_credentials ` +
        `client needs its scopes reachable under the "${API_POLICY}" policy.`,
    )
  }

  persist(credentials)
  console.log('Done. Credentials written to e2e-config.local.json.')
}

main().catch((error: unknown) => {
  fail((error as Error).message)
})
