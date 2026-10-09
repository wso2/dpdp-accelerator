# Test Scenario Catalogue

**Every test in this suite: what it drives and what it asserts.** This is the file to open when
you need to understand what is covered, plan a change to a test, or work out what a failure
in CI was actually checking.

> **Keep this current.** A commit that adds, deletes, or changes the behaviour of a test updates
> this file in the same commit. `npm run verify:ids` fails the build if an ID here has no test, or
> a test has no entry here — see [`AGENTS.md`](AGENTS.md), "Keeping TEST-SCENARIOS.md current".

| | |
|---|---|
| **Tests** | 202 across 54 spec files in 10 areas |
| **Removed, not skipped** | `09.08`'s fan-out persistence rollback case, `09.10`'s stuck-in-flight reclaim case - see "What this suite cannot verify" |
| **Skipped when unconfigured** | `04.09.03` (expiry cron); `09.10.01`, `09.10.02`, `09.10.03` (shortened backoff) |
| **Rules and conventions** | [`AGENTS.md`](AGENTS.md) |
| **Setup and how to run** | [`README.md`](README.md) |

## Finding a test from a failure

IDs are derived from location — `<area>.<file>.<test>` — so a failing `04.05.04` is the fourth
test in `tests/04-consents/04.05-*.spec.ts`. Playwright also prints `file:line` in every report
line, which is more precise still.

```sh
npx playwright test --grep "04\.06\.04"        # one test — escape the dots, they are wildcards
npx playwright test --grep "04\.06\."          # one file
npx playwright test tests/04-consents          # one area
```

## Why the assertions look the way they do

Every test drives a **real, already-running WSO2 IS + accelerator** over real OAuth2 logins
against a real consent database. Nothing is mocked and **the environment never resets**. Three
consequences shape every scenario below — see [`AGENTS.md`](AGENTS.md) for the rules they produce:

1. **No emptiness, total, or row-count assertions on a shared list.** Tests assert that *their own*
   row is present or absent, by unique marker or server-issued id.
2. **Personas log in at most once per run**, cached to `.auth/` and shared across workers — IS
   allows one active session per account.
3. **Nothing a test creates is cleaned up afterward** — Elements, Purposes, Consents, and
   complaints all accumulate permanently in the shared environment. That's fine as long as
   leftover data never affects another test run, which the unique-marker/server-issued-id
   assertions above already guarantee.

**Personas:** `user` (plain `internal_login`; `CONSENTS_*_SELF` and `COMPLAINTS_*_SELF` only),
`consent-admin` (`dpdp-consent-admin`; every `internal_consent_mgt_*` and `notifications:*` scope,
no complaint scope), `dpo` (`dpdp-consent-dpo`; the `:any` complaint scopes only), `user-2`
(optional), plus per-worker throwaway tenants and throwaway users.

---

## `01-provisioning/` — Per-run setup

Not feature tests: the Playwright setup projects the two profiles depend on (see
`playwright.config.ts`). `01.01`/`01.02` back `tenant-setup`/`user-setup`, which `multi-tenant`
depends on; `01.03` backs its own `super-tenant-user-setup`, which `super-tenant` depends on
directly - it never creates or needs a tenant. `01.01`/`01.02` are resumable via
`.e2e-run-state.json`, checked first so re-running the suite never creates a second tenant or
re-provisions personas that already exist; `01.03` is resumable the same way via
`e2e-config.local.json` instead (see `utils/config.ts`).

**3 tests, 3 spec files.**

### `01.01-tenant-creation.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `01.01.01` | Creates a fresh tenant via the root-organization wizard, and its owner can sign into it | Skips tenant creation on a resumed run; the sign-in assertion always runs, on both the create and resume paths. |

### `01.02-user-provisioning.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `01.02.01` | Provisions the per-run tenant's four personas and assigns their roles | Bootstraps a tenant-scoped M2M client through the tenant's own Console, then SCIM2. |

### `01.03-super-tenant-user-provisioning.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `01.03.01` | Provisions the super tenant's four personas and assigns their roles | Reuses an already-configured persona's password from `e2e-config.local.json` rather than regenerating it. Kept out of `01.02` so the "super-tenant" Playwright project never depends on tenant-setup - see `playwright.config.ts`. |

## `02-elements/` — Element catalog

Admin-only. Every test drives the real "Add Element" dialog; elements created are tracked for
deletion, except where deletion is itself under test.

**12 tests, 4 spec files.**

### `02.01-admin-creating-elements.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `02.01.01` | A newly created element's detail page shows its display name, description, and properties correctly | The happy path: created with a display name, description and 2 properties; re-navigates to the detail page fresh (not the post-submit redirect) to prove the server actually persisted every field. |
| `02.01.02` | Leaving name empty shows the required-field error and blocks submission |  |
| `02.01.03` | Creating an element with a name that already exists shows the duplicate-name message | Exact duplicate-name message; dialog stays open, so nothing was created twice. |
| `02.01.04` | A property value with no key blocks submission until the key is filled in or the row is removed | Create button disabled while an orphaned value exists. |

### `02.02-admin-viewing-elements-list.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `02.02.01` | The list renders and its rows-per-page control accepts a new page size without erroring |  |
| `02.02.02` | The rows-per-page control caps the number of rendered rows at the selected size | Seeds 11 elements, sets page size to 10: exactly 10 rows and Next enabled. A page-size cap, not a shared-list count. |
| `02.02.03` | An unknown element id shows the load-failed message with a way back to the list |  |

### `02.03-admin-searching-elements.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `02.03.01` | Searching by a partial name still finds the matching element | Searches on the middle timestamp segment only, proving the API filter is `name co` (substring). |
| `02.03.02` | Resetting the search clears the filter and shows the unfiltered list again | The empty-results placeholder is itself a row, so the message plus the row count staying at 1 is what proves the filter applied. |
| `02.03.03` | A search with no matches shows the empty-results message |  |

### `02.04-admin-deleting-elements.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `02.04.01` | An admin deletes an element that isn't referenced by any purpose | Confirms via a fresh detail-page navigation afterward (load-failed) that the server actually deleted it, not just that the UI navigated away. |
| `02.04.02` | An element still referenced by a purpose cannot be deleted | The purpose is created via `PurposeFormDialog.addElementByName`, which searches the picker server-side rather than relying on the unfiltered (oldest-first, capped) page - see `pages/PurposeFormDialog.ts`. Asserts the 409 conflict message and that the element still resolves afterward. |

## `03-purposes/` — Purpose catalog

Same shape as elements, plus a type filter and version management (a Purpose has one or more
versions; exactly one is "latest" at a time - the one whose elements/properties/description the
overview card shows).

**18 tests, 5 spec files.**

### `03.01-admin-creating-purposes.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `03.01.01` | A purpose with no elements and no properties shows the catalog empty-state messages | Detail page shows "No custom properties." and "No elements are configured for this version." |
| `03.01.02` | A newly created purpose's detail page shows its type, latest version, description, elements, and properties correctly | Created with an element (`addElementByName`, not `addElements` - needs this specific element), a description and 2 properties; re-navigates to the detail page fresh to prove the server actually persisted every field. |
| `03.01.03` | Leaving name, type, and version empty shows all three required-field errors and blocks submission |  |
| `03.01.04` | A property value with no key blocks submission until the key is filled in or the row is removed |  |

### `03.02-admin-viewing-purposes-list.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `03.02.01` | The rows-per-page control accepts a new page size without erroring |  |
| `03.02.02` | An unknown purpose id shows the load-failed message with a way back to the list |  |
| `03.02.03` | The rows-per-page control caps the number of rendered rows at the selected size | Seeds 11 purposes, sets page size to 10: exactly 10 rows and Next enabled. |

### `03.03-admin-searching-purposes.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `03.03.01` | Searching by a partial name still finds the matching purpose |  |
| `03.03.02` | Filtering by an exact type finds only purposes of that type | Uses a unique type value, not a realistic one - type is matched exactly (`eq`), so a common value would be ambiguous in a shared environment. |
| `03.03.03` | Resetting the search clears both filters and shows the unfiltered list again | Both name and type inputs cleared; rows return. |
| `03.03.04` | A search with no matches shows the empty-results message |  |

### `03.04-admin-deleting-purposes.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `03.04.01` | An admin deletes a purpose that isn't referenced by any consent | Confirms via a fresh detail-page navigation afterward (load-failed) that the server actually deleted it. |
| `03.04.02` | A purpose still referenced by a consent cannot be deleted | The purpose is one `seedConsentViaApi` creates for itself (Consents are permanent, so this is the only way to get one genuinely referenced). Asserts the 409 conflict message and that the purpose still resolves afterward. |

### `03.05-admin-managing-purpose-versions.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `03.05.01` | Adding a new version does not change which version is latest unless "Set as latest" is checked | The checkbox defaults to checked; this test explicitly unchecks it. |
| `03.05.02` | Adding a version with a name that already exists shows the duplicate-version validation error and blocks submission | The Create button itself stays enabled - this validation is a no-op in the submit handler, not a disabled button; the real proof is the dialog staying open. |
| `03.05.03` | Setting a version as latest moves the "Latest" label to it, and its own delete action becomes enabled | Also confirms the reverse: the version just promoted away from latest becomes deletable, and the newly-latest one's own delete becomes disabled. |
| `03.05.04` | Deleting a non-latest version removes it from the version history |  |
| `03.05.05` | A version referenced by a consent cannot be deleted | The server rejects this, but the frontend shows only a generic error - see "Product bugs the tests work around" below. |

## `04-consents/` — Consent records

The largest area. **Consent creation has no UI at all**, so `seedConsentViaApi` creates the Element, Purpose, and Consent all through the admin API - none of these tests exercise the create-Element/create-Purpose forms themselves (see `02-elements/02.01-*` and `03-purposes/03.01-*` for those). `state: PENDING` is expressed by supplying `authorizations` - the v2 API rejects an explicit `PENDING`.

**49 tests, 12 spec files.**

### `04.01-user-viewing-consents.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.01.01` | The detail page renders subject, service, and purpose/element structure | Subject, service id, "Not applicable", and the element row under its expanded purpose. |
| `04.01.02` | An unknown consent id shows the load-failed message with a way back to the registry |  |
| `04.01.03` | A different user cannot open another user's consent by its URL | Ownership isolation - requires `personas.user2`. |
| `04.01.04` | The rows-per-page control caps the number of rendered rows at the selected size | Seeds one more than the smallest page size, so a next page is guaranteed regardless of how many consents already exist. |
| `04.01.05` | A rejected consent shows Rejected and no further action on a fresh detail-page load | Re-navigates after confirming, so the check is against server-persisted state, not the dialog's own optimistic update. A decision is final once made: Approve, Reject, and Revoke all disappear, replaced by "You've rejected this consent." |

### `04.02-user-searching-consents.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.02.01` | The state filter narrows the list to only the selected state | Two consents on one service id; after Clear, re-narrows to prove the *state* filter reset too, not just the service box. |
| `04.02.02` | Searching by the exact service id finds the matching consent |  |
| `04.02.03` | A service filter matching nothing shows the empty-results message |  |
| `04.02.04` | A service search for only a partial match finds nothing | Finds nothing - serviceId is an exact server-side match, unlike the catalog's substring search. |

### `04.03-user-acting-on-consents.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.03.01` | Approving a Pending consent from the list moves it to Active |  |
| `04.03.02` | Rejecting a Pending consent from its detail page moves it to Rejected |  |
| `04.03.03` | Revoking an Active consent from the list moves it to Revoked and removes the revoke action | Row reads Revoked **and** the Revoke button is gone from that row. |
| `04.03.04` | Approving from the detail page works the same way as from the list |  |
| `04.03.05` | A Rejected consent offers no approve, reject, or revoke - rejection is final | No button of any kind once Rejected; "You've rejected this consent." shows instead. |

### `04.04-admin-viewing-consents.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.04.01` | A consent created via the API appears in the admin list with its subject |  |
| `04.04.02` | An unknown consent id shows the load-failed message with a way back to the registry |  |
| `04.04.03` | The rows-per-page control caps the number of rendered rows at the selected size |  |

### `04.05-admin-searching-consents.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.05.01` | Filtering by the exact consent id shows only that consent and disables the state filter | Only that consent shown, **and the state filter is disabled**. |
| `04.05.02` | The advanced subject and service filters narrow the list |  |
| `04.05.03` | Combining the state filter with the advanced subject/service filters narrows the list further | State filter stays *enabled* with subject/service filters, unlike with consent-ID. |
| `04.05.04` | Searching by a non-existent consent id shows the load-failed message, not the empty-results one | Load-failed, not "no results" - the consent-ID path is a direct GET-by-ID that 404s. |
| `04.05.05` | A subject/service filter matching nothing shows the empty-results message | Empty-results - subject/service go through the real list-filter API. |
| `04.05.06` | The Relation filter distinguishes a consent's subject from its authorizer | Seeds a PENDING consent whose subject and authorizer are deliberately different personas, same as 04.07's delegated-consent case. |

### `04.06-admin-acting-on-consents.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.06.01` | Admin can revoke an Active consent from the list |  |
| `04.06.02` | The admin detail page shows Revoke but never Approve or Reject for an Active consent | Revoke visible; Approve/Reject absent. The admin registry never offers approve/reject. |
| `04.06.03` | The admin list shows no Approve action for a Pending consent, and no Revoke action either | Neither Approve nor Revoke offered on a Pending row. |
| `04.06.04` | The admin detail page offers only Revoke for a Pending consent, never Approve or Reject | Admin oversight can revoke a still-Pending request outright, unlike the admin list (`04.06.03`), which is deliberately left Active-only. |

### `04.07-user-viewing-consent-history.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.07.01` | Approving a Pending consent records CREATE then AUTHORIZE_APPROVE, oldest-first in the table and newest-first in the dialog | CREATE (admin) then AUTHORIZE_APPROVE (user); oldest-first in the lifecycle table, newest-first in the dialog; initial-snapshot chip on CREATE; a real diff tag on APPROVE. |
| `04.07.02` | Rejecting a Pending consent records AUTHORIZE_REJECT with a diffed authorization |  |
| `04.07.03` | A full self-service lifecycle (created, approved, then revoked) is captured in order end to end | All three entries in strict order in both views; the revoke entry renders a real diff. |
| `04.07.04` | A delegated consent (parent approving on behalf of a child) attributes the approval to the parent, not the subject | The child's own history attributes the approval to the **parent**. Requires `personas.user2`. |

### `04.08-admin-viewing-consent-history.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.08.01` | Revoking an Active consent as admin attributes CREATE and REVOKE to the admin, showing only the state transition in the diff | The diff shows only the `state` transition Active - Revoked. |
| `04.08.02` | The admin surface shows the data principal's own approval, not just admin-authored history | The admin surface shows the data principal's approval, proving the ANY-scoped endpoints return another user's history. |
| `04.08.03` | A full multi-actor lifecycle (admin creates, the data principal approves, admin revokes) is captured in order with each actor attributed correctly | Full ordering, each entry matched on **action + actor** together. |

### `04.09-consent-expiry-reconciliation.spec.ts`

Exercises `DPDPConsentExpiryReconciler`. Asserts only on API responses, but still needs a browser for seeding.

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.09.01` | A consent whose expiry time has not yet passed has no EXPIRE entry in its history | Negative control: no EXPIRE entry for a future expiry. |
| `04.09.02` | Revoking a consent past its expiry time first reconciles the lapse into an EXPIRE history entry | EXPIRE written with `actionBy=SYSTEM`, `currentStatus=EXPIRED`, in both status-audit and history. The revoke's own 409 is deliberately not asserted. |
| `04.09.03` | The background ConsentExpiryJob reconciles a lapsed consent within one scheduler cycle, with an accurate history timestamp | Waits on the real `ConsentExpiryJob` with no mutation, and checks `actionTime` falls between due and observed. Skips unless `consentExpiry.schedulerPollTimeoutMs` is set (needs `schedule_mode = "interval"` and a server restart) - CI sets this automatically. |

### `04.10-user-acting-on-delegated-consents.spec.ts`

A Delegated Consent: the subject (`personas.user`) never appears in `authorizations` - `personas.user2` is the sole named authoriser, deciding on the subject's behalf. Covers what the subject and the authoriser each see on the detail page, which `04.07.04` never asserts (it only checks history attribution).

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.10.01` | The subject of a Pending delegated consent sees only a waiting message, never a button | No entry of their own - "Waiting for authoriser approval." |
| `04.10.02` | The subject of an Active delegated consent (approved by the authoriser) sees only an approved message | Authoriser approves via `authorizeMyConsent`, not the UI - the point is the subject's resulting view. |
| `04.10.03` | The subject of a Rejected delegated consent (rejected by the authoriser) sees only a rejected message |  |
| `04.10.04` | The named authoriser, not the subject, sees Approve and Reject while the consent is Pending | Logs in as `personas.user2` via `getPersonaState`/`pageForPersonaState`. |
| `04.10.05` | The subject of a Revoked delegated consent sees only a revoked message, even though they were never a decision-maker | Authoriser approves then revokes via the API - anyone with an entry can revoke once Active, not only the subject. |

### `04.11-user-acting-on-coauthorized-consents.spec.ts`

A Co-Authorized Consent: the subject (`personas.user`) is also one of two named authorisers, alongside `personas.user2`, deciding for themself exactly like any other authoriser. First file to seed more than one `authorizations` entry - `seedConsentViaApi` already accepted a list, nothing before this exercised it. IS moves the consent to ACTIVE only once every authoriser has approved, and to REJECTED as soon as any one rejects.

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.11.01` | Both co-authorisers independently see Approve and Reject while their own decision is pending |  |
| `04.11.02` | Once one co-authoriser approves, they see a waiting message while the other still has their own Approve/Reject | Proves the gate is per-caller, not on the aggregate state, which is still Pending. |
| `04.11.03` | Once every co-authoriser has approved, the consent is Active and either of them can revoke it |  |
| `04.11.04` | A single co-authoriser rejecting ends the consent for both, immediately | The one who never decided sees the generic "This consent has been rejected.", not "You've rejected this consent." |
| `04.11.05` | Revoking a still-Pending consent leaves an authoriser who already approved seeing only the revoked message | Regression for wso2/dpdp-accelerator#271 - admin revokes while still Pending on the other authoriser; a stale APPROVED must not resurface a button. |

### `04.12-consent-creation-keeps-earlier-consents.spec.ts`

Pins the shipped `[consent_mgt] revoke_active_consents_on_create = false`. Asserts only on API responses, but still needs a browser for seeding.

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.12.01` | Creating a consent for the same subject, service and purpose leaves the earlier ACTIVE consent ACTIVE | Both consents are seeded against one catalog (`seedCatalogViaApi`) under a shared `serviceId`. States are read from the admin list filtered by `serviceId` + `purposeId`, which proves both carry that purpose; the earlier consent's status history must have no REVOKED entry. Fails on a deployment with the switch `true`, or on an Identity Server below U2 update level 17, which ignores the key. |
| `04.12.02` | Creating a consent for the same subject, service and purpose leaves the earlier PENDING consent PENDING | Same as `04.12.01` for a PENDING earlier consent - the product's auto-revoke covers PENDING as well as ACTIVE. |

## `05-authorization/` — Route guards and sidebar visibility

Tests the global mechanism - `AuthorizedRoute` plus `AppSidebar`'s scope filter. A single feature's own guard lives with that feature (08.08, 09.05).

**Not covered:** `NoAccessPage` ("No portal access") - no persona in this suite is scope-less, so it is unreachable here.

**8 tests, 2 spec files.**

### `05.01-route-redirects.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `05.01.01` | A user navigating directly to /purposes is redirected to the dashboard |  |
| `05.01.02` | A user navigating directly to /elements is redirected to the dashboard |  |
| `05.01.03` | A user navigating directly to /administration/consents is redirected to the dashboard |  |
| `05.01.04` | A user navigating directly to a Purpose detail page by link is redirected to the dashboard | `AuthorizedRoute` checks scope before any lookup by id, which is what the detail-route variants prove - the placeholder UUID is irrelevant. |
| `05.01.05` | A user navigating directly to an Element detail page by link is redirected to the dashboard |  |
| `05.01.06` | A user navigating directly to an admin Consent detail page by link is redirected to the dashboard |  |

### `05.02-sidebar-visibility.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `05.02.01` | A user's sidebar shows only the Dashboard and Consent sections | Absent items asserted with `toHaveCount(0)` - filtered out of the DOM, not hidden. |
| `05.02.02` | A Consent Admin's sidebar shows every section, including Definitions and Administration | Admin has no "My Consents" and no Consent category at all: `hideSelfConsentsForAdmins: true` means the admin is not a superset of the user. |

## `06-multi-tenancy/` — Cross-tenant data isolation

Only runs under the "multi-tenant" project - the "super-tenant" project has no second tenant to
compare against, so `playwright.config.ts` excludes this whole directory there. Reuses this run's
own per-run tenant (`consentAdminConsentApi`) and the super tenant (its own consent-admin, logged
into separately via `getPersonaState`'s explicit target override) - no dedicated throwaway tenant
is created for this.

**1 test, 1 spec file.**

### `06.01-purpose-data-isolation-across-tenants.spec.ts` · API-only

| ID | Scenario | Notes |
| --- | --- | --- |
| `06.01.01` | A Purpose created in one tenant is invisible from the other, and vice versa | Invisible in both directions (`totalResults === 0`). |

## `07-account/` — Self-service account deletion

Destructive and irreversible, so each test creates and signs in as its own throwaway user and removes it afterwards.

**5 tests, 2 spec files.**

### `07.01-user-deleting-own-account.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `07.01.01` | A user deletes their own account, or raises a request when approval is required | Accepts both deployment shapes: 204 means gone (verified against the user store), 202 means an approval request was raised and the account still exists - and the page must not claim deletion. |
| `07.01.02` | Cancelling leaves the account untouched |  |
| `07.01.03` | The self-delete scope does not authorize deleting anybody else | The point of the custom `account:self:delete` scope: the default `internal_user_mgt_delete` would have authorized deleting anyone. |

### `07.02-account-deletion-visibility.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `07.02.01` | A user is offered account deletion in the profile menu |  |
| `07.02.02` | A Consent Admin is not offered account deletion | `account:self:delete` is deliberately kept off `dpdp-consent-admin` so an admin cannot orphan a tenant. |

## `08-complaints/` — Grievance redressal

Two surfaces: the Data Principal's `/complaints` and the officer's `/complaint-management`. The officer persona is `dpdp-consent-dpo`, the only role holding the `:any` complaint scopes; `dpdp-consent-admin` holds none. Complaints are seeded via `seedComplaintViaApi`; status moves via `moveComplaintToStatusViaApi`, which hops through `WAITING_ON_CLIENT` to reach `AWAITING_INTERNAL_REVIEW` and always sends a note (a null note would blank the whole activity feed).

**Not covered:** the list's true empty state - the shared `user` persona always has history.

**44 tests, 9 spec files.**

### `08.01-data-principal-creating-complaints.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.01.01` | Submitting a complaint with a category and description shows a success banner with a reference id |  |
| `08.01.02` | Submitting without selecting a category shows a validation error and does not submit |  |
| `08.01.03` | Submitting without a description shows a validation error and does not submit |  |
| `08.01.04` | Attaching a file before submitting carries it through to the created complaint | Banner is not the "attachments failed to upload" variant, and the file appears on the created complaint. |
| `08.01.05` | "Upload files" is disabled while a file is staged, and removing it lets a different file be attached | Upload disabled while staged; removing re-enables; a second file replaces the first. |
| `08.01.06` | Cancelling the dialog discards the draft without creating a complaint | Reopening shows an empty description - the draft was discarded, not hidden. |

### `08.02-data-principal-viewing-complaints.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.02.01` | The complaint list shows reference id, category, status, submitted and updated columns |  |
| `08.02.02` | A freshly submitted complaint appears in the list with its category and Open status |  |
| `08.02.03` | Opening a complaint from the list navigates to its detail page showing the same reference id |  |
| `08.02.04` | A complaint's detail page shows its category, description, submitted date, and an empty attachments tab | Also asserts the empty-attachments message. |
| `08.02.05` | Navigating to an unknown complaint id shows the not-found state with a way back to the list |  |

### `08.03-data-principal-searching-complaints.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.03.01` | Filtering to "Open" shows an Open complaint and hides an In Progress one | Assertions are always "my complaint is/isn't in this view", never row counts. |
| `08.03.02` | Filtering to "In Progress" shows the In Progress complaint and hides the Open one |  |
| `08.03.03` | Clearing the status filter (back to "All") restores complaints of every status |  |

### `08.04-data-principal-replying-in-thread.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.04.01` | Sending a reply appends it to the activity feed |  |
| `08.04.02` | Sending a reply clears the composer's text field |  |
| `08.04.03` | The composer has no "Internal note" toggle for a Data Principal | No "Internal note" toggle in the DOM at all for a Data Principal. |
| `08.04.04` | Replying to a freshly-OPEN complaint posts the message without changing its status | Status stays Open - `onSend` attaches a `toStatus` only when the complaint is currently WAITING_ON_CLIENT. |
| `08.04.05` | Replying to a complaint the officer asked for more information on routes it back for internal review | The one auto-advance the backend allows: after the reply the chip reads Waiting on Internal Review. |
| `08.04.06` | Replying while the complaint is In Progress posts the message without changing its status | The `POST /comments` returns 200, not 201; status unchanged. |
| `08.04.07` | "Attach" is disabled while a file is staged, and removing it lets a different file be attached |  |
| `08.04.08` | Replying to a resolved complaint reopens it for internal review | RESOLVED is the one status an officer cannot manually transition out of, so a reply is the sole reopen path. Needs an explicit IN_PROGRESS hop first - OPEN to RESOLVED is not a direct transition. |

### `08.05-officer-viewing-complaints.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.05.01` | The queue table shows reference id, user, category, priority, status, SLA and updated columns |  |
| `08.05.02` | Opening a case from the queue navigates to its detail page showing the same reference id |  |
| `08.05.03` | Navigating to an unknown case id shows the not-found state with a way back to the queue |  |

### `08.06-officer-searching-complaints.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.06.01` | Filtering by status shows a matching complaint and hides a non-matching one |  |
| `08.06.02` | Filtering by priority shows a matching complaint and hides a non-matching one | DATA_BREACH auto-maps to Critical priority. |
| `08.06.03` | A resolved complaint shows in the default queue view and when filtering by "Resolved" | Regression cover for the queue once dropping resolved complaints from the default (status=All) view. Asserted on the specific row, since the "Resolved" stat tile always puts that word on the page. |
| `08.06.04` | Searching by reference id narrows the queue to that complaint | Search is server-side; all tests here still set rows-per-page to 25 first so an unsearched queue reliably shows the test's own complaint. |
| `08.06.05` | Searching by the Data Principal's name narrows the queue to that principal's complaints |  |

### `08.07-officer-replying-in-thread.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.07.01` | Sending a public reply appends it to the activity feed |  |
| `08.07.02` | Sending a reply with a status change transitions the complaint and records the transition |  |
| `08.07.03` | Only OPEN's curated next statuses (In Progress, Waiting on Client) appear in the status menu | Asserts the UI's deliberately curated subset of the backend's transition graph, not the backend's own rules. |
| `08.07.04` | Switching to "Internal note" posts a note the Data Principal never sees | Absent from the Data Principal's own timeline API response - verified against the API, not the UI. |
| `08.07.05` | Resolving requires confirmation, and cancelling leaves the complaint open and the draft intact | Status stays In Progress (verified via the API) **and** the typed draft is still in the composer. |
| `08.07.06` | Confirming the resolve dialog resolves the complaint and locks the composer |  |
| `08.07.07` | Sending a reply with a status change and an attachment transitions the complaint and uploads the file | Message, chip and attachment tile all present; composer draft and staged file both cleared. |

### `08.08-complaints-authorization.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.08.01` | A Data Principal navigating directly to /complaints is not redirected away |  |
| `08.08.02` | A Data Principal navigating directly to /complaint-management is redirected away |  |
| `08.08.03` | A Data Principal's sidebar shows a "My Complaints" entry, not the officer's "Complaints" entry | The `:self` and `:any` complaint scopes go to different roles, so the two sidebar entries never co-exist. "Complaints" still appears once, as the category heading above "My Complaints"; the officer's item is ruled out by the absent "Administration" category. |
| `08.08.04` | A DPO can reach /complaint-management directly, and their sidebar shows "Complaints", not "My Complaints" |  |
| `08.08.05` | A Consent Admin navigating directly to /complaint-management is redirected away, and their sidebar shows no complaint entry | Complaint oversight is DPO-only; `dpdp-consent-admin` is provisioned with no complaint scope. |

### `08.09-end-to-end-scenarios.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.09.01` | A citizen replying to a Resolved complaint posts the message and reopens it | The reply carries a `toStatus` of AWAITING_INTERNAL_REVIEW, the one transition `StatusTransitionValidator.java` allows out of RESOLVED, so the complaint returns to the officer's queue. |
| `08.09.02` | A multi-round officer/citizen exchange leaves the whole thread visible to both sides | Several rounds, each read from the other side, catching ordering and visibility bugs a single reply does not. |

## `09-event-notifications/` — Topics, subscriptions and events

Mixed UI and API. Two server behaviours drive most of the test design: `groupId` is silently forced to the org id on every subscription, so tests read the *returned* `groupId` back and use two topics (or disjoint purpose filters) when they need two distinct subscriptions; and `GET /events` hardcodes the caller's orgId as `GROUP_ID`, so an event published under any other group id can never be found through it at all.

**53 tests, 12 spec files.**

### `09.01-admin-managing-topics.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.01.01` | Creates a user topic through the Register Topic dialog | Row appears Active with its description. There is no success toast - the dialog closing and the row appearing are the only success signals. |
| `09.01.02` | Leaving the topic name empty shows the required-field error and blocks submission | Blocked by **native** HTML constraint validation, so the component's own custom message is unreachable; asserts `validity.valid === false`, the observable outcome. |
| `09.01.03` | Creating a topic whose name already exists is rejected case-insensitively | Rejected case-insensitively with the server's exact message; the API confirms only one row exists. |
| `09.01.04` | Topic input is trimmed before persistence |  |
| `09.01.05` | Deletes a user-created topic with no active subscriptions |  |

### `09.02-admin-viewing-topics.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.02.01` | The Topics list renders and paginates | Seeds one active topic. Deliberately not asserting a deleted row - nothing here creates one. |
| `09.02.02` | Searching by a partial topic name finds the matching row | Asserted with a filtered locator, never a loop over `rows.all()` - that snapshot approach flaked in CI twice. |

### `09.03-admin-viewing-subscriptions.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.03.01` | The subscription list renders configuration and accepts pagination |  |
| `09.03.02` | Status and delivery-mode filters narrow the list and Clear restores it | Each filter change gets its own checkpoint so two requests cannot resolve out of order onto a stale combination. |
| `09.03.03` | Searching by a partial subscription, topic, or callback value finds matching rows |  |
| `09.03.04` | Subscription details show configuration, timestamps, and deliveries | Delivery verified via the API first; a poll delivery's empty attempt-history modal opens cleanly. |
| `09.03.05` | An unknown subscription id shows load failure without leaking data |  |
| `09.03.06` | The subscriptions list and details view display multiple topic chips and support topic search | Chip expander (+1 more) displays remaining topics; details view supports associated topic search. |

### `09.04-admin-viewing-events.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.04.01` | The Events list renders publication and delivery summary data with pagination | Renders topic, group id and a "1 Subscriber" chip, with working pagination. |
| `09.04.02` | Search finds an event by a partial payload value | Proven at the API level first - the backend matches `LOWER(payload)` even though the UI placeholder advertises only id/topic. |
| `09.04.03` | Event details show exact payload, metadata, and subscription-specific deliveries | Two DISJOINT purpose filters; clipboard permission granted explicitly, or Chromium's default denial would look like a product bug. |
| `09.04.04` | An event with no matching subscribers shows the no-deliveries state |  |
| `09.04.05` | An unknown or cross-tenant event id is not exposed | Unknown **and** cross-tenant ids both load-fail, with the other tenant's topic name never appearing. |

### `09.05-event-notifications-authorization.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.05.01` | A Consent Admin sees and can open Events, Topics, and Subscriptions |  |
| `09.05.02` | A Data Principal without Event Notification scopes cannot access event routes | Nav items absent from the DOM, every event route redirects, and all three list APIs return 403. |
| `09.05.03` | A token without write scopes cannot perform write operations | Documents explicitly that this environment has no read-only persona, so true read/write scope separation is unprovable here. |
| `09.05.04` | Missing, expired, or wrong-tenant tokens cannot access Event Notification APIs | Another tenant's valid token replayed against the super tenant is refused - with an honest caveat that the missing token-binding cookie may be what is rejected. |

### `09.06-topic-lifecycle-api.spec.ts` · API-only

Server-side rules the Topics UI cannot reach.

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.06.01` | A topic with a live subscription cannot be deleted | 409 "has active subscriptions"; the topic stays Active. |
| `09.06.02` | Deleting the same topic twice does not mutate it again |  |
| `09.06.03` | Re-registering a previously deleted topic name creates a new topic | A new topic id; the old row stays Deleted. |
| `09.06.04` | Any topic linked to a multi-topic subscription cannot be deleted until the subscription is deleted | 409 "has active subscriptions" on all associated topics until subscription row is deleted. |

### `09.07-subscription-lifecycle-api.spec.ts` · API-only

Register conflicts, re-verification, and delete guards.

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.07.01` | An overlapping subscription with equivalent purposes and callback URL is rejected | Same canonicalized callback URL and equivalent purposes (different case/order/duplicates). |
| `09.07.02` | The same tenant/group/topic cannot mix webhook and poll delivery modes | Rejected in both orders. |
| `09.07.03` | An active or deleted subscription cannot be re-verified |  |
| `09.07.04` | Deleting a subscription soft-deletes it while preserving its record | A soft delete: status becomes `deleted` but the record and its delivery list stay readable. |
| `09.07.05` | A subscription with a pending delivery cannot be deleted | 409 EN-4090; the subscription stays active. |
| `09.07.06` | Deleting an already-deleted subscription returns not found |  |
| `09.07.07` | Registering with empty topics or duplicate topic names is rejected | 400 with descriptive error message. |
| `09.07.08` | Registering with a non-existent or inactive topic is rejected | 404 with topic not active in organization error. |
| `09.07.09` | Multi-topic subscriptions containing user lifecycle topics require the all purpose filter | 422 with lifecycle topic filter requirement error. |
| `09.07.10` | Delivery mode conflict is rejected when any topic overlaps in the same group | 409 across shared topic associations. |

### `09.08-publishing-events-api.spec.ts` · API-only

`POST /events`. There is no publish-event screen anywhere in the portal.

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.08.01` | Publishing an event creates matching delivery records atomically | Readable from the event side and the subscription side, with the payload marker intact. |
| `09.08.02` | Publishing without a group-id header is rejected |  |
| `09.08.03` | Publishing to an unknown or deleted topic is rejected |  |
| `09.08.04` | A null or missing payload is rejected rather than treated as an empty object | 422 EN-4002 - not silently treated as `{}`. |
| `09.08.05` | An ALL-filter subscription receives every event regardless of purposes | No/one/many purposes, exactly one delivery each. |
| `09.08.06` | SPECIFIC purpose matching is case-insensitive and requires overlap | Overlapping purposes deliver; unrelated ones do not. |
| `09.08.07` | ALL_EXCEPT matches only when the event carries a purpose outside the exclusion set |  |
| `09.08.08` | A multi-topic subscription receives fan-out deliveries across all registered topics | Events on multiple topics match and deliver to the same subscription. |

### `09.09-event-queries-api.spec.ts` · API-only

Query and delivery-scoping rules on the read endpoints.

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.09.01` | The subscriptionId filter returns only events delivered to that subscription | Checked in both directions. |
| `09.09.02` | A delivery id belonging to another subscription can't be read through the wrong subscription path | 404 through the wrong subscription, OK through the right one. |

### `09.10-webhook-delivery-api.spec.ts` · API-only

Every test needs a network-reachable receiver (`webhook.receiverHost`). `09.10.01`/`09.10.02`/`09.10.03` also
need `webhook.baseBackoffSecondsOverride`/`maxRetriesOverride`. See "Known gaps" for the
stuck-in-flight reclamation case removed from this file.

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.10.01` | A non-2xx response records failure and retries with the same delivery id | **Skipped unless `webhook.baseBackoffSecondsOverride`/`maxRetriesOverride` are set** - real elapsed time scales with those values. |
| `09.10.02` | Persistent receiver failure transitions the delivery to failed | Same opt-in as `09.10.01` - exhausts every retry, so scales with `maxRetriesOverride` too. |
| `09.10.03` | Manual retry on a failed delivery dispatches a new attempt and re-exposes retry when it fails | Same opt-in as `09.10.01` - triggers manual retry on a failed delivery, executes retry attempt, and verifies retry remains available upon repeated failure. |

### `09.11-tenant-isolation-api.spec.ts` · API-only

Two independently provisioned throwaway tenants; `TENANT.ORG_ID` is the isolation boundary.

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.11.01` | Tenants with the same topic name receive separate topic identities and lists | Different topic ids and disjoint lists. |
| `09.11.02` | Tenant A cannot read, delete, verify, or list history for tenant B resources | 404 - never 403 - on every cross-tenant operation, and tenant B's own view is unaffected. |
| `09.11.03` | A newly created tenant receives Event Notification authorization and default topics | The five system topics exist as active/system, and the owner can create user topics and subscriptions with no manual API-resource registration. |

### `09.12-deadlock-repro.spec.ts` · API-only

Concurrency stress test that reproduces the MySQL InnoDB deadlock between `POST /events` and
`POST /subscriptions`. Runs 3 concurrent pairs of publish/subscribe loops against one shared topic
to trigger the AB-BA lock-order inversion. Fails on an unpatched MySQL deployment; passes on
PostgreSQL, H2, and any deployment where the `ACTIVE_NAME` index fix is applied.

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.12.01` | Concurrent publish and subscribe must not produce EN-5001 (MySQL deadlock) | 3 worker pairs × 30 publish + 30 subscribe iterations; asserts zero HTTP 500 responses on POST /events. |


## `10-dashboard/` — Dashboard counts and links

What the dashboard renders, and nothing else: every assertion is on its cards and links; API calls
are setup plus setup-precondition checks. Every exact count runs as a throwaway account created for
that one test (the `throwawayAccounts` fixture), because only an account nothing else writes to
has predictable totals - the one deliberate exception to "no totals on shared data". Cards are
located by their `data-stat` attribute, pinned by `DashboardPage.test.tsx`.

**9 tests, 4 spec files.**

### `10.01-user-dashboard-consent-counts.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `10.01.01` | A new user sees zero in every consent and complaint status card | Also asserts the internal-review card reads **Waiting on DPO** and that the removed Total cards are gone. |
| `10.01.02` | Each consent status card counts the user's own consents in that state | Subject-only consents, no authorizations: 2 ACTIVE, 1 REJECTED, 1 revoked by the user, 1 lapsed. The lapsed one needs no expiry job - IS resolves EXPIRED at read time for an ACTIVE/PENDING receipt past `EXPIRY_TIME` - so it is seeded first with a 5s expiry and polled via the API until EXPIRED. |
| `10.01.03` | "View all consents" opens My Consents | Cached `user` persona - asserts no counts. |

### `10.02-user-dashboard-consent-relations.spec.ts`

Two throwaway accounts, U and V, whose dashboards are both asserted; the `user` persona is an
unrelated third party. Guards #273/#274 (the dashboard once sent no `relation`, and IS defaults to
SUBJECT).

| ID | Scenario | Notes |
| --- | --- | --- |
| `10.02.01` | The dashboard counts every consent the user is the subject or an authorizer of, once each, and no others | Nine consents covering every relation: own; own but decided by V; V's decided by U; U as subject and authorizer; U as subject and one of two authorizers; a third party's with U and V as authorizers; V's with U as a `PARENT` authorizer; a third party's; V's own. U expects Pending 6 / Active 1, V Pending 5 / Active 1 - distinct so a missed relation, a double count, or a leaked unrelated consent each change a number. |
| `10.02.02` | Consent state changes by any party are reflected on every party's dashboard | One of two authorizers approving keeps it Pending for both; both approving makes it Active; one rejecting makes it Rejected while the other is still undecided; an authorizer revoking shows Revoked to the subject; a pending consent lapsing shows Expired to both. The revoke comes from the authorizer, not the subject - see "Product bugs the tests work around". Each ends in a different state, so each card maps to one scenario. |

### `10.03-user-dashboard-complaint-counts.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `10.03.01` | Each complaint status card counts only the user's own complaints in that status | One throwaway complaint per status (RESOLVED via IN_PROGRESS - OPEN -> RESOLVED isn't a direct transition), plus an OPEN complaint by the `user` persona that must not count. Seeded one at a time - see "Product bugs the tests work around". |
| `10.03.02` | "View all complaints" opens My Complaints | Cached `user` persona. |

### `10.04-admin-dashboard.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `10.04.01` | An admin sees tenant-wide consent status cards and Purposes/Elements counts | Each card loads a real value (`^\d+\+?$`), not an exact one - see "What this suite cannot verify". No Total card, no complaints section. |
| `10.04.02` | "View all consents" opens the admin consent registry |  |

---

# Known gaps

Coverage that is absent, and why. Kept here so a gap is never mistaken for a passing feature.

## The complaints API suite was removed upstream

Commit `e278405` ("Sync tests with the source", 2026-08-31, PR #65) deleted
`tests/06-complaints-api/` in full - 6 spec files, 53 tests - and one UI end-to-end test. Almost
all of it is still covered, one layer down or through the UI:

- **Java unit tests** in the complaint `service` and `endpoint` modules cover the rules those API
  tests checked: create and comment validation (including the 5000-character limit), status
  transitions and the note required to resolve, attachment limits, content types, sizes and the
  `isPublic` download boundary, ownership (a second user's read, comment, transition, upload and
  download), officer-assisted intake, unknown filter values returning 422, and pagination.
- **`08-complaints/`** covers the same flows through the portal: creation and required fields,
  status and priority filtering (`08.06.01`, `08.06.02` - DATA_BREACH maps to Critical), the
  internal-note boundary (`08.07.04`, checked against the citizen's own timeline API), transitions
  (`08.04.05`, `08.07.02`), reopening a resolved complaint (`08.04.08`, `08.09.01`) and the
  unknown-id path (`08.02.05`).

### What is genuinely uncovered

| Gap | Why it matters |
|---|---|
| **API-level auth** | A missing or malformed bearer token returning 401, and a Data Principal's token refused by the officer API with 403. The Identity Server enforces these through `deployment.toml`'s `[[resource.access_control]]` rules, not Java code, so only a test against a running server can check them. `08.08.02` covers only the UI redirect. |
| **Concurrent replies** | A citizen and an officer replying at nearly the same time both landing in the timeline. Needs a real database. |

One product observation the deleted tests recorded: a Data Principal **can** resolve their own
complaint by posting a comment with a `toStatus`, but **never** through the status-only endpoint,
because that path always passes an empty note and `RESOLVED` requires one. Whether that asymmetry
is intended is a product question.

## No webhook happy-path coverage

`09.10-webhook-delivery-api.spec.ts`'s three core success-path tests (payload envelope and
integrity headers, HMAC signature verification, 2xx-marks-delivered) were deleted — they were
unreliable on a machine whose LAN IP changes mid-session. `09.10.01`/`09.10.02` cover retry and
retry-exhaustion behavior instead, opt-in via `webhook.baseBackoffSecondsOverride`/
`maxRetriesOverride` (see AGENTS.md, "Webhook-dependent tests") since the real product defaults
make them take minutes - CI sets both automatically, so these two run on every E2E workflow. Its
third case, stuck-in-flight reclamation, was removed rather than implemented - see "What this
suite cannot verify".

## Smaller gaps

| Gap | Why |
|---|---|
| `NoAccessPage` ("No portal access") | No persona in this suite is scope-less, so the page is unreachable |
| The complaint list's true empty state | The shared `user` persona always has history |
| Event Notification read-only vs. write scope separation | No role holds only some `notifications:*` scopes - `dpdp-consent-admin` holds all of them, `dpdp-consent-user` and `dpdp-consent-dpo` none (`09.05.03` documents this explicitly) |

## What this suite cannot verify

**Exact admin dashboard counts.** The admin dashboard counts the whole tenant, which every parallel
test writes to - and on the super tenant, every earlier run too - so no exact or before/after value
is stable. `10.04.01` checks every card loads a real value instead. Considered and rejected: a
Playwright project that runs last (there is still no known expected value, and a dependent project
is skipped whenever any test it depends on fails) and a dedicated throwaway tenant (slow Console-UI
setup for no counting logic the user dashboard's `10.01`-`10.03` don't already prove). The admin
view's query shape is pinned by `DashboardPage.test.tsx`.

**Two E2E cases removed rather than skipped** (listed in the header table). Both are unreachable
from a black-box HTTP test, and both are covered one layer down by Java unit tests with a mocked
DAO - so removing the dead `test.skip()`'d placeholders loses no real verification.

**`09.08`'s fan-out persistence rollback case.** Forcing a `DELIVERY` insert to fail
mid-transaction, purely to prove the whole publish rolls back atomically, has no trigger reachable
through legitimate API calls - it would mean shipping production code whose only purpose is to be
exploitable by a test, which stays out of scope here. Covered instead by
`EventPublishTransactionAtomicityTest.testFanOutFailureCausesEventPublishToFailWith500`
(`event.notifications.service` module, forces the fan-out DAO call to throw and asserts the whole
publish fails with a 500) and `DatabaseUtilsTest` (`common` module, verifies the transaction
wrapper genuinely calls `connection.rollback()` on failure and never on success).

**`09.10`'s stuck-in-flight delivery reclamation case.** The original plan for this one turned out
to be wrong, not just risky - worth recording so it isn't tried again the same way. `claim`/
`reclaim`/`complete` (`EventNotificationCommonDBQueries.java`'s `getClaimWebhookDeliveryQuery`/
`getClaimStuckWebhookDeliveryQuery`/`getUpdateWebhookDeliveryStatusQuery`) really are optimistic on
`STATUS`, keyed only on elapsed time (`stuck_inflight_threshold_seconds`), and the webhook HTTP
call really does have a fixed 5s timeout (`WEBHOOK_HTTP_TIMEOUT_SECONDS`) - that part of the
analysis was correct. What it missed: `DeliveryRecoveryService.activate()` throws
`IllegalStateException` at server startup if `stuck_inflight_threshold_seconds <=
WEBHOOK_HTTP_TIMEOUT_SECONDS` ("...so an active webhook request cannot be reclaimed") - a
deliberate, hard-enforced product invariant guaranteeing the exact race this test wanted to
construct can never be configured, not merely one that's hard to time. Confirmed live: setting
`stuck_inflight_threshold_seconds = 1` in CI crashed the event-notification service bundle's
activation entirely, cascading into unrelated startup failures (`SubscriptionEndpoint` failing to
instantiate, super-tenant role provisioning failing) across three consecutive CI runs before the
real cause was found. Covered instead by `DeliveryRecoveryServiceTest.
activationRejectsStuckThresholdAtHttpTimeout` (asserts this exact guard),
`WebhookDeliveryWorkerStuckRecoveryTest`, and `WebhookDeliveryWorkerTest.
testEmptyPendingTriggersStuckPass` (`event.notifications.service` module, all with a mocked DAO).

---

# Product bugs the tests work around

Real defects that dictate how tests above are written. Recorded here so nobody
"fixes" a test that is correctly encoding a bug.

| Bug | Effect on the tests |
|---|---|
| **`GET /events` hardcodes the caller's orgId as `GROUP_ID`** and does not even declare a `groupId` query param. An event published under any other group id can never be found through `GET /events`, whatever the search term. | Every event test reads a seeded subscription's *returned* `groupId` and publishes with that exact value. |
| **`SubscriptionHandler.createSubscription` silently forces `groupId` to the org id**, ignoring what the caller sent. Fan-out matches on exact `(ORG_ID, GROUP_ID, TOPIC_ID)`. | Two subscriptions on one topic are always "the same group", so tests needing two distinct subscriptions use two topics or disjoint purpose filters. |
| **Consent mutations do not invalidate the history query keys.** | `03.07`/`03.08` navigate a second time after each action, or the lifecycle card and dialog show stale data. |
| **Creating a v2 consent auto-revokes the same subject/service/purpose's ACTIVE and PENDING consents without firing `pre/postRevokeConsent`** (product-is#28405), so the accelerator's status audit and history never record the revoke. | The accelerator ships `revoke_active_consents_on_create = false`; `04.12` pins that. |
| **`CM_RECEIPT.LANGUAGE` is `NOT NULL` with no server-side default**, so omitting it yields a generic `CM_00084` wrapping an H2 constraint violation. | `seedConsentViaApi` always sends `language: 'en'`. |
| **Deleting a Purpose version referenced by a consent is rejected server-side, but `PurposeDetailsPage.tsx`'s `deleteVersionErrorMessage` treats every failure as unexpected** and shows a generic "Something went wrong" message - unlike the whole-Purpose delete, which has its own "still referenced by one or more consents" text. | `03.05.05` asserts the generic text, since that is what the product actually shows. |
| **`ComplaintActivityFeed.tsx` calls `entry.message.trim()` with no null guard**, blanking the whole feed for any complaint whose timeline holds a note-less status change. | `moveComplaintToStatusViaApi` always sends a note, even where the API does not require one. |
| **`TopicRegisterDialog.tsx`'s custom "Topic name is required." branch is unreachable** — the form has no `noValidate` and the field is natively `required`, so the browser blocks submit before React sees it. | `09.01.02` asserts `validity.valid === false`, the observable outcome. |
| **A subject's self-service revoke of a consent that has authorizers returns success and changes nothing.** `/me/consents/{id}/revoke` is IS's `authorizeConsent(id, caller, REVOKED)`: with authorizations present it updates only the caller's own authorization row, and a subject who isn't an authorizer has none, so the UPDATE matches nothing and the recomputed state is unchanged. | `10.02.02` revokes through the authorizer; `revokeConsentViaApi` re-reads the state so a silent no-op fails as setup. |
| **Two tenant creations at the same moment can fail one of them with a 500.** `POST /api/server/v1/tenants` returns `TM-65002`: during `onTenantCreate`, IS's OIDC-scope setup (`OAuth2Util.initiateOIDCScopes`) hits a `ConcurrentModificationException` in `ClaimDAO.addClaimProperties` when another tenant is being created - wso2/product-is#28519. | `ConsoleRootOrganizationWizard.createTenant` holds a cross-process lock (`utils/crossProcessLock.ts`) around the create request, so no two workers ever create a tenant at once. Remove it once the IS bug is fixed. |
| **Simultaneous complaint creates in one org can fail with `CO-5000`.** The reference ID is count-then-insert (`ComplaintServiceUtil.generateReferenceId`); `createComplaint` retries a duplicate with a fresh ID, but a burst of creates recounting the same rows exhausts `MAX_REFERENCE_ID_ATTEMPTS`. | `10.03.01` seeds its complaints sequentially. |

---

# Known flakiness

**Worker count is the dominant factor.** Playwright's own CPU-based default (half the detected
cores) drives one full Chromium instance per worker alongside WSO2 IS and MySQL on the same
machine — measured directly: a full-suite run at 4 workers on an 8-core machine produced a wide,
inconsistent spread of session and API-auth failures (`401`s on cached-token API calls, timeouts
inside `page.waitForRequest`/fixture setup, occasional browser-context crashes), while repeated
full-suite runs at 2 workers came back completely clean (0 failed, 0 flaky) on the same
environment. `playwright.config.ts` now caps local workers at 2 for exactly this reason — see its
own comment. The older "one run failed 18 tests with `401` on API seeding... unreproduced and
unexplained" note this section used to carry was very likely this same cause, just not yet
isolated to worker count at the time it was written.

**`04.05.06`'s old "sometimes fails" was not flakiness — it was two real, deterministic bugs**,
both since fixed (see `pages/AdminConsentPage.ts`'s `filterByUserRelationAndService` and
`clearAllFilters`): a Relation-filter query that could fall off its own default page once a shared
persona's consent count passed one page, and a filter-panel remount race after "Clear all" that
could silently wipe a just-typed value. Verified with 8 consecutive clean runs (zero retries)
after the fix, versus a measured ~30-60% failure rate before it.

**CI fails a flaky run.** A test that fails and then passes on its retry still fails the run
(`failOnFlakyTests` in `playwright.config.ts`); the report marks it flaky rather than failed.
Local runs keep passing on a retry.

Run with `--workers=1` to distinguish a real failure from a flake.

---

# What this suite does well

Worth stating, since everything above is a gap or a caveat:

- **Assertions are honest about the product.** Several tests deliberately pin *current* behaviour
  and say so when it differs from what the backend supports — `09.01.02`'s unreachable validation
  message, `04.05.04`'s load-fail-vs-empty-results distinction.
  That is the right call for a regression suite.
- **Claims are verified, not assumed.** Comments record what was observed in practice:
  the exact-vs-substring semantics of each filter, the forced `groupId`, the `GET /events` bug,
  SCIM2's tenant limitation.
- **Negative assertions use `toHaveCount(0)`**, matching how the sidebar and action buttons behave
  — removed from the DOM, not hidden.
- **Setup goes through the real UI only when the test is exercising that UI, or has no API
  alternative** (event publishing). Incidental fixture data - Elements/Purposes/Consents created
  purely so some other feature has something to act on or page through, never to test creation
  itself - goes through the admin API instead (`seedConsentViaApi`, the two rows-per-page pagination
  seeds in `02-elements/02.02-*` and `03-purposes/03.02-*`): faster, and avoids exercising the
  same create-form flow dozens of times per run for no additional coverage.
- **Known flakiness is measured**, not hand-waved.
