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

import fs from 'node:fs'
import path from 'node:path'

/**
 * The suite's configuration, and the only place it comes from.
 *
 * `e2e-config.json` (committed) carries every setting and its default. `e2e-config.local.json`
 * (gitignored, optional) overrides individual values for one machine or one run - it is what
 * scripts/setup-local.sh and the CI workflow write, since the test-account password is generated
 * per environment rather than committed.
 *
 * There are deliberately NO environment-variable fallbacks and no defaults buried in code: a
 * setting either resolves from these two files or the suite fails and says which key is missing.
 * Values are also never derived from the accelerator's own install config
 * (accelerators/dpdp-is/repository/conf/configure.properties) - the suite declares what it needs
 * and the deployment is expected to match, rather than the two silently drifting apart.
 *
 * JSON rather than TypeScript because scripts/provision-test-users.sh reads the same file, so
 * the persona usernames have exactly one definition across both languages. The prose that used
 * to live in .env.example's comments is in README.md, under "Configuration".
 */

export interface Credentials {
  username: string
  password: string | null
}

export interface E2EConfig {
  identityServer: {
    baseUrl: string
    portalBaseUrl: string
    ignoreHttpsErrors: boolean
  }
  superAdmin: Credentials
  /**
   * The machine-to-machine client scripts/provision-test-users.sh and utils/provisioningClient.ts
   * authenticate as, written by scripts/bootstrap-provisioning-app.ts. It lives here rather than
   * under .auth/ because global-teardown.ts removes that whole directory after every run, which
   * would throw the credential away and force a browser sign-in before each run.
   */
  provisioningClient: { clientId: string; clientSecret: string } | null
  personas: {
    user: Credentials
    user2: Credentials
    consentAdmin: Credentials
  }
  personaRoles: {
    user: string
    consentAdmin: string
  }
  webhook: {
    receiverHost: string | null
    allowPrivateNetwork: boolean
  }
  consentExpiry: {
    schedulerPollTimeoutMs: number | null
  }
}

const suiteRoot = path.resolve(import.meta.dirname, '..')
export const CONFIG_PATH = path.join(suiteRoot, 'e2e-config.json')
export const LOCAL_CONFIG_PATH = path.join(suiteRoot, 'e2e-config.local.json')

function readJson(file: string): Record<string, unknown> {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8')) as Record<string, unknown>
  } catch (error) {
    throw new Error(`Could not read ${path.basename(file)}: ${(error as Error).message}`)
  }
}

/**
 * Merges one level deeper than a spread: the local file names individual settings
 * (`{"personas": {"user": {"password": "..."}}}`) without having to restate every sibling key
 * of every group it touches.
 */
function merge(base: Record<string, unknown>, override: Record<string, unknown>): Record<string, unknown> {
  const merged: Record<string, unknown> = { ...base }
  for (const [key, value] of Object.entries(override)) {
    const existing = merged[key]
    const bothPlainObjects =
      existing !== null &&
      value !== null &&
      typeof existing === 'object' &&
      typeof value === 'object' &&
      !Array.isArray(existing) &&
      !Array.isArray(value)
    merged[key] = bothPlainObjects
      ? merge(existing as Record<string, unknown>, value as Record<string, unknown>)
      : value
  }
  return merged
}

function load(): E2EConfig {
  if (!fs.existsSync(CONFIG_PATH)) {
    throw new Error(`Missing ${path.basename(CONFIG_PATH)} - it is committed, so this is a broken checkout.`)
  }
  const base = readJson(CONFIG_PATH)
  const merged = fs.existsSync(LOCAL_CONFIG_PATH) ? merge(base, readJson(LOCAL_CONFIG_PATH)) : base
  return merged as unknown as E2EConfig
}

export const config: E2EConfig = load()

// Node's global fetch has no per-call option to accept an untrusted certificate - it only honors
// this process-wide variable, and the shipped Identity Server certificate is self-signed. Set on
// import so it is in place for whichever process actually makes a plain fetch() call; see the
// longer note in utils/env.ts on why a global-setup-only assignment is not enough.
if (config.identityServer.ignoreHttpsErrors) {
  process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'
}

/**
 * Reads a configured value that must be present, naming the exact key when it is not. Used for
 * anything that has no sensible committed default - chiefly the generated test-account password,
 * which scripts/setup-local.sh writes into the local file.
 */
export function requireConfigured<T>(value: T | null | undefined, key: string): T {
  if (value === null || value === undefined || value === '') {
    throw new Error(
      `"${key}" is not configured. Set it in ${path.basename(LOCAL_CONFIG_PATH)} ` +
        `(or ${path.basename(CONFIG_PATH)}), or run ./scripts/setup-local.sh to generate it.`,
    )
  }
  return value
}

/**
 * Merges a patch into e2e-config.local.json, preserving whatever else is already in it, and
 * updates the in-memory config to match. Owner-only on disk: the file carries real credentials.
 */
export function updateLocalConfig(patch: Record<string, unknown>): void {
  const existing = fs.existsSync(LOCAL_CONFIG_PATH) ? readJson(LOCAL_CONFIG_PATH) : {}
  const updated = merge(existing, patch)
  fs.writeFileSync(LOCAL_CONFIG_PATH, `${JSON.stringify(updated, null, 2)}\n`, { mode: 0o600 })
  Object.assign(config, merge(config as unknown as Record<string, unknown>, patch))
}

export function trimTrailingSlash(value: string): string {
  return value.endsWith('/') ? value.slice(0, -1) : value
}
