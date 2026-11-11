# Managing grievances

This reference covers the complaint (grievance) API: its two access surfaces, the requests an application or integration makes, and how to customize complaint emails. For the concepts and the status lifecycle, see [Understanding Complaints](learn/complaint.md). For a step-by-step walkthrough in the Consent Portal, see [Try Out: Complaints](try-out/complaint.md).

For every endpoint and schema, see the [Complaint Server API specification](https://github.com/wso2/dpdp-accelerator/blob/v1.0.0/dpdp-accelerator/internal-webapps/org.wso2.dpdp.accelerator.complaint.mgt.endpoint/src/main/resources/complaint-server-API.yaml).

## 1. Understand the two access surfaces

The API has two deliberately separate surfaces under `https://<host>:9443/api/dpdp/complaints/v1` (add `/t/<tenant-domain>` after the port for a specific tenant):

| Surface | Who uses it | Scopes | What it permits |
|---|---|---|---|
| `/me/complaints/*` | The signed-in Data Principal, using their own user token | `complaints:read:self`, `complaints:write:self` | Create, view, and reply to their own complaints, and add public attachments |
| `/complaints/*` | A Data Protection Officer (DPO) or a trusted system | `complaints:read:any`, `complaints:write:any` | Search and manage every complaint in the tenant, add public replies or internal notes, change status, and manage attachments |

- The `me` surface derives the Data Principal from the token, so never put a `userId` in a self-service request. A caller asking for someone else's complaint gets `404`, with no hint that the ID exists.
- The `dpdp-consent-user` role grants the self-service scopes and `dpdp-consent-dpo` grants the management scopes. `dpdp-consent-admin` has no complaint scopes. For a system integration, authorize a dedicated application for only the scopes it needs. See [Managing Access](learn/managing-access.md#complaints).
- A DPO can create a complaint on a Data Principal's behalf through `POST /complaints`, with the `userId` in the body, when the complaint arrives by phone, in person, or on paper.
- Complaints and attachments are isolated by tenant. Use a token issued by the same tenant as the URL.

Deadlines, attachment limits, and email notifications are set in `deployment.toml`. See [Configure complaint management](install-and-setup/configuring-the-accelerator.md#configure-complaint-management).

The examples use these variables:

```bash
export API_BASE="https://localhost:9443/api/dpdp/complaints/v1"
export USER_TOKEN="<data-principal-access-token>"
export DPO_TOKEN="<dpo-or-integration-access-token>"
```

## 2. Create a complaint

List the categories, each with the priority a new complaint in it receives:

```bash
curl "${API_BASE}/me/complaints/categories" \
  -H "Authorization: Bearer ${USER_TOKEN}"
```

The categories are `DATA_BREACH`, `UNAUTHORIZED_DATA_SHARING`, `CONSENT_WITHDRAWN_DATA_STILL_USED`, `PURPOSE_VIOLATION`, `DATA_ERASURE_NOT_COMPLETED`, `DATA_CORRECTION_NOT_COMPLETED`, `CONSENT_LIFECYCLE_ISSUE`, `DATA_ACCESS_DENIED`, `EXCESSIVE_DATA_COLLECTION`, and `OTHER`.

Create the complaint with the Data Principal's own token:

```bash
curl -X POST "${API_BASE}/me/complaints" \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "subjectCategory": "CONSENT_WITHDRAWN_DATA_STILL_USED",
    "description": "I revoked my consent for marketing emails, but I still receive them."
  }'
```

The response is `201 Created`, with the complaint ID, reference ID, priority, status (`OPEN`), and statutory due date.

## 3. Add attachments

Upload evidence as multipart form data. Each file must be a PDF, DOCX, PNG, or JPEG within the configured size limit, and one request can carry up to `attachment_max_files_per_upload` files (5 by default). The Consent Portal sends one file per upload.

Self-service uploads are always public:

```bash
curl -X POST "${API_BASE}/me/complaints/<complaint-id>/attachments" \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  -F "file=@incident-screenshot.png;type=image/png"
```

On the management surface, the `isPublic` part is required and must be sent as `text/plain`. Without that content type, the value can't be read and the upload is stored as internal:

```bash
curl -X POST "${API_BASE}/complaints/<complaint-id>/attachments" \
  -H "Authorization: Bearer ${DPO_TOKEN}" \
  -F "file=@investigation-notes.pdf;type=application/pdf" \
  -F "isPublic=false;type=text/plain"
```

## 4. Reply, add internal notes, and change status

A DPO can post a public reply and change the status in one request:

```bash
curl -X POST "${API_BASE}/complaints/<complaint-id>/comments" \
  -H "Authorization: Bearer ${DPO_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "We are reviewing your request.",
    "isPublic": true,
    "toStatus": "IN_PROGRESS"
  }'
```

- Set `"isPublic": false` for an internal note. Internal notes and internal attachments never appear on the `me` surface.
- Moving a complaint to `RESOLVED` requires a message describing the outcome. `POST /complaints/<complaint-id>/status` changes the status without a comment, and takes `toStatus` and a required `note`.
- Every transition is checked against the permitted transitions listed in [How status transitions work](learn/complaint.md#how-status-transitions-work). A transition that isn't permitted returns `409` (`CO-4090`).

The Data Principal replies through `POST /me/complaints/<complaint-id>/comments`, with a `message` and an optional `toStatus`. When the complaint is `WAITING_ON_CLIENT` or `RESOLVED`, the Consent Portal sends `"toStatus": "AWAITING_INTERNAL_REVIEW"` with the reply. This is how a resolved complaint is reopened. An application on the `me` surface should do the same.

Use `GET /me/complaints/<complaint-id>/timeline` for the public activity and its attachments, and `GET /complaints/<complaint-id>/timeline` for the full activity, including internal notes.

## 5. Customize complaint notification emails

Complaint emails are off by default. To send them, set `email_notifications_enabled = true` and configure the email sender, as described in [Configure complaint email notifications](install-and-setup/configuring-the-accelerator.md#configure-complaint-email-notifications).

`ComplaintCreated`, `ComplaintCommentAdded`, and `ComplaintAcknowledged` are standard Identity Server notification templates. Edit them per tenant in the Console under **Email Templates**.

Placeholders: `{{reference-id}}`, `{{message-excerpt}}`, `{{data-principal-name}}`, `{{actor-name}}`, `{{category-label}}`, `{{priority-label}}`, `{{status-label}}`, `{{sla-label}}`, `{{action-url}}`, `{{recipient-role-label}}`, `{{headline-html}}`, `{{footer-text}}`, `{{action-badge-html}}`, `{{logo-url}}`.

A template is written once, when the tenant is first provisioned, and never rewritten, so a Console edit is permanent. The bundled default comes from `<IS_HOME>/repository/conf/email/email-dpdp-config.xml`. Editing that file needs no rebuild, but only affects tenants provisioned afterward. The file is read once and cached, so **restart the server after editing it**.

## 6. Troubleshoot common problems

| Symptom | Check |
|---|---|
| `403 Forbidden` | The token lacks the operation's scope: `complaints:read:self` or `complaints:write:self` on `/me/*`, and `complaints:read:any` or `complaints:write:any` on `/complaints/*`. After assigning a role, sign in again or get a new token. |
| `404 Not Found` on `/me` | The complaint belongs to another user, or the ID is incorrect. |
| `400` when resolving | Moving to `RESOLVED` needs a `message` (comments) or a `note` (status endpoint). |
| `409` on a status change | The target status isn't permitted from the current status. |
| Attachment rejected | Check the file type, the file size, and the number of files in the request against the configured limits. |
| A management upload is internal when it should be public | Send the `isPublic` part as `text/plain`, for example `-F 'isPublic=true;type=text/plain'`. |
| No complaint emails | `email_notifications_enabled` is `false` by default, and the email sender must also be configured. |
