# @wso2-dpdp/cookie-consent

Cookie banner, script blocking and consent recording for a website whose backend runs Node.js
and Express. Choices are stored as DPDP consents in WSO2 Identity Server, so they appear in the
DPDP consent portal, keep their history, expire and raise Event Notifications like any other
consent. The banner is [vanilla-cookieconsent](https://github.com/orestbida/cookieconsent) 3.1.0
(MIT), an exact-version dependency that your backend serves at `/cookie-consent/vendor/*`, so
visitors' browsers never fetch it from a third-party CDN.

## How it works

The browser talks only to your backend. Your backend calls IS Consent Management v2.0 with your
site's own M2M application. Each cookie category is an IS Purpose of type `Cookie`, each cookie an
Element named after the browser cookie. One consent is kept per category per person: `ACTIVE` when
accepted, `REJECTED` or `REVOKED` when declined, and made `ACTIVE` again when accepted again. A first-party HttpOnly cookie, `dpdp_cc`, holds an
encrypted visitor ID. When the visitor logs in, `linkOnLogin` copies their choices onto their user (property
`linkedFrom` names the visitor consent) and revokes the visitor's accepted consents.

## Set up Identity Server

1. Create one Element per cookie (name = cookie name, e.g. `_ga`) and one Purpose per category
   with **type `Cookie`** (e.g. `cookie_analytics`) listing those Elements.
2. Create an M2M application for the site and authorize it for:
   - Consent Management V2 Purposes API: `internal_consent_mgt_purpose_view`
   - Consent Management V2 Consents API: `internal_consent_mgt_consent_create`,
     `internal_consent_mgt_consent_view`, `internal_consent_mgt_consent_update`

These consent scopes are tenant-wide: keep the M2M credentials on the server only.

## Use it

```js
const { createCookieConsent } = require('@wso2-dpdp/cookie-consent');

const cookieConsent = createCookieConsent({
  isBaseUrl: 'https://is.example.com',
  clientId: process.env.COOKIE_CONSENT_CLIENT_ID,
  clientSecret: process.env.COOKIE_CONSENT_CLIENT_SECRET,
  serviceId: process.env.COOKIE_CONSENT_CLIENT_ID,
  siteName: 'Example Insurance',
  cookieSecret: process.env.COOKIE_CONSENT_SECRET, // 32+ characters, keep it stable
  categories: [
    { key: 'analytics', purposeName: 'cookie_analytics', title: 'Analytics cookies' },
    { key: 'marketing', purposeName: 'cookie_marketing', title: 'Marketing cookies' },
  ],
  getUsername: (req) => req.session?.username ?? null, // the IS username of the logged-in user
});

app.use('/cookie-consent', cookieConsent.router());

// In your OIDC callback, after the session is created:
await cookieConsent.linkOnLogin(req, res, username);
```

On every page:

```html
<script src="/cookie-consent/loader.js" defer></script>
<script type="text/plain" data-category="analytics" data-src="https://www.googletagmanager.com/gtag/js?id=G-XXXX"></script>
<a href="#" data-cc="show-preferencesModal">Cookie preferences</a>
```

To reopen the preferences, use a link with `data-cc="show-preferencesModal"` (above), a floating
button, or both. For the button, add `data-floating-button="bottom-right"` (or `"bottom-left"`) to
the loader's script tag. It shows once a choice exists and uses the banner's theme colours
(`--cc-btn-primary-bg`, `--cc-btn-primary-color`).

Optional settings: `secureCookies` (default `true`), `expiryDays` (365), `texts` (see
`src/defaultTexts.js`), `fetch`, `onApiCall`, `onError`, `subjectQuery` (`'userId'` or
`'property'`, see `../spec/verify-is-api.mjs`).

## Behaviour to know

- If the backend or IS is unreachable, no banner is shown and optional scripts stay blocked.
- A new Purpose version shows the banner again. The backend checks for one at most every 5
  minutes: categories are configured by Purpose name, which is the same in every environment, and
  each Purpose's ID is remembered after the first lookup so later checks cost one call per category.
- After logout the browser keeps applying the user's choices; a change made while logged out is
  saved for the browser only and merged at the next login (most recent choice wins).
- A revocation in the consent portal takes effect on the next page load.
- A choice that could not be saved is kept in the browser and sent again on the next page load; until then the browser applies it.
  It is dropped instead if the decision shown on that page has changed (its save did go through, a
  withdrawal or another device overtook it, or another account signed in), so it never undoes a
  newer decision. Choices are sent one at a time, so a quick change of mind cannot arrive first.

## Calls to Identity Server

The browser calls only your backend: on every page `GET /cookie-consent/state`, and on a choice
`POST /cookie-consent/decision`. Your backend then calls IS Consent Management v2.0
(`/api/identity/consent-mgt/v2.0`). `onApiCall` receives each of these IS calls; the token call is
not reported.

Shared by all visitors:

- **Token:** `POST /oauth2/token` (client credentials), reused until 30 seconds before it expires.
- **Cookie categories:** loaded on the first request and checked again at most every 5 minutes.
  The first load costs two calls per category, `GET /purposes?filter=name eq "<purposeName>"`
  then `GET /purposes/{id}` (the search result has no Elements). Later checks cost one,
  `GET /purposes/{id}` with the remembered ID, and fall back to the name search if that Purpose
  is gone or renamed. Requests that arrive during a load wait for it instead of loading again.

Per visitor, after the categories are loaded:

| Scenario | Calls | Why |
|---|---|---|
| Page load, no decision yet (no `dpdp_cc` cookie) | none | There is no subject to look up. |
| Page load, any other visitor or a logged-in user | `GET /consents?userId=<subject>&relation=SUBJECT&filter=properties.consentType eq cookie`, one call per 100 consents (IS lists oldest first, so every page is read) | IS holds the decision, so a revocation in the portal, a choice on another device, an expiry or a new Purpose version reaches the page. |
| First choice by a new visitor (banner) | `POST /consents` per category | One consent per category, so each can be withdrawn on its own: `ACTIVE` if accepted, `REJECTED` if declined. |
| Any other choice, including a logged-in user's | `GET /consents?…`, then per changed category: accepting is `PATCH /consents/{id}` when the category already has a consent for the current Purpose version (its own approval makes it `ACTIVE` again, with a fresh expiry), otherwise `POST /consents`; declining an accepted category is `POST /consents/{id}/revoke`; declining an undecided one is `POST /consents` (`REJECTED`) | The read gives the current decision and the consent IDs to revoke. Unchanged categories cost nothing, and the result is not read back. |
| Login (`linkOnLogin`) | `GET /consents?…` for the visitor and the user (in parallel); per category where the visitor's choice is newer, the same writes as a choice, made for the user (`PATCH` or `POST /consents` with `linkedFrom`, plus a revoke when it replaces an accepted user consent); then `POST /consents/{id}/revoke` for each accepted visitor consent | IS cannot move a consent to another subject, so the choice is copied to the user, and the visitor's copies are revoked so nothing unused stays `ACTIVE`. If a copy fails, nothing is revoked and the next login tries again. |
| Login with no cookie, or on a browser linked to another user | none | Nothing to copy; the cookie is linked to the user. |

With `subjectQuery: 'property'` the list filters on `properties.subjectRef` instead of `userId`.
For example, with two categories, "Accept all" by a new visitor costs 2 calls, and logging in
after it costs 6: 2 reads, 2 copies and 2 revokes.

## Develop

```sh
npm install
npm test
npm run verify:is   # live IS check, see ../spec/verify-is-api.mjs
npm pack            # wso2-dpdp-cookie-consent-<version>.tgz
```

The browser loader is shared with other SDKs and lives in `../browser` (its tests: `npm test` there).
The router serves it from there; `npm pack` copies it into the package.

After re-packing, reinstall the tarball in any app that depends on it (`npm install ./vendor/<tarball>`), because the app's lockfile pins the tarball's checksum.
