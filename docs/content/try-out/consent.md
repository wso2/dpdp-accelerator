# Consent Management

Explore the consent lifecycle using **CarePulse** (Data Fiduciary): define what data is needed, create a consent, review and revoke it as the Data Principal, and inspect the audit record. The scenario also covers consents that another person must authorize.

## Before you begin

- Ensure WSO2 Identity Server with the DPDP Accelerator is installed and running.
- Create these users and assign their roles, as described in [Configuring Users and Roles](../install-and-setup/configuring-users-and-roles.md):
  - **Priya** (Data Principal), username `priya@example.com`: `dpdp-consent-user`
  - **Raj** (additional authorizer), username `raj@example.com`: `dpdp-consent-user`
  - **Anika** (Data Fiduciary Administrator): `dpdp-consent-admin`
- [Create one application](../learn/managing-access.md#step-1-create-an-application) for **CarePulse** and [authorize it](../learn/managing-access.md#step-2-authorize-the-required-scopes) for the **Consent Management V2 – Consents** and **DPDP Consent History** APIs.

Before running the API examples, set the base URL and access token. [Obtain a client-credentials token](../learn/managing-access.md#step-3-get-an-access-token) using the **CarePulse** application's credentials, with the scopes required for the operations below.

```bash
export BASE_URL="https://localhost:9443"
export ACCESS_TOKEN="<carepulse-client-credentials-token>"
```

The base URL is for the super tenant. For a specific tenant, use `https://localhost:9443/t/<tenant-domain>`.

:::note Local testing with a self-signed certificate
Add `-k` to the `curl` commands if your local Identity Server uses the default self-signed certificate. Never use `-k` in production — configure a trusted CA certificate, or pass it with `--cacert`.
:::

## Define a purpose and its data element

**CarePulse** first defines which data it needs (an **element**) and why (a **purpose**).

### Step 1: Create an element

1. Sign in to the Consent Portal as **Anika**.
2. Open **Elements** and select **Add element**.
3. Enter:

   | Field | Value |
   |---|---|
   | Name | `contact-email` |
   | Display name | `Contact email` |
   | Description | `Email address used to send optional product updates.` |

4. Select **Create**, then open the new row and note its ID.

![Add element dialog filled in with the contact-email example](../../assets/images/try-out/consent/add-element-form.png)

### Step 2: Create a purpose

1. Open **Purposes** and select **Add purpose**.
2. Enter:

   | Field | Value |
   |---|---|
   | Name | `marketing-email` |
   | Type | `optional-marketing` |
   | Version | `1.0` |
   | Description | `Send occasional product news by email.` |

3. Add the `contact-email` element and leave it optional.
4. Select **Create**, then open the purpose and note its ID.

![Add purpose dialog with the contact-email element associated](../../assets/images/try-out/consent/add-purpose-form.png)

Expected result: both appear in the catalog, ready for **CarePulse** to use when it creates a consent.

## Review, authorize, revoke, and audit a consent

**CarePulse** records **Priya**'s consent for marketing emails. **Priya** reviews it and later withdraws it, and **Anika** checks the audit record.

### Step 1: Create a consent

The portal can't create consents, so **CarePulse** creates it through the API, using the access token you set earlier ([how to get a token](../learn/managing-access.md#step-3-get-an-access-token)). The token needs the `internal_consent_mgt_consent_create` scope. For the full request format, see the [Consent Management v2 API reference](https://is.docs.wso2.com/en/latest/apis/consent-management-v2-api-definition/). Replace the two IDs with the ones you noted.

```bash
curl -X POST "${BASE_URL}/api/identity/consent-mgt/v2.0/consents" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "subjectId": "priya@example.com",
    "serviceId": "CarePulse-client",
    "purposes": [
      { "id": "<marketing-email-purpose-id>",
        "elements": [ { "id": "<contact-email-element-id>" } ] }
    ],
    "language": "en",
    "expiryTime": 1990411096000,
    "authorizations": [],
    "properties": {}
  }'
```

There are no other authorizers, so this is a **Direct Consent** and it's `ACTIVE` straight away. Note the returned consent ID.

### Step 2: Review the consent as Priya (Data Principal)

1. Sign in to the Consent Portal as **Priya**.
2. Open **My Consents** and select the new consent.
3. Check its purpose and data element. The authorizations table is empty, because no one else needs to approve it.

![Direct Consent detail, Active, with Revoke available to the Data Principal](../../assets/images/try-out/consent/direct-consent-active.png)

### Step 3: Revoke the consent as Priya

1. On the same page, select **Revoke** and confirm.
2. Reopen it and check that its state is `REVOKED`, with the change shown in **Consent Lifecycle**.

### Step 4: Inspect the consent as Anika (Data Fiduciary Administrator)

1. Sign in as **Anika** and open **Administration → All Consents**.
2. Find **Priya**'s consent (filter by consent ID, user, or state) and open it.

**Anika** sees every consent in the organization. **Priya** only sees her own.

### Step 5: Inspect the status history (optional)

You can use the DPDP Consent History API to verify the consent's lifecycle history independently of the portal.

Use the **CarePulse** application's client-credentials token. To read the history, the token must include the `consent:status-history:view:any` scope, as defined in the [Consent History API contract](https://github.com/wso2/dpdp-accelerator/blob/main/dpdp-accelerator/internal-webapps/org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint/src/main/resources/consent-history.yaml).

```bash
curl "${BASE_URL}/api/dpdp/consent-mgt/v1/consents/<consent-id>/status-history?limit=100&offset=0" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Representative response after revocation (the IDs and timestamps will differ in your environment):

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

Expected result: the consent is `REVOKED`, and the status history records the transition from `ACTIVE` to `REVOKED`.

## Authorize consent as a guardian or delegate

A **Delegated Consent** allows someone other than the Data Principal to make the authorization decision. The Data Principal can view the consent, but the designated authorizer is responsible for authorizing it.

In this example, **CarePulse** creates a consent for **Priya** and designates **Raj** as the additional authorizer.

The API records the Data Principal and the required authorizers but does not verify whether an authorizer is a parent, legal guardian, or other authorized representative. The Data Fiduciary must verify this relationship before creating the delegated consent.

![Delegated consent flow from connected-application creation through guardian approval and data-subject verification](../../assets/images/diagrams/dpdp-consent-delegation-flow.svg)

### Step 1: Create a delegated consent

Ensure that **Priya** and **Raj** have separate test accounts. Create the consent with the same **CarePulse** application's client-credentials token, using the purpose and element IDs from [Define a purpose and its data element](#define-a-purpose-and-its-data-element).

The access token must include the `internal_consent_mgt_consent_create` scope. For details, see the [Consent Management v2 API reference](https://is.docs.wso2.com/en/latest/apis/consent-management-v2-api-definition/).

```bash
curl -X POST \
  "${BASE_URL}/api/identity/consent-mgt/v2.0/consents" \
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

`subjectId` already identifies the person whose data the consent concerns, while `authorizations` contains the additional people who must decide on that person's behalf. When at least one authorization is supplied, Identity Server stores the new consent as `PENDING`.

### Step 2: Observe the consent as Priya

1. Sign in to the Consent Portal as **Priya** and open **My Pending Consents**.
2. Open the consent. Since **Priya** is not a named authorizer, the page explains that it is waiting for authorization instead of showing Approve/Reject actions.

![Consent detail for the subject of a Delegated Consent, with no action available](../../assets/images/try-out/consent/pending-consent-observer-view.png)

### Step 3: Approve the consent as Raj, the delegate

1. Sign in to the Consent Portal as **Raj**, the authorizer. Open the consent from **My Consents**, filtered to **Managed**, or open **My Pending Consents**.
2. Confirm the Data Principal, service, purpose, data elements, and authorization entry. **Raj** sees Approve/Reject actions that **Priya** does not.
3. Select **Approve** and confirm.
4. Reopen the consent and confirm that **Raj**'s authorization is `APPROVED`. Because **Raj** is the only required authorizer in this example, the aggregate consent becomes `ACTIVE`. If several authorizers were supplied, it would remain `PENDING` until all required authorizers approved.

![The same consent from the authorizer's view, with Approve and Reject available](../../assets/images/try-out/consent/pending-consent-authorizer-view.png)

### Step 4: Verify the result as Priya

1. Sign back in to the Consent Portal as **Priya**.
2. Open **My Consents** and choose **Personal**.
3. Open the consent and verify that it is `ACTIVE` and records **Raj**'s approved authorization.
4. Inspect **Consent Lifecycle** and **View Full Snapshot History** to confirm that the authorization and aggregate state transition were audited.

![Full snapshot history showing the consent created by admin and approved by Raj](../../assets/images/try-out/consent/snapshot-history-after-approval.png)

## Create a co-authorized consent

Unlike delegated consent, a **Co-authorized Consent** names the Data Principal as one of the authorizers alongside at least one other person. In this example, both **Priya** and **Raj** must approve the same consent.

### Step 1: Create the co-authorized consent

Use the **CarePulse** application's client-credentials token and the purpose and element IDs from [Define a purpose and its data element](#define-a-purpose-and-its-data-element). Include both **Priya** and **Raj** in the `authorizations` list.

The access token must include the `internal_consent_mgt_consent_create` scope. For details, see the [Consent Management v2 API reference](https://is.docs.wso2.com/en/latest/apis/consent-management-v2-api-definition/).

```bash
curl -X POST \
  "${BASE_URL}/api/identity/consent-mgt/v2.0/consents" \
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
      { "userId": "priya@example.com", "type": "USER" },
      { "userId": "raj@example.com", "type": "USER" }
    ],
    "properties": {}
  }'
```

### Step 2: Approve as Priya

1. Sign in as **Priya** and open **My Consents**, filtered to **Managed**.
2. Approve **Priya**'s own authorization entry.
3. Reopen the consent and confirm that **Priya**'s decision is recorded. The overall consent remains `PENDING` until **Raj** also makes his decision.

### Step 3: Approve as Raj

1. Sign in as **Raj** and open the same consent from **My Consents**, filtered to **Managed**, or from **My Pending Consents**.
2. Review the consent and approve **Raj**'s own authorization entry.

### Step 4: Verify the result

Reopen the consent as **Priya** or **Raj**. With both authorizations `APPROVED`, the aggregate consent becomes `ACTIVE`. Both named authorizers are required to approve independently before it becomes active.

This differs from the other models:

- **Direct Consent:** no named authorizers; the consent becomes `ACTIVE` immediately.
- **Delegated Consent:** the subject is not a named authorizer; another person makes the authorization decision.
- **Co-authorized Consent:** the subject and at least one other person are named authorizers and must each make a decision.

## Next Steps

- **[Learn: Understanding Consent](../learn/consent.md):** the concepts behind these flows.
- **[Try Out: Complaint Management](complaint.md):** submit and resolve a complaint.
- **[Try Out: Event Notifications](event.md):** notify connected systems when a consent changes.
