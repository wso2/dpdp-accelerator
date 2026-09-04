# Setting up the DPDP Identity Server Accelerator

Gets the Identity Server running with the consent portal deployed. Complete
this before [`configuration-guide.md`](configuration-guide.md), which
explains the applications provisioned automatically and the available runtime
settings.

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
See the [repository README](../README.md#build) for details.

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

Edit `repository/conf/configure.properties` first for the supported hostname,
administrator-credential, and schema-migration substitutions. `IS_PORT` is
present in that file but is not currently substituted by `configure.sh`; make
non-default listener-port changes in the Identity Server configuration itself.
External database connection details must likewise be applied to the shipped
`deployment.toml` template or the installed file. The script installs
`deployment.toml`, applies the Identity Server's own consent schema migration,
and creates the `WSO2DPDP_DB` database with every available DPDP feature schema.

> **Creating the DPDP database and tables.**
>
> With the embedded H2 database (`DB_TYPE=h2`, the default), this step:
> - Creates `WSO2DPDP_DB`.
> - Runs every `h2.sql` it finds under
>   `accelerators/dpdp-is/carbon-home/dbscripts/dpdp-accelerator/` — one
>   subdirectory per DPDP feature (`consent-history/`, `complaint/` and
>   `event-notification/`).
> - A feature added later just needs its own subdirectory — no script
>   changes required.
>
> For any other `DB_TYPE`:
> - Create the database yourself first.
> - Configure `[datasource.WSO2DPDP_DB]` in the installed `deployment.toml`
>   with the correct driver, URL, username, password, and validation query.
>   Changing `DB_TYPE` does not rewrite the shipped H2 datasource.
> - Execute each available feature subdirectory's matching `<db-type>.sql`
>   against the database, in any order.
> - The scripts are idempotent (`CREATE TABLE IF NOT EXISTS`) and
>   independent of each other.

> **Example: use MySQL for the Identity Server and DPDP databases.**
>
> 1. Create the databases and a dedicated account in MySQL. Keep the
>    Identity Server database and `WSO2DPDP_DB` as separate databases, as shown
>    below (replace the password and host values for your environment):
>
>    ```sql
>    CREATE DATABASE WSO2IDENTITY_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
>    CREATE DATABASE WSO2DPDP_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
>    CREATE USER 'wso2dpdp'@'localhost' IDENTIFIED BY '<strong-password>';
>    GRANT ALL PRIVILEGES ON WSO2IDENTITY_DB.* TO 'wso2dpdp'@'localhost';
>    GRANT ALL PRIVILEGES ON WSO2DPDP_DB.* TO 'wso2dpdp'@'localhost';
>    FLUSH PRIVILEGES;
>    ```
>
> 2. In `repository/conf/configure.properties`, set the database type before
>    running `configure.sh`. This selects the matching Identity Server
>    migration and DPDP scripts, but does not apply them automatically to
>    MySQL:
>
>    ```properties
>    DB_TYPE=mysql
>    APPLY_IS_CONSENT_MGT_V2_MIGRATION=true
>    APPLY_DPDP_DB_MIGRATION=true
>    ```
>
> 3. After `configure.sh` installs `deployment.toml`, change the Identity
>    Server's database section and replace the H2 DPDP datasource. The complete
>    Identity Server deployment may also require its shared and other product
>    databases to be moved to MySQL; configure those sections using the same
>    MySQL host, credentials, and WSO2 Identity Server database guidance.
>
>    At minimum, change the identity database and DPDP datasource as follows:
>
>    ```toml
>    [database.identity_db]
>    type = "mysql"
>    url = "jdbc:mysql://localhost:3306/WSO2IDENTITY_DB?useSSL=false&serverTimezone=UTC"
>    username = "wso2dpdp"
>    password = "<strong-password>"
>    ```
>
>    ```toml
>    [datasource.WSO2DPDP_DB]
>    id = "WSO2DPDP_DB"
>    url = "jdbc:mysql://localhost:3306/WSO2DPDP_DB?useSSL=false&serverTimezone=UTC"
>    username = "wso2dpdp"
>    password = "<strong-password>"
>    driver = "com.mysql.cj.jdbc.Driver"
>    ```
>
>    Keep `data_source_name = "jdbc/WSO2DPDP_DB"` in
>    `[dpdp_accelerator.jdbc_persistence_manager]`; it must match the
>    datasource `id`.
>
> 4. Apply the Identity Server's MySQL consent migration to `WSO2IDENTITY_DB`
>    and apply all three DPDP feature scripts to `WSO2DPDP_DB` before starting
>    Identity Server:
>
>    ```sh
>    mysql -u wso2dpdp -p WSO2IDENTITY_DB \
>      < <IS_HOME>/dbscripts/migrations/consent/mysql-migration.txt
>    mysql -u wso2dpdp -p WSO2DPDP_DB \
>      < <ACCELERATOR_HOME>/carbon-home/dbscripts/dpdp-accelerator/consent-history/mysql.sql
>    mysql -u wso2dpdp -p WSO2DPDP_DB \
>      < <ACCELERATOR_HOME>/carbon-home/dbscripts/dpdp-accelerator/complaint/mysql.sql
>    mysql -u wso2dpdp -p WSO2DPDP_DB \
>      < <ACCELERATOR_HOME>/carbon-home/dbscripts/dpdp-accelerator/event-notification/mysql.sql
>    ```
>
>    Verify that the MySQL Connector/J driver is present in the Identity
>    Server distribution before starting the server. The accelerator includes
>    the MySQL driver dependency in its packaged components.
>
> A complete schema set currently exists for H2 and MySQL.

> **`deployment.toml` is replaced, not merged.**
>
> - The accelerator ships a complete file —
>   `repository/resources/wso2is-7.3.0-deployment.toml` — the stock Identity
>   Server 7.3.0 configuration plus the accelerator's own settings.
> - This includes the `[tenant_context.rewrite]` entry that serves the
>   portal tenant-qualified at `/t/<tenant>/consent-portal/`.
> - Your existing file is copied to `deployment.toml.bak-<timestamp>` first
>   — re-apply any local customisation from that backup before starting the
>   server.
> - To target a different Identity Server version, add a template beside
>   the shipped one and point `PRODUCT_CONF_PATH` at it.
> - It also carries the `[dpdp_accelerator.consent_portal]` section that
>   controls the portal's auto-provisioning — edit it here if you want
>   different values from the start; see
>   [`configuration-guide.md`](configuration-guide.md#2-change-or-turn-off-the-auto-provisioning).

## 6. Start the Identity Server

From `<IS_HOME>`:

```sh
sh bin/wso2server.sh
```

On Windows, run `bin\wso2server.bat` instead.

Wait for the console to print `WSO2 Identity Server started in ...` before
continuing.

## Next: configure access and runtime features

Follow the [`Configuration Guide`](configuration-guide.md), or use the
[`Quickstart`](quickstart.md) to verify a local H2 deployment first.
