---
title: Cookie Consent
sidebar_position: 6
---

# Cookie Consent

Record website cookie choices as DPDP consents. A cookie banner on your site sends each choice to
your backend, which saves it in WSO2 Identity Server through the cookie consent SDK. Cookie
consents then appear in the Consent Portal next to your application consents, with the same
history, expiry and Event Notifications.

Supported today: Node.js/Express backends, English banner text, cookies listed by hand.

## How it works

- Each cookie category (for example Analytics) is a **Purpose of type `Cookie`**. Each cookie is
  an **Element** whose name is the cookie's name.
- One consent is kept per category per person: `ACTIVE` when accepted, `REJECTED` or `REVOKED`
  when declined.
- Before login, consents belong to an anonymous visitor ID kept in an encrypted, first-party,
  HttpOnly cookie. At login they are copied to the user's account: each copy records the visitor
  consent it came from (property `linkedFrom`), and the visitor's accepted consents are revoked,
  so each `consent.revoke` event at login is this move, not a withdrawal by the person.
- A user can change cookie choices on the website (**Cookie preferences**) or revoke them in the
  Consent Portal. The website applies a revocation on the next page load.
- The website's backend reads the person's cookie consents from Identity Server on each page
  load, and writes only when a choice changes or the person logs in. The SDK README lists every
  call for each case ("Calls to Identity Server").

## Set up

1. In the Consent Portal, create an Element per cookie and a Purpose per category with type
   `Cookie`, listing those Elements.
2. In the Console, create an M2M application for the website and authorize it for the
   **Consent Management V2 Purposes API** (`internal_consent_mgt_purpose_view`) and the
   **Consent Management V2 Consents API** (`internal_consent_mgt_consent_create`,
   `internal_consent_mgt_consent_view`, `internal_consent_mgt_consent_update`). These scopes cover
   every consent in the organization, so keep the credentials on the server.
3. Add the SDK to your backend and pages. See
   `dpdp-accelerator/sdks/cookie-consent/node/README.md` in the accelerator repository.

## What users see in the Consent Portal

Cookie consents are listed in **My Consents** like any other consent, one per cookie category
(for example `cookie_analytics`). Users revoke them from the consent's detail page.

## Current limits

- Static sites with no server code are not supported.
- If your backend cannot reach Identity Server, the banner is not shown and optional scripts stay
  blocked until a later page load succeeds.
- A browser stays linked to the last user who logged in on it. Choices made there after logout
  are saved for the browser only and merged at the next login.
