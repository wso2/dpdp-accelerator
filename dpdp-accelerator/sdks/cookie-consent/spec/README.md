# Cookie consent SDK contract

What every cookie consent SDK must do, whatever its language, so that the shared browser loader
(`../browser`) works with it and so that consents written by any SDK look the same in Identity
Server. The Node SDK (`../node`) is the reference implementation.

## Browser API

[`cookie-consent.yaml`](cookie-consent.yaml) (OpenAPI 3.0.3) describes every operation the SDK
serves to the loader: `GET /state`, `POST /decision`, `GET /loader.js` and the two
`/vendor/*` files, with their request and response bodies, status codes and caching. An SDK in a
language with an OpenAPI generator can generate its request and response types from it.

The site mounts the SDK under one base path, `/cookie-consent` in the examples; the loader finds
it from its own script URL, so any path works. The vendor files are vanilla-cookieconsent
**3.1.0** exactly: the loader reads the library's own preferences cookie, whose format is specific
to that version.

## Visitor cookie

`dpdp_cc`: `HttpOnly`, `SameSite=Lax`, `Path=/`, `Secure` unless the site turns it off, lifetime
equal to the consent expiry (365 days by default). Its value is opaque to the browser: it must not
reveal the subject and must not be forgeable. A visitor's subject is `v-` followed by a random
UUID; after login it is the IS username. The value only needs to be readable by the SDK that wrote
it.

## Consents in Identity Server

One consent per category per subject and Purpose version, created through Consent Management
v2.0 with:

- `subjectId`: the subject; `serviceId`: the site's M2M client ID; `language`: `en`.
- `purposes`: the category's Purpose (latest version) with all its Elements.
- `state`: `ACTIVE` when accepted, `REJECTED` when declined and undecided before.
- `expiryTime`: now plus the configured expiry, in epoch milliseconds.
- `properties`: `consentType` = `cookie`, `category` = the category key, `siteName`, `subjectRef` =
  the subject, `decidedAt` = the decision time in epoch milliseconds, and on a copy made at login
  `linkedFrom` = the visitor consent's ID.

Declining an accepted category revokes every `ACTIVE` consent of that category. Accepting a
category whose consent for the current Purpose version is `REJECTED`, `REVOKED` or `EXPIRED`
reactivates that consent instead of creating another: one `PATCH /consents/{id}` sets an
`APPROVED` authorization for the subject, a new `expiryTime` and the full property map (PATCH
replaces it). The consent keeps its creation `timestamp`, so the current decision is the record
with the latest `decidedAt`, authorization `updatedTime` or `timestamp`. If IS refuses the PATCH
with a client error, a new consent is created instead. The rules for
reading the current decision, saving a change and linking at login are in
`../node/src/consentState.js` and `../node/src/cookieConsentService.js`; the calls they make are
listed in the Node README ("Calls to Identity Server").

## Checking Identity Server

`verify-is-api.mjs` checks, against a live IS, the consent-mgt v2 behaviour every SDK relies on
(results of the last run in `verify-is-api-results.md`). It needs Node.js 20+ and no install. From
`sdks/cookie-consent`:

```sh
IS_BASE_URL=https://localhost:9443 COOKIE_CONSENT_CLIENT_ID=… COOKIE_CONSENT_CLIENT_SECRET=… \
  NODE_TLS_REJECT_UNAUTHORIZED=0 node spec/verify-is-api.mjs
```
