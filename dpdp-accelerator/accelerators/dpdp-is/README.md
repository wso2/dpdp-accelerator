# WSO2 DPDP Identity Server Accelerator

Adds a DPDP consent self-care portal on top of WSO2 Identity Server 7.3, backed by
the Identity Server's own consent management APIs. No separate consent server is
involved.

## What it deploys

| Artifact | Location |
|---|---|
| Consent portal (React SPA + JSP shell; no Java BFF) | `<IS_HOME>/repository/deployment/server/webapps/consent-portal/` |
| Portal runtime configuration | `<IS_HOME>/repository/deployment/server/webapps/consent-portal/deployment.config.json` |
| Server settings | `<IS_HOME>/repository/conf/deployment.toml`, replaced from the shipped template |
| Generated accelerator configuration | `<IS_HOME>/repository/conf/dpdp-accelerator.xml` |

## Prerequisites

- WSO2 Identity Server 7.3.0
- JDK 21 or later on the PATH
- Mandatory WSO2 U2 updates applied to the Identity Server pack

## Installation

Building from source? See the [repository README](../../../README.md#build)
— this section installs an already-built accelerator zip.

1. Unzip this accelerator inside `<IS_HOME>` (or anywhere, passing `<IS_HOME>` to each script).

2. Copy the artifacts in, with the server stopped:
   ```
   sh bin/merge.sh <IS_HOME>
   ```

3. Apply the configuration, still stopped:
   ```
   sh bin/configure.sh <IS_HOME>
   ```
   Edit `repository/conf/configure.properties` first for hostname,
   administrator-credential, and schema-migration substitutions. `IS_PORT` is
   not currently substituted, and external database connection details must be
   applied to the deployment template or installed `deployment.toml`. This
   step installs `deployment.toml`, applies the Identity Server consent
   migration, and creates the available DPDP schemas for Consent History,
   Complaint Management, and Event Notifications.

   > **`deployment.toml` is replaced, not merged.** The accelerator ships a
   > complete file — `repository/resources/wso2is-7.3.0-deployment.toml`, the
   > stock Identity Server 7.3.0 configuration plus the accelerator's own
   > settings. Your existing file is copied to `deployment.toml.bak-<timestamp>`
   > first; re-apply any local customisation from that backup before starting
   > the server. To target a different Identity Server version, add a template
   > beside the shipped one and point `PRODUCT_CONF_PATH` at it.

4. Start the Identity Server.

5. Wait for tenant provisioning to create the **DPDP Consent Portal**
   application and the three portal roles. For a new ordinary tenant, create
   the tenant after startup. For an existing tenant, update one tenant property
   to run reconciliation.

6. Assign the required role to a user and sign in again to obtain a fresh
   access token.

7. Open `https://<host>:9443/consent-portal/`.

For a first local verification, follow the
[`Quickstart`](../../../docs/quickstart.md).

## Granting administration access

Every authenticated user can see and manage their own consents without a
portal role. Assign `dpdp-consent-user` for personal history, complaint
self-service, and account deletion; `dpdp-consent-admin` for full portal
administration; or `dpdp-consent-dpo` for organization-wide complaint handling
without full administration. See the
[`Role Management Guide`](../../../docs/role-guide.md).
