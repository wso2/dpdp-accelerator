# OpenFGC Portal Agent Guide

This is the cross-agent, provider-neutral instruction file for this repository.

Use this file as the canonical shared policy for the frontend. For Oxygen UI component-specific guidance, also follow `.ai/oxygen-ui/AGENTS.md`.

## Required Stack and Patterns

- React + TypeScript + Vite
- pnpm for package management
- Oxygen UI for UI components
- Vitest + React Testing Library for tests
- ESLint + Prettier for code quality

## Non-Negotiable Rules

- Import UI components from @wso2/oxygen-ui only. Do not import from @mui/material.
- Use OxygenUIThemeProvider at app root.
- Use sx with theme tokens. Avoid hardcoded colors/spacing and inline styles.
- Use functional components only.
- Keep components focused and extract reusable logic into hooks.
- Avoid prop drilling; prefer context/state management where appropriate.
- Do not use any. Use unknown or generics.
- Add explicit return types for function signatures.
- Prefer interfaces for object shapes.
- Do not disable ESLint rules to bypass quality checks.

## Naming and Structure

- Components: PascalCase.tsx, one component per file, default export.
- Logic and utils: camelCase.ts.
- Variables/functions: camelCase.
- Interfaces/types: PascalCase.
- Constants: UPPER_SNAKE_CASE.
- Folders: kebab-case.
- Keep code under src/components, src/features, src/hooks, src/types, src/utils, src/__tests__.

## Testing and Quality Gates

- Add tests for components and hooks.
- Keep tests under src/__tests__ using *.test.tsx (or co-located when justified).
- Use PascalCase for test filenames in src/__tests__ (for example, ConsentRegistryModals.test.tsx).
- Test happy path and error path, and mock network requests when needed.
- Prefer behavior-focused tests over implementation details.
- Keep tests deterministic and use clear Arrange-Act-Assert structure.
- Before merge, ensure lint, format, test, and build all pass.

## API and Data Layer

- Use the shared `fetch` API layer and TanStack Query for server state.
- Keep API access in dedicated modules and hooks, not presentational components.
- Define typed request and response contracts for every endpoint.
- Handle loading, empty, error, and success states explicitly.
- Centralize error mapping for consistent user-facing messages.
- Use request cancellation or abort signals where appropriate.

## Security and Accessibility Baseline

- Never expose secrets in frontend code.
- Use import.meta.env.VITE_* for client-side config.
- Treat user input as untrusted and sanitize when rendering rich content.
- Avoid `dangerouslySetInnerHTML`; if unavoidable, sanitize content first.
- Do not log tokens, email addresses, or other personally identifiable information.
- Avoid hardcoded URLs and environment-specific assumptions.
- Use semantic HTML and correct landmark structure.
- Ensure all interactive elements are keyboard accessible, visibly focused, and predictably ordered.
- Give controls accessible names and do not communicate status through color alone.
- Add `aria-*` attributes only when native semantics are insufficient.
- Include accessibility checks in component tests where practical.

## i18n Baseline

- Externalize user-facing strings to i18n resources; avoid hardcoded copy in components.
- Use stable, descriptive translation keys and keep naming patterns consistent.
- Ensure English defaults/fallbacks exist for new keys, use locale-aware formatting (date, time, number, currency), and preserve graceful missing-key behavior.
- Cover i18n updates with tests for translated rendering and fallback paths.

## Oxygen UI Notes

The generated Oxygen-specific catalog and examples are maintained in:

- .ai/oxygen-ui/AGENTS.md

Keep that file as framework reference. Keep this file focused on project standards.

## Documentation Hygiene

- Keep README and setup documentation aligned with scripts and tooling.
- Document non-obvious behavior, edge cases, and accessibility considerations.
- Remove dead code and debug logging before merge.
