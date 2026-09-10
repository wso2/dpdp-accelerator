# DPDP Accelerator Integration Test Suite

Playwright-based integration tests that run against a **real, already-deployed** WSO2 Identity
Server with the DPDP accelerator merged in — the same topology real users hit, exercised through
real OAuth2 logins and a real consent-management database. Nothing here is mocked or stubbed.

## Contents

- [Prerequisites](#prerequisites)
- [Setup](#setup)
- [Running the tests](#running-the-tests)
- [Project structure](#project-structure)
- [Test categories](#test-categories)
- [Operating principles](#operating-principles)
- [Known limitations](#known-limitations)
- [Further reading](#further-reading)

## Prerequisites

1. A running WSO2 Identity Server with the DPDP Accelerator deployed. The Consent Portal
   application and the `dpdp-consent-user` / `dpdp-consent-admin` roles are provisioned
   automatically at startup — there is nothing to register by hand. Confirm the server is up:
   ```sh
   curl -sk https://<host>:9443/oauth2/jwks
   curl -sk https://<host>:9443/consent-portal/
   ```
2. Three user accounts, with roles assigned. Role *membership* is the one thing the accelerator
   never provisions, so `scripts/provision-test-users.sh` does it (see Setup below). If you would
   rather use existing accounts, they must satisfy:
   - **User** — no role needed; every signed-in user manages their own consents. Must *not* be an
     administrator: `tests/04-authorization` asserts this account holds only `internal_login`.
   - **Consent Admin** — assigned `dpdp-consent-admin` (see `../docs/content/configuration-guide.md`,
     "Grant administration access"). Drives the admin UI and seeds Purposes/Elements/Consents via
     the API for `tests/01-elements`, `tests/02-purposes` and `tests/03-consents`.
   - **Second User** — optional, a distinct plain account. Without it the ownership-isolation
     tests skip themselves.

   All three must live in the super tenant: the suite has no `/t/<tenant>` support.
3. Node.js 20.19+ (or 22.12+), matching the rest of the repository.

## Setup

```sh
# Create the three accounts and assign their roles. Idempotent - safe to re-run.
TEST_PASSWORD='Str0ng!Pass' bash scripts/provision-test-users.sh

cp .env.example .env
# edit .env: fill in TEST_USER_USERNAME/PASSWORD, TEST_CONSENT_ADMIN_USERNAME/PASSWORD,
# and PORTAL_BASE_URL/IS_BASE_URL if not localhost:9443
npm install
npx playwright install chromium
```

The script prints the usernames it provisioned (`dpdp-ci-user`, `dpdp-ci-user-2`,
`dpdp-ci-admin`); put those and `TEST_PASSWORD` into `.env`. It reads `IS_BASE_URL`,
`IS_ADMIN_USERNAME` and `IS_ADMIN_PASSWORD` from the environment - set them to whatever
`repository/conf/configure.properties` was set to for this install, since the script's own
built-in fallback (`https://localhost:9443`, the stock product admin/admin) only matches an
unconfigured Identity Server.

## Continuous integration

`.github/workflows/pr-e2e.yml` runs this suite against a freshly deployed Identity Server, and can
be dispatched manually from the Actions tab. On a pull request it is not automatic: a maintainer
applies the `Action/trigger-e2e` label after reviewing the diff, and the label is stripped again on
every new push. It performs the same steps as the setup above, so a change that breaks local setup
breaks CI too.

## Running the tests

```sh
./run-e2e.sh                     # everything
./run-e2e.sh tests/03-consents    # one category
npm run report                   # open the last HTML report
```

`run-e2e.sh` installs dependencies and the Chromium browser on first run, then forwards its
arguments straight to `npx playwright test` — any Playwright CLI flag works, including `--ui`.
Equivalent npm scripts are also available:

| Command | Runs |
| --- | --- |
| `npm test` | the full suite |
| `npm run test:elements` | `tests/01-elements` |
| `npm run test:purposes` | `tests/02-purposes` |
| `npm run test:consents` | `tests/03-consents` |
| `npm run test:authorization` | `tests/04-authorization` |
| `npm run test:multi-tenancy` | `tests/05-multi-tenancy` |
| `npm run test:account` | `tests/06-account` |
| `npm run test:ui` | any of the above, in Playwright's [UI mode](https://playwright.dev/docs/test-ui-mode) |
| `npm run report` | opens the last HTML report |

### UI mode

Playwright's UI mode gives a watch-mode runner with a time-travelling trace viewer per test —
useful for picking individual tests, re-running just the failed ones, and stepping through what
the browser actually did.

```sh
npm run test:ui                              # everything, in UI mode
./run-e2e.sh --ui                            # same, via run-e2e.sh
npx playwright test tests/03-consents --ui   # one category, in UI mode
```

## Project structure

| Path | Purpose |
| --- | --- |
| `tests/` | Spec files, grouped by feature area — see [Test categories](#test-categories) |
| `pages/` | Page Objects for the portal UI — one class per screen or dialog |
| `clients/` | `ConsentApiClient`, a typed wrapper over WSO2 IS's own consent-mgt v2 and self-service consent REST APIs |
| `fixtures/` | Authenticated personas (User, Consent Admin) and the test-data cleanup tracker |
| `utils/` | Env/config loading, auth-storage helpers, unique test-data generators |

## Test categories

Full scenario-by-scenario coverage (test IDs, expected results, regression-suite membership) is
tracked in the team's test-scenario spreadsheet; this is the map of what each directory is
responsible for.

| Directory | Covers |
| --- | --- |
| `01-elements/` | Element catalog: admin creating, viewing, and searching Elements |
| `02-purposes/` | Purpose catalog: admin creating, viewing, and searching Purposes |
| `03-consents/` | Consent records: User and admin registries (view/search/act) |
| `04-authorization/` | Route-level access control and sidebar visibility per persona's scopes, including who is offered self-service account deletion |
| `05-multi-tenancy/` | Tenant provisioning, data isolation and user/role assignment, driven through the real Console UI |
| `06-account/` | Self-service account deletion end to end. Destructive and irreversible, so each test creates and signs in as its own throwaway user rather than any shared persona, and removes it again afterwards |

## Operating principles

A handful of things shape how every test here is written, driven by running against a real,
persistent, shared environment rather than a disposable one:

- **The environment never resets.** Data from every prior run is still there. Tests assert by
  unique marker or server-issued ID, never by "the list is empty" or exact row counts.
- **Tests run in parallel by default** (Playwright's `fullyParallel: true`) — no extra setup
  needed to make a full run fast.
- **Personas log in once per run, not once per test.** IS allows only one active session per
  account; `fixtures/auth.fixtures.ts` caches each persona's login across every worker so
  concurrent tests don't invalidate each other's sessions.
- **Tests clean up their own setup data — except Consents.** Elements/Purposes created as setup
  are deleted when the test finishes; Consents are left in place, since the product has no
  delete-by-id for them.
- **Every spec is independent.** Nothing in the suite uses `test.describe.serial` — every test can
  run in any order, on any worker, without coordination.

## Known limitations

- **Session concurrency is capped by IS itself**, not this suite — scaling truly concurrent
  logins for the same persona means provisioning additional test accounts, not a config change.
- **Consents created as test setup are never deleted** and accumulate in the shared environment
  over time (see [Operating principles](#operating-principles)).

