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

import { defineConfig, devices } from '@playwright/test'
import { env } from './utils/env'

// No webServer entry: this suite targets a real, already-running WSO2 IS + accelerator
// deployment (configured via e2e-config.json), not something this config starts itself.
export default defineConfig({
  testDir: 'tests',
  fullyParallel: true,
  // Every test authenticates as one of a couple of shared IS accounts (ctizen1, dpdp.testuser),
  // and IS enforces a single active session per account. fixtures/auth.fixtures.ts's
  // getPersonaState logs each persona in at most once per run (via a file-based cross-process
  // cache under .auth/, guarded by a lock only for the brief moment of that one login) precisely
  // so that multiple workers don't each log in independently and keep invalidating each other's
  // sessions - see that file for the full mechanism. No `workers` override is needed here as a
  // result; Playwright's own CPU-based default applies.
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: [['html', { open: 'never' }]],
  globalSetup: './global-setup.ts',
  globalTeardown: './global-teardown.ts',
  use: {
    baseURL: env.portalNavigationBaseUrl,
    ignoreHTTPSErrors: env.ignoreHttpsErrors,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
