# DPDP Accelerator Integration Test Suite

Playwright-based integration tests that run against a **real, already-deployed** WSO2 Identity
Server with the DPDP accelerator merged in — the same topology real users hit, exercised through
real OAuth2 logins and a real consent-management database. Nothing here is mocked or stubbed.

## Contents

- [Prerequisites](#prerequisites)
- [Setup](#setup)
- [Continuous integration](#continuous-integration)
- [Running the tests](#running-the-tests)
- [Project structure](#project-structure)
- [Test areas](#test-areas)
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
   rather use existing accounts, name them under `personas` in `e2e-config.json` - they must
   satisfy:
   - **User** — no role needed; every signed-in user manages their own consents. Must *not* be an
     administrator: `tests/04-authorization` asserts this account holds only `internal_login`.
   - **Consent Admin** — assigned `dpdp-consent-admin` (see `../docs/content/configuration-guide.md`,
     "Grant administration access"). Drives the admin UI and seeds Purposes/Elements/Consents via
     the API for `tests/02-elements`, `tests/03-purposes` and `tests/04-consents`.
   - **Second User** — optional, a distinct plain account. Without it the ownership-isolation
     tests skip themselves.

   All three must live in the super tenant: the suite has no `/t/<tenant>` support.
3. Node.js 20.19+ (or 22.12+), matching the rest of the repository.

## Setup

Against a freshly installed, already-running Identity Server, one command does everything:

```sh
./scripts/setup-local.sh
```

It installs dependencies and Chromium, generates the test-account password, mints the
provisioning client, and creates the three test accounts with their roles. Idempotent - safe to
re-run, and an existing `e2e-config.local.json` is never overwritten.

Then run the suite with `./run-e2e.sh`. Setup is a one-time step per environment, not per run.

If the server is not on `https://localhost:9443`, or its admin account is not the one below, edit
`e2e-config.json` first - see [Configuration](#configuration).

### Why a browser opens during setup

Identity Server 7.3 disables HTTP Basic auth on protected resources for any deployment whose
root organization was created on or after the cutoff in the product's own
`repository/conf/compatibility-settings-metadata.json` (`basicAuth.disableBasicAuth`,
`2026-08-13`). The shipped H2 database carries a root organization stamped at WSO2's build time
(`2026-04-23`), so Basic auth still works there; MySQL and every other backend write
`CURRENT_TIMESTAMP` at install and land past the cutoff. Everything administrative in this suite
therefore authenticates with a `client_credentials` token, which behaves identically on every
database type.

Minting that client is itself an authenticated call, so `scripts/bootstrap-provisioning-app.ts`
signs in to the Console through a real browser once, creates the `DPDP E2E Provisioning`
application, and records its credentials under `provisioningClient` in `e2e-config.local.json`
(gitignored). Every later run reuses them and launches no browser.

They are kept there rather than under `.auth/` deliberately: `global-teardown.ts` removes that
whole directory after every run so the next one starts from a fresh login, which would discard
the client too and force a browser sign-in before every run.

### Running the steps individually

```sh
npm install && npx playwright install chromium
npm run bootstrap:provisioning-app        # once per environment
bash scripts/provision-test-users.sh      # needs the password in e2e-config.local.json
```

## Configuration

`e2e-config.json` is the single place this suite reads its settings from. It is committed and
carries every default. `e2e-config.local.json` beside it (gitignored, optional) overrides
individual values and is what `setup-local.sh` and CI write, since the account password is
generated per environment rather than committed.

There are no environment variables and no defaults inlined in code - a missing value fails with
the key that is missing. Settings are also never derived from the accelerator's own install
config (`accelerators/dpdp-is/repository/conf/configure.properties`): the suite declares what it
needs and the deployment is expected to match, rather than the two silently drifting apart.

The override file only names what it changes; it is merged into the defaults key by key:

```json
{
  "identityServer": { "baseUrl": "https://is.example.test:9443" },
  "personas": { "user": { "password": "..." } }
}
```

| Key | Default | Meaning |
| --- | --- | --- |
| `identityServer.baseUrl` | `https://localhost:9443` | The Identity Server itself - `/oauth2/jwks`, `/authenticationendpoint/*`, the management APIs. |
| `identityServer.portalBaseUrl` | `…/consent-portal` | Where the consent-portal WAR is served from; the URL real users hit. |
| `identityServer.ignoreHttpsErrors` | `true` | The shipped certificate is self-signed. Set `false` only against a properly trusted one. |
| `superAdmin.username` / `.password` | `admin@wso2.com` / `wso2123` | The Console account the one-time bootstrap signs in as, and the account `tests/05-multi-tenancy` creates a throwaway organization with. Must match what this deployment actually has. |
| `personas.user.*` | `dpdp-user-1@dpdp.test` | The low-privilege persona. Deliberately not an administrator: `tests/04-authorization` asserts it holds only `internal_login`. |
| `personas.user2.*` | `dpdp-user-2@dpdp.test` | A second distinct user, required just like `personas.user` - ownership-isolation tests fail loudly, rather than skipping, if its password is unset. |
| `personas.consentAdmin.*` | `dpdp-admin@dpdp.test` | Holds `dpdp-consent-admin`, which grants every `internal_consent_mgt_*` scope - this one persona drives the admin registry UI and seeds Purposes/Elements/Consents via the API. |
| `personaRoles.user` / `.consentAdmin` | `dpdp-consent-user` / `dpdp-consent-admin` | The roles provisioning assigns. The accelerator creates the roles themselves; it never assigns membership. |
| `webhook.receiverHost` | `null` | A host the Identity Server can actually reach over the network. Loopback is rejected outright by `EventNotificationUrlValidator`, so webhook tests skip themselves while this is unset. See [`AGENTS.md`](AGENTS.md), "Webhook-dependent tests". |
| `webhook.allowPrivateNetwork` | `false` | Set `true` only once the deployment's `[dpdp_accelerator.event_notifications.webhook] allow_private_network_callback_targets` is also true - required whenever `receiverHost` is an RFC1918 address. |
| `webhook.baseBackoffSecondsOverride` / `.maxRetriesOverride` | `null` | Set to whatever the deployment's `base_backoff_seconds`/`max_retries` were shortened to - opt-in for the two retry-timing tests in `09.10-webhook-delivery-api.spec.ts`, which take minutes at the real defaults. See [`AGENTS.md`](AGENTS.md), "Webhook-dependent tests". |
| `consentExpiry.schedulerPollTimeoutMs` | `null` | Opt-in. The real `ConsentExpiryJob` defaults to a daily cron, far too slow to wait on; set this only after switching the deployment's `[dpdp_accelerator.consent_expiry]` to `schedule_mode = "interval"` with a short `interval_seconds` and restarting the server. Unset skips that one test; every other consent-expiry test triggers reconciliation via a mutation and runs regardless. CI does this automatically - see `scripts/enable-fast-consent-expiry.sh`. |

The passwords the personas need are not committed - `setup-local.sh` generates one and writes it
into `e2e-config.local.json`, which is also where the accounts themselves get it from, so the two
cannot disagree.

## Continuous integration

`.github/workflows/pr-e2e.yml` runs this suite against a freshly deployed Identity Server, and can
be dispatched manually from the Actions tab. On a pull request it is not automatic: a maintainer
applies the `Action/trigger-e2e` label after reviewing the diff, and the label is stripped again on
every new push. It performs the same steps as the setup above, so a change that breaks local setup
breaks CI too.

PR runs exercise only the `multi-tenant` project - `super-tenant` differs mainly in the frontend's
unqualified-root routing (`basePath.ts`) and skipping tenant creation, not in re-testing already
covered features, and running both sequentially in the same job roughly doubled the runtime.
`.github/workflows/nightly-e2e.yml` runs every project once a day instead, so a super-tenant-only
regression surfaces within a day rather than only at the next weekly or release run.
`weekly-e2e-is-master.yml` and the release gate also run every project, unconditionally.

`.github/workflows/e2e.yml` (the reusable job every one of the above calls) also resolves the
runner's own private IP as `WEBHOOK_RECEIVER_HOST` and runs
`scripts/enable-webhook-callbacks.sh` against the deployed `deployment.toml`, so
`tests/09-event-notifications`'s real webhook-delivery tests run in every one of these workflows
too - see [`AGENTS.md`](AGENTS.md), "Webhook-dependent tests", for what that script changes and
why.

## Running the tests

```sh
./run-e2e.sh                       # everything
./run-e2e.sh tests/04-consents     # one area
npm run report                     # open the last HTML report
```

`run-e2e.sh` installs dependencies and the Chromium browser on first run, then forwards its
arguments straight to `npx playwright test` — any Playwright CLI flag works, including `--ui`.

Selecting a single test or file goes through its ID (**escape the dots** — `--grep` takes a regex):

```sh
npx playwright test --grep "03\.06\.04"     # one test
npx playwright test --grep "03\.06\."       # one file
npx playwright test --workers=1              # serial; distinguishes a real failure from a flake
```

Equivalent npm scripts:

| Command | Runs |
| --- | --- |
| `npm test` | the full suite |
| `npm run test:elements` | `tests/02-elements` |
| `npm run test:purposes` | `tests/03-purposes` |
| `npm run test:consents` | `tests/04-consents` |
| `npm run test:authorization` | `tests/05-authorization` |
| `npm run test:multi-tenancy` | `tests/06-multi-tenancy` |
| `npm run test:account` | `tests/07-account` |
| `npm run test:complaints` | `tests/08-complaints` |
| `npm run test:event-notifications` | `tests/09-event-notifications` |
| `npm run test:dashboard` | `tests/10-dashboard` |
| `npm run test:ui` | any of the above, in Playwright's [UI mode](https://playwright.dev/docs/test-ui-mode) |
| `npm run report` | opens the last HTML report |

Two checks need **no running server** and gate every PR — run them before pushing:

| Command | Checks |
| --- | --- |
| `npx tsc --noEmit` | Playwright never typechecks, so a bad import otherwise fails at runtime and reports as `0 tests in 0 files` |
| `npm run verify:ids` | The numbering rules in [`AGENTS.md`](AGENTS.md), and that `TEST-SCENARIOS.md` matches the tree |

### UI mode

Playwright's UI mode gives a watch-mode runner with a time-travelling trace viewer per test —
useful for picking individual tests, re-running just the failed ones, and stepping through what
the browser actually did.

```sh
npm run test:ui                              # everything, in UI mode
./run-e2e.sh --ui                            # same, via run-e2e.sh
npx playwright test tests/04-consents --ui   # one category, in UI mode
```

## Project structure

| Path | Purpose |
| --- | --- |
| `tests/<NN>-<area>/` | Spec files, grouped by feature area — see [Test areas](#test-areas) |
| `pages/` | Page Objects for the portal and Console UIs — one class per screen or dialog |
| `clients/` | Typed wrappers over the consent-mgt, complaint and event-notification REST APIs |
| `fixtures/` | Authenticated personas, throwaway tenants, API clients, cleanup tracker |
| `utils/` | Env/config, auth storage, seed helpers, unique test-data generators |
| `scripts/verify-test-ids.mjs` | Enforces the numbering rules; runs on every PR |
| [`AGENTS.md`](AGENTS.md) | Rules and conventions for writing a test |
| [`TEST-SCENARIOS.md`](TEST-SCENARIOS.md) | The scenario catalogue — every test, what it asserts, known gaps |

## Test areas

**[`TEST-SCENARIOS.md`](TEST-SCENARIOS.md) is the scenario-by-scenario catalogue** — every test's
ID, what it drives and what it asserts, plus known gaps, the product bugs the tests work around,
and the measured flake profile. Open that when you need to know what is covered, plan a change, or
work out what a CI failure was checking. This table is only the map of what each directory owns.

Test IDs are derived from location — `<area>.<file>.<test>`, so `04.06.04` is the fourth test in
`tests/04-consents/04.06-*.spec.ts`. See [`AGENTS.md`](AGENTS.md), "Numbering and layout".

| Directory | Tests | Covers |
| --- | --- | --- |
| `01-provisioning/` | 3 | Per-run setup: creates the throwaway tenant, provisions its personas and the super tenant's |
| `02-elements/` | 12 | Element catalog: admin creating, viewing, searching, and deleting Elements |
| `03-purposes/` | 18 | Purpose catalog: admin creating, viewing, searching, deleting Purposes, and managing versions |
| `04-consents/` | 49 | Consent records: user and admin registries (view/search/act), consent history, expiry reconciliation |
| `05-authorization/` | 8 | The global route-guard and sidebar-visibility mechanism, per persona's scopes |
| `06-multi-tenancy/` | 1 | Cross-tenant Purpose data isolation - "multi-tenant" project only |
| `07-account/` | 5 | Self-service account deletion, and who is offered it. Destructive, so each test uses its own throwaway user |
| `08-complaints/` | 44 | Grievance redressal: the Data Principal's list and the officer's queue — submit, view, search, reply, resolve, authorization |
| `09-event-notifications/` | 60 | Topics, subscriptions, event publishing and fan-out, webhook delivery, authorization and tenant isolation |
| `10-dashboard/` | 9 | Dashboard counts and links: per-state and per-relation consent counts and complaint counts for throwaway users, the admin view's shape |

A filename ending `-api.spec.ts` drives no browser at all.

## Further reading

| | |
|---|---|
| [`AGENTS.md`](AGENTS.md) | Rules and conventions — read before writing or changing a test. Covers the shared-environment premise every rule follows from, the numbering scheme, personas, page objects and the seed helpers |
| [`TEST-SCENARIOS.md`](TEST-SCENARIOS.md) | Every test, known gaps, product bugs, known flakiness |
| [Quickstart](../docs/content/quickstart.md) | Installing and starting the Identity Server |
| [Configuration Guide](../docs/content/configuration-guide.md) | Portal application and role configuration |
