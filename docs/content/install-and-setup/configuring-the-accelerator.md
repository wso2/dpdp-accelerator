---
title: Configuring the DPDP Accelerator
sidebar_position: 2
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Configuring the DPDP Accelerator

This section covers the configuration options available for the DPDP Accelerator. 
All settings are configured in `<IS_HOME>/repository/conf/deployment.toml`. After making changes, restart WSO2 Identity Server for them to take effect.

## Server and administrator

Set these values according to your environment:

```toml
[server]
hostname = "<public host name of the Identity Server>"

[super_admin]
username = "<administrator username>"
password = "<administrator password>"
create_admin_account = true
```

With the accelerator's user-store configuration, usernames are email
addresses. Use an email address for the administrator as well.

See the Identity Server documentation on
[Encrypting passwords with the Cipher Tool](https://is.docs.wso2.com/en/7.3.0/deploy/security/encrypt-passwords-with-cipher-tool/)
to learn how to provide the administrator password as an encrypted secret.

## Consent configurations

These settings control how consent records are created, updated, and managed:

```toml
[consent_mgt]
enable_v2_api = true
revoke_active_consents_on_create = false
```

- `enable_v2_api` — Required for the accelerator. Keep this set to `true`.
- `revoke_active_consents_on_create` — Recommended: `false`. This prevents a new consent from automatically revoking previously active consents.

## Configure automatic application and role provisioning

By default, the accelerator automatically creates the portal application and
roles for each tenant. You only need to change these settings if you want to
manage them manually or use your own provisioning logic.

```toml
[dpdp_accelerator.consent_portal]
auto_provisioning_enabled = true
client_id = "DPDP_CONSENT_PORTAL"
```

- `auto_provisioning_enabled = false` — Disables automatic provisioning. You
  will need to create and manage the portal application and the three roles
  yourself.
- `client_id` — The client ID of the DPDP Consent Portal application. If you
  change it, make sure the same value is configured in the portal's
  `deployment.config.json` file at
  `<IS_HOME>/repository/deployment/server/webapps/consent-portal/deployment.config.json`.
  Both values must match for sign-in to work.

### Consent API Invoker provisioning

The accelerator can automatically provision a sample application for testing
consent flows through the REST API. You can use this application with the
[Try Out flows](../try-out/consent.md).

```toml
[dpdp_accelerator.consent_api_invoker]
auto_provisioning_enabled = true
client_id = "DPDP_CONSENT_API_INVOKER"
```

- To disable it, set `auto_provisioning_enabled = false`.
- To find the client secret, go to Console → **Applications** → **DPDP Consent API
  Invoker** → **Protocol**.

> Note: Disabling auto-provisioning for the DPDP Consent Portal also
disables this provisioning, even when auto_provisioning_enabled is set to
true.

## Restore tenant resources

You only need to follow these steps if the tenant's automatically provisioned
resources are missing or misconfigured. For example, this can happen if the
Consent Portal application is deleted or a role's permissions are changed.

1. Make sure auto-provisioning is enabled (see above).
2. In Console, go to **Tenant Management**, open the affected tenant, and click
   **Update**. Saving the tenant again triggers the repair.

The accelerator restores any missing resources, including the application, API
permissions, and role permissions. Existing roles and their user assignments
are not deleted.

## Configure complaint management

Use these settings to control complaint deadlines, attachment limits, and
email notifications.

```toml
[dpdp_accelerator.complaints]
statutory_due_period_days = 90
attachment_max_size_bytes = 10485760
attachment_max_files_per_upload = 5
email_notifications_enabled = false
```

- `statutory_due_period_days` — The number of days a complaint can remain open
  before it is marked as overdue.
- `attachment_max_size_bytes` — The maximum allowed size for each attachment.
- `attachment_max_files_per_upload` — The maximum number of files that can be
  attached to a complaint.
- `email_notifications_enabled` — Enables email notifications for complaints.
  This is false by default and should only be enabled after configuring email
  notifications.

### Configure complaint email notifications

Configure these settings if you want to send email notifications for
complaints. Email notifications are optional and are not included in the
default configuration. Add this section to `deployment.toml` when you want to
enable them.

```toml
[output_adapter.email]
from_address = "abc@gmail.com"
username = "abc@gmail.com"
password = "<GMAIL_APP_PASSWORD>"
hostname = "smtp.gmail.com"
port = 587
```

- `from_address` — The email address shown as the sender.
- `username` / `password` — The credentials used to connect to the SMTP
  server. For Gmail, use an App Password instead of your regular account
  password.
- `hostname` / `port` — The SMTP server and port used by your email provider.
  The example above uses Gmail's SMTP server with port 587.

Email notifications are sent when a complaint is created, commented on, or
acknowledged. To receive these notifications, the user must also have a **Primary Email** configured in (**Console** → **User
Management → Users**)

## Configure consent history and status audit trail

These settings control how consent changes are recorded and stored for
future reference.

```toml
[dpdp_accelerator.consent_history]
enabled = true
snapshot_enabled = true
```

- `enabled = false` Disables consent history and the audit trail.
- `snapshot_enabled = false` Records that a consent changed, but does not store a full snapshot of the consent at that point in time.

## Configure periodical consent expiration

The accelerator uses a background job to mark consents as `EXPIRED` after
their expiry date has passed. Consent history must be enabled for this feature
to work.

```toml
[dpdp_accelerator.consent_expiry]
enabled = true
schedule_mode = "daily"
daily_time = "00:00"
# timezone = "Asia/Colombo"
# interval_seconds = 300
batch_size = 100
max_batches_per_run = 1000
max_run_seconds = 300
```

- `schedule_mode` — Controls how often the job runs. Use `"daily"` to run it
  once a day or `"interval"` to run it at a fixed interval. For interval mode,
  set this to `"interval"` and configure `interval_seconds`.
- `daily_time` — The local time when the job runs. Used only in daily mode,
  for example, `"00:00"`.
- `timezone` — The timezone used to interpret `daily_time`. If not specified,
  the server's timezone is used.
- `interval_seconds` — The number of seconds between runs. Required when
  `schedule_mode` is `"interval"` and ignored in daily mode.
- `batch_size`, `max_batches_per_run`, `max_run_seconds` — Control how many
  consents the job processes in a single run. The default values are suitable
  for most installations.
- You can run the job on multiple server instances. The same consent will not
  be processed more than once.

## Configure Event Notifications

Event notifications allow other systems to receive notifications when
consents or user data change. See the [Event Notification Guide](event-notification-guide.md)
for a complete walkthrough of creating topics and subscriptions.

```toml
[dpdp_accelerator.event_notifications]
system_topics_auto_create_enabled = true

[dpdp_accelerator.event_notifications.payload_signing]
enabled = true
audience = "dpdp-event-notifications"
```

- `system_topics_auto_create_enabled` — Automatically creates the five
  standard topics for each tenant. See
  [Configure lifecycle event publishing](#configure-lifecycle-event-publishing)
  for the list of topics.
- `payload_signing.enabled` — Signs each event so the receiving system can
  verify that it was sent by the accelerator. Keep this enabled.
- `payload_signing.audience` — The audience value included in the event
  signature. The receiving system can use it to verify that the event is
  intended for it.

### Configure lifecycle event publishing

```toml
[dpdp_accelerator.event_notifications.lifecycle_events]
publishing_enabled = true
```

`publishing_enabled` Enables automatic publishing to the five standard
topics when a matching event occurs. Set this to `false` if you want to
publish events yourself or disable automatic publishing. The topics remain
available even when publishing is disabled.

| Topic | Published when... |
|---|---|
| `consent.update` | A consent's state changes (e.g. approved, a decision recorded). |
| `consent.revoke` | A consent is revoked or withdrawn. |
| `consent.expire` | A consent expires (see [consent expiry](#configure-periodical-consent-expiration)). |
| `user.data.change` | A user's profile or attributes are modified. |
| `user.account.delete` | A user's account is deleted. |

`user.data.change` and `user.account.delete` also require the following event handler registration. 
It is enabled by default, so make sure it is still present if you have manually edited deployment.toml:

```toml
[[event_handler]]
name = "dpdpUserLifecycleEventHandler"
subscriptions = ["POST_DELETE_USER", "POST_SET_USER_CLAIMS"]
```

See the [Event Notification Guide](event-notification-guide.md#enabling-userdatachange--useraccountdelete)
for details. The `consent.*` topics also require
[consent history](#configure-consent-history-and-status-audit-trail) to be
enabled.

### Configure event polling

```toml
[dpdp_accelerator.event_notifications.polling]
default_return_immediately = true
default_max_events = 20
max_events_limit = 100
request_hmac_validation_enabled = false
```

- `default_return_immediately` — Controls whether a poll request returns
  immediately by default or waits for new events.
- `default_max_events` — The default number of events returned in a poll
  request.
- `max_events_limit` — The maximum number of events that can be requested in a
  single poll. Requests above this limit are rejected.
- `request_hmac_validation_enabled` — Requires poll requests to include a
  signed header that is verified against the subscription's shared secret.
  Disabled by default.

### Configure webhook delivery

```toml
[dpdp_accelerator.event_notifications.webhook]
thread_pool_size = 4
base_backoff_seconds = 5
max_retries = 5
allow_http_callback_url = false
allowed_callback_ports = "-1,443,8443"
allow_private_network_callback_targets = false
delivery_worker_batch_size = 50
delivery_worker_poll_seconds = 5
stuck_inflight_threshold_seconds = 10
max_verification_response_body_bytes = 4096
pending_subscription_recovery_threshold_seconds = 60
background_worker_initial_delay_seconds = 10
pending_subscription_recovery_interval_seconds = 30
pending_subscription_recovery_batch_size = 20
worker_shutdown_timeout_seconds = 5
```

- `thread_pool_size` — The number of webhook deliveries that can be sent
  concurrently.
- `base_backoff_seconds` — The initial wait time before retrying a failed
  delivery.
- `max_retries` — The maximum number of retries for a failed delivery. A value
  of `5` results in up to 6 delivery attempts in total.
- `allow_http_callback_url` — Allows callback URLs that use HTTP instead of
  HTTPS. Keep this disabled in production.
- `allowed_callback_ports` — The ports that can be used by callback URLs.
- `allow_private_network_callback_targets` — Allows callback URLs that point
  to private or internal networks. Enable this only for controlled on-premises
  environments.
- `delivery_worker_batch_size` — The number of pending deliveries a worker
  processes at a time.
- `delivery_worker_poll_seconds` — How often a worker checks for pending
  deliveries.
- `stuck_inflight_threshold_seconds` — How long a delivery can remain in
  progress before it is considered stuck and retried.
- `max_verification_response_body_bytes` — The maximum response size read when
  verifying a subscription's callback URL.
- `pending_subscription_recovery_threshold_seconds` — How long a subscription
  can remain in `pending verification` before it is picked up for recovery.
- `background_worker_initial_delay_seconds` — How long to wait after server
  startup before delivery workers start.
- `pending_subscription_recovery_interval_seconds` — How often the system
  checks for subscriptions that are stuck in `pending verification`.
- `pending_subscription_recovery_batch_size` — The number of pending
  subscriptions recovered in a single check.
- `worker_shutdown_timeout_seconds` — How long to wait for in-flight
  deliveries to complete when the server shuts down.

See the [Event Notification Guide](event-notification-guide.md) for more on
any of these.

## Next Steps

Once you've made the required configuration changes, start the server for
them to take effect.

### Start the server

Go to `<IS_HOME>/bin` and start Identity Server:

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```sh
./wso2server.sh
```

</TabItem>
<TabItem value="macos" label="macOS">

```sh
./wso2server.sh
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
.\wso2server.bat
```

</TabItem>
</Tabs>

- Continue with [Configuring Users and Roles](configuring-users-and-roles.md)
  to create users and assign them accelerator roles.
