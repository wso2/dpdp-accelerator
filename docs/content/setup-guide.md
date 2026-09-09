# Setting up databases for the DPDP Accelerator

Use this guide to prepare the databases and datasource configuration for WSO2
Identity Server and the DPDP Accelerator. Complete the Identity Server and
accelerator installation first, then follow these steps before starting the
server.

For the supported database versions, vendor-specific database creation
guidance, and additional Identity Server database requirements, see the WSO2
[database setup documentation](https://ob.docs.wso2.com/en/latest/install-and-setup/setting-up-databases/).

## Prerequisites

- WSO2 Identity Server 7.3.0 with the mandatory U2 updates applied
- A database administrator account for the selected DBMS
- The database client tools needed to create databases and execute SQL scripts
- The JDBC driver JAR for the selected DBMS

## 1. Create the databases

Create the databases required by the Identity Server and the accelerator. Use
separate databases for the Identity Server data and the DPDP data:

- `WSO2IDENTITY_DB` for Identity Server identity and consent data
- `WSO2SHARED_DB` for Identity Server shared data
- `WSO2AGENTIDENTITY_DB` for the Identity Server `AgentIdentity` datasource
- `WSO2DPDP_DB` for DPDP Accelerator data

Use the database names, users, character sets, and permissions recommended by
your DBMS documentation and the WSO2 database setup documentation.

For example, the following MySQL commands create the databases used by the
Identity Server and accelerator. Replace the user, host, character set, and
collation values for your environment:

```sql
CREATE DATABASE WSO2IDENTITY_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE WSO2SHARED_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE WSO2AGENTIDENTITY_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE WSO2DPDP_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER '<database-user>'@'localhost' IDENTIFIED BY '<database-password>';
GRANT ALL PRIVILEGES ON WSO2IDENTITY_DB.* TO '<database-user>'@'localhost';
GRANT ALL PRIVILEGES ON WSO2SHARED_DB.* TO '<database-user>'@'localhost';
GRANT ALL PRIVILEGES ON WSO2AGENTIDENTITY_DB.* TO '<database-user>'@'localhost';
GRANT ALL PRIVILEGES ON WSO2DPDP_DB.* TO '<database-user>'@'localhost';
FLUSH PRIVILEGES;
```

For PostgreSQL, Oracle, and Microsoft SQL Server, create equivalent databases
or schemas and grant the required permissions using the commands recommended
by the selected DBMS.

## 2. Install the JDBC driver

Download the JDBC driver that matches the selected DBMS and its supported
version. Copy the driver JAR to:

```text
<IS_HOME>/repository/components/dropins
```

> **Note:** The MySQL, PostgreSQL, Oracle, and Microsoft SQL Server JDBC driver
> JARs must be downloaded separately and copied to the Identity Server
> `dropins` directory before the server starts. Use the driver version
> recommended for your DBMS and Identity Server version.

The driver versions listed in the WSO2 reference are:

| DBMS | JDBC driver JAR |
|---|---|
| MySQL 8.0 | `mysql-connector-java-5.1.44.jar` |
| Oracle 19c | `ojdbc11.jar` |
| Microsoft SQL Server 2022 | `mssql-jdbc-12.10.0.jre11.jar` |
| PostgreSQL 17.2 | `postgresql-42.2.17.jar` |

## 3. Configure `deployment.toml`

Open `<IS_HOME>/repository/conf/deployment.toml` and update the database
sections for the selected DBMS. The relevant sections are:

- `[database.identity_db]`
- `[database.shared_db]`
- `[datasource.AgentIdentity]`
- `[datasource.WSO2DPDP_DB]`

Keep the existing section names and datasource IDs. In particular,
`[dpdp_accelerator.jdbc_persistence_manager]` must continue to use
`data_source_name = "jdbc/WSO2DPDP_DB"`, matching the `WSO2DPDP_DB` datasource
ID.

The following examples show the DBMS-specific connection values to use. Apply
the selected values to each relevant database section, using the database name
assigned to that section.

| Database | `deployment.toml` section |
|---|---|
| `WSO2IDENTITY_DB` | `[database.identity_db]` |
| `WSO2SHARED_DB` | `[database.shared_db]` |
| `WSO2AGENTIDENTITY_DB` | `[datasource.AgentIdentity]` |
| `WSO2DPDP_DB` | `[datasource.WSO2DPDP_DB]` |

<details>
<summary>MySQL</summary>

```toml
    [database.identity_db]
    type = "mysql"
    url = "jdbc:mysql://localhost:3306/WSO2IDENTITY_DB?useSSL=false&serverTimezone=UTC"
    username = "<database-user>"
    password = "<database-password>"

    [database.shared_db]
    type = "mysql"
    url = "jdbc:mysql://localhost:3306/WSO2SHARED_DB?useSSL=false&serverTimezone=UTC"
    username = "<database-user>"
    password = "<database-password>"

    [datasource.AgentIdentity]
    id = "AgentIdentity"
    url = "jdbc:mysql://localhost:3306/WSO2AGENTIDENTITY_DB?useSSL=false&serverTimezone=UTC"
    username = "<database-user>"
    password = "<database-password>"
    driver = "com.mysql.cj.jdbc.Driver"

    [datasource.WSO2DPDP_DB]
    id = "WSO2DPDP_DB"
    url = "jdbc:mysql://localhost:3306/WSO2DPDP_DB?useSSL=false&serverTimezone=UTC"
    username = "<database-user>"
    password = "<database-password>"
    driver = "com.mysql.cj.jdbc.Driver"
    jmx_enable = false
    pool_options.maxActive = "150"
    pool_options.maxWait = "60000"
    pool_options.minIdle = "5"
    pool_options.testOnBorrow = true
    pool_options.validationQuery = "SELECT 1"
    pool_options.validationInterval = "30000"
    pool_options.defaultAutoCommit = true
```

</details>

<details>
<summary>PostgreSQL</summary>

```toml
    [database.identity_db]
    type = "postgresql"
    url = "jdbc:postgresql://localhost:5432/WSO2IDENTITY_DB"
    username = "<database-user>"
    password = "<database-password>"

    [database.shared_db]
    type = "postgresql"
    url = "jdbc:postgresql://localhost:5432/WSO2SHARED_DB"
    username = "<database-user>"
    password = "<database-password>"

    [datasource.AgentIdentity]
    id = "AgentIdentity"
    url = "jdbc:postgresql://localhost:5432/WSO2AGENTIDENTITY_DB"
    username = "<database-user>"
    password = "<database-password>"
    driver = "org.postgresql.Driver"

    [datasource.WSO2DPDP_DB]
    id = "WSO2DPDP_DB"
    url = "jdbc:postgresql://localhost:5432/WSO2DPDP_DB"
    username = "<database-user>"
    password = "<database-password>"
    driver = "org.postgresql.Driver"
    jmx_enable = false
    pool_options.maxActive = "150"
    pool_options.maxWait = "60000"
    pool_options.minIdle = "5"
    pool_options.testOnBorrow = true
    pool_options.validationQuery = "SELECT 1"
    pool_options.validationInterval = "30000"
    pool_options.defaultAutoCommit = true
```

</details>

<details>
<summary>Oracle</summary>

```toml
    [database.identity_db]
    type = "oracle"
    url = "jdbc:oracle:thin:@localhost:1521/WSO2IDENTITY_DB"
    username = "<database-user>"
    password = "<database-password>"

    [database.shared_db]
    type = "oracle"
    url = "jdbc:oracle:thin:@localhost:1521/WSO2SHARED_DB"
    username = "<database-user>"
    password = "<database-password>"

    [datasource.AgentIdentity]
    id = "AgentIdentity"
    url = "jdbc:oracle:thin:@localhost:1521/WSO2AGENTIDENTITY_DB"
    username = "<database-user>"
    password = "<database-password>"
    driver = "oracle.jdbc.OracleDriver"

    [datasource.WSO2DPDP_DB]
    id = "WSO2DPDP_DB"
    url = "jdbc:oracle:thin:@localhost:1521/WSO2DPDP_DB"
    username = "<database-user>"
    password = "<database-password>"
    driver = "oracle.jdbc.OracleDriver"
    jmx_enable = false
    pool_options.maxActive = "150"
    pool_options.maxWait = "60000"
    pool_options.minIdle = "5"
    pool_options.testOnBorrow = true
    pool_options.validationQuery = "SELECT 1 FROM DUAL"
    pool_options.validationInterval = "30000"
    pool_options.defaultAutoCommit = true
```

</details>

<details>
<summary>Microsoft SQL Server</summary>

```toml
    [database.identity_db]
    type = "mssql"
    url = "jdbc:sqlserver://localhost:1433;databaseName=WSO2IDENTITY_DB;encrypt=false"
    username = "<database-user>"
    password = "<database-password>"

    [database.shared_db]
    type = "mssql"
    url = "jdbc:sqlserver://localhost:1433;databaseName=WSO2SHARED_DB;encrypt=false"
    username = "<database-user>"
    password = "<database-password>"

    [datasource.AgentIdentity]
    id = "AgentIdentity"
    url = "jdbc:sqlserver://localhost:1433;databaseName=WSO2AGENTIDENTITY_DB;encrypt=false"
    username = "<database-user>"
    password = "<database-password>"
    driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver"

    [datasource.WSO2DPDP_DB]
    id = "WSO2DPDP_DB"
    url = "jdbc:sqlserver://localhost:1433;databaseName=WSO2DPDP_DB;encrypt=false"
    username = "<database-user>"
    password = "<database-password>"
    driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver"
    jmx_enable = false
    pool_options.maxActive = "150"
    pool_options.maxWait = "60000"
    pool_options.minIdle = "5"
    pool_options.testOnBorrow = true
    pool_options.validationQuery = "SELECT 1"
    pool_options.validationInterval = "30000"
    pool_options.defaultAutoCommit = true
```

</details>

Keep the `id` values as `AgentIdentity` and `WSO2DPDP_DB`. Preserve the
remaining Identity Server and accelerator settings in the file.

## 4. Create the database tables

Run the Identity Server SQL migrations and the DPDP Accelerator SQL scripts
against the databases selected in `deployment.toml`.

The Identity Server scripts are under:

```text
<IS_HOME>/dbscripts
```

The accelerator feature scripts are under:

```text
<ACCELERATOR_HOME>/carbon-home/dbscripts/dpdp-accelerator/
```

Each feature directory contains the DBMS-specific script where that DBMS is
provided:

- `consent-history/`
- `complaint/`
- `event-notification/`

Choose the script matching the selected DBMS. Do not run a script for a
different database engine. If the package does not contain a matching DPDP
script for a DBMS, confirm the supported schema package or migration path with
the release documentation before starting the server.

## 5. Start Identity Server

After the databases, JDBC driver, datasource settings, and SQL migrations are
ready, start Identity Server from `<IS_HOME>`:

```sh
sh bin/wso2server.sh
```

On Windows, run `bin\\wso2server.bat` instead.

After the server starts, continue with the [Configuration Guide](configuration-guide.md)
to configure access and runtime features.
