# Consent Management

The Consent Portal supports catalog administration, self-service consent
review, authorization and revocation, tenant-wide administrative review, and
consent history. It deliberately does not create consents: a connected
application or the Consent Management API creates the consent before the Data
Principal acts on it.

The curl examples below assume you already have an access token with the
right scopes. See [Managing Access](../learn/managing-access.md) for
how to create an application, authorize it, and get a token.

## Define a purpose and its data element

This flow shows how a Data Fiduciary can model why personal data is requested
and which data element is involved.

**Portal:** Sign in as the portal administrator and use **Elements** and
**Purposes**. The API examples show the requests made for the same operations.

#### Create an element

1. Sign in as the portal administrator.
2. Open **Elements** and select **Add Element**.
3. Enter:

   | Field | Example |
   |---|---|
   | Name | `contact-email` |
   | Display name | `Contact email` |
   | Description | `Email address used to send optional product updates.` |

4. Select **Create**.
5. Open the resulting row and confirm its identifier, display name,
   description, and properties.

![Add Element dialog filled in with the contact-email example](../../assets/images/try-out/consent/add-element-form.png)

The equivalent request is (see
[Managing Access](../learn/managing-access.md) if you don't have an
access token yet):

```bash
curl -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/identity/consent-mgt/v2.0/elements" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "contact-email",
    "displayName": "Contact email",
    "description": "Email address used to send optional product updates."
  }'
```

Representative response:

```json
{
  "id": "b9dd5f0d-4981-4d56-86b6-65b5d8d32e24",
  "name": "contact-email",
  "displayName": "Contact email",
  "description": "Email address used to send optional product updates."
}
```

#### Create a purpose

1. Open **Purposes** and select **Add Purpose**.
2. Enter:

   | Field | Example |
   |---|---|
   | Name | `marketing-email` |
   | Type | `optional-marketing` |
   | Version | `1.0` |
   | Description | `Send occasional product news by email.` |

3. In the element selector, add `contact-email` and choose whether it is
   mandatory for this purpose.
4. Select **Create**.
5. Open the purpose and verify that version `1.0` references the expected
   element.

![Add Purpose dialog with the contact-email element associated](../../assets/images/try-out/consent/add-purpose-form.png)

Replace `<element-id>` with the `id` returned above. The equivalent request is:

```bash
curl -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/identity/consent-mgt/v2.0/purposes" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "marketing-email",
    "type": "optional-marketing",
    "version": "1.0",
    "description": "Send occasional product news by email.",
    "elements": [
      {
        "id": "<element-id>",
        "mandatory": false
      }
    ]
  }'
```

Representative response:

```json
{
  "id": "d223f77a-54ed-4099-99b2-9900795483ae",
  "name": "marketing-email",
  "description": "Send occasional product news by email.",
  "type": "optional-marketing",
  "latestVersion": {
    "id": "787c0c69-7a84-4a62-b5f1-3706ce818d72",
    "version": "1.0"
  },
  "elements": [
    {
      "id": "<element-id>",
      "name": "contact-email",
      "displayName": "Contact email",
      "mandatory": false
    }
  ]
}
```

Expected result: the purpose and element appear in the tenant's catalog and
can be used by a connected application when it creates a consent.

> The portal does not contain an **Add Consent** action. A consent is created
> by an application through WSO2 Identity Server's Consent Management v2 API
> or as part of its consent journey. The automatically provisioned **DPDP
> Consent API Invoker** application supports machine-to-machine access to the
> consents resource; see
> [Consent API Invoker provisioning](../install-and-setup/configuring-the-accelerator.md#consent-api-invoker-provisioning).

## Review, authorize, revoke, and audit a consent

This flow requires a consent created for the Data Principal by a connected
application or the Consent Management v2 API. Use a disposable consent because
approval, rejection, and revocation change its state.

**Portal:** The Data Principal performs the self-service actions under
**Pending Consents** and **All Consents**. An authorizer named on someone
else's consent acts from their own **My Consents**, filtered to **Managed**.
The administrator uses **Administration → Consents**. Creating the
prerequisite consent is not a portal operation.

Create the consent using the Consent API Invoker's client-credentials token.
This is a **Direct Consent** — no named authorizers — so it starts `ACTIVE`
immediately, since Priya is consenting for herself:

```bash
curl -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/identity/consent-mgt/v2.0/consents" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "subjectId": "priya@example.com",
    "serviceId": "CarePulse-client",
    "purposes": [
      {
        "id": "<marketing-email-purpose-id>",
        "elements": [
          { "id": "<contact-email-element-id>" }
        ]
      }
    ],
    "language": "en",
    "expiryTime": 1990411096000,
    "authorizations": [],
    "properties": {}
  }'
```

See [Authorize consent as a guardian or delegate](#authorize-consent-as-a-guardian-or-delegate)
below for a consent that instead names someone else as the authorizer.

#### Act on your own consent (Direct Consent)

1. Sign in as the Data Principal.
2. Open **All Consents** and select the Direct Consent created above.
3. Review its metadata, properties, purposes, and data elements. An empty
   authorizations table confirms it's a Direct Consent — no one else needed
   to approve it.
4. In **Consent Lifecycle**, confirm that the status timeline records its
   creation.
5. Select **Revoke** and confirm the action.

![Direct Consent detail, Active, with Revoke available to the Data Principal](../../assets/images/try-out/consent/direct-consent-active.png)

The equivalent API calls:

```bash
curl \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/users/v1/me/consents/<consent-id>" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

```bash
curl -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/users/v1/me/consents/<consent-id>/revoke" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Revocation has no request or response body. Expected result: the consent
shows `REVOKED`, and the lifecycle section contains the corresponding audit
entry. With lifecycle event publication enabled, a supported revocation
lifecycle path publishes `consent.revoke`.

#### Inspect the same consent as an administrator

1. Sign in as the portal administrator.
2. Open **Administration → Consents**.
3. Filter by consent ID, user, state, service, purpose, or the available
   advanced filters.
4. Open the consent and compare the tenant-wide administrative view with the
   Data Principal's view.

The self-service view only returns consents involving the signed-in user. The
administrative registry is tenant-wide and requires the administrative consent
scopes.

To verify the lifecycle audit independently of the UI:

```bash
curl \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/dpdp/consent-mgt/v1/consents/<consent-id>/status-history?limit=100&offset=0" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Representative response after revocation:

```json
{
  "consentId": "81c6dcb8-3df9-4b15-a43e-50f253792dbe",
  "statusHistory": [
    {
      "previousStatus": "ACTIVE",
      "currentStatus": "REVOKED",
      "actionType": "REVOKE",
      "actionBy": "priya@example.com",
      "actionTime": 1788230520000
    }
  ],
  "pagination": {
    "limit": 100,
    "offset": 0,
    "totalCount": 1
  }
}
```

## Authorize consent as a guardian or delegate

A Delegated Consent names someone other than the data subject as the
authorizer — the subject is not asked to decide, only notified. This is the
pattern behind the parent-managed child-account use case described in the
DPDP solution design, but the API itself only stores a subject and one or
more separate authorizers; it does not establish that a person is actually a
parent, lawful guardian, or other representative. A trusted Data Fiduciary
system must verify that relationship before creating the delegated
authorization.

![Delegated consent flow from connected-application creation through guardian approval and data-subject verification](../../assets/images/diagrams/dpdp-consent-delegation-flow.svg)

**Portal:** The authorizer uses **My Pending Consents** and can filter **My
Consents** by **Managed**. The data subject can filter the same page by
**Personal**, where they'll see the consent explained as waiting on someone
else, with no action available. A connected application creates the
prerequisite consent using the Consent Management API — the portal has no
**Add Consent** action. The purposes and elements it references, though, can
be created either through the portal admin UI (see
[Define a purpose and its data element](#define-a-purpose-and-its-data-element)
above) or directly via the REST API shown there.

#### Create a delegated consent

In this example, Priya and her spouse Raj are enrolling in a shared family
health plan: CarePulse names Raj as the sole authorizer on Priya's consent,
so he must approve it on her behalf.

1. Create disposable user accounts for the data subject and the authorizer in
   the same tenant, and verify their relationship in the trusted system used
   for this test — the Consent Management API does not perform this
   verification.
2. Obtain a client-credentials token for the automatically provisioned **DPDP
   Consent API Invoker** application.
3. Use the purpose and element identifiers from
   [Define a purpose and its data element](#define-a-purpose-and-its-data-element)
   to create a consent whose `subjectId` is Priya and whose `authorizations`
   list contains Raj.
4. Retain the returned consent ID.

```bash
curl -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/identity/consent-mgt/v2.0/consents" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "subjectId": "priya@example.com",
    "serviceId": "CarePulse-client",
    "purposes": [
      {
        "id": "<marketing-email-purpose-id>",
        "elements": [
          { "id": "<contact-email-element-id>" }
        ]
      }
    ],
    "language": "en",
    "expiryTime": 1990411096000,
    "authorizations": [
      { "userId": "raj@example.com", "type": "USER" }
    ],
    "properties": {}
  }'
```

Do not send the data subject as another authorization entry. `subjectId`
already identifies the person whose data the consent concerns; the
`authorizations` array contains the other users who must decide on that
person's behalf. When at least one authorization is supplied, Identity Server
stores the new consent as `PENDING` even if the request omits `state` or sends
the normal `ACTIVE` default.

#### Observe as the data subject

1. Sign in as Priya and open **Pending Consents**.

   ![Pending Consents list showing the delegated consent waiting on Raj](../../assets/images/try-out/consent/pending-consents-list.png)

2. Open the consent. Because Priya is not a named authorizer, the page
   explains that it's waiting for authoriser approval instead of showing
   Approve/Reject actions.

   ![Consent detail for the subject of a Delegated Consent, with no action available](../../assets/images/try-out/consent/pending-consent-observer-view.png)

#### Approve as the guardian or delegate

1. Sign in as Raj (the authorizer) and open the same consent from **My
   Consents**, filtered to **Managed**. Alternatively, open **My Pending
   Consents**.
2. Confirm the data subject, service, purposes, elements, and authorization
   entry. Raj sees Approve/Reject actions that Priya does not.

   ![The same consent from the authorizer's view, with Approve and Reject available](../../assets/images/try-out/consent/pending-consent-authorizer-view.png)

3. Select **Approve** and confirm.

   ![Review & Approve Consent confirmation dialog](../../assets/images/try-out/consent/approve-confirmation.png)

4. Reopen the consent and confirm that Raj's authorization is `APPROVED`.
   With this single required authorizer, the aggregate consent is now
   `ACTIVE`. If several authorizers were supplied, it remains `PENDING`
   until every required authorizer approves.

Raj can inspect the pending consent directly before approving it:

```bash
curl \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/users/v1/me/consents?state=PENDING&relation=AUTHORIZER&attributes=purposes,authorizations" \
  -H "Authorization: Bearer ${RAJ_ACCESS_TOKEN}"
```

Approve the consent with Raj's token:

```bash
curl -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/users/v1/me/consents/<consent-id>/authorize" \
  -H "Authorization: Bearer ${RAJ_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{ "state": "APPROVED" }'
```

A successful authorization has no required response body.

#### Verify as the data subject

1. Sign back in as Priya and open **My Consents**, choosing **Personal**.
2. Open the consent and verify that it is `ACTIVE` and records Raj's approved
   authorization.
3. Inspect **Consent Lifecycle** and **View Full Snapshot History** to
   confirm that the authorization and aggregate state transition were
   audited.

   ![Full snapshot history showing the consent created by admin and approved by Raj](../../assets/images/try-out/consent/snapshot-history-after-approval.png)

The equivalent subject-scoped query is:

```bash
curl \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/users/v1/me/consents?relation=SUBJECT&attributes=purposes,authorizations" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Expected result: Priya and Raj see the same consent through different
relationships. Only the listed authorizer can record that delegated
decision, and the consent becomes usable only after all required
authorizers approve. Relationship validation and downstream enforcement
remain the Data Fiduciary's responsibility; this flow proves storage,
authorization, state resolution, and audit behavior in the accelerator.

---

## Next Steps

- Continue with [Complaint Management](complaint.md) to submit, manage, and
  resolve a grievance.
