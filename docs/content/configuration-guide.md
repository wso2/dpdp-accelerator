---
title: Configuring the Consent Portal application
sidebar_position: 2
---

# Configuring the Consent Portal application

Complete this after installing the accelerator and starting the Identity
Server — see [`setup-guide.md`](setup-guide.md) if you haven't done that yet.

The portal is a single page application. It has no backend of its own: it
signs the user in with OpenID Connect and calls the Identity Server's consent
management APIs directly, the same way the built-in My Account application
works. There is **no client secret to configure and nothing to register** —
the application is provisioned automatically, the same way My Account is.

One deployed application serves every tenant, at
`https://<host>:9443/consent-portal/` for the super tenant and
`https://<host>:9443/t/<tenant>/consent-portal/` for the rest, all sharing the
client id `DPDP_CONSENT_PORTAL`.

## 1. The application is provisioned automatically

The moment a tenant exists — including the super tenant, on first server
startup — the accelerator registers **DPDP Consent Portal** in it directly,
with no operator step and no REST call involved:

| Setting | Value | Why |
|---|---|---|
| Public client | enabled | A single page application cannot keep a secret. |
| PKCE | mandatory | Proves the authorization code was requested by this app. |
| Access token binding | `cookie` | Ties the token to a cookie the page cannot read. |
| Validate token bindings | enabled | A token lifted out of the browser is rejected. |
| Revoke tokens on logout | enabled | Signing out invalidates the tokens immediately. |

It also authorizes the consent management, consent-history, event-notification
and complaint-management APIs (RBAC), and creates two roles. `dpdp-consent-admin`
holds every consent management scope, the consent-history "any" scopes, the
event-notification scopes, and the complaint management API's two "any" scopes
(`complaints:read:any`, `complaints:write:any`) — viewing and managing every
complaint in the org, including internal notes and status transitions.
`dpdp-consent-user` holds `account:self:delete` (see
[Self-service account deletion](#7-self-service-account-deletion)) plus the
complaint API's two "self" scopes (`complaints:read:self`,
`complaints:write:self`) — the rest of what it needs, the `internal_consent_mgt_*`
scopes for managing one's own consents, comes from Identity Server's own default
role configuration rather than from this role at all.

Provisioning checks each of these — application, API authorization, and each
role — individually, creating what's missing and adding any permission a role
is still short of, so it's always safe to re-run (see
[Recovering a broken tenant](#3-recovering-a-broken-tenant) below).

## 2. Change or turn off the auto-provisioning

Two settings in `deployment.toml` control this, under `[dpdp_accelerator.consent_portal]`:

```toml
[dpdp_accelerator.consent_portal]
auto_provisioning_enabled = true
client_id = "DPDP_CONSENT_PORTAL"
```

| Setting | Default | Change it if... |
|---|---|---|
| `auto_provisioning_enabled` | `true` | You want to manage the application and its roles by hand instead. Set to `false`. This only turns off the automatic *creation* of the application and roles — it does not disable the portal or sign-in. |
| `client_id` | `DPDP_CONSENT_PORTAL` | You're changing it, you **must** also update `clientID` in the deployed portal's own `deployment.config.json` — the two have to match or sign-in breaks. |

Edit the value in the accelerator's
`repository/resources/wso2is-7.3.0-deployment.toml` before running
`configure.sh` (see [`setup-guide.md`](setup-guide.md)), or directly in
`<IS_HOME>/repository/conf/deployment.toml` afterwards. Either way, restart
the server for the change to take effect.

## 3. Recovering a broken tenant

If a tenant's portal application or roles get deleted or corrupted, restore
them without a server restart:

1. In the Console, delete the **DPDP Consent Portal** application for that
   tenant (Roles are left alone even if the application is gone — deleting
   them too is optional, but harmless, since provisioning recreates whatever
   it doesn't find).
2. Update any property of the tenant (Console → **Tenant Management** → the
   tenant → **Update**).

Saving the update re-runs provisioning for that tenant, recreating the
application and any missing role.

The same step is how a tenant provisioned by an older version of the
accelerator picks up a newly introduced scope: re-running provisioning adds
whatever permissions its existing roles are missing, without recreating the
roles or touching any permission an operator granted by hand. A tenant created
before self-service account deletion existed gets `account:self:delete` on
`dpdp-consent-user` this way — no restart, no role deletion.

## 4. Assign portal roles

Roles are assigned in the Console under **User Management → Users → *user* →
Roles**. Roles belong to one tenant, so do this in each tenant.

**Signing in and managing your own consents needs no role at all.** Every
authenticated user gets `internal_login`, and the self-service consent API
scopes every call to the caller, so a user with no portal role can sign in,
see their dashboard and manage their own consents. The two roles below grant
what is *beyond* that.

| Role | Assign to | Grants |
|---|---|---|
| `dpdp-consent-user` | Regular users | Deleting their own account, and reading/writing their own complaints (`complaints:read/write:self`). Neither is needed for self-service consent management, which works without any role. |
| `dpdp-consent-admin` | Administrators | Administering *other people's* consents, editing the purpose and element catalog, and reading/writing *any* complaint in the org (`complaints:read/write:any`), including internal notes and status transitions. **Not** self-service account deletion, which is `dpdp-consent-user` only. |

> **Users who don't hold `dpdp-consent-user` will not see "Delete my
> account".** The option is gated on the `account:self:delete` scope that
> only this role grants, so assign it to every user who should be able to
> delete their own account. Before self-service deletion existed this role
> granted nothing, so accounts created earlier are unlikely to hold it —
> check rather than assume.

> **Assigning both roles to one user re-enables self-deletion for them.** The
> two roles' permissions add up, so an administrator who also holds
> `dpdp-consent-user` receives `account:self:delete` and can delete their own
> account. Keep administrators out of `dpdp-consent-user` if that matters —
> they lose nothing else by not holding it.

## 5. Configure email notifications

The Consent Portal can send email notifications through an SMTP server.
Configure the SMTP sender settings in the Identity Server's
`deployment.toml`.

For Gmail or Google Workspace, use the following configuration:

```toml
# SMTP email sender settings.
[output_adapter.email]
from_address = "abc@gmail.com"
username = "abc@gmail.com"
password = "<GMAIL_APP_PASSWORD>"
hostname = "smtp.gmail.com"
port = 587
```

### Configure the recipient's primary email

The user's **primary email address** must be configured in their user profile
for the user to receive email notifications.

In the Console:

1. Go to **User Management → Users**.
2. Select the user.
3. Open the user's profile.
4. Add or update the user's **Primary Email** address.
5. Save the changes.

Make sure the primary email address is valid and accessible. Notifications
sent to the user will be delivered to the configured primary email address.

## 6. Open the portal

| Tenant | URL |
|---|---|
| Super tenant | `https://<host>:9443/consent-portal/` |
| Any other tenant | `https://<host>:9443/t/<tenant>/consent-portal/` |

No restart is needed.

The accelerator's `deployment.toml` already carries the tenant rewrite
configuration that makes the tenant-qualified URL resolve to the deployed
webapp, so there is nothing to configure for multi-tenancy beyond registering
each tenant above. Consents, catalog data, roles and sessions are all
partitioned per tenant by the server.

## 6. Configuring periodical consent expiration

The Identity Server already treats a consent as expired the moment its
`expiryTime` passes — any API call that reads the consent reflects this
automatically. This job only adds the missing **history record** for that
transition; it never changes the consent itself.

Configure it under `[dpdp_accelerator.consent_expiry]` in `deployment.toml`:

```toml
[dpdp_accelerator.consent_expiry]
enabled = true
cron_value = "0 0 0 * * ?"
batch_size = 100
```

| Setting | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Turns the scheduled job on or off. |
| `cron_value` | `"0 0 0 * * ?"` | Quartz cron expression for how often the job checks for newly-expired consents. The default runs once daily at midnight. |
| `batch_size` | `100` | Maximum number of expired consents recorded per run, so a large backlog drains gradually instead of in one long transaction. |

Edit these before running `configure.sh`, or directly in
`<IS_HOME>/repository/conf/deployment.toml` afterwards, and restart the
server for the change to take effect.

### Clustering requirements

By default, with no extra setup, the job runs correctly on a single server.
**In a cluster, this default is not safe as-is**: without further
configuration, every node runs its own independent, in-memory copy of the
job, so it fires once *per node* instead of once for the whole cluster on
each scheduled tick.

To run this job correctly across a cluster:

1. Copy the sample `repository/conf/samples/quartz.properties` shipped with
   the accelerator to `<IS_HOME>/repository/conf/quartz.properties` on
   **every** node in the cluster. Unlike a hand-written Quartz config, this
   sample does not need to be edited per node — it uses
   `org.quartz.scheduler.instanceId = AUTO`, so each node identifies itself
   automatically.
2. Create Quartz's own clustering tables (`QRTZ_*`) in the accelerator's
   `WSO2DPDP_DB` database, once, using the DDL scripts published by the
   Quartz project itself for version 2.3.x
   (`https://github.com/quartz-scheduler/quartz/tree/quartz-2.3.x/quartz-core/src/main/resources/org/quartz/impl/jdbcjobstore`)
   — pick the script matching your database (H2, MySQL, PostgreSQL, etc.).
   These tables are not created by `configure.sh`; apply them the same way
   you would apply any other third-party schema.
3. Restart every node.

With this in place, Quartz coordinates through the shared database so that
exactly one node executes the job on each scheduled tick, no matter how many
nodes are running. To confirm it's working, check the logs after a
scheduled run — only one node should log the job firing, not all of them.

## 7. Self-service account deletion

A user holding `dpdp-consent-user` sees **Delete my account** in the portal's
profile menu, beside Sign out. Confirming it calls `DELETE /scim2/Me`, clears
the browser session and lands the user on a public confirmation page. The
deletion is immediate and irreversible — unless an approval workflow is
configured for the operation, in which case it becomes a request; see
[With an approval workflow on Delete User](#with-an-approval-workflow-on-delete-user).

Users without that role do not see the option, and the portal is perfectly
usable without it — so assigning it is a deliberate step, not something
existing accounts have already. See [Assign portal roles](#4-assign-portal-roles).

### How it is restricted

Identity Server protects `DELETE /scim2/Me` with `internal_user_mgt_delete` by
default — a scope that *also* authorizes `DELETE /scim2/Users/{id}`, so
granting it to portal users would let any one of them delete anybody. The
accelerator's `deployment.toml` therefore overrides that one endpoint to
require a much narrower scope instead:

```toml
[[resource.access_control]]
context = "(.*)/scim2/Me"
allowed_auth_handlers = ["OAuthAuthentication"]
secure = "true"
http_method = "DELETE"
scopes = ["account:self:delete"]
```

Tenant provisioning registers `account:self:delete`, authorizes the portal
application for it, and grants it through the `dpdp-consent-user` role only.
`internal_user_mgt_delete` is never granted to portal users, so
`DELETE /scim2/Users/{id}` stays administrator-only.

**The scope check on the token is the enforcement.** An administrator's token
does not carry `account:self:delete`, so the server answers their
`DELETE /scim2/Me` with a 403 whether it arrives from the portal, curl, or
anywhere else. The portal hiding the menu item for them is a convenience on
top of that, not the control itself.

### With an approval workflow on Delete User

If an approval workflow is associated with the **Delete User** operation, the
account is not deleted when the user confirms. The Identity Server records a
request and answers `202` with *"User deletion has sent for the approval"*,
and the account stays fully usable — the user can keep working and can sign in
again — until an approver acts.

The portal tells the two outcomes apart by the status code and says which one
happened, because it has no way of knowing in advance whether a workflow is
configured:

| Response | What the portal does |
|---|---|
| `204` | Clears the session and shows the account-deleted page. |
| `202` | Keeps the user signed in and reports that the request is awaiting approval. |
| `400` | Reports that a deletion request is already awaiting approval — the server refuses a second one while the first is pending. |

Approvers act on the request in **My Account** (`/myaccount`) under its
approvals section — accept or reject it there. The Console's **Workflow
Requests** page is a monitoring view: it lists requests and can abort one, but
it does not offer an approve action.

The portal cannot show a user that their own request is pending: the
workflow-request APIs are administrative, and there is no self-service
endpoint for "my pending requests". A user who tries again simply gets the
`400` message above.

### What this does and does not cover

- It prevents administrators deleting **their own** account *through the
  portal*, which is what would otherwise risk leaving a tenant with no
  administrator. It does not restrict Identity Server administration: anyone
  holding `internal_user_mgt_delete` can still delete any account, their own
  included, via `/scim2/Users/{id}` and the Console. That is unchanged and
  intended.
- A user holding both portal roles *can* self-delete — see the note in
  [Assign portal roles](#4-assign-portal-roles).
- **The user's DPDP data is not cleaned up.** Deleting the account removes the
  user from the user store; their consent records and event subscriptions stay
  behind, now referencing a user that no longer exists. Purging or anonymising
  that data is a separate operator task today.

### Deployments that override the requested scopes

If your deployment ships its own `scope` array in the portal's
`deployment.config.json` rather than using the shipped one, add
`account:self:delete` to it. A scope the application never asks for is a scope
the token never carries, and the menu item stays hidden.

# Configuring Event Notifications

Event Notification Framework runtime settings are configured in the same
`deployment.toml` file under `[dpdp_accelerator.event_notifications]` and its
`[dpdp_accelerator.event_notifications.webhook]` sub-table. The accelerator
provisions these values into `dpdp-accelerator.xml`; the ENF configuration
component then maps them to the typed ENF configuration parser before the
delivery services activate.

For the user workflow—creating topics and subscriptions, preparing a webhook,
publishing events, and viewing delivery history—see
[`event-notification-guide.md`](event-notification-guide.md).

```toml
[dpdp_accelerator.event_notifications]
system_topics_auto_create_enabled = true

[dpdp_accelerator.event_notifications.webhook]
thread_pool_size = 4
base_backoff_seconds = 5
max_retries = 5
allow_http_callback_url = true
allowed_callback_ports = "-1,80,443,8443"
allow_private_network_callback_targets = false
delivery_worker_batch_size = 50
delivery_worker_poll_seconds = 5
stuck_inflight_threshold_seconds = 10
max_verification_response_body_bytes = 4096
pending_subscription_recovery_threshold_seconds = 60
```

These are server-wide runtime settings. Subscription `shared_secret` values
remain per-subscription data and are not placed in `dpdp-accelerator.xml`.
