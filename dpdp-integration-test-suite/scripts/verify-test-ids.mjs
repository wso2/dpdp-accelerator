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
 * Enforces the numbering and layout rules in AGENTS.md ("Numbering and layout").
 *
 * Deliberately NOT a Playwright test: the e2e suite only runs behind the Action/trigger-e2e
 * label and needs a live Identity Server, so a guard living there would let drift land on every
 * unlabelled PR. This runs in pr-build.yml on every PR instead, and needs nothing but the files.
 *
 * Plain Node with no dependencies, for the same reason.
 */

import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, basename, dirname, relative } from 'node:path'

const ROOT = join(import.meta.dirname, '..')
const TESTS = join(ROOT, 'tests')
const CATALOGUE = 'TEST-SCENARIOS.md'
const README = 'README.md'

const problems = []
const fail = (where, message) => problems.push(`${where}: ${message}`)

/** Matches `test('NN.MM.KK - …')`, including the multi-line and `.skip` / `tenantTest` forms. */
const TEST_ID = /\b(?:test|tenantTest)(?:\.skip)?\(\s*(['"`])(\d{2}\.\d{2}\.\d{2})\s*-/g
/** An ID mentioned anywhere (comments included). Backslash-escaped dots are normalised first. */
const ANY_ID = /\b(\d{2}\.\d{2}\.\d{2})\b/g
/** A spec-file reference such as `07.04-data-principal-replying-in-thread.spec.ts`. */
const FILE_REF = /\b(\d{2}\.\d{2})-[a-z0-9-]+\.spec\.ts/g

function walk(dir) {
  return readdirSync(dir).flatMap((entry) => {
    const full = join(dir, entry)
    return statSync(full).isDirectory() ? walk(full) : [full]
  })
}

const sourceFiles = ['tests', 'utils', 'pages', 'fixtures', 'clients']
  .map((d) => join(ROOT, d))
  .flatMap(walk)
  .filter((f) => f.endsWith('.ts'))

const areas = readdirSync(TESTS)
  .filter((e) => statSync(join(TESTS, e)).isDirectory())
  .sort()

// ── R1: area directories are `NN-kebab-name`, sequential from 01 ────────────────────────────
areas.forEach((area, index) => {
  if (!/^\d{2}-[a-z][a-z0-9-]*$/.test(area)) {
    fail(`tests/${area}`, 'area directory must be `NN-kebab-name` (R1)')
    return
  }
  const expected = String(index + 1).padStart(2, '0')
  if (area.slice(0, 2) !== expected) {
    fail(`tests/${area}`, `area number is not sequential - expected ${expected} (R1/R5)`)
  }
})

const allIds = new Map() // id -> file
const idsByFile = new Map() // relative path -> ids in declaration order
const knownFiles = new Set() // `NN.MM` prefixes that exist

for (const area of areas) {
  const dirPath = join(TESTS, area)
  const specs = readdirSync(dirPath).filter((f) => f.endsWith('.spec.ts')).sort()
  const areaNumber = area.slice(0, 2)

  specs.forEach((spec, index) => {
    const where = `tests/${area}/${spec}`

    // ── R2: `NN.MM-kebab-description.spec.ts`, NN matching the area, MM sequential ───────────
    const nameMatch = /^(\d{2})\.(\d{2})-([a-z0-9-]+)\.spec\.ts$/.exec(spec)
    if (!nameMatch) {
      fail(where, 'filename must be `NN.MM-kebab-description.spec.ts`, hyphens only (R2)')
      return
    }
    const [, fileArea, fileNumber] = nameMatch
    if (fileArea !== areaNumber) {
      fail(where, `filename area ${fileArea} does not match its directory ${areaNumber} (R8)`)
    }
    const expectedNumber = String(index + 1).padStart(2, '0')
    if (fileNumber !== expectedNumber) {
      fail(where, `file number ${fileNumber} is not sequential - expected ${expectedNumber} (R5)`)
    }
    knownFiles.add(`${fileArea}.${fileNumber}`)

    const source = readFileSync(join(dirPath, spec), 'utf8')

    // ── R4/R5/R8: test IDs are `<area>.<file>.<KK>`, dense from 01, in declaration order ─────
    const ids = [...source.matchAll(TEST_ID)].map((m) => m[2])
    idsByFile.set(where, ids)
    ids.forEach((id, position) => {
      const expectedId = `${areaNumber}.${fileNumber}.${String(position + 1).padStart(2, '0')}`
      if (id !== expectedId) {
        fail(where, `test #${position + 1} is '${id}' - expected '${expectedId}' (R4/R5/R8)`)
      }
      const clash = allIds.get(id)
      if (clash) fail(where, `duplicate test ID '${id}' - also in ${clash}`)
      else allIds.set(id, where)
    })
    if (ids.length === 0) fail(where, 'no test IDs found - is the title format `NN.MM.KK - …`?')

    // ── R6: an `-api.spec.ts` file drives no browser ─────────────────────────────────────────
    if (spec.endsWith('-api.spec.ts')) {
      const browserUse = /\bloginAs\w+\(|\bpage\.(goto|getByRole|getByText|locator)\(/.exec(source)
      if (browserUse) {
        fail(where, `-api.spec.ts must not drive a browser, found \`${browserUse[0]}\` (R6)`)
      }
    }
  })
}

// ── Cross-references: every ID or spec-file mentioned outside a test title must resolve ──────
for (const file of sourceFiles) {
  const where = relative(ROOT, file)
  const raw = readFileSync(file, 'utf8')
  const source = raw.replaceAll('\\.', '.') // grep examples escape the dots
  const titleSpans = [...source.matchAll(TEST_ID)].map((m) => [m.index, m.index + m[0].length])
  const inTitle = (index) => titleSpans.some(([a, b]) => index >= a && index < b)

  for (const match of source.matchAll(ANY_ID)) {
    if (inTitle(match.index)) continue
    if (!allIds.has(match[1])) {
      fail(where, `references test ID '${match[1]}', which does not exist`)
    }
  }
  for (const match of source.matchAll(FILE_REF)) {
    if (!knownFiles.has(match[1])) {
      fail(where, `references spec file '${match[0]}', which does not exist`)
    }
  }
  for (const match of source.matchAll(/\btests\/\d{2}-[a-z0-9-]+\/[a-zA-Z0-9._-]+/g)) {
    try {
      statSync(join(ROOT, match[0]))
    } catch {
      fail(where, `references path '${match[0]}', which does not exist`)
    }
  }
}

// ── The catalogue is the single human-facing index, so it must list every test, and only ─────
// ── tests that exist (AGENTS.md, "Keeping TEST-SCENARIOS.md current"). ───────────────────────
let catalogue
try {
  catalogue = readFileSync(join(ROOT, CATALOGUE), 'utf8')
} catch {
  fail(CATALOGUE, 'is missing - it is the suite\'s scenario catalogue and must be kept current')
}
if (catalogue) {
  const listed = new Set([...catalogue.matchAll(ANY_ID)].map((m) => m[1]))
  for (const [id, where] of allIds) {
    if (!listed.has(id)) fail(CATALOGUE, `does not document '${id}' (${basename(dirname(where))})`)
  }
  for (const id of listed) {
    if (!allIds.has(id)) fail(CATALOGUE, `documents '${id}', which no longer exists`)
  }

  // ── The summary totals are prose, so the ID checks above do not cover them. They are what
  // ── actually goes stale: a test arriving in a merge updates the tables (or verify:ids would
  // ── have caught it) while the counts above them quietly stay wrong.
  const summary = catalogue.match(
    /\|\s*\*\*Tests\*\*\s*\|\s*(\d+) across (\d+) spec files in (\d+) areas\s*\|/,
  )
  if (!summary) {
    fail(CATALOGUE, 'has no `| **Tests** | N across M spec files in K areas |` summary row')
  } else {
    const [, tests, files, areaCount] = summary
    if (Number(tests) !== allIds.size) {
      fail(CATALOGUE, `summary says ${tests} tests, the tree has ${allIds.size}`)
    }
    if (Number(files) !== idsByFile.size) {
      fail(CATALOGUE, `summary says ${files} spec files, the tree has ${idsByFile.size}`)
    }
    if (Number(areaCount) !== areas.length) {
      fail(CATALOGUE, `summary says ${areaCount} areas, the tree has ${areas.length}`)
    }
  }

  // Each area's own `**N tests, M spec files.**` line, matched to the `## \`NN-name/\`` heading
  // it follows.
  for (const area of areas) {
    const expectedTests = [...allIds.keys()].filter((id) => id.startsWith(`${area.slice(0, 2)}.`)).length
    const expectedFiles = [...idsByFile.keys()].filter((where) => where.includes(`/${area}/`)).length
    const section = catalogue.split(new RegExp(`^## \\\`${area}/\\\``, 'm'))[1]
    if (section === undefined) {
      fail(CATALOGUE, `has no \`## \\\`${area}/\\\`\` section`)
      continue
    }
    const counts = section.match(/\*\*(\d+) tests?, (\d+) spec files?\.\*\*/)
    if (!counts) {
      fail(CATALOGUE, `${area}: no \`**N tests, M spec files.**\` line`)
      continue
    }
    if (Number(counts[1]) !== expectedTests) {
      fail(CATALOGUE, `${area}: says ${counts[1]} tests, the tree has ${expectedTests}`)
    }
    if (Number(counts[2]) !== expectedFiles) {
      fail(CATALOGUE, `${area}: says ${counts[2]} spec files, the tree has ${expectedFiles}`)
    }
  }
}

// ── README.md repeats the per-area counts in its own table. A third copy of the same numbers is
// ── a third thing to forget, so it is checked against the tree too.
let readme
try {
  readme = readFileSync(join(ROOT, README), 'utf8')
} catch {
  readme = undefined
}
if (readme) {
  for (const area of areas) {
    const expected = [...allIds.keys()].filter((id) => id.startsWith(`${area.slice(0, 2)}.`)).length
    const row = readme.match(new RegExp(`\\|\\s*\`${area}/\`\\s*\\|\\s*(\\d+)\\s*\\|`))
    if (!row) continue // the table is optional prose; only its numbers are checked
    if (Number(row[1]) !== expected) {
      fail(README, `${area}: says ${row[1]} tests, the tree has ${expected}`)
    }
  }
}

// ── Report ───────────────────────────────────────────────────────────────────────────────────
if (problems.length > 0) {
  console.error(`\n${problems.length} problem(s) found:\n`)
  for (const problem of problems) console.error(`  ${problem}`)
  console.error('\nSee AGENTS.md, "Numbering and layout", for the rules these check.\n')
  process.exit(1)
}
console.log(`OK - ${allIds.size} test IDs across ${idsByFile.size} spec files in ${areas.length} areas.`)
