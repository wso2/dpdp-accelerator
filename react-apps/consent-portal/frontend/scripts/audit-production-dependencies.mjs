/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

import { spawnSync } from 'node:child_process'

const allowedAdvisories = new Map([
  [
    'GHSA-qwww-vcr4-c8h2',
    {
      description: 'React Router unstable RSC APIs are not used by this Vite SPA',
      pathPattern: /^\. > react-router-dom@[^ ]+ > react-router@[^ ]+$/,
    },
  ],
])

const pnpmCli = process.env.npm_execpath

if (!pnpmCli) {
  throw new Error('Unable to locate the active pnpm CLI from npm_execpath')
}

const audit = spawnSync(process.execPath, [pnpmCli, 'audit', '--prod', '--json'], {
  cwd: process.cwd(),
  encoding: 'utf8',
  shell: false,
})

if (audit.error) {
  throw new Error(`Unable to run pnpm audit: ${audit.error.message}`)
}

let report

try {
  report = JSON.parse(audit.stdout)
} catch {
  const details = [audit.stdout, audit.stderr].filter(Boolean).join('\n').trim()
  throw new Error(`Unable to parse pnpm audit output${details ? `:\n${details}` : ''}`)
}

if (audit.status !== 0 && audit.status !== 1) {
  throw new Error(`pnpm audit failed with exit code ${String(audit.status)}:\n${audit.stderr}`)
}

const advisories = Object.values(report.advisories ?? {})
const blockingAdvisories = []
const acceptedAdvisories = []

for (const advisory of advisories) {
  if (advisory.severity === 'high' || advisory.severity === 'critical') {
    const exception = allowedAdvisories.get(advisory.github_advisory_id)
    const paths = advisory.findings?.flatMap((finding) => finding.paths ?? []) ?? []
    const pathsMatch = paths.length > 0 && paths.every((path) => exception?.pathPattern.test(path))

    if (exception && pathsMatch) {
      acceptedAdvisories.push({ advisory, description: exception.description })
    } else {
      blockingAdvisories.push(advisory)
    }
  }
}

for (const { advisory, description } of acceptedAdvisories) {
  console.warn(
    `Accepted documented advisory ${advisory.github_advisory_id}: ${advisory.title}\n` +
      `Reason: ${description}\n`,
  )
}

if (blockingAdvisories.length > 0) {
  const summary = blockingAdvisories
    .map(
      (advisory) =>
        `- ${advisory.github_advisory_id ?? advisory.id}: ${advisory.title} (${advisory.severity})`,
    )
    .join('\n')

  throw new Error(`Production dependency audit found blocking advisories:\n${summary}`)
}

console.log('Production dependency audit passed.')
