---
title: Complaints
---

# Complaint Management

Explore how the WSO2 DPDP Accelerator supports complaint management, from submission by a Data Principal to investigation and resolution by a Data Protection Officer (DPO). The example continues the **CarePulse** (Data Fiduciary) story: **Priya** raises a complaint, and **Ravi** handles it.

![Grievance flow from Data Principal submission through complaint-officer review, communication, and resolution](../../assets/images/diagrams/dpdp-grievance-flow.svg)

For the complete list of endpoints and schemas, see the [Complaint Server API specification](https://github.com/wso2/dpdp-accelerator/blob/main/dpdp-accelerator/internal-webapps/org.wso2.dpdp.accelerator.complaint.mgt.endpoint/src/main/resources/complaint-server-API.yaml).

## Before you begin

- Ensure WSO2 Identity Server with the DPDP Accelerator is installed and running.
- Create these users and assign their roles, as described in [Configuring Users and Roles](../install-and-setup/configuring-users-and-roles.md):
  - **Priya** (Data Principal), username `priya@example.com`: `dpdp-consent-user`
  - **Ravi** (Data Protection Officer), username `ravi@example.com`: `dpdp-consent-dpo`
- [Create one application](../learn/managing-access.md#step-1-create-an-application) for **CarePulse** and [authorize it](../learn/managing-access.md#step-2-authorize-the-required-scopes) for the **DPDP Complaint Management** API, with the `complaints:read:any` and `complaints:write:any` scopes.

This guide primarily uses the Consent Portal. REST API examples are also provided for the officer's operations.

Before running the API examples, set the base URL and access token. [Obtain a client-credentials token](../learn/managing-access.md#step-3-get-an-access-token) using the **CarePulse** application's credentials, with the `complaints:read:any` and `complaints:write:any` scopes.

```bash
export BASE_URL="https://localhost:9443"
export ACCESS_TOKEN="<carepulse-client-credentials-token>"
```

The base URL is for the super tenant. For a specific tenant, use `https://localhost:9443/t/<tenant-domain>`.

:::note Local testing with a self-signed certificate
Add `-k` to the `curl` commands if your local Identity Server uses the default self-signed certificate. Never use `-k` in production — configure a trusted CA certificate, or pass it with `--cacert`.
:::

## Step 1: Submit a complaint as Priya (Data Principal)

1. Sign in to the Consent Portal as **Priya** and open **Complaints → My Complaints**.
2. Select **+ Submit a complaint** and enter the details:
   - **Category:** for example, **Consent withdrawal issue**.
   - **Description:** explain the issue without including unnecessary sensitive personal data. For example: *"I revoked my consent for marketing purposes, but I am still receiving promotional messages."*
   - **Attachments (optional):** upload one file as supporting evidence, such as a screenshot. Supported formats are PNG, JPG, PDF, and DOCX, up to 10 MB. You can add more evidence later in follow-up messages, one file per message.
3. Select **Submit complaint**.

   ![Submit a complaint dialog](../../assets/images/try-out/complaints/submit-complaint-modal.png)

4. Confirm that the complaint appears in **My Complaints** with a reference ID (for example, `CMP-2026-00001`), status, and submission date.

   ![Complaint submitted confirmation and table entry](../../assets/images/try-out/complaints/complaint-submitted-success.png)

5. Open the complaint to review its status and statutory due date. The **Activity** tab shows the timeline and supports public follow-up messages; the **Attachments** tab lists the submitted evidence.

   ![Data Principal complaint detail view](../../assets/images/try-out/complaints/complaint-details-principal-view.png)

An application can also submit a complaint on **Priya**'s behalf, using her own user token, through `POST /api/dpdp/complaints/v1/me/complaints`. See the [Complaint Server API specification](https://github.com/wso2/dpdp-accelerator/blob/main/dpdp-accelerator/internal-webapps/org.wso2.dpdp.accelerator.complaint.mgt.endpoint/src/main/resources/complaint-server-API.yaml) for the request format.

## Step 2: Investigate and communicate as Ravi (Data Protection Officer)

### Using the Consent Portal

1. Sign in as **Ravi** and open **Administration → Complaints**.

   ![Complaint queue under Administration > Complaints](../../assets/images/try-out/complaints/officer-complaints-queue.png)

2. Find the complaint by its reference ID and open it.
3. Review the complaint details, due date, and evidence in the **Attachments** tab.
4. Use **Reply** to send a message visible to **Priya**, such as *"We are reviewing your request."* Use **Internal note** for investigation notes that only officers can see.

   ![DPO's complaint view with Reply and Internal note options](../../assets/images/try-out/complaints/officer-complaint-handling.png)

5. Sign back in as **Priya** and verify that the public reply appears in the activity timeline and the internal note is not visible.

### Using the REST API (optional)

Use the **CarePulse** application's client-credentials token for officer-level requests. The token must include the `complaints:read:any` and `complaints:write:any` scopes. Replace `<complaint-id>` with the complaint's ID.

Retrieve the complaint details, including **Priya**'s description and any associated attachments:

```bash
curl \
  "${BASE_URL}/api/dpdp/complaints/v1/complaints/<complaint-id>" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Post a public reply and move the complaint to `IN_PROGRESS` in one request:

```bash
curl -X POST \
  "${BASE_URL}/api/dpdp/complaints/v1/complaints/<complaint-id>/comments" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "We are reviewing your request.",
    "isPublic": true,
    "toStatus": "IN_PROGRESS"
  }'
```

To add an internal note instead, set `"isPublic": false`. Internal notes are visible to officers only.

## Step 3: Resolve the complaint

1. As **Ravi**, open the complaint and use the status menu beside the comment composer.
2. Select **Resolved**, enter a final message describing the outcome, and submit it.
3. Confirm that the complaint shows **Resolved**.

   ![Final resolved view of the complaint](../../assets/images/try-out/complaints/complaint-final-resolved-view.png)

4. Sign back in as **Priya** and verify that the resolution message and status appear in **My Complaints**.

To resolve the complaint through the API, send the same `comments` request with `"toStatus": "RESOLVED"` and a message describing the outcome. A message is required when resolving.

## Next Steps

- **[Learn: Understanding Complaints](../learn/complaint.md):** the concepts behind complaint handling.
- **[Try Out: Event Notifications](event.md):** notify connected systems when a consent changes.
- **[Developer Guide: Grievances](../grievances-guide.md):** full API details, attachment limits, and email template customization.
