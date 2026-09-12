---
title: Setting up the DPDP Identity Server Accelerator
sidebar_position: 1
---

# Setting up the DPDP Identity Server Accelerator

Gets the Identity Server running with the consent portal deployed. Complete
this before [`configuration-guide.md`](configuration-guide.md), which
registers the portal application.

## Prerequisites

- JDK 21 or later on the PATH
- Maven 3.6.3+ and Node.js 20.19+ (or 22.12+) with npm, only if building the
  accelerator from source
- A WSO2 subscription, to apply the mandatory U2 updates to the Identity Server

## 1. Get WSO2 Identity Server

Download and extract WSO2 Identity Server 7.3.0 from the
[WSO2 Identity Server](https://wso2.com/identity-and-access-management/)
site. The extracted directory is `<IS_HOME>` in the steps below.

Apply the U2 updates to the pack before going any further. The accelerator does
not run on an un-updated 7.3.0.

## 2. Get the accelerator

Either download a released `wso2-dpdpiam-accelerator-<version>.zip`, or build
it from source:

```sh
mvn clean install
```

Run from the repository root — this produces
`dpdp-accelerator/accelerators/dpdp-is/target/wso2-dpdpiam-accelerator-<version>.zip`.
See the [repository README](https://github.com/wso2/dpdp-accelerator#build) for details.

## 3. Extract the accelerator

Unzip the accelerator inside `<IS_HOME>` (or anywhere, passing `<IS_HOME>` to
each script in the next steps).

## 4. Copy the artifacts in

With the server stopped:

```sh
sh bin/merge.sh <IS_HOME>
```

## 5. Apply the configuration

Still stopped:

```sh
sh bin/configure.sh <IS_HOME>
```

Edit `repository/conf/configure.properties` first if your hostname, port,
administrator credentials or database differ from the defaults. This step
installs `deployment.toml`, applies the Identity Server's own consent schema
migration, and creates the `WSO2DPDP_DB` database with every DPDP feature's
schema.

> **Creating the DPDP database and tables.** With the embedded H2 database
> (`DB_TYPE=h2`, the default), this step creates `WSO2DPDP_DB` and runs every
> `h2.sql` it finds under
> `accelerators/dpdp-is/carbon-home/dbscripts/dpdp-accelerator/` — one
> subdirectory per DPDP feature (currently `complaint/`, `consent-history/`
> and `event-notification/`); a feature added later just needs its own
> subdirectory, no script changes required. The scripts are idempotent
> (`CREATE TABLE IF NOT EXISTS`), so re-running is always safe.

### Running against MySQL instead of H2

Set `DB_TYPE=mysql` in `repository/conf/configure.properties`, along with
`DB_HOST`, `DB_USER` and `DB_PASS` for a MySQL server you already have running.
`configure.sh` then does the rest: it fills the datasource placeholders in
`deployment.toml` with the MySQL URL, driver, credentials and database names,
downloads the pinned JDBC driver into `<IS_HOME>/repository/components/lib`,
creates the four databases, and applies both the Identity Server's own schema and
the DPDP feature schemas.

Unlike H2 — where WSO2 bakes a fully populated `WSO2IDENTITY_DB` into the pack —
a MySQL server starts empty, so the product's own `dbscripts` have to be applied:

| Script (under `<IS_HOME>`) | Database |
| --- | --- |
| `dbscripts/mysql.sql` | `WSO2SHARED_DB` |
| `dbscripts/identity/mysql.sql` | `WSO2IDENTITY_DB` |
| `dbscripts/consent/mysql.sql` | `WSO2IDENTITY_DB` |
| `dbscripts/migrations/consent/mysql-migration.txt` | `WSO2IDENTITY_DB` |
| `dbscripts/identity/agent/mysql.sql` | `WSO2AGENTIDENTITY_DB` |
| `dbscripts/dpdp-accelerator/*/mysql.sql` | `WSO2DPDP_DB` |

The account in `DB_USER` needs `CREATE DATABASE` rights on the first run.

> **Re-running never destroys data.** The Identity Server's own DDL is not
> idempotent (`dbscripts/consent/mysql.sql` guards none of its ten
> `CREATE TABLE`s), so it is applied *only* to a database that this run just
> created. A database that already exists is left untouched, and the script says
> so. To start clean — for a test run, or a local reset — set
> `RECREATE_DATABASES=true`, which **drops and recreates all four databases**.
> It is off by default.

**Supporting another database type.** Two additions, no change to `configure.sh`:
a block in `repository/conf/dbprofiles.properties` keyed on the uppercased type
name (the JDBC URL, driver, and how to create the databases and run the client);
and a matching `<type>.sql` in each `dbscripts/dpdp-accelerator/<feature>/`
directory. The datasource placeholders in `deployment.toml` are shared by every
type. Note that only `event-notification` currently ships DDL beyond H2 and MySQL,
so the other features' scripts would have to be written first.

> **`deployment.toml` is replaced, not merged.** The accelerator ships a
> complete file — `repository/resources/wso2is-7.3.0-deployment.toml`, the
> stock Identity Server 7.3.0 configuration plus the accelerator's own
> settings, including the `[tenant_context.rewrite]` entry that serves the
> portal tenant-qualified at `/t/<tenant>/consent-portal/`. Your existing file is copied to `deployment.toml.bak-<timestamp>`
> first; re-apply any local customisation from that backup before starting
> the server. To target a different Identity Server version, add a template
> beside the shipped one and point `PRODUCT_CONF_PATH` at it. It also carries
> the `[dpdp_accelerator.consent_portal]` section that controls the portal's
> auto-provisioning — edit it here if you want different values from the
> start; see [`configuration-guide.md`](configuration-guide.md#2-change-or-turn-off-the-auto-provisioning).

## 6. Start the Identity Server

From `<IS_HOME>`:

```sh
sh bin/wso2server.sh
```

On Windows, run `bin\wso2server.bat` instead.

Wait for the console to print `WSO2 Identity Server started in ...` before
continuing.

## Next: register the portal application

Follow [`configuration-guide.md`](configuration-guide.md).
