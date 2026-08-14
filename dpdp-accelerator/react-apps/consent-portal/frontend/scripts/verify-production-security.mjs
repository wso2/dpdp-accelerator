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
import { join, relative, resolve, sep } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const html = readFileSync(join(root, 'dist', 'index.html'), 'utf8')
const headers = readFileSync(join(root, 'dist', '_headers'), 'utf8')

const failures = []
const assertAbsent = (value, pattern, message) => {
  if (pattern.test(value)) failures.push(message)
}

assertAbsent(html, /<script(?![^>]*\bsrc=)[^>]*>/i, 'production HTML contains an inline script')
assertAbsent(html, /\son[a-z]+\s*=/i, 'production HTML contains an inline event handler')
assertAbsent(html, /\sstyle\s*=/i, 'production HTML contains an inline style attribute')
assertAbsent(html, /<style\b/i, 'production HTML contains an inline style element')

if (!headers.includes('Content-Security-Policy:')) failures.push('CSP header artifact is missing')
if (!headers.includes("script-src 'self'")) failures.push('script-src is not restricted to self')
if (!headers.includes("frame-ancestors 'none'")) failures.push('frame-ancestors is not denied')
assertAbsent(headers, /script-src[^;]*'unsafe-inline'/i, 'inline scripts are allowed by CSP')
assertAbsent(headers, /'unsafe-eval'/i, 'string-to-code execution is allowed by CSP')

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

/**
 * Web storage is forbidden because anything in it is readable by any script that
 * runs on the page, so a token placed there is a token an XSS can take.
 *
 * These files are exempt for that sink only. None of them stores a credential:
 * the delegated token lives in an HttpOnly cookie precisely so JavaScript cannot
 * reach it, and what is kept here is the id of the account being acted for and
 * the chosen language.
 *
 * The acting id has to be per tab and has to survive the sign-in redirect, which
 * is exactly what sessionStorage is and what a cookie is not - a cookie is
 * shared by every tab, so one tab starting a session would silently change who
 * the others were acting as.
 *
 * Every other sink still applies to these files, and this sink still applies to
 * every other file.
 */
const storageSinkExemptions = new Map([
  ['src/features/nominee/actingAs/actingAsContext.ts', ['sessionStorage']],
  ['src/features/nominee/actingAs/ActingAsProvider.tsx', ['sessionStorage']],
  ['src/utils/queryClient.ts', ['sessionStorage']],
  ['src/i18n/languages.ts', ['localStorage']],
])

for (const path of productionSources) {
  const source = readFileSync(path, 'utf8')
  const key = relative(root, path).split(sep).join('/')
  const allowed = storageSinkExemptions.get(key) ?? []
  for (const [pattern, sink] of forbiddenSinks) {
    if (pattern.test(source) && !allowed.includes(sink)) {
      failures.push(`${key} contains forbidden sink ${sink}`)
    }
  }
}

if (failures.length > 0) {
  throw new Error(`Production security verification failed:\n- ${failures.join('\n- ')}`)
}

console.log('Production CSP, HTML, and source-sink verification passed.')
