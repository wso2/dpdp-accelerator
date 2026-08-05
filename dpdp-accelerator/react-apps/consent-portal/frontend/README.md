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

# OpenFGC Portal Frontend

The OpenFGC Portal Frontend is designed to create a comprehensive Consent Management Portal User Interface, leveraging the [OpenFGC Consent Management API](https://github.com/wso2/openfgc).

React 19 + TypeScript + Vite app using WSO2 Oxygen UI.

## Requirements

- Node.js 20.19+ (or 22.12+)

## Quickstart

From `portal/frontend`:

1. Copy `.env.example` to `.env` and configure `VITE_API_BASE_URL` for the Portal Backend.
2. Ensure pnpm 10.6.5 is available. If pnpm was installed through another method, skip this command; otherwise enable it through Corepack:

   ```shell
   corepack enable
   ```

3. Install dependencies:

   ```shell
   pnpm install
   ```

4. Start the development server:

   ```shell
   pnpm dev
   ```

Open the local URL printed by Vite, typically `http://localhost:5173`.

## Package Manager

This project uses [pnpm](https://pnpm.io/), with the version pinned in `package.json`.

Enable pnpm through Corepack:

```bash
corepack enable
```

Note: If your machine cannot install Corepack shims, prefix pnpm commands with `corepack`, for example `corepack pnpm install`.

Alternatively, follow the other [recommended pnpm installation options](https://pnpm.io/installation).

## Environment

Create a local `.env` file from `.env.example` before running or building the portal.

| Variable                               | Description                                                                                                               | Example                   |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- | ------------------------- |
| `VITE_API_BASE_URL`                    | Required base URL for the OpenFGC Portal backend API. Vite embeds this value at build time.                               | `http://localhost:8080`   |
| `VITE_AUTH_ENABLED`                    | Enables frontend authentication gating; set to `true` for protected deployments.                                          | `true`                    |
| `VITE_AUTH_ACCESS_TOKEN_PART1_COOKIE`  | Cookie name for the readable access-token part.                                                                           | `portal-at-p1`            |
| `VITE_AUTH_REFRESH_TOKEN_PART1_COOKIE` | Cookie name for the readable refresh-token part.                                                                          | `portal-rt-p1`            |
| `VITE_AUTH_ID_TOKEN_PART1_COOKIE`      | Cookie name for ID-token part 1.                                                                                          | `portal-id-p1`            |
| `VITE_AUTH_ID_TOKEN_PART2_COOKIE`      | Cookie name for ID-token part 2.                                                                                          | `portal-id-p2`            |
| `VITE_AUTH_LOGOUT_ALLOWED_ORIGINS`     | Exact comma-separated origins accepted for logout navigation. Include the IdP origin when using its end-session endpoint. | `https://idp.example.com` |

The authenticated user and organization IDs are resolved from `GET /me`; they are not frontend build-time configuration.

## Production security headers

`pnpm build` emits `dist/_headers`; configure the production static host to apply
it and build with the deployment's `VITE_API_BASE_URL`.

## Scripts

```bash
pnpm start
pnpm dev
pnpm lint
pnpm lint:fix
pnpm format
pnpm format:check
pnpm test
pnpm test:watch
pnpm test:coverage
pnpm build
pnpm security:verify
pnpm preview
```

## Testing

Tests are written with [Vitest](https://vitest.dev/) and [React Testing Library](https://testing-library.com/react).

- **Test files**: Located in `src/__tests__/` with `.test.ts`/`.test.tsx` extensions
- **Setup**: Global setup in `vitest.setup.ts` imports jest-dom matchers
- **Run tests**: `pnpm test` or `pnpm test:watch` for watch mode
- **Coverage**: `pnpm test:coverage` generates HTML coverage report in `coverage/`

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

This project uses `i18next` and `react-i18next` for UI translations.

- Add locale resources under `src/i18n/resources/<locale>/`.
- Keep keys grouped by namespace (for example `common`) and feature intent (`app`, `forms`, `buttons`).
- In components, use `useTranslation` and keys instead of hardcoded user-facing text.
- Keep accessibility labels and user-visible messages localized as well.

Example:

```tsx
import { useTranslation } from 'react-i18next'

function Example(): React.JSX.Element {
  const { t } = useTranslation('common')

  return <h1>{t('app.title')}</h1>
}
```

## CI

GitHub Actions CI runs pnpm-based install, lint, and build checks on every push and pull request on portal/frontend directory.

## AI Instructions

This repository uses AGENTS.md files to keep AI-generated changes aligned with project and organization standards.

- Frontend standards: `portal/frontend/AGENTS.md`
