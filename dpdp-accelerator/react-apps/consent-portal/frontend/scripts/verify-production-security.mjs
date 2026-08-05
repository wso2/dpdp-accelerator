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

import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const html = readFileSync(join(root, 'dist', 'index.html'), 'utf8')

const failures = []
const assertAbsent = (value, pattern, message) => {
  if (pattern.test(value)) failures.push(message)
}

assertAbsent(html, /<script(?![^>]*\bsrc=)[^>]*>/i, 'production HTML contains an inline script')
assertAbsent(html, /\son[a-z]+\s*=/i, 'production HTML contains an inline event handler')
assertAbsent(html, /\sstyle\s*=/i, 'production HTML contains an inline style attribute')
assertAbsent(html, /<style\b/i, 'production HTML contains an inline style element')

// The portal ships its CSP as a meta element because it is served by the
// Identity Server's Tomcat rather than a static host with a _headers file.
const metaMatch = html.match(
  /<meta[^>]*http-equiv="Content-Security-Policy"[^>]*content="([^"]*)"/i,
)
// Vite HTML-escapes the attribute value, so unescape quotes before matching.
const metaPolicy = (metaMatch?.[1] ?? '').replace(/&#39;|&apos;/g, "'").replace(/&amp;/g, '&')
if (!metaPolicy) failures.push('CSP meta element is missing from production HTML')
if (!metaPolicy.includes("script-src 'self'")) failures.push('script-src is not restricted to self')
assertAbsent(metaPolicy, /script-src[^;]*'unsafe-inline'/i, 'inline scripts are allowed by CSP')
assertAbsent(metaPolicy, /'unsafe-eval'/i, 'string-to-code execution is allowed by CSP')

const productionSources = []
const collect = (directory) => {
  for (const name of readdirSync(directory)) {
    const path = join(directory, name)
    if (statSync(path).isDirectory()) {
      if (name !== '__tests__') collect(path)
    } else if (
      /\.(ts|tsx)$/.test(name) &&
      !name.endsWith('.test.ts') &&
      !name.endsWith('.test.tsx')
    ) {
      productionSources.push(path)
    }
  }
}
collect(join(root, 'src'))

const forbiddenSinks = [
  [/dangerouslySetInnerHTML/, 'dangerouslySetInnerHTML'],
  [/\.innerHTML\b/, 'innerHTML'],
  [/\.outerHTML\b/, 'outerHTML'],
  [/document\.write\s*\(/, 'document.write'],
  [/\beval\s*\(/, 'eval'],
  [/\bnew\s+Function\s*\(/, 'Function constructor'],
  [/\bconsole\.(?:debug|error|info|log|warn)\s*\(/, 'console logging'],
  [/\blocalStorage\b/, 'localStorage'],
  [/\bsessionStorage\b/, 'sessionStorage'],
]

for (const path of productionSources) {
  const source = readFileSync(path, 'utf8')
  for (const [pattern, sink] of forbiddenSinks) {
    if (pattern.test(source)) {
      failures.push(`${relative(root, path)} contains forbidden sink ${sink}`)
    }
  }
}

if (failures.length > 0) {
  throw new Error(`Production security verification failed:\n- ${failures.join('\n- ')}`)
}

console.log('Production CSP, HTML, and source-sink verification passed.')
