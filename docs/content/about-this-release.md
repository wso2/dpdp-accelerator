# About this Release

The WSO2 Digital Personal Data Protection (DPDP) Accelerator extends WSO2 Identity Server to provide consent lifecycle management, Data Principal complaint (grievance) handling, audit logging, and asynchronous event notifications.

This document describes the features, enhancements, behavioral changes, deprecated APIs, and upgrade instructions for **WSO2 DPDP Accelerator v1.1.0**.

---

## What is new in this release

### V1.1 Event Notifications REST API
Introduced the OpenAPI 3.0 V1.1 contract (`event-notifications-v1.1.yaml`) hosted under the base URL `/api/dpdp/event-notifications/v1.1` (and `/t/{tenantDomain}/api/dpdp/event-notifications/v1.1` for secondary tenants). The web application runtime is configured with multi-version routing to serve V1.1 while preserving backwards compatibility with V1.

### Dedicated delivery resource routes
Reorganized delivery operations under direct top-level `/deliveries` endpoints to decouple delivery management from parent events and subscriptions:
* `GET /deliveries/{deliveryId}`: Direct endpoint to retrieve delivery status and details.
* `GET /deliveries/{deliveryId}/history`: Direct endpoint to retrieve attempt-by-attempt audit logs for a delivery.
* `POST /deliveries/{deliveryId}/retry`: Direct endpoint to manually re-queue webhook deliveries that have exhausted automatic retry attempts and reached `failed` status (requires the `notifications:subscriptions:write` scope and returns HTTP `202 Accepted`).
* `GET /subscriptions/{subscriptionId}/deliveries`: Endpoint to list all deliveries associated with a specific subscription.

### Uniform status update responses
Standardized the JSON responses returned by state-changing operations across subscriptions and topics:
* `POST /subscriptions/{id}/verify` and `DELETE /subscriptions/{id}` now return a uniform `SubscriptionStatusUpdate` object containing `subscriptionId`, `status`, and `updatedAt`.
* `DELETE /topics/{id}` returns a uniform `TopicStatusUpdate` object containing `topicId` and `status` (`deleted` in `v1.1`).

### `consent.create` system topic and startup reconciliation
* Added the **`consent.create`** predefined system topic (bringing the total to six system topics alongside `consent.update`, `consent.revoke`, `consent.expire`, `user.data.change`, and `user.account.delete`).
* Added `system_topics_verify_on_startup_enabled = true` under `[dpdp_accelerator.event_notifications]` (requires `system_topics_auto_create_enabled = true`) to reconcile missing system topics across all active tenants at server startup, promoting matching active user-created topics to system topics while leaving `deleted` user topics untouched.

### Configurable subscription constraint checks
Added `[dpdp_accelerator.event_notifications.subscription] subscription_constraint_checks_enabled` (defaults to `false`). When `false`, multiple subscriptions (such as multiple poll consumers or coexisting webhook and poll consumers) can subscribe to the same topic with overlapping filters, while active subscription name uniqueness within an organization is always enforced. Set it to `true` to enforce legacy mixed delivery mode and overlapping purpose filter checks.

### Versioned database migration scripts
Included idempotent `1.1.0` migration scripts under `carbon-home/dbscripts/dpdp-accelerator/event-notification/migrations/1.1.0/` in the accelerator snapshot (copied to `<IS_HOME>/dbscripts/dpdp-accelerator/event-notification/migrations/1.1.0/` upon running `merge.sh`):
* `h2.sql` (H2 database)
* `mysql.sql` (MySQL 8.0)
* `postgresql.sql` (PostgreSQL 15, 16, 17)

These scripts normalize legacy `TOPIC.STATUS` values from `'deregistered'` to `'deleted'` (and update the `TOPIC.STATUS` constraint/enum to `('active', 'deleted')`), and backfill `WEBHOOK_DELIVERY.STATUS = 'completed'` for deliveries with `WEBHOOK_DELIVERY_ACK.COMPLETION_STATUS = 'completed'`.

---

## What has changed in this release

### Tenant-scoped event notifications
V1.1 uses the request's tenant context for event publication, polling, and delivery completion, and no longer requires the `group-id` header.

### Topic deletion status alignment
Topic soft-deletion is standardized to `deleted` in the database, the `v1.1` REST API, and the Consent Portal UI (replacing `deregistered`). Legacy `v1` endpoints translate `deleted` back to `deregistered` in responses and map `status=deregistered` filters to `deleted` for backwards compatibility.

### Delivery completion status transition
Submitting a completion report with `completionStatus: "completed"` transitions the webhook delivery's status from `delivered` to `completed` with the completion evidence attached; `ack`, `disputed`, and `partial` keep the delivery in `delivered` with the report recorded alongside it.

### Streamlined event publication response
The `POST /events` response body now returns the resolved `topic` name string and matching `purposes` array directly, omitting internal `groupId` and `orgId` attributes.

### Consent Portal V1.1 integration
The built-in Consent Portal frontend events and subscriptions client was upgraded to communicate directly with the V1.1 REST API endpoints.

### Delivery permission realignment
Inspecting delivery details (`GET /deliveries/{id}`) and delivery history (`GET /deliveries/{id}/history`) now requires the `notifications:subscriptions:read` scope instead of `notifications:events:read`.

---

## Removed and deprecated features

The following nested and legacy delivery routes are removed from the V1.1 REST API specification and replaced by top-level `/deliveries` and `/subscriptions` routes:

| Removed Route (V1.1) | Replacement Route | Required Scope | Description |
|---|---|---|---|
| `GET /subscriptions/{subscriptionId}/events` | `GET /subscriptions/{subscriptionId}/deliveries` | `notifications:subscriptions:read` | List all deliveries for a subscription |
| `GET /subscriptions/{subscriptionId}/events/{deliveryId}` | `GET /deliveries/{deliveryId}` | `notifications:subscriptions:read` | Retrieve delivery status and details |
| `POST /subscriptions/{subscriptionId}/events/{deliveryId}/retry` | `POST /deliveries/{deliveryId}/retry` | `notifications:subscriptions:write` | Retry an exhausted webhook delivery |
| `GET /events/{deliveryId}/history` | `GET /deliveries/{deliveryId}/history` | `notifications:subscriptions:read` | Retrieve delivery attempt audit history |

:::note Legacy V1 Endpoints
The legacy V1 routes remain accessible at `/api/dpdp/event-notifications/v1` for backward compatibility with existing V1 integrations, but new integrations and upgraded clients must target the V1.1 endpoints.
:::

---

## Compatibility

| Component | Supported Version |
|---|---|
| **Base Product** | WSO2 Identity Server 7.3.0 |
| **Java** | JDK 21 |
| **Databases** | MySQL 8.0, PostgreSQL 15, 16, 17, H2 (embedded, evaluation only) |

---

## Upgrade and migration

Follow these steps to upgrade an existing DPDP Accelerator deployment to version 1.1.0:

### 1. Apply database migration scripts
Apply the `1.1.0` migration script matching your database engine to `WSO2DPDP_DB` from the accelerator snapshot (or `<IS_HOME>` after running `merge.sh`), or let `configure.sh` apply all versioned migrations automatically:

```sh
# For MySQL
mysql -u <user> -p WSO2DPDP_DB < <DPDP_ACCELERATOR_HOME>/carbon-home/dbscripts/dpdp-accelerator/event-notification/migrations/1.1.0/mysql.sql

# For PostgreSQL
psql -U <user> -d WSO2DPDP_DB -f <DPDP_ACCELERATOR_HOME>/carbon-home/dbscripts/dpdp-accelerator/event-notification/migrations/1.1.0/postgresql.sql
```

For embedded H2 or automated execution across all database types, ensure `APPLY_DPDP_DB_MIGRATION=true` is set in `<DPDP_ACCELERATOR_HOME>/repository/conf/configure.properties` (the default) and run `./configure.sh` from `<DPDP_ACCELERATOR_HOME>/bin`.

### 2. Reconcile custom roles and permissions
The automatically provisioned `dpdp-consent-admin` role already holds `notifications:subscriptions:read` and `notifications:subscriptions:write`. 

If your organization has configured custom roles that inspect delivery details in the Consent Portal:
* Assign `notifications:subscriptions:read` so operators can inspect delivery records without receiving HTTP `403 Forbidden`.
* Assign `notifications:subscriptions:write` if operators need permission to manually trigger delivery retries.

### 3. Update client applications
* Point your API clients to the V1.1 base URL according to your tenant context:
  * **Super tenant (`carbon.super`):** `https://<host>:9443/api/dpdp/event-notifications/v1.1`
  * **Secondary tenants:** `https://<host>:9443/t/{tenantDomain}/api/dpdp/event-notifications/v1.1`
  *(Use the super tenant URL for the default root tenant; include the `/t/{tenantDomain}` path prefix whenever issuing requests within a secondary organization tenant).*
* When migrating from V1 to V1.1, remove the `group-id` header from publishers and receivers, update topic deletion handling from `deregistered` to `deleted`, and switch nested delivery routes to the top-level `/deliveries` and `/subscriptions/{subscriptionId}/deliveries` routes.

