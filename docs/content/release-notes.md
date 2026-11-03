# Release Notes

This page tracks the version history, new features, behavioral changes, deprecations, and upgrade instructions for the WSO2 DPDP Accelerator, following [Semantic Versioning](https://semver.org/) and the [Keep a Changelog](https://keepachangelog.com/) standard.

---

## Version 1.1.0

*Release Date: November 2026*

Version 1.1.0 introduces the **V1.1 Event Notifications REST API**, delivery lifecycle enhancements (including manual retry for exhausted deliveries), uniform status transition responses, and streamlined tenant-scoped event publishing.

### Added

* **V1.1 Event Notifications API Contract:** Introduced the OpenAPI 3.0 V1.1 specification (`event-notifications-v1.1.yaml`) hosted at `/api/dpdp/event-notifications/v1.1`, configured for multi-version routing alongside V1.
* **Manual Webhook Delivery Retry:** Added `POST /deliveries/{deliveryId}/retry` to re-queue exhausted or failed webhook deliveries for immediate worker retry. Requires the `notifications:subscriptions:write` scope.
* **Dedicated Delivery Resource Routes:** Added `GET /deliveries/{deliveryId}` and `GET /deliveries/{deliveryId}/history` for direct inspection of delivery status and attempt audit records.
* **Subscription Deliveries Endpoint:** Added `GET /subscriptions/{subscriptionId}/deliveries` to list all deliveries associated with a specific subscription.
* **Uniform Status Update Responses:** Standardized responses across status-transition operations:
  * `POST /subscriptions/{id}/verify` and `DELETE /subscriptions/{id}` return a uniform `SubscriptionStatusUpdate` payload (`subscriptionId`, `status`, `updatedAt`).
  * `DELETE /topics/{id}` returns a uniform `TopicStatusUpdate` payload (`topicId`, `status`).
* **Database Migration Scripts:** Added schema delta scripts for existing deployments under `repository/deployment/server/event-notification/migrations/`:
  * `h2.sql` (H2 database)
  * `mysql.sql` (MySQL)
  * `postgresql.sql` (PostgreSQL)

### Changed

* **Optional `group-id` Header:** The `group-id` HTTP request header on `POST /events`, `POST /events/poll`, and `POST /deliveries/{deliveryId}/completion` is now optional. When omitted, the service automatically defaults to the request's Carbon tenant domain (`orgId`).
* **Event Creation Response Payload:** `POST /events` response body now returns the resolved `topic` name string and matching `purposes` array directly, omitting internal `groupId` and `orgId` attributes.
* **Consent Portal V1.1 Client:** Updated the built-in Consent Portal frontend events and subscriptions client to communicate with the V1.1 endpoints.
* **Delivery Scope Alignment:** Delivery details and attempt history (`GET /deliveries/{id}`, `GET /deliveries/{id}/history`) now require the `notifications:subscriptions:read` scope.

### Deprecated

* **Legacy V1 Delivery Endpoints:** The following V1 routes are deprecated in favor of their V1.1 equivalents:
  * `GET /events/{deliveryId}/history` → use `GET /deliveries/{deliveryId}/history`
  * `GET /subscriptions/{subscriptionId}/events` → use `GET /subscriptions/{subscriptionId}/deliveries`
  * `GET /subscriptions/{subscriptionId}/events/{deliveryId}` → use `GET /deliveries/{deliveryId}`

### Upgrade and Migration Guide

To upgrade an existing DPDP Accelerator deployment to Version 1.1.0:

1. **Apply Database Migration Scripts:**
   Run the corresponding delta SQL script for your database engine from the accelerator distribution:
   ```sh
   # For MySQL
   mysql -u <user> -p <database_name> < repository/deployment/server/event-notification/migrations/mysql.sql

   # For PostgreSQL
   psql -U <user> -d <database_name> -f repository/deployment/server/event-notification/migrations/postgresql.sql
   ```

2. **Reconcile Custom Role Permissions:**
   The automatically provisioned `dpdp-consent-admin` role already holds `notifications:subscriptions:read` and `notifications:subscriptions:write`. If you have configured custom roles that inspect delivery history in the Consent Portal:
   * Grant `notifications:subscriptions:read` so operators can view delivery details without HTTP `403 Forbidden`.
   * Grant `notifications:subscriptions:write` to allow operators to trigger manual delivery retries.

3. **Update Client Applications:**
   * Update your API base URL to `https://<host>:9443/api/dpdp/event-notifications/v1.1`.
   * Remove the mandatory `group-id` header from your publishers and receivers unless you use custom group partitioning.

---

## Version 1.0.0

*Initial Release*

* **Consent Management:** Consent collection, revocation, tracking, and purpose filtering.
* **Grievance / Complaint Lifecycle:** Data Principal complaint registration, investigation, and resolution workflows.
* **Event Notifications (V1):** Asynchronous event delivery via webhooks and polling, payload HMAC signing, and JWT verification.
* **Role-Based Access Control:** Pre-configured `dpdp-consent-admin`, `dpdp-consent-user`, and `dpdp-consent-dpo` roles.
* **Consent Portal:** Built-in web application for Data Principals and administrators.

