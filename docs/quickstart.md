# DPDP Accelerator quickstart

Use this guide to install the accelerator with its embedded H2 database, open
the Consent Portal for an ordinary tenant, and confirm that a predefined
lifecycle event is published automatically. Follow the linked reference guides
before using the deployment in production.

## Prerequisites

- WSO2 Identity Server 7.3.0 with the mandatory U2 updates applied
- JDK 21 or later
- A released `wso2-dpdp-is-accelerator-<version>.zip`, or a ZIP built from the
  repository with `mvn clean install`

The extracted Identity Server directory is referred to as `<IS_HOME>` below.

## 1. Install the accelerator

Extract the accelerator ZIP, enter its directory, and run the two installation
scripts while Identity Server is stopped:

```sh
sh bin/merge.sh <IS_HOME>
sh bin/configure.sh <IS_HOME>
```

The quickstart uses the default H2 configuration. `configure.sh` backs up and
then replaces `<IS_HOME>/repository/conf/deployment.toml`; review that backup
before using the same process on an existing deployment.

Expected result: both scripts finish successfully, and `configure.sh` reports
that the DPDP schema was created in `WSO2DPDP_DB`.

For source builds, external databases, and configuration replacement details,
see the [Setup Guide](setup-guide.md).

## 2. Start Identity Server

```sh
sh <IS_HOME>/bin/wso2server.sh
```

Wait until the terminal reports that WSO2 Identity Server has started, then
open the Console:

```text
https://localhost:9443/console
```

## 3. Create an ordinary tenant

In the Console, create a tenant such as `example.com`. Use an ordinary tenant
for this quickstart because the five predefined Event Notification topics are
automatically provisioned for ordinary tenants, but not for `carbon.super` or
organization tenants.

Tenant creation automatically provisions:

- The **DPDP Consent Portal** application
- The **DPDP Consent API Invoker** application, when its independent
  auto-provisioning setting is enabled
- `dpdp-consent-user`
- `dpdp-consent-admin`
- `dpdp-consent-dpo`
- The five predefined Event Notification topics

If you use a tenant that existed before installing the accelerator, update any
tenant property once to run the same reconciliation. See
[Recovering a broken tenant](configuration-guide.md#3-recovering-a-broken-tenant).

## 4. Create users and assign portal access

Switch to the new tenant in the Console and open **User Management → Users**.
Create the users who will access the portal if they do not already exist, then
assign each user the appropriate role:

- `dpdp-consent-admin` for portal administrators, including the user who will
  verify the portal in this quickstart
- `dpdp-consent-user` for regular users who need personal consent history,
  complaint, or account-deletion features
- `dpdp-consent-dpo` for Data Protection Officers and complaint-handling users

The roles are created automatically, but users and role memberships are not.
After assigning a role, have the user sign out and sign in again so the new
access token contains the role's scopes.

See the [Role Management Guide](role-guide.md) before assigning roles. Basic
self-service consent management does not require a portal role.

## 5. Open the tenant portal

Open:

```text
https://localhost:9443/t/example.com/consent-portal/
```

Sign in as the tenant user holding `dpdp-consent-admin`. Confirm that the
**Event Notifications** navigation and its Topics and Events pages are visible.

## 6. Understand the predefined topics

Open **Event Notifications → Topics** to see the five system-managed topics.
With automatic lifecycle publication enabled, events are published to them as
follows:

| Topic | Automatic event |
|---|---|
| `consent.update` | A consent is updated or an authorization is approved or rejected. |
| `consent.revoke` | A consent or authorization is revoked. |
| `consent.expire` | A consent reaches its configured expiry. |
| `user.data.change` | A user's profile claims are changed. |
| `user.account.delete` | A user account is deleted. |

See the [Event Notification Guide](event-notification-guide.md) for
subscriptions, delivery modes, required user-event configuration, and
troubleshooting.

## Next steps

- [Tryout Flows](tryout-flows.md) — catalog, consent lifecycle, complaint,
  automatic event, and account-deletion walkthroughs
- [Grievances Guide](grievances-guide.md) — complaint submission, handling,
  attachments, visibility, and status transitions
- [Configuration Guide](configuration-guide.md) — production settings,
  application provisioning, expiry, account deletion, and Event Notification
  configuration
- [Role Management Guide](role-guide.md) — portal and integration roles
- [Event Notification Guide](event-notification-guide.md) — subscriptions,
  webhooks, polling, event signatures, and delivery completion
- [Localization Guide](localization-guide.md) — portal and catalog translations
