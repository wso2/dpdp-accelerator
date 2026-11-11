# Consent Lifecycle Guide

This reference explains who can approve, reject, or revoke a consent, what the Consent Portal shows when there's nothing to do, and which API call sits behind each action. For the consent states and the three consent models (direct, delegated, and co-authorized), see [Understanding Consent](learn/consent.md#understanding-consent-states).

Everything below is decided by one thing: whether the person viewing the consent has their own entry in its `authorizations` list.

| Model | `authorizations` list | The subject is |
|---|---|---|
| Direct consent | Empty | The only person who can act. The consent is `ACTIVE` immediately. |
| Delegated consent | Names one or more people other than the subject | An observer: they can view it but not act on it. |
| Co-authorized consent | Names the subject and at least one other person | An authorizer, like everyone else named. |

## 1. Who can approve, reject, or revoke

- **Each authorizer decides only for themself.** Every named authorizer's decision is recorded separately. The consent becomes `ACTIVE` only when all of them have approved, and a single rejection makes it `REJECTED`.
- **Once you've decided, your buttons disappear**, even if the consent is still `PENDING` because someone else hasn't decided yet.
- **Revoking is different from rejecting.** Any named authorizer can revoke a consent once it's `ACTIVE`, not only the one who approved it.
- **Administrators can revoke, but never approve or reject** on someone else's behalf. They can revoke a consent that is `PENDING` or `ACTIVE`, from **Administration → All Consents**.
- **`REVOKED` and `EXPIRED` are final.** They override any earlier per-authorizer decision.

| Who's viewing | Approve / Reject | Revoke |
|---|---|---|
| Subject of a direct consent | Nothing to approve; it's already `ACTIVE` | Yes, while `ACTIVE` |
| Subject of a delegated consent | No | No |
| A named authorizer (in a delegated or co-authorized consent, including the subject of a co-authorized consent) | Yes, until they've decided, while `PENDING` | Yes, while `ACTIVE` |
| Administrator, under **All Consents** | No | Yes, while `PENDING` or `ACTIVE` |

## 2. The API call behind each action

| Action | Request | Token |
|---|---|---|
| Approve | `POST /api/users/v1/me/consents/{consentId}/authorize` with `{"state": "APPROVED"}` | The authorizer's own user token. Self-service calls need only `internal_login`. |
| Reject | `POST /api/users/v1/me/consents/{consentId}/authorize` with `{"state": "REJECTED"}` | The authorizer's own user token |
| Revoke (self-service) | `POST /api/users/v1/me/consents/{consentId}/revoke`, with no body | The subject's or an authorizer's own user token |
| Revoke (administrator) | `POST /api/identity/consent-mgt/v2.0/consents/{consentId}/revoke`, with no body | A token with `internal_consent_mgt_consent_update` |

The rules in this guide are enforced by the Consent Portal. Identity Server itself accepts an `/authorize` call in any consent state, so an application calling the API directly must apply the same checks, for example never approving a `REVOKED` or `EXPIRED` consent.

## 3. What you'll see instead of a button

When there's nothing for you to do, the consent detail page explains why rather than just hiding the button:

| You'll see this | When |
|---|---|
| "Waiting for authoriser approval." | You're the subject of a delegated consent, and it's still `PENDING`. |
| "This consent has been approved." / "...rejected." | You're the subject of a delegated consent, and a decision has been made. |
| "You've made your decision. Waiting for the rest to decide." | You're a named authorizer who has already approved, and the consent is still `PENDING` on someone else. |
| "You've rejected this consent." | You're the authorizer whose rejection ended it. |
| "This consent has been revoked." / "...expired." | The consent has reached a final state, and no further action is possible for anyone. |

## 4. Troubleshoot common problems

| Symptom | Explanation |
|---|---|
| No Approve/Reject button, and I expected one | Check whether you're named in the consent's `authorizations`. The subject of a delegated consent isn't automatically an authorizer. |
| I approved, but the consent is still Pending | Another named authorizer hasn't decided yet. It becomes Active only once everyone has approved. |
| I can't find a Reject button, only Revoke | The consent is already Active. Rejection only applies while it's Pending, so use Revoke to withdraw it instead. |
| I can't revoke a consent I'm the subject of | You're the subject of a delegated consent and weren't named as an authorizer. Only the named authorizers, or an administrator, can revoke it. |
| As an administrator, I see no Revoke button | Open the consent from **Administration → All Consents**, not **My Consents**. The consent must also be `PENDING` or `ACTIVE`. |
