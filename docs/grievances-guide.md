# Managing grievances

The DPDP Accelerator provides a tenant-scoped grievance (complaint) service for
Data Principals and the people who handle their cases. The Consent Portal is
the ready-made user interface; the same operations are available through the
complaint API for an application or an integration.

Complete the [Quickstart](quickstart.md) first. Assign users and roles as
described in the [Role Management Guide](role-guide.md), then sign in again so
new role scopes are present in their access tokens.

## 1. Understand the two access surfaces

The API has two deliberately separate namespaces:

| Surface | Who uses it | What it permits |
|---|---|---|
| `/me/complaints/*` | The authenticated Data Principal | Create, view, reply to, and add public attachments to their own complaints |
| `/complaints/*` | A complaint officer, DPO, administrator, or trusted system | Search and manage complaints across the tenant, add public replies or internal notes, change status, and manage attachments |

The service derives the Data Principal from the bearer token on the `me`
surface. Do not put a `userId` in a self-service request. A complaint officer
can create a complaint on behalf of a Data Principal through the management
surface when a complaint was received by phone, in person, or on paper.

All complaints and attachments are isolated by the Identity Server tenant. Use
the tenant-qualified URL for an ordinary tenant:

```text
https://<host>:9443/t/<tenant-domain>/api/dpdp/complaints/v1
```

For the super tenant, omit `/t/<tenant-domain>`.

The examples below use these shell variables:

```sh
BASE_URL="https://localhost:9443"
TENANT_DOMAIN="example.com"
ACCESS_TOKEN="<data-principal-access-token>"
OFFICER_ACCESS_TOKEN="<complaint-officer-access-token>"
```

## 2. Assign the required roles and scopes

The automatically provisioned roles grant these complaint permissions:

| Operation | Scope | Role |
|---|---|---|
| Create, list, view and reply to own complaints | `complaints:read:self`, `complaints:write:self` | `dpdp-consent-user` |
| List, view and manage all complaints | `complaints:read:any`, `complaints:write:any` | `dpdp-consent-dpo` or `dpdp-consent-admin` |

The `dpdp-consent-admin` role also grants catalog, consent, and Event
Notification administration. The DPO role is limited to organization-wide
complaint handling. For machine-to-machine use, assign the two `complaints:*`
scopes to a dedicated integration role instead of using a portal administrator
role.

## 3. Configure deadlines and attachments

Set these values in `deployment.toml` under
`[dpdp_accelerator.complaints]`:

```toml
statutory_due_period_days = 90
attachment_max_size_bytes = 10485760
attachment_max_files_per_upload = 5
```

The due date is calculated from submission time. Each attachment must be a PDF,
DOCX, PNG, or JPEG and must stay within the configured size limit. An upload
request can contain up to the configured number of files. Restart Identity
Server after changing these server-side limits.

## 4. Submit and track a grievance in the portal

**Portal:** Sign in as a user with `dpdp-consent-user`, open **My Complaints**,
and select **Submit New Complaint**.

1. Choose a category and describe the issue without unnecessary personal data.
2. Optionally add one or more supported files within the configured limits.
3. Submit the complaint and retain its generated reference ID.
4. Open the case to see its status, statutory due date, public activity, and
   public attachments.
5. Use the reply composer to send a public follow-up. If the case is waiting
   for information, the portal sends the transition to
   `AWAITING_INTERNAL_REVIEW` with the reply.

The self-service create request is:

```bash
curl -k -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/dpdp/complaints/v1/me/complaints" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "subjectCategory": "DATA_ACCESS_DENIED",
    "description": "I cannot obtain the information associated with my consent."
  }'
```

The server derives `userId` from the token and returns `201 Created` with the
complaint ID, reference ID, category, priority, status, description,
timestamps, and statutory due date. Upload evidence afterward with multipart
form data; self-service uploads are always public:

```bash
curl -k -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/dpdp/complaints/v1/me/complaints/<complaint-id>/attachments" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -F "file=@incident-screenshot.png;type=image/png" \
  -F "file=@supporting-report.pdf;type=application/pdf"
```

Use `GET /me/complaints/<complaint-id>/timeline` to retrieve the public
activity and the attachments associated with each timeline entry. Internal
officer notes are never returned on this surface.

## 5. Handle a grievance in Complaint Management

**Portal:** Sign in as a DPO or administrator and open **Complaint Management**.

1. Find the case by reference ID, Data Principal, status, or priority.
2. Open it to inspect the description, attachments, and complete activity
   timeline.
3. Send a public reply when the Data Principal should see the message.
4. Add an **Internal Note** for officer-only information.
5. Use the status menu to choose a permitted next state.
6. Resolve the case after the review is complete, then confirm that the public
   view contains no internal note.

The officer endpoint can add a public reply and transition the case in one
request:

```bash
curl -k -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/dpdp/complaints/v1/complaints/<complaint-id>/comments" \
  -H "Authorization: Bearer ${OFFICER_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "We are reviewing your request.",
    "isPublic": true,
    "toStatus": "IN_PROGRESS"
  }'
```

Set `isPublic` to `false` for an internal note. Management attachments may be
marked public or internal with the multipart `isPublic` field. Only public
entries and attachments are visible to the Data Principal.

## 6. Follow the permitted status lifecycle

The service validates every requested transition:

| Current status | Permitted next status |
|---|---|
| `OPEN` | `IN_PROGRESS`, `WAITING_ON_CLIENT` |
| `IN_PROGRESS` | `WAITING_ON_CLIENT`, `RESOLVED` |
| `WAITING_ON_CLIENT` | `AWAITING_INTERNAL_REVIEW` |
| `AWAITING_INTERNAL_REVIEW` | `IN_PROGRESS`, `WAITING_ON_CLIENT`, `RESOLVED` |
| `RESOLVED` | `AWAITING_INTERNAL_REVIEW` through a valid self-service API transition |

The portal hides resolved cases from the officer queue by default, but a Data
Principal can still post a public reply to a resolved case. The current portal
reply action does not automatically reopen it; use the self-service status API
explicitly when a reopening workflow is required.

For example, a Data Principal can request the valid reopening transition with:

```bash
curl -k -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/dpdp/complaints/v1/me/complaints/<complaint-id>/status" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"toStatus":"AWAITING_INTERNAL_REVIEW"}'
```

An attempt to skip a required transition returns the complaint error response
with HTTP `409`.

## 7. Protect tenant and personal data

- Use a token issued for the same tenant as the URL.
- Use `me` endpoints for user-driven actions so identity is resolved server-side.
- Keep internal notes and internal attachments off the public surface.
- Store the reference ID, not unnecessary personal data, in support workflows.
- Treat uploaded files as personal data and protect them in transit and at rest.

The management API returns the full timeline for authorized officers; the
self-service timeline filters out internal entries. A caller who does not own a
complaint receives no confirmation that another user's complaint ID exists on
the self-service surface.

## 8. Troubleshoot common problems

| Symptom | Check |
|---|---|
| `403 Forbidden` | The token lacks the required `complaints:read:*` or `complaints:write:*` scope, or the user has not signed in again after role assignment. |
| `404 Not Found` on `/me` | The complaint belongs to another user, or the ID is incorrect. |
| Attachment rejected | Confirm the MIME type, file size, and number of files against the configured limits. |
| `409` on a status change | The requested target is not permitted from the current status. |
| Internal note visible to a Data Principal | Verify that the note was posted through `/complaints/*` with `isPublic: false`, and that the reader is using the `/me/*` timeline. |

For a complete end-to-end walkthrough, see [Tryout Flows](tryout-flows.md).
For server-side deadline and upload settings, see
[Configuration Guide](configuration-guide.md#6-configure-complaint-management).
