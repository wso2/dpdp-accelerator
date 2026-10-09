# Understanding Consent

Consent is more than a yes/no decision. It starts with clearly defining what data is needed and why, and continues throughout the consent lifecycle as people give, change, or withdraw their decisions.

This guide walks through that lifecycle using a simple example. It explains how to define the data required for a purpose, collect consent, handle changes and withdrawals, and understand who can act on a consent at each stage.

## The people in this example

To make the concepts easier to follow, we'll use a healthcare service called **CarePulse**.

* **Priya** is a Data Principal who uses CarePulse's online healthcare service.
* **CarePulse** is the Data Fiduciary that decides why Priya's data is processed.
* **Anika** is CarePulse's **Data Fiduciary administrator**. She manages the Purposes and Elements catalog and monitors consent records across the tenant.

## 1. Define what data is needed

Before asking Priya for consent, CarePulse needs to clearly describe what data it wants to use and why.

Anika does this by defining the required information in the catalog.

For example:

1. In **Elements**, Anika creates an element called `contact-email` with a clear display name and description.
2. In **Purposes**, she creates a `marketing-email` purpose, version `1.0`, and associates the email element with it.
3. CarePulse can now use this definition when presenting a consent request to Priya.

![Elements list showing reusable consent elements and their descriptions](../../assets/images/learn/admin-element-list-view.png)

![Purposes list showing each purpose, its type, version, and description](../../assets/images/learn/admin-purpose-list-view.png)

This separation is important. The catalog describes **what data is needed and why**. Creating an Element or Purpose does not create a consent.

The connected application or Consent Management API creates the actual consent using these catalog definitions.

**Try it:** [Define a purpose and its data element](../try-out/consent.md#define-a-purpose-and-its-data-element).

### Record the data the person actually agreed to

When CarePulse creates a consent, the request identifies the purpose and the elements that Priya selected.

Suppose Priya agrees to prescription delivery but does not provide her optional phone number. The consent request can contain:

```json
{
  "purposes": [
    {
      "id": "<prescription-delivery-purpose-id>",
      "elements": [
        { "id": "<delivery-address-element-id>" }
      ]
    }
  ]
}
```

If Priya also agrees to provide her phone number, its element ID is included in the same array.

The consent record captures the **specific elements Priya selected**, along with the version of the purpose that was used when the consent was created.

## 2. Give the person a clear choice

Once CarePulse has defined its purposes and data requirements, its application can present the consent request to Priya.

For example, while booking a consultation, Priya might see separate choices for:

* Consultation-related processing
* Prescription delivery
* Optional wellness tips

Priya agrees to the consultation and prescription delivery but leaves wellness tips unchecked.

The application records her decision, and the accelerator maintains the resulting consent record and its history.

Priya can later open **My Consents** to see what she agreed to, which service requested the consent, its current state, and when it expires.

Anika, as CarePulse's Data Fiduciary administrator, can review tenant-wide consent records through **All Consents** when she has the required permissions.

The Consent Portal provides a place to **review and manage consent records**.

**Try it:** [Review, authorize, revoke, and audit a consent](../try-out/consent.md#review-authorize-revoke-and-audit-a-consent).

## 3. Change or withdraw a consent

Consent does not necessarily remain unchanged after it is given.

For example, several weeks later, Priya may decide that she no longer wants to receive wellness messages. She can open her consent history, review the relevant consent, and withdraw it.

The consent history allows CarePulse to determine:

* What changed
* When the change happened
* Which purpose version was involved
* How the consent moved through its lifecycle

Revoking consent changes the consent record. It does **not** automatically erase every copy of the data that may already exist with a Data Processor.

Any follow-up processing, such as notifying processors or handling data according to retention requirements, is coordinated through the relevant lifecycle events and CarePulse's own contracts and retention rules.

See [Event Notifications](event.md) for more information.

**Try it:** [Review, authorize, revoke, and audit a consent](../try-out/consent.md#review-authorize-revoke-and-audit-a-consent).

---

## Understanding consent states

A consent moves through a defined lifecycle. Its current state determines whether it can still be acted on.

| State      | Meaning                                                                                       |
| ---------- | --------------------------------------------------------------------------------------------- |
| `PENDING`  | The consent is waiting for one or more people to approve or reject it.                        |
| `ACTIVE`   | The consent has been approved and is currently in effect.                                     |
| `REJECTED` | An authorizer rejected the consent. This is a final state and the consent cannot be reopened. |
| `REVOKED`  | The consent was withdrawn after becoming active. This is a final state.                       |
| `EXPIRED`  | The consent passed its expiry time. This is a final state.                                    |

The starting state depends on how the consent is configured.

A consent created for the subject alone becomes **ACTIVE immediately**, because there is no other person whose decision is required.

If one or more authorizers are specified, the consent starts as **PENDING**. It becomes **ACTIVE only after every named authorizer approves it**.

If any named authorizer rejects a pending consent, the consent immediately becomes **REJECTED**.

## Three consent models

The accelerator supports three common ways of setting up consent.

### Direct consent

In a direct consent, the person gives consent for themself. No other person is required to make a decision.

For example, Alice gives consent for her own data:

```json
{
  "subjectId": "alice@wso2.com",
  "purposes": [
    {
      "id": "...",
      "elements": [
        { "id": "..." }
      ]
    }
  ]
}
```

The consent becomes `ACTIVE` immediately.

Alice can manage the consent herself, including revoking it after it becomes active.

### Delegated consent

In a delegated consent, another person gives consent on behalf of the subject.

This can be useful when a parent gives consent for a child or a guardian gives consent for a dependent.

The subject is not automatically an authorizer. They can see the consent, but they cannot approve, reject, or revoke it unless they are explicitly included as an authorizer.

You can also require more than one authorizer.

For example, both parents may need to approve a child's consent:

```json
{
  "subjectId": "child@wso2.com",
  "purposes": [
    {
      "id": "...",
      "elements": [
        { "id": "..." }
      ]
    }
  ],
  "authorizations": [
    { "userId": "mother@wso2.com", "type": "USER" },
    { "userId": "father@wso2.com", "type": "USER" }
  ]
}
```

The consent remains `PENDING` until both parents approve it.

If either parent rejects it, the consent becomes `REJECTED`.

### Co-authorized consent

A co-authorized consent requires the subject and at least one other person to make the decision together.

For example, two joint account holders may both need to approve a consent:

```json
{
  "subjectId": "alice@wso2.com",
  "purposes": [
    {
      "id": "...",
      "elements": [
        { "id": "..." }
      ]
    }
  ],
  "authorizations": [
    { "userId": "alice@wso2.com", "type": "USER" },
    { "userId": "bob@wso2.com", "type": "USER" }
  ]
}
```

Because Alice and Bob are both authorizers, both must approve before the consent becomes `ACTIVE`.

## Review the purpose and enforce consent before processing

Before relying on a consent, an application can fetch the full consent record — its current state, the purpose it covers, and the specific data elements involved — rather than assuming an earlier decision still applies.

```http
GET /api/identity/consent-mgt/v2.0/consents/<consent-id>
```

The response includes the purpose and its elements:

```json
{
  "id": "<consent-id>",
  "state": "ACTIVE",
  "purposes": [
    {
      "name": "marketing-email",
      "version": "1.0",
      "elements": [
        { "id": "...", "name": "contact-email", "displayName": "Contact email" }
      ]
    }
  ]
}
```

A consuming application should validate this response as part of consent
enforcement — checking the consent's state, the purpose, and the specific
elements selected — before processing the person's data, rather than
assuming a prior approval still applies.

## Next Steps

Now that you understand the consent lifecycle, try it yourself, or continue
to see what happens after a consent is revoked:

- [Define a purpose and its data element](../try-out/consent.md#define-a-purpose-and-its-data-element)
- [Review, authorize, revoke, and audit a consent](../try-out/consent.md#review-authorize-revoke-and-audit-a-consent)
- [Complaints](complaint.md) — see what happens when a Data Principal believes something went wrong despite their consent decision


