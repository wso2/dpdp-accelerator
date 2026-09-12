# DPDP Integration Test Suite — Agent Guide

Rules and conventions for writing tests in this directory. Read this before adding or changing a
spec or page object.

| Need | Go to |
|---|---|
| What every test covers, known gaps, product bugs, flakiness | [`TEST-SCENARIOS.md`](TEST-SCENARIOS.md) |
| Setup, prerequisites, how to run, CI | [`README.md`](README.md) |
| Rules, conventions, numbering | this file |

## The one thing that shapes everything

These tests drive a **real, already-running WSO2 Identity Server** with the DPDP accelerator
deployed, through real OAuth2 logins, against a real consent database. Nothing is mocked, stubbed,
or reset. Data from every previous run is still there and will still be there after yours.

This suite does not start the server. If `PORTAL_BASE_URL` / `IS_BASE_URL` aren't up,
`global-setup.ts` fails fast with a reachability error — that's the environment, not your test.

## Two commands that are not optional

```sh
npx tsc --noEmit        # REQUIRED before you call a change done
npm run verify:ids      # REQUIRED - enforces the numbering rules below
```

**`npx tsc --noEmit` is not optional.** Playwright transpiles each file with esbuild and never
typechecks, so a missing import or a renamed method fails at *runtime* — and because Playwright
aborts collection when any file fails to load, one bad import reports as `0 tests in 0 files`
across the whole suite, not as one broken spec. This has already happened once here.

`npm run verify:ids` needs no server and runs on every PR in `pr-build.yml`. See
[`README.md`](README.md) for the full command list.

## Layout

| Path | What belongs there |
| --- | --- |
| `tests/<NN>-<area>/` | Specs, grouped by feature area — see [Numbering and layout](#numbering-and-layout) |
| `pages/` | Page Objects — one class per screen or dialog; all locators live here, never in a spec |
| `clients/` | `ConsentApiClient`, `ComplaintApiClient`, `EventNotificationApiClient` — typed wrappers over the REST APIs |
| `fixtures/auth.fixtures.ts` | Personas, API-client fixtures, cleanup tracker. Re-exports `test`/`expect` |
| `fixtures/tenant.fixtures.ts` | Throwaway-tenant fixtures — see [Multi-tenancy](#multi-tenancy) |
| `utils/` | Env/config, auth headers, seed helpers (`consentSetup`, `complaintSetup`, `eventNotificationSetup`), unique test-data generators, MUI and filter helpers, `throwawayUser`, `webhookReceiver` |
| `scripts/` | `verify-test-ids.mjs` — the numbering guard |
| `TEST-SCENARIOS.md` | The scenario catalogue. **Update it in the same commit as any test change** |

Import `test` and `expect` **from `../../fixtures/auth.fixtures`**, never from `@playwright/test`
directly — the fixtures are only available on the extended `test`. Specs needing a throwaway tenant
import from `../../fixtures/tenant.fixtures` instead, which itself extends `auth.fixtures`'s `test`
(so `consentAdminConsentApi` etc. are still available there too). `tests/05-multi-tenancy/` and
three files in `tests/08-event-notifications/` do this today.

## Numbering and layout

**One numbering system, derived entirely from where a test lives.** An ID is a path, not a
reference to anything external.

| Rule | |
|---|---|
| **R1 · Area** | `tests/<NN>-<kebab-name>/`. `NN` is two digits, sequential from `01`. |
| **R2 · Spec file** | `<NN>.<MM>-<kebab-description>.spec.ts`. `MM` sequential from `01` within the area. Separator always `-`; description always kebab-case. |
| **R3 · Describe titles** | Plain prose, no IDs. Nest freely for grouping. |
| **R4 · Test** | `test('<NN>.<MM>.<KK> - <observable behaviour>')`. `KK` sequential from `01`, **flat across any nesting**, in declaration order. |
| **R5 · Sequential** | Every level is dense — no gaps. A deletion renumbers the survivors after it, **and their cross-references**. |
| **R6 · API-only files** | A filename ending `-api.spec.ts` means the file **drives no browser at all**. A test that needs a browser for setup is not an `-api` file even if it asserts only on API responses (`03.09` is exactly this case). |
| **R7 · Cross-cutting** | `04-authorization` and `05-multi-tenancy` hold only tests of a *global mechanism* or tests spanning features. A single feature's own guard lives in that feature's area as `<NN>.<MM>-<feature>-authorization.spec.ts` (see `07.08`, `08.05`). |
| **R8 · Area exclusivity** | Every filename and every test ID under `tests/<NN>-*/` begins with `<NN>`. No exceptions. |

`npm run verify:ids` enforces all of the above, plus that every ID mentioned in a comment resolves
to a real test and every referenced path exists.

**Worked example — R4 and R6 together.** A file whose tests split between UI and API becomes two
files, with `KK` continuing across a nested describe boundary rather than restarting:

```
08.01-admin-managing-topics.spec.ts            UI
  Admin managing Topics
    Registering
      08.01.01 … 08.01.04
    Deregistering
      08.01.05                                 ← continues the sequence across the group

08.06-topic-lifecycle-api.spec.ts              API — no browser anywhere in the file
  Topic lifecycle rules
      08.06.01 … 08.06.03
```

The describe chain is already printed in Playwright's report line, so the group does not need to
appear in the number.

### Selecting tests

```sh
npx playwright test --grep "03\.06\.04"        # one test — ESCAPE THE DOTS or they are wildcards
npx playwright test --grep "03\.06\."          # one file
npx playwright test tests/03-consents          # one area, by path
```

`--grep` matches the file path as well as the describe and test titles, which is why an ID prefix
selects a whole file. Unescaped dots match any character: `--grep "05.01"` matches more than you
mean.

## Keeping TEST-SCENARIOS.md current

[`TEST-SCENARIOS.md`](TEST-SCENARIOS.md) is the single file a human opens to understand what is
covered, plan a change, or interpret a CI failure. It is **not** optional documentation:

- A commit that **adds** a test adds its row.
- A commit that **deletes** a test removes its row — and renumbers the survivors (R5).
- A commit that **moves** a test updates both the row and every cross-reference to its old ID.
- A commit that **changes what a test asserts** updates its Notes.
- Any of the above updates the counts, in all three places they appear: the `| **Tests** |`
  summary row at the top of the catalogue, the `**N tests, M spec files.**` line under the area's
  own heading, and the per-area table in [`README.md`](README.md).
- New known gaps, product bugs found, and flakiness observations go in its tail sections.

`npm run verify:ids` fails if an ID in the tree has no entry there, if an entry names an ID that
does not exist, or if any of those counts disagrees with the tree. That catches the mechanical
half; the prose is on you.

The counts are checked because they are what actually goes stale. The tables are hard to forget —
verify:ids rejects the commit — but the totals above them are prose, and a test arriving through a
merge leaves them quietly wrong.

## Personas and auth

```ts
const page = await loginAsUser(browser)          // any signed-in user; manages only their own consents
const page = await loginAsConsentAdmin(browser)  // holds dpdp-consent-admin; every internal_consent_mgt_* scope
```

Both take the worker-scoped `browser` fixture and return an already-signed-in `Page`. **You own the
returned page's context and must close it**: `await page.context().close()` at the end of the test.
Leaking contexts is the fastest way to make the suite flaky.

A persona logs in for real at most **once per run**, cached to `.auth/<persona>.json` and shared
across workers. Do not add your own login flow, and do not call `getPersonaState` unless you need a
persona with no fixture (only `user-2` qualifies).

"Officer" in the complaint tests is not a separate persona — it is any `dpdp-consent-admin` holder,
so `loginAsConsentAdmin` doubles as the complaint officer and the event-notification admin.

A second user account is optional. Tests needing two distinct real users must guard themselves:

```ts
test.skip(!hasSecondUser(), 'personas.user2 is not configured')
```

### API access

Fixtures are requested by destructuring the test callback's first argument:

```ts
test('...', async ({ browser, consentAdminConsentApi, consentCleanupTracker }) => { ... })
```

- `userConsentApi` / `consentAdminConsentApi` — `ConsentApiClient` bound to that persona's headers.
- `userComplaintApi` / `officerComplaintApi` — `ComplaintApiClient`, self-service and `:any` surfaces.
- `userEventApi` / `consentAdminEventApi` — `EventNotificationApiClient`; the user holds no
  `notifications:*` scope, which is what makes it useful for proving a 403.
- `consentCleanupTracker` — register created records for teardown (below).

To prove a scope boundary, construct a client yourself with the *wrong* persona's headers and call
a privileged method — that's the intended way to assert a token is rejected.

**Every API client's bearer token comes from a real browser login** (`loginAndCaptureState` lifts it
off the SPA's first authenticated request) and is bound to the `atbv` cookie by IS's
`CookieBasedTokenBinder`. There is no non-browser grant anywhere in this suite. That is why an
`-api.spec.ts` file is only browser-free if its *setup* is too.

## Multi-tenancy

```ts
const page = await loginAsTenantOwner(browser, tenant)         // holds every internal_consent_mgt_* scope
const page = await loginAsTenantConsentUser(browser, tenant)   // holds dpdp-consent-user (no permissions)
```

`tenant` (worker-scoped, from `fixtures/tenant.fixtures.ts`) creates one throwaway tenant per
worker — a unique domain every run, an owner, and a second `dpdp-consent-user` account with its
role already assigned — entirely by driving the real Console UI in a browser, the same way an
actual admin would. There's no teardown call: a fresh domain every run means nothing to collide
with, and there's no real tenant delete on this product without enabling a `carbon.xml` flag this
accelerator doesn't set. `tenantB` gives a second, independent tenant for isolation tests.

**Do not add a `TenantScimClient` or call SCIM2 against a secondary tenant directly.** Confirmed
live, repeatedly: Basic-auth and Bearer-token SCIM2 calls against `/t/<tenant>/scim2/...` both
401 for any tenant other than `carbon.super`, regardless of whose credentials — a real IS 7.3.0
product limitation (see the WSO2 IAM community discussion "Invalid tenant domain of user error
when use scim2 API"), not something fixable from this codebase. What *does* work is driving the
same operations through Console's own UI, which is why tenant creation, second-user creation and
role assignment all go through `ConsoleRootOrganizationWizard`, `ConsoleAddUserWizard` and
`ConsoleRoleAssignment` in `pages/`. If you need a tenant-side Console operation this suite doesn't
have yet, add another page object in that style rather than reaching for SCIM2.

Tenant creation has no REST shortcut worth using either: `POST /api/server/v1/tenants`'s
`owners[].password` doesn't become usable for login without a separate follow-up call, while
Console's "New Root Organization" form's password works immediately.

## Non-negotiable rules for writing a test

**Stamp every record you create, and assert by that marker or by the server-issued ID.** Use
`uniqueMarker`, `uniquePurposeName`, `uniqueElementName`, `uniqueServiceId`, or the realistic
`randomElementProfile` / `randomPurposeProfile` / `randomServiceId` from `utils/testData.ts`.

**Never assert on emptiness, totals, or row counts of a shared list.** `expect(rows).toHaveCount(1)`
and "the list is empty" are always wrong here — a concurrent test or a previous run will break them.
Assert that *your* row is present (or absent) by its id. To prove a filter excluded everything else,
use `expect(rows.filter({ hasNotText: marker })).toHaveCount(0)` — one assertion over a filtered
locator, never a loop over `rows.nth(i)`: the row count can change between the `count()` and the
assertion while a refetch is still narrowing the table. CI has failed exactly that way.

**Gate a snapshot read on the content, never on its container.** `allTextContents()`, `count()`
and `textContent()` are one-shot reads with no auto-retry, unlike `expect()`. Before one, wait on
an auto-retrying assertion for something the read itself expects to find. Waiting on the container
proves nothing when the container renders before its data: the consent full-history dialog fetches
lazily *on open*, so its heading — which is what the page object's `dialog` locator is built from —
appears while the request is still in flight. Gating on the heading and then snapshotting the entry
list yields an empty array under load. Gate on the newest entry instead.

**Track what you create.** `consentCleanupTracker.trackElement(id)` / `.trackPurpose(id)` deletes
them when the test finishes. Read the id out of the detail URL after a create-form redirect:

```ts
await expect(page).toHaveURL(/\/elements\/[^/]+$/)
const id = /\/elements\/([^/]+)$/.exec(page.url())?.[1]
```

**Consents and complaints cannot be cleaned up** — the product has no delete-by-id for either, so
every seeded record is permanent. Seed the minimum you need.

**Assume parallel execution.** `fullyParallel: true` and no `workers` override. Your test must not
depend on ordering, on another test's data, or on being alone. If you genuinely need ordered steps,
use `test.describe.serial` and say why in a comment.

**Use the seed helpers** rather than hand-rolling setup: `seedConsent` (`utils/consentSetup.ts`),
`seedComplaint` / `moveComplaintToStatus` (`utils/complaintSetup.ts`), `seedActiveTopic` /
`seedPollSubscription` / `publishMarkedEvent` (`utils/eventNotificationSetup.ts`). Consent creation
is the only step with no create UI, so it goes through the admin API; the Element and Purpose it
needs are created through the real admin forms. Note `state: 'PENDING'` is expressed by supplying
`authorizations` — the v2 API sets PENDING itself and rejects an explicit `PENDING`.

## Page objects

**All locators live in `pages/`.** A spec that contains `getByRole`/`getByText` is doing the page
object's job.

**Source every user-facing string from the frontend's i18n, not from memory or from the rendered
page.** The single largest category of breakage in this suite has been locators drifting from
renamed copy. Before writing a locator, find the key:

```sh
grep -rn "someKey" ../dpdp-accelerator/react-apps/consent-portal/frontend/public/i18n/en/common.json
```

Then confirm which key the component actually renders. Traps that have already cost real time:

- `sidebar.allConsents` is **"My Consents"** (the user's own list); **"All Consents"** is
  `sidebar.adminConsents`, the *admin* registry. A user-persona test asserting "All Consents" is
  asserting the opposite of what it reads like.
- `catalog.messages.noElements` is "No elements are configured for this **version**", while
  `consentRegistry.details.noElements` is "No elements are **associated** with this purpose" — near
  identical strings, different pages.
- `sidebar.myComplaints` renders "My Complaints" (the Data Principal's own item) while
  `sidebar.complaintManagement` renders just **"Complaints"** — not "Complaint Management" as the
  key name suggests.
- In the event-notification sidebar, "Events" is both a nav *item* and the *category* heading above
  it. Target the item by its button role to disambiguate.

Also note the elements and purposes lists have **different** search placeholders ("Search by element
name" / "Search by purpose name"), so no shared "Search by name" locator can match either.
`getByPlaceholder` matches on substring, which makes a wrong guess here look plausible.

**Never use a leading slash in `goto()`.** Playwright resolves a leading-slash path against
`baseURL` by *replacing* its path, so `goto('/consents')` leaves the portal entirely and
`goto('/')` lands on the Identity Server root (which redirects to the Console). Always
`page.goto('consents')`, and `'./'` for the portal root. `utils/env.ts` documents why `baseURL`
carries a trailing slash; don't remove it.

**Registry rows are addressed by `data-consent-id`**, via `ConsentRegistryTable.rowByConsentId`.
Both registries extend that base class — put anything shared between them there, not in one subclass.

**MUI `<Select>` is not a native `<select>`.** Use `selectMuiOption(page, id, optionName)` from
`utils/muiSelect.ts`; `selectOption()` will not work. The open menu portals to `<body>`, outside any
dialog, which is why the helper looks options up on the page.

**Give every filter change its own checkpoint.** Two filter requests can resolve out of order and
leave a table on a stale intermediate combination. `utils/filterCommit.ts` exists for this; a row
assertion cannot stand in for it.

**Sidebar absence means "not in the DOM".** `AppSidebar.tsx` filters items the persona lacks the
scope for out entirely, and drops a whole category once no item in it survives — so assert
`toHaveCount(0)`, not `not.toBeVisible()`. Route guards behave differently: an unauthorized route
*redirects* (to the dashboard, or renders `NoAccessPage`), so assert on the resulting URL.

**Admin-scoped personas lose the self-service nav.** `deployment.config.json` ships
`hideSelfConsentsForAdmins: true`, so an account with `CONSENTS_READ_ANY` has no "My Consents" and
no "Consent" category at all. Don't assume the admin sees a superset of what the user sees.

**Rows-per-page options differ per table.** The event-notification tables offer `[10, 20, 50]`
(`pages/TopicsPage.ts`'s `ROWS_PER_PAGE_OPTIONS`); the catalog and complaint tables offer 25. Check
the page object, don't assume.

## Webhook-dependent tests

`tests/08-event-notifications/08.10-webhook-delivery-api.spec.ts` needs a receiver the WSO2 IS
process can actually reach, and skips itself otherwise. To run it:

1. Set `webhook.receiverHost` to this machine's **LAN IP** — never `localhost`/`127.0.0.1`, which
   `EventNotificationUrlValidator` rejects unconditionally regardless of any config flag.
2. If that address is RFC1918/site-local (it normally will be), the running deployment's
   `[dpdp_accelerator.event_notifications.webhook]` table needs
   `allow_private_network_callback_targets = true` — it does **not** by default — and then set
   `WEBHOOK_RECEIVER_ALLOW_PRIVATE_NETWORK=true`.

A machine whose LAN IP changes mid-session breaks webhook verification regardless of the tests
being correct. All three tests in that file are also skipped in code for runtime reasons — see
[`TEST-SCENARIOS.md`](TEST-SCENARIOS.md), "Known gaps".

## Auth-fixture internals you must not undo

Two non-obvious lines in `pageForPersonaState` are load-bearing for parallel runs. Both have long
comments; read them before touching that function.

1. **`JSESSIONID` is filtered out of the reused `storageState`.** The portal's JSP shell parks the
   authorization code in the servlet HTTP session and hands it over exactly once. Sharing one
   `JSESSIONID` across contexts means concurrent callbacks stomp a single parked code. `commonAuthId`
   is deliberately kept so sign-in stays silent.
2. **The Bearer-request watcher is armed before navigating, not after.** A reused session signs in
   during the `goto`, so a watcher started afterwards waits 30 s for a request already made.

## Before you call a change done

1. `npx tsc --noEmit` — clean.
2. `npm run verify:ids` — clean.
3. `TEST-SCENARIOS.md` updated for anything you added, deleted, or changed.
4. `npx playwright test <your specs> --workers=1` — passing.
5. `npx playwright test` — full suite, parallel, compared against the flake profile in
   [`TEST-SCENARIOS.md`](TEST-SCENARIOS.md), "Known flakiness".
6. State actual counts. "Tests pass" without the numbers is not a result.
