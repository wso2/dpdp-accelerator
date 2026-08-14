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

import { describe, expect, it } from 'vitest'
import { contentSecurityPolicy, staticHeadersFile } from '../security/contentSecurityPolicy'

describe('frontend content security policy', () => {
  it('restricts executable content and connects only to the exact BFF origin', () => {
    const policy = contentSecurityPolicy({
      apiBaseURL: 'https://bff.example.com/api',
      upgradeInsecureRequests: true,
    })

    expect(policy).toContain("default-src 'self'")
    expect(policy).toContain("script-src 'self'")
    expect(policy).toContain("connect-src 'self' https://bff.example.com")
    expect(policy).toContain("object-src 'none'")
    expect(policy).toContain("base-uri 'none'")
    expect(policy).toContain("frame-ancestors 'none'")
    expect(policy).toContain("form-action 'self'")
    expect(policy).toContain('upgrade-insecure-requests')
    expect(policy).not.toContain("script-src 'self' 'unsafe-inline'")
    expect(policy).not.toContain("'unsafe-eval'")
  })

  it('keeps the temporary Emotion exception limited to styles', () => {
    const policy = contentSecurityPolicy({ apiBaseURL: 'http://localhost:8080' })

    expect(policy).toContain("style-src 'self' 'unsafe-inline'")
    expect(policy).not.toContain('upgrade-insecure-requests')
  })

  it('omits header-only directives from the meta policy', () => {
    const policy = contentSecurityPolicy({
      apiBaseURL: 'https://bff.example.com',
      includeFrameAncestors: false,
    })

    expect(policy).not.toContain('frame-ancestors')
  })

  it('rejects non-HTTP BFF origins', () => {
    expect(() => contentSecurityPolicy({ apiBaseURL: `javascript${':'}alert(1)` })).toThrow(
      'must use the http or https scheme',
    )
  })

  it('emits the enforced policy and companion security headers', () => {
    const policy = contentSecurityPolicy({ apiBaseURL: 'https://bff.example.com' })
    const headers = staticHeadersFile(policy)

    expect(headers).toContain(`Content-Security-Policy: ${policy}`)
    expect(headers).toContain('X-Content-Type-Options: nosniff')
    expect(headers).toContain('X-Frame-Options: DENY')
    expect(headers).toContain('Cross-Origin-Opener-Policy: same-origin')
  })
})
