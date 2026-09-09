# DPDP Accelerator quickstart

Use this guide to install the accelerator, open the Consent Portal, and verify
the initial portal access. Follow the linked reference guides before using the
deployment in production.

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

`configure.sh` backs up and then replaces
`<IS_HOME>/repository/conf/deployment.toml`; review that backup before using the
same process on an existing deployment.

Expected result: both scripts finish successfully and the accelerator
configuration is applied.

For source builds, external databases, and configuration replacement details,
see the [Setup Guide](setup-guide.md).

## 2. Start Identity Server

```sh
sh <IS_HOME>/bin/wso2server.sh
```

After WSO2 Identity Server starts, open the Console:

```text
https://localhost:9443/console
```

## 3. Sign in to the Console

Sign in to the Console with the default administrator credentials:

```text
Username: admin@wso2.com
Password: wso2123
```

These credentials are for the quickstart only. Change the administrator
password before using the deployment in production.

## 4. Create users and assign portal access

Open **User Management → Users** and create three users for portal access.
Assign one of the three provisioned roles to each user:

- `dpdp-consent-admin` for portal administrators, including the user who will
  verify the portal in this quickstart
- `dpdp-consent-user` for regular users who need personal consent history,
  complaint, or account-deletion features
- `dpdp-consent-dpo` for Data Protection Officers and complaint-handling users

The roles are created automatically, but users and role memberships are not.
After assigning a role, have each user sign out and sign in again so the new
access token contains the role's scopes.

See the [Role Management Guide](role-guide.md) before assigning roles. Basic
self-service consent management does not require a portal role.

## 5. Open the Portal

Open:

```text
https://localhost:9443/consent-portal/
```

Sign in as the user holding `dpdp-consent-admin` and confirm that the portal
loads.

## Next steps

- [Learn through real stories](learn.md) — understand how the major areas fit
  together from the perspectives of a Data Principal, administrator, processor,
  and grievance officer
- [Tryout Flows](tryout-flows.md) — catalog, consent lifecycle, complaint,
  automatic event, and account-deletion walkthroughs
