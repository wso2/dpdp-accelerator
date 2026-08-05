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

import react from '@vitejs/plugin-react'
import { loadEnv } from 'vite'
import type { Plugin } from 'vite'
import { defineConfig } from 'vitest/config'
import { contentSecurityPolicy } from './src/security/contentSecurityPolicy'

function securityHeadersPlugin(metaPolicy: string): Plugin {
  return {
    name: 'portal-security-headers',
    transformIndexHtml: {
      order: 'pre',
      handler() {
        return [
          {
            tag: 'meta',
            attrs: { 'http-equiv': 'Content-Security-Policy', content: metaPolicy },
            injectTo: 'head-prepend',
          },
        ]
      },
    },
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  // Empty or path-only values mean the BFF is same-origin (WAR deployment inside
  // WSO2 Identity Server); absolute URLs support a separately hosted BFF.
  const apiBaseURL = env.VITE_API_BASE_URL || '/consent-portal'
  const basePath = env.VITE_BASE_PATH || '/consent-portal/'

  const production = mode === 'production'
  const upgradeInsecureRequests = production && apiBaseURL.startsWith('https://')
  const policy = contentSecurityPolicy({
    apiBaseURL,
    upgradeInsecureRequests,
  })
  // frame-ancestors is supported only in the HTTP header, not a CSP meta element.
  const metaPolicy = contentSecurityPolicy({
    apiBaseURL,
    includeFrameAncestors: false,
    upgradeInsecureRequests,
  })

  return {
    base: basePath,
    plugins: [react(), ...(production ? [securityHeadersPlugin(metaPolicy)] : [])],
    preview: {
      headers: {
        'Content-Security-Policy': policy,
        'Cross-Origin-Opener-Policy': 'same-origin',
        'Permissions-Policy': 'camera=(), geolocation=(), microphone=()',
        'Referrer-Policy': 'strict-origin-when-cross-origin',
        'X-Content-Type-Options': 'nosniff',
        'X-Frame-Options': 'DENY',
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: './vitest.setup.ts',
      css: true,
      server: {
        deps: {
          inline: [
            '@wso2/oxygen-ui',
            '@wso2/oxygen-ui-icons-react',
            '@mui/x-data-grid',
            '@mui/x-date-pickers',
            '@mui/x-tree-view',
          ],
        },
      },
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html'],
      },
    },
  }
})
