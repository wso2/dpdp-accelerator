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

export interface ContentSecurityPolicyOptions {
  apiBaseURL: string
  includeFrameAncestors?: boolean
  upgradeInsecureRequests?: boolean
}

function httpOrigin(value: string): string {
  const url = new URL(value)
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error('VITE_API_BASE_URL must use the http or https scheme.')
  }
  return url.origin
}

/**
 * Builds the connect-src directive.
 *
 * The base may be an absolute origin, when the BFF is deployed separately, or a
 * path such as "/consent-portal" when the SPA is packaged into the BFF's own
 * webapp. A path means same-origin, which 'self' already covers - and it is not
 * a valid URL, so it must never reach the URL parser.
 */
function connectSrc(apiBaseURL: string): string {
  if (!apiBaseURL || apiBaseURL.startsWith('/')) {
    return "connect-src 'self'"
  }
  return `connect-src 'self' ${httpOrigin(apiBaseURL)}`
}

/** Builds the production portal CSP from exact deployment origins. */
export function contentSecurityPolicy(options: ContentSecurityPolicyOptions): string {
  const directives = [
    "default-src 'self'",
    "script-src 'self'",
    // Oxygen UI uses Emotion to create runtime style elements. Replace this
    // exception with a style nonce when the static host supports per-request HTML.
    "style-src 'self' 'unsafe-inline'",
    connectSrc(options.apiBaseURL),
    "img-src 'self' data:",
    "font-src 'self' data:",
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'self'",
    "manifest-src 'self'",
  ]

  if (options.includeFrameAncestors !== false) {
    directives.push("frame-ancestors 'none'")
  }
  if (options.upgradeInsecureRequests) {
    directives.push('upgrade-insecure-requests')
  }

  return `${directives.join('; ')};`
}

/** Renders a deployment artifact understood by static hosts supporting `_headers`. */
export function staticHeadersFile(policy: string): string {
  return [
    '/*',
    `  Content-Security-Policy: ${policy}`,
    '  X-Content-Type-Options: nosniff',
    '  X-Frame-Options: DENY',
    '  Referrer-Policy: strict-origin-when-cross-origin',
    '  Permissions-Policy: camera=(), geolocation=(), microphone=()',
    '  Cross-Origin-Opener-Policy: same-origin',
    '',
    '/index.html',
    '  Cache-Control: no-cache',
    '',
  ].join('\n')
}
