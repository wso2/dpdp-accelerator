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
* `GET /subscriptions/{subscriptionId}/deliveries`: Endpoint to list all deliveries associated with a specific subscription.

### Manual webhook delivery retry
Added `POST /deliveries/{deliveryId}/retry` to allow operators to manually re-queue webhook deliveries that have exhausted automatic retry attempts and reached `failed` status. This operation requires the `notifications:subscriptions:write` scope and returns HTTP `202 Accepted`.

### Uniform status update responses
Standardized the JSON responses returned by state-changing operations across subscriptions and topics:
* `POST /subscriptions/{id}/verify` and `DELETE /subscriptions/{id}` now return a uniform `SubscriptionStatusUpdate` object containing `subscriptionId`, `status`, and `updatedAt`.
* `DELETE /topics/{id}` returns a uniform `TopicStatusUpdate` object containing `topicId` and `status`.

### Database schema migration scripts
Included schema migration scripts under `carbon-home/dbscripts/dpdp-accelerator/event-notification/migrations/` in the accelerator snapshot (copied to `<IS_HOME>/dbscripts/dpdp-accelerator/event-notification/migrations/` upon running `merge.sh`) to support upgrading existing database schemas:
* `h2.sql` (H2 database)
* `mysql.sql` (MySQL 8.x)
* `postgresql.sql` (PostgreSQL 14+)

---

## What has changed in this release

### Optional `group-id` header
The `group-id` HTTP request header on `POST /events`, `POST /events/poll`, and `POST /deliveries/{deliveryId}/completion` is now optional. When omitted, the service automatically defaults to the request's Carbon tenant domain (`orgId`), streamlining tenant-scoped integrations.

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
| **Java** | OpenJDK 17 or higher (LTS recommended) |
| **Databases** | MySQL 8.x, PostgreSQL 14+, H2 (embedded, evaluation only) |

---

## Upgrade and migration

Follow these steps to upgrade an existing DPDP Accelerator deployment to version 1.1.0:

### 1. Apply database migration scripts
Apply the database migration script matching your database engine to `WSO2DPDP_DB` from the accelerator snapshot (or `<IS_HOME>` after running `merge.sh`), or use `configure.sh` with migration enabled:

```sh
# For MySQL
mysql -u <user> -p WSO2DPDP_DB < <DPDP_ACCELERATOR_HOME>/carbon-home/dbscripts/dpdp-accelerator/event-notification/migrations/mysql.sql

# For PostgreSQL
psql -U <user> -d WSO2DPDP_DB -f <DPDP_ACCELERATOR_HOME>/carbon-home/dbscripts/dpdp-accelerator/event-notification/migrations/postgresql.sql

# For embedded H2 (evaluation environments) or automated setup (run from <IS_HOME>/<DPDP_ACCELERATOR_HOME>/bin):
APPLY_DPDP_DB_MIGRATION=true ./configure.sh
```

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
* Remove the mandatory `group-id` header from publishers and receivers unless custom group partitioning is explicitly required.

