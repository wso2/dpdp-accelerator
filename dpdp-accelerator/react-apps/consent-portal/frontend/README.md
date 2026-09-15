<!--
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
 -->

# DPDP Consent Portal Frontend

The DPDP Consent Portal Frontend is the Consent Management Portal User Interface for the WSO2 DPDP
Accelerator, talking directly to the Identity Server's consent-mgt REST APIs.

React 19 + TypeScript + Vite app using WSO2 Oxygen UI.

## Requirements

- Node.js 20.19+ (or 22.12+)

## Quickstart

From `dpdp-accelerator/react-apps/consent-portal/frontend`:

1. Copy `.env.example` to `.env`. `VITE_API_BASE_URL` is optional — leave it empty when deployed
   inside IS (the default; see Environment below).
2. Install dependencies:

   ```shell
   npm install
   ```

3. Start the development server:

   ```shell
   npm run dev
   ```

Open the local URL printed by Vite, typically `http://localhost:5173`.

## Package Manager

This project uses npm. `package-lock.json` is the committed lockfile; the Maven build invokes
`npm install` / `npm run build` directly (`react-apps/consent-portal/pom.xml`).

## Environment

Create a local `.env` file from `.env.example` before running or building the portal.

| Variable            | Description                                                                                                                                                                                                                                             | Example    |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| `VITE_API_BASE_URL` | Optional. Vite embeds this at build time. Leave empty for the normal deployment: inside IS, the portal's APIs are same-origin and tenant-qualified, resolved at runtime by `src/utils/basePath.ts`. Only set this pointing a dev server at a remote IS. | `` (empty) |
| `VITE_AUTH_ENABLED` | Enables frontend authentication gating; set to `true` for protected deployments.                                                                                                                                                                        | `true`     |

There is no backend of the portal's own and no `GET /me` endpoint — the portal is a public OIDC
client (`@asgardeo/auth-spa`) talking directly to the Identity Server's REST APIs. Tokens live in
the auth SDK's web worker, never in page script; see root `CLAUDE.md`'s "Portal auth: no backend of
our own" section for the full picture.

## Production security headers

The build's CSP ships as a `<meta http-equiv="Content-Security-Policy">` element baked into
`index.html` at build time, not as a `dist/_headers` file — the portal is served by the Identity
Server's Tomcat, not a static host that reads `_headers` conventions (see
`scripts/verify-production-security.mjs` and `src/security/contentSecurityPolicy.ts`).
`npm run build` verifies this automatically via the chained `security:verify` step.

## Scripts

```bash
npm start
npm run dev
npm run lint
npm run lint:fix
npm run format
npm run format:check
npm test
npm run test:watch
npm run test:coverage
npm run build
npm run security:audit
npm run security:verify
npm run i18n:verify
npm run generate:shell
npm run preview
```

`npm run build` is a chain — `tsc -b` → `vite build` → `security:verify` → `i18n:verify` →
`generate:shell` — and a failure in any of the last three steps looks nothing like a Vite error.
This chain is also what the Maven build invokes, so any of these four can fail `mvn clean install`
from the repository root.

## Testing

Tests are written with [Vitest](https://vitest.dev/) and [React Testing Library](https://testing-library.com/react).

- **Test files**: Located in `src/__tests__/` with `.test.ts`/`.test.tsx` extensions
- **Setup**: Global setup in `vitest.setup.ts` imports jest-dom matchers
- **Run tests**: `npm test` or `npm run test:watch` for watch mode
- **Coverage**: `npm run test:coverage` generates HTML coverage report in `coverage/`

## Project Structure

```text
src/
├── components/       # Reusable UI components
├── features/         # Feature-level modules (pages, domains)
├── hooks/            # Custom React hooks
├── i18n/             # i18n initialization and locale resources
├── types/            # TypeScript interfaces and types
├── utils/            # Utility functions and helpers
├── __tests__/        # Test files
├── App.tsx           # Root component
└── main.tsx          # Entry point
```

## Internationalization

This project uses `i18next` and `react-i18next` for UI translations, covering
English plus the 22 languages of the Eighth Schedule to the Constitution of
India. Translations are fetched at run time from `public/i18n/<lang>/`, not
bundled into the JS.

- Add keys to `public/i18n/en/common.json` and mirror them into every other
  language's `common.json` (English text as a placeholder is fine pending
  translation). `npm run i18n:verify` and `src/__tests__/I18nKeys.test.ts`
  enforce this at build time.
- In components, use `useTranslation('common')` and keys instead of
  hardcoded user-facing text.
- `catalog.json` holds wording for Purposes/Elements administrators create at
  run time (see `src/i18n/catalogText.ts`) and is intentionally allowed to be
  incomplete — it's excluded from the build-time check.

For operating a live deployment (correcting wording, localizing a Purpose or
Element without a rebuild), see
[`docs/content/localization-guide.md`](../../../../docs/content/localization-guide.md) at the
repository root.

## CI

`.github/workflows/pr-build.yml` builds the whole accelerator (including this frontend, via
`mvn clean install` from the repository root) on every pull request to `main` and `dev`. That
build runs the Vitest suite too, since it is bound to the Maven test phase. The same workflow's
`Frontend quality` job checks Prettier formatting; `npm run lint` is not enforced yet.

## AI Instructions

This repository uses AGENTS.md files to keep AI-generated changes aligned with project and organization standards.

- Frontend standards: `dpdp-accelerator/react-apps/consent-portal/frontend/AGENTS.md`
