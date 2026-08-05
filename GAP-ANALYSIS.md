# Gap Analysis — OpenFGC Consent Portal vs DPDP Accelerator Consent Portal

The DPDP accelerator's consent portal was migrated from the OpenFGC portal and trimmed to fit WSO2
Identity Server 7.3's consent model. This catalogues what was lost, what can be rebuilt, and what
cannot.

**Baseline** — the OpenFGC portal as users experienced it: `portal/frontend` and its Go BFF at
`portal/backend` in the OpenFGC repository, branch `feature/portal`. Paths below given relative to
that repository are prefixed *OpenFGC*.
**Subject** — `dpdp-accelerator/react-apps/consent-portal` in this repository. All other paths are
relative to the repository root.

## How to use this document

Each gap is self-contained: pick one, read its section, implement it. Ordered by DPDP compliance
value, then effort. Feasibility tiers:

| Tier | Meaning |
|---|---|
| **T1** | Implementable on stock IS 7.3 today. Endpoints verified against a live pack. |
| **T2** | Implementable with a portal-side workaround. Works, but read the caveat before committing. |
| **T3** | Blocked. Requires a change to `carbon-consent-management` upstream. |

Everything marked T1 below was **executed against a running IS 7.3 instance** while writing this
document — the request/response shapes are observed, not inferred from the spec.

## Summary

| # | Gap | Tier | Compliance | Effort |
|---|---|---|---|---|
| A1 | Consent audit / status history | T3 | **Critical** | — |
| A2 | Per-element (granular) consent | T3 | **Critical** | — |
| A3 | Purpose authoring (create / version / set-latest / delete) | T1 | High | L |
| A4 | Element authoring (create / delete) | T1 | High | M |
| A5 | Consent properties — display, edit, search | T1 | High | M |
| A6 | Expiry management | T1 | Medium | S |
| A7 | Consent validation | T1 | Medium | S |
| B1 | Admin consent creation | T1 | Low | M |
| B2 | Authorization state override | T1 | Medium | S |
| B3 | Authorization `resources` + modal | T3 | Low | — |
| B4 | Purpose filter on consent lists | T1 | Medium | M |
| B5 | Multi-value state filter | T2 | Low | S |
| B6 | Sort controls | T2/T3 | Low | M |
| B7 | Date-range filter | T2/T3 | Medium | M |
| B8 | Exact totals / numbered pagination | T2 | Low | M |
| B9 | Bulk element creation | T2 | Low | S |
| C1 | `groupId` scoping | T3 | Medium | — |
| C2 | Element versioning / namespace / type / schema | T3 | Medium | — |
| C3 | Recurring, frequency, lookback duration | T2 | Medium | M |
| D1–D5 | UX parity items | T1 | — | S each |
| E1–E5 | Housekeeping | T1 | — | S each |

---

# A. Compliance-critical

## A1 — Consent audit / status history
**T3** · Compliance: **critical** · Effort: n/a (blocked)

**What OpenFGC did.** Two mechanisms. Every consent carried `statusHistory[]`
(`ConsentStatusAuditItem{statusAuditId, previousStatus, currentStatus, actionTime, actionBy, reason}`),
returned when `GET /consents/{id}?includeStatusHistory=true`. Separately
`GET /consents/{id}/history?includeSnapshots=true` returned `ConsentHistoryItem{historyId, actionTime,
actionBy, reason, snapshot}` where `snapshot` was a **complete pre-mutation copy of the consent**.
The portal rendered `features/consent-registry/components/details/ConsentLifecycleSection.tsx`: a table
of Event Type / Date / Time / Description, sorted ascending by `actionTime`, each row prefixed with an
8px status dot (ACTIVE→success, CREATED→warning, REJECTED/REVOKED→error, EXPIRED→disabled).

**What we have now.** Nothing. `AdminApiServlet.translate` explicitly returns `null` for any path
ending `/history`. `ConsentDetailsPage.test.tsx` asserts *"renders no consent lifecycle history section"*.

**IS 7.3 support.** None. There is no history endpoint in consent-mgt v2 and no audit table in the
consent schema (`dbscripts/migrations/consent/*-migration.txt` adds `CM_CONSENT_AUTHORIZATION`,
`CM_PURPOSE_VERSION*` and cursor columns — no audit/history table).

**Upstream change required.** `carbon-consent-management` needs to persist state transitions and
expose them. Minimum viable shape:
```
GET /api/identity/consent-mgt/v2.0/consents/{consentId}/history
  → { history: [ { previousState, currentState, actionTime, actionBy, reason } ] }
```
A new table `CM_CONSENT_STATE_AUDIT(consent_id, previous_state, current_state, action_time, action_by,
reason)` written from the same code path that mutates `CM_RECEIPT.STATE`.

**Why this matters most.** DPDP requires a Data Fiduciary to *demonstrate* that valid consent was
obtained and to show its lifecycle. Without an audit trail the portal cannot evidence when consent was
given, withdrawn, or by whom. Everything else in this document is a feature gap; this one is a
compliance gap. **Raise upstream first.**

Related IS defect found while testing: revoking a non-revokable consent returns
`CM_00112 "Cannot authorize consent: only PENDING consents may be authorized"` — the message says
*authorize* for a *revoke* call. Cosmetic, but worth reporting with A1.

## A2 — Per-element (granular) consent
**T3** · Compliance: **critical** · Effort: n/a (blocked)

**What OpenFGC did.** Elements carried a per-consent `approved` boolean. The approval dialog let a data
principal tick individual optional elements; mandatory ones were forced true. The BFF
(`internal/me/service.go:213 BuildApprovalUpdatePayload`) rebuilt the whole consent, matching client
selections on the 4-tuple `(purposeId, purposeVersion, elementId, elementVersion)` and rejecting the
request outright if any selection matched nothing. The detail page showed a
`{approved}/{total} approved` chip per purpose and Approved / Required columns per element.

**What we have now.** Whole-consent Approve/Reject only. `MyConsentsServlet` deliberately ignores the
request body. `types/consent.ts` documents that consent-level elements carry no approval flags.

**IS 7.3 support.** None. `POST /api/users/v1/me/consents/{id}/authorize` takes only
`{"state":"APPROVED"|"REJECTED"}` for the entire consent.

**Upstream change required.** Either extend the authorize body to accept per-element decisions:
```
POST /consents/{id}/authorize
  { "state": "APPROVED", "elements": [ { "id": "...", "approved": true } ] }
```
or add an `approved` column to the receipt↔element association and expose it on the consent DTO.

**Why this matters.** DPDP requires consent to be *specific* — a free, specific, informed and
unambiguous indication for **each** purpose. Approving a consent wholesale is a genuine weakening of
that. Note the partial mitigation available today: purposes are separate consent records, so
per-*purpose* granularity already exists; only per-*element* granularity is lost. Say so explicitly
when discussing compliance posture.

## A3 — Purpose authoring
**T1** · Compliance: high · Effort: L

**What OpenFGC did.** `features/catalog/components/PurposeFormDialog.tsx` — create purpose and create
new version. "Add purpose" button on the list (gated on `PURPOSES_WRITE`), a version selector and
"Create new version" on the detail page, and delete-version with navigate-away when the last version
goes.

**What we have now.** Read-only. `catalogApi.ts` exposes no writes and `CatalogApi.test.ts:118`
asserts that.

**IS 7.3 support — verified live:**
```
POST   /purposes                        {name*, type*, version*, description, elements[{id, mandatory}], properties}
POST   /purposes/{id}/versions          {version*, setAsLatest, description, elements, properties}
PUT    /purposes/{id}/versions/latest   {id}
DELETE /purposes/{id}
DELETE /purposes/{id}/versions/{versionId}
```
Observed create response:
```json
{"id":"2eee...","name":"gap-check-purpose","description":"probe","type":"CONSENT",
 "latestVersion":{"id":"d36a...","version":"1.0.0"},
 "elements":[{"id":"e12b...","name":"gap-check-el","displayName":"Gap Check","mandatory":true}],
 "properties":{"lawfulBasis":"consent"}}
```

**Implementation.**
- **No BFF change needed.** `AdminApiServlet.translate` already maps `/consent-purposes/**` →
  `/purposes/**` and `service()` already allows POST/PATCH/DELETE.
- Add writes to `frontend/src/features/catalog/api/catalogApi.ts`: `createPurpose`,
  `createPurposeVersion`, `setLatestPurposeVersion`, `deletePurpose`, `deletePurposeVersion`.
- New `features/catalog/components/PurposeFormDialog.tsx`. Fields: Name (required), Type (required —
  IS accepts free text; use a select seeded from existing purposes' `type` values), Version (required,
  free text — IS does **not** auto-increment, unlike OpenFGC), Description, element picker with a
  per-element Mandatory toggle, and the property editor from D5.
- Wire "Add purpose" into `PurposeListPage.tsx` behind `ScopeGuard` on `PORTAL_SCOPES.PURPOSES_WRITE`,
  and version actions into `PurposeDetailsPage.tsx`.
- Invalidate `['catalog','purposes']` and `['catalog','purpose', id]` on success.

**Prerequisites.** The signed-in user needs `internal_consent_mgt_purpose_create` /`_update`/`_delete`,
which `register-portal-app.sh` already authorizes and the `dpdp-consent-admin` role already grants;
`ScopeMapper` already maps them to `portal:purposes:write`.

**Caveats.**
- **Version strings are caller-supplied**, not auto-incremented. Validate uniqueness client-side and
  surface IS's 409 cleanly.
- `DELETE /purposes/{id}` returns **409 `CM_00079`** — *"Purpose Id: N is associated with one or more
  receipt(s)"* — once any consent references it. Verified. Present delete as a best-effort action and
  render the 409 description.
- A new version created without `elements` comes back with `"elements":[]` — the element list is **not**
  inherited from the previous version. The dialog must pre-populate from the version being copied.

**Verification.** Create a purpose with one mandatory element; confirm it appears in the list and its
detail shows the Mandatory chip; add a v2 with `setAsLatest:true` and confirm the Latest chip moves;
attempt to delete a purpose bound to a consent and confirm the 409 message is shown to the user.

## A4 — Element authoring
**T1** · Compliance: high · Effort: M

**What OpenFGC did.** `ElementFormDialog.tsx` — dual mode (create / new version) with Name, Type
toggle (Basic/JSON/XML), Namespace mode toggle (Default/Custom), Display name, Description, a Schema
field required for JSON/XML, and a property editor. Bulk creation via a JSON array payload.

**What we have now.** Read-only list and detail.

**IS 7.3 support — verified live:**
```
POST   /elements       {name*, displayName, description, properties}
DELETE /elements/{id}
```
Observed response: `{"id":"e12b...","name":"gap-check-el","displayName":"Gap Check","description":"probe","properties":{"pii":"true"}}`

**Implementation.** Add `createElement` / `deleteElement` to `catalogApi.ts`; new `ElementFormDialog`
with **only** Name, Display name, Description and properties. Wire "Add element" into
`ElementListPage.tsx` behind `PORTAL_SCOPES.ELEMENTS_WRITE`.

**Caveats.**
- **Drop Type, Namespace and Schema entirely** — IS has no such fields (see C2). Do not add UI that
  pretends otherwise.
- Creation is one-at-a-time; see B9 for bulk.
- Deletion of a referenced element returns 409, same pattern as purposes.

## A5 — Consent properties (OpenFGC "attributes")
**T1** · Compliance: high · Effort: M

**What OpenFGC did.** `attributes` (`map[string]string`) on every consent, plus a dedicated search
`GET /consents/attributes?key=X[&value=Y]` returning matching consent IDs.

**What we have now.** `ConsentDetail.properties` exists in the type but is **never rendered**, never
edited, never searched.

**IS 7.3 support — verified live.** `properties` is accepted on `POST /consents`, returned on
`GET /consents/{id}`, replaced wholesale by `PATCH /consents/{id}`, and searchable with the SCIM-like
`filter` parameter:
```
PATCH /consents/{id}   {"properties":{"noticeVersion":"n2","channel":"web"}}   → 200
GET   /consents?filter=properties.channel eq web                              → 1 match
```
Supported filter operators: `eq`, `sw`, `co`, `ew`, combinable with `and` / `or`.

**Implementation.**
- Render a Properties card on `ConsentDetailsPage` (mirror `PurposeDetailsPage`'s existing properties
  card — reuse that markup).
- Admin edit: a dialog PATCHing `properties`; **send the complete map**, since PATCH replaces rather
  than merges.
- Search: add a property key/value pair to `AdminConsentFilters`, mapping to
  `filter=properties.<key> eq <value>`. This is the natural home for the DPDP notice metadata that
  C1/C3 can't store elsewhere.

**Caveats.** `properties` replaces the whole map — read-modify-write or you will silently drop keys.
Values are strings only.

## A6 — Expiry management
**T1** · Compliance: medium · Effort: S

**What OpenFGC did.** `expirationTime` on create/update; "Valid Until" on the detail card, with
`0` meaning **never expires**.

**What we have now.** `expiryTime` displayed read-only as "Valid Until", falling back to
"Not applicable". No way to set or change it.

**IS 7.3 support — verified live.** `expiryTime` (epoch ms) on `POST /consents`, changeable via
`PATCH /consents/{id}`, and `GET /consents/{id}/validate` lazily transitions an expired consent.

**Implementation.** A "Set expiry" action on the admin consent detail, PATCHing `{"expiryTime": <ms>}`.
Reuse `utils/dateTime.ts` helpers.

**Caveats — verified, and they matter:**
- `{"expiryTime": null}` in a PATCH is **ignored** (treated as field-omitted). The existing value survives.
- `{"expiryTime": 0}` does **not** mean "never" as it did in OpenFGC — it sets the expiry to epoch 0,
  and the consent immediately becomes **EXPIRED**. Confirmed: a consent PATCHed to `0` read back as
  `state: EXPIRED`.
- Therefore **there is no way to clear an expiry once set.** Do not offer a "remove expiry" control.
  If clearing is required it is an upstream ask.
- An EXPIRED consent cannot then be revoked (409), so an accidental `0` is a one-way door.

## A7 — Consent validation
**T1** · Compliance: medium · Effort: S

**What OpenFGC did.** `POST /consents/validate` — a gateway authorization hook taking the inbound
request's headers/payload/resource and returning `isValid` plus an enriched consent representation.
Never surfaced in the portal UI, but allowlisted in the BFF.

**IS 7.3 support — verified live.** `GET /consents/{consentId}/validate` → `{"state":"ACTIVE","expiryTime":1785989010826}`.
Much narrower than OpenFGC's: no request-context evaluation, no mandatory-element check.

**Implementation.** A "Check validity" action on the admin consent detail showing the live state and
expiry, useful because it also forces the lazy expiry transition. Add `/consents/{id}/validate` — it
already passes through `AdminApiServlet` unchanged.

**Caveat.** Do not present this as an API-gateway authorization primitive; it is a status probe.

---

# B. Admin capability

## B1 — Admin consent creation · T1 · Low · M
`POST /consents {subjectId*, serviceId*, purposes*, language, state, expiryTime, authorizations, properties}`.
Verified live. Neither portal had a UI for it (OpenFGC only allowlisted it in the BFF). Useful for
seeding and testing. `state` accepts `ACTIVE`/`REJECTED`; supplying `authorizations` makes it `PENDING`.

## B2 — Authorization state override · T1 · Medium · S
`PATCH /consents/{id} {"authorizations":[{"userId":"dpdp.user","type":"authorization","state":"APPROVED"}]}`
→ verified: upserts by `userId`. OpenFGC had full authorization sub-resource CRUD; PATCH covers the
useful part. Add to the admin detail page's authorizations table (add/override authorizer).

## B3 — Authorization `resources` + View Resources modal · T3 · Low
OpenFGC authorizations carried a free-form JSON `resources` blob rendered in a pretty-printed modal
(`ConsentResourcesModal.tsx`). IS authorizations are `{userId, state, updatedTime}` only. Blocked;
would need a `resources` column upstream. The consent `properties` map (A5) is the pragmatic substitute.

## B4 — Purpose filter on consent lists · T1 · Medium · M
OpenFGC filtered by `purposeName` (plus `purposeVersion`, dependent on it). IS accepts `purposeId` and
`purposeVersionId` — verified: `GET /consents?purposeId=<id>` returned the expected single match.
Implement as a purpose **picker** (Autocomplete over `GET /purposes`) resolving to an id, not a text
box. Self-service `/me/consents` does **not** support it — the BFF would have to filter in memory
after the fan-out.

## B5 — Multi-value state filter · T2 · Low · S
OpenFGC took `consentStatuses` as CSV. IS takes a single `state`, and
`MyConsentsServlet.listConsents` currently uses only the first CSV value (`firstValue()`), silently
ignoring the rest — arguably a bug today. Either restrict the UI to one state (honest) or fan out one
call per state in the BFF and merge (caveat: pagination across merged sets is not sound).

## B6 — Sort controls · T2/T3 · Low · M
OpenFGC supported multi-key sort over 6 fields, default `createdTime:desc`. IS `GET /consents` has no
`sort` parameter. Client-side sort within the current page is T2 and misleading on multi-page data;
true sorting is T3.

## B7 — Date-range filter · T2/T3 · Medium · M
OpenFGC had `fromTime`/`toTime`. IS has neither. Note `frontend/src/utils/dateTime.ts` still exports
`toStartOfDayEpochMilliseconds` / `toEndOfDayEpochMilliseconds` **with tests but no callers** — left
over from this filter. Client-side filtering only covers the fetched page. Compliance-relevant for
"show me consents collected in period X", so worth an upstream ask alongside A1.

## B8 — Exact totals / numbered pagination · T2 · Low · M
IS's cursor pagination reports no grand total; `metadata.total` from the BFF is documented as "seen so
far". Self-service could count exhaustively (per-user volumes are small); admin cannot without walking
every cursor page.

## B9 — Bulk element creation · T2 · Low · S
OpenFGC accepted a JSON array with per-item success/failure. IS creates one element per call. Loop
client-side and report partial failures in the same shape.

---

# C. Model concepts absent from IS

## C1 — `groupId` scoping · T3 · Medium
Pervasive in OpenFGC: required header on consent create/update, optional on purpose create (omitted =
org-level), server-enforced purpose access rule, `groupIds` filters, `sort=groupId`, a dedicated
`GET /consents/group-ids?userId=` lookup, a Group ID table column, grouped rows, and a
purpose-list **scope toggle** (Organization-wide / All purposes) with an always-visible scope chip.
IS has no equivalent. `properties` could carry a group key (A5) giving filterable but **unenforced**
grouping — do not describe that as access control. Real fix is upstream.

## C2 — Element versioning, namespaces, type, schema · T3 · Medium
OpenFGC elements were identified by `name + namespace`, had an immutable `type` (basic/json/xml), an
optional `schema`, and an auto-incrementing version chain with its own CRUD. IS elements are flat:
`{id, name, displayName, description, properties}`. `AdminApiServlet` rejects any
`/consent-elements/**/versions**` path. Consequences: no `ElementVersionSelect`, no namespace filters,
no schema validation of collected values. Upstream ask.

## C3 — Recurring / frequency / lookback duration · T2 · Medium
OpenFGC consents carried `recurringIndicator`, `frequency` (Access Limit, "N times per day") and
`dataAccessValidityDuration` (Lookback Period, seconds → hours/days/years with derived units and help
tooltips). None exist in IS. They could be stored in `properties`, but **IS would enforce none of
them** — a displayed "Access Limit: 5 times per day" that nothing enforces is worse than absent.
Recommendation: leave dropped and document it, unless enforcement is added upstream.

---

# D. UX parity (no API dependency, all T1)

| # | Item | Where it lived | Notes |
|---|---|---|---|
| D1 | One-page-ahead prefetch in list hooks | both consent list hooks | Straight TanStack Query `prefetchQuery` |
| D2 | Purpose version selector on detail ("Viewing" chip, latest prepended, navigate away when last version deleted) | `PurposeDetailsPage.tsx` | Purposes only — elements blocked by C2. Pairs with A3 |
| D3 | `mandatory` on consent-detail elements | `ConsentPurposesSection.tsx` | The consent DTO omits it, but the purpose version has it: join `GET /purposes/{purposeId}` and match by element id. Restores the Required column without needing A2 |
| D4 | Element Autocomplete pickers in filters, with `noOptionsText` and name-only fallback matching | `PurposeFiltersPanel` | Needed by B4's purpose picker too |
| D5 | Property key/value editor component | `PropertyEditor.tsx` | Shared prerequisite for A3, A4, A5 — build it first |

Also dropped and worth a deliberate decision rather than silent omission: hard-throw on an unsupported
consent state (currently kept), approve/revoke mutual exclusion in the actions column (kept), and the
`?state=PENDING` deep link from sidebar and dashboard (kept).

---

# E. Housekeeping

| # | Item | Action |
|---|---|---|
| E1 | `frontend/openapi/portal-backend.yaml` still describes the **OpenFGC** BFF — group ids, `statusHistory`, element versions, `/health`. It is load-bearing: `PortalScopes.test.ts` reads it for the scope registry | Regenerate to describe the Java BFF, keeping the scope list intact |
| E2 | No `/health`, `/health/liveness`, `/health/readiness` servlets though the spec declares them | Add a trivial `HealthServlet`, or drop them from the spec |
| E3 | No Java tests at all — `src/test` absent, TestNG + Mockito already in the pom | Cover `CookieUtil` splitting, `ScopeMapper`, `AdminApiServlet.translate`, and the PENDING-state guard |
| E4 | `toStartOfDayEpochMilliseconds` / `toEndOfDayEpochMilliseconds` have tests but no callers | Remove, or keep deliberately for B7 |
| E5 | Search box labelled "Search by service" performs an **exact match** — `svc-1` returns nothing while `svc-10` works | Relabel, or filter client-side over the fetched page |
| E6 | Missing static assets return `200` + HTML rather than 404 | Narrow `SpaServlet`'s fallback to extensionless paths |

---

# Appendix — verified IS 7.3 facts

Everything here was executed against the live pack while writing this document.

**Endpoint surface (consent-mgt v2):**
`/purposes` POST GET · `/purposes/{id}` GET DELETE · `/purposes/{id}/versions` GET POST ·
`/purposes/{id}/versions/latest` PUT GET · `/purposes/{id}/versions/{versionId}` GET DELETE ·
`/elements` POST GET · `/elements/{id}` GET DELETE · `/consents` POST GET ·
`/consents/{id}` GET PATCH · `/consents/{id}/revoke` POST GET · `/consents/{id}/validate` GET

**`GET /consents` accepts only:** `subjectId`, `serviceId`, `state` (single value), `purposeId`,
`purposeVersionId`, `filter`, `limit`, `after`, `before`. No sort, no date range, no multi-value.

**Behavioural findings:**
1. `PATCH` `expiryTime: null` is ignored; `0` sets expiry to epoch 0 and the consent becomes EXPIRED.
   There is no way to clear an expiry.
2. `PATCH` `properties` replaces the entire map.
3. `PATCH` `authorizations` upserts by `userId`.
4. `DELETE /purposes/{id}` → 409 `CM_00079` when any consent references it.
5. Purpose versions do **not** inherit the previous version's elements.
6. Creating a consent for the same subject+service+purpose **auto-revokes the previous one**.
7. `authorize` on a non-PENDING consent resurrects it to ACTIVE — the BFF guards against this
   (`MyConsentsServlet.authorize`); anything else calling IS directly is exposed.
8. Revoking a non-revokable consent returns a misleading message about *authorize*.
9. IS revokes a user's previous access token when the same user re-authenticates for the same client —
   a user cannot hold two concurrent portal sessions.

**Environment prerequisites for any of this work:**
- `[consent_mgt] enable_v2_api = true` in `deployment.toml` (registers the v2 API resources and scopes).
- The consent DB migration applied (`dbscripts/migrations/consent/<db>-migration.txt`).
- Portal app authorized for the three v2 API resources with an RBAC role granting the scopes —
  `dpdp-accelerator/accelerators/dpdp-is/bin/register-portal-app.sh` does this.
- `[[resource.access_control]]` entry making `(.*)/consent-portal(.*)` unsecured.
- A TLS certificate with `CA:FALSE` and ≤398-day validity, or Chrome/Safari refuse the site outright.
