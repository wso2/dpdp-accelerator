---
title: Setting up the database
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Setting up the database

This section explains how to create the required physical databases, populate them with the necessary database scripts, and configure the datasources for WSO2 Identity Server and the DPDP Accelerator.

Throughout this guide:
- `<IS_HOME>` refers to the root directory of the WSO2 Identity Server 7.3.0 installation.
- `<ACCELERATOR_HOME>` refers to the root directory of the extracted DPDP Accelerator distribution.

---

## 1. Create the databases

Create four databases:

| Database | Used by |
|---|---|
| `WSO2IDENTITY_DB` | Identity Server identity and consent data |
| `WSO2SHARED_DB` | Identity Server shared data |
| `WSO2AGENTIDENTITY_DB` | Identity Server `AgentIdentity` datasource |
| `WSO2DPDP_DB` | DPDP Accelerator data |

<Tabs groupId="database">
<TabItem value="mysql" label="MySQL" default>

Keep the three Identity Server databases on `latin1`. The shipped Identity Server scripts mix tables that are explicitly `latin1` with tables that inherit the database's character set, including in foreign keys, and MySQL rejects those keys if the character sets differ. `WSO2DPDP_DB` holds multilingual data, so it uses `utf8mb4`.

```sql
CREATE DATABASE WSO2IDENTITY_DB CHARACTER SET latin1 COLLATE latin1_swedish_ci;
CREATE DATABASE WSO2SHARED_DB CHARACTER SET latin1 COLLATE latin1_swedish_ci;
CREATE DATABASE WSO2AGENTIDENTITY_DB CHARACTER SET latin1 COLLATE latin1_swedish_ci;
CREATE DATABASE WSO2DPDP_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

</TabItem>
<TabItem value="postgresql" label="PostgreSQL">

Quote the database names. Without quotes, PostgreSQL folds them to lowercase, and the JDBC URLs in datasource configurations that reference `WSO2IDENTITY_DB` and so on will fail to connect. If you prefer lowercase names, use them consistently everywhere.

```sql
CREATE DATABASE "WSO2IDENTITY_DB" ENCODING 'UTF8' TEMPLATE template0;
CREATE DATABASE "WSO2SHARED_DB" ENCODING 'UTF8' TEMPLATE template0;
CREATE DATABASE "WSO2AGENTIDENTITY_DB" ENCODING 'UTF8' TEMPLATE template0;
CREATE DATABASE "WSO2DPDP_DB" ENCODING 'UTF8' TEMPLATE template0;
```

</TabItem>
</Tabs>

---

## 2. Create the database tables

To create the database tables, navigate to the script locations within `<IS_HOME>/dbscripts` and execute the scripts that correspond to your DBMS against the respective databases.

<Tabs groupId="database">
<TabItem value="mysql" label="MySQL" default>

| Database | Script Location |
|---|---|
| `WSO2SHARED_DB` | `<IS_HOME>/dbscripts/mysql.sql` |
| `WSO2IDENTITY_DB` | `<IS_HOME>/dbscripts/identity/mysql.sql` |
| `WSO2IDENTITY_DB` | `<IS_HOME>/dbscripts/consent/mysql.sql` |
| `WSO2AGENTIDENTITY_DB` | `<IS_HOME>/dbscripts/identity/agent/mysql.sql` |
| `WSO2DPDP_DB` | `<IS_HOME>/dbscripts/dpdp-accelerator/complaint/mysql.sql`<br/>`<IS_HOME>/dbscripts/dpdp-accelerator/consent-history/mysql.sql`<br/>`<IS_HOME>/dbscripts/dpdp-accelerator/event-notification/mysql.sql` |

:::note Consent Migration Script
For `WSO2IDENTITY_DB`, execute the consent migration script located at `<IS_HOME>/dbscripts/migrations/consent/mysql-migration.txt`. For instructions on executing migration scripts, refer to the [WSO2 Identity Server Documentation](https://is.docs.wso2.com/en/latest/apis/use-the-consent-management-rest-apis/#consent-management-api-v2).
:::

</TabItem>
<TabItem value="postgresql" label="PostgreSQL">

| Database | Script Location |
|---|---|
| `WSO2SHARED_DB` | `<IS_HOME>/dbscripts/postgresql.sql` |
| `WSO2IDENTITY_DB` | `<IS_HOME>/dbscripts/identity/postgresql.sql` |
| `WSO2IDENTITY_DB` | `<IS_HOME>/dbscripts/consent/postgresql.sql` |
| `WSO2AGENTIDENTITY_DB` | `<IS_HOME>/dbscripts/identity/agent/postgresql.sql` |
| `WSO2DPDP_DB` | `<IS_HOME>/dbscripts/dpdp-accelerator/complaint/postgresql.sql`<br/>`<IS_HOME>/dbscripts/dpdp-accelerator/consent-history/postgresql.sql`<br/>`<IS_HOME>/dbscripts/dpdp-accelerator/event-notification/postgresql.sql` |

:::note Consent Migration Script
For `WSO2IDENTITY_DB`, execute the consent migration script located at `<IS_HOME>/dbscripts/migrations/consent/postgresql-migration.txt`. For instructions on executing migration scripts, refer to the [WSO2 Identity Server Documentation](https://is.docs.wso2.com/en/latest/apis/use-the-consent-management-rest-apis/#consent-management-api-v2).
:::

</TabItem>
</Tabs>

:::note Upgrading from 1.0.x
If you are upgrading an existing `WSO2DPDP_DB` from `1.0.x` to `1.1.0`, run the idempotent event-notification migration script for your database engine in version order (`<IS_HOME>/dbscripts/dpdp-accelerator/event-notification/migrations/1.1.0/mysql.sql` or `postgresql.sql`). When `APPLY_DPDP_DB_MIGRATION=true` in `<ACCELERATOR_HOME>/repository/conf/configure.properties` (the default), `configure.sh` applies versioned migration scripts automatically.
:::

---

## 3. Configure the datasources

Configure the datasources by following the sample below:

<Tabs groupId="database">
<TabItem value="mysql" label="MySQL" default>

Open the `<IS_HOME>/repository/conf/deployment.toml` file and update the datasource configurations:

```toml
[database.identity_db]
url = "jdbc:mysql://<database-host>:3306/WSO2IDENTITY_DB?autoReconnect=true&amp;useSSL=false"
username = "<database-user>"
password = "<database-password>"
driver = "com.mysql.cj.jdbc.Driver"

[database.identity_db.pool_options]
maxActive = "150"
maxWait = "60000"
minIdle = "5"
testOnBorrow = true
validationQuery = "SELECT 1"
validationInterval = "30000"
defaultAutoCommit = false
```

Map the created databases to the corresponding TOML configuration sections:

| Database | TOML Configuration |
|---|---|
| `WSO2IDENTITY_DB` | `[database.identity_db]` |
| `WSO2SHARED_DB` | `[database.shared_db]` |
| `WSO2AGENTIDENTITY_DB` | `[datasource.AgentIdentity]` |
| `WSO2DPDP_DB` | `[datasource.WSO2DPDP_DB]` |

For custom datasources (`[datasource.AgentIdentity]` and `[datasource.WSO2DPDP_DB]`), configure them using the following format:

```toml
[datasource.WSO2DPDP_DB]
id = "WSO2DPDP_DB"
url = "jdbc:mysql://<database-host>:3306/WSO2DPDP_DB?autoReconnect=true&amp;useSSL=false"
username = "<database-user>"
password = "<database-password>"
driver = "com.mysql.cj.jdbc.Driver"
pool_options.maxActive = "150"
pool_options.maxWait = "60000"
pool_options.minIdle = "5"
pool_options.testOnBorrow = true
pool_options.validationQuery = "SELECT 1"
pool_options.validationInterval = "30000"
pool_options.defaultAutoCommit = false
```

</TabItem>
<TabItem value="postgresql" label="PostgreSQL">

Open the `<IS_HOME>/repository/conf/deployment.toml` file and update the datasource configurations:

```toml
[database.identity_db]
url = "jdbc:postgresql://<database-host>:5432/WSO2IDENTITY_DB?sslmode=verify-full"
username = "<database-user>"
password = "<database-password>"
driver = "org.postgresql.Driver"

[database.identity_db.pool_options]
maxActive = "150"
maxWait = "60000"
minIdle = "5"
testOnBorrow = true
validationQuery = "SELECT 1"
validationInterval = "30000"
defaultAutoCommit = false
```

Map the created databases to the corresponding TOML configuration sections:

| Database | TOML Configuration |
|---|---|
| `WSO2IDENTITY_DB` | `[database.identity_db]` |
| `WSO2SHARED_DB` | `[database.shared_db]` |
| `WSO2AGENTIDENTITY_DB` | `[datasource.AgentIdentity]` |
| `WSO2DPDP_DB` | `[datasource.WSO2DPDP_DB]` |

For custom datasources (`[datasource.AgentIdentity]` and `[datasource.WSO2DPDP_DB]`), configure them using the following format:

```toml
[datasource.WSO2DPDP_DB]
id = "WSO2DPDP_DB"
url = "jdbc:postgresql://<database-host>:5432/WSO2DPDP_DB?sslmode=verify-full"
username = "<database-user>"
password = "<database-password>"
driver = "org.postgresql.Driver"
pool_options.maxActive = "150"
pool_options.maxWait = "60000"
pool_options.minIdle = "5"
pool_options.testOnBorrow = true
pool_options.validationQuery = "SELECT 1"
pool_options.validationInterval = "30000"
pool_options.defaultAutoCommit = false
```

</TabItem>
</Tabs>

---

## 4. Install the JDBC driver

Copy the JDBC driver JAR for your database into `<IS_HOME>/repository/components/lib` before starting the server. The `dropins` directory is for OSGi bundles, not for drivers.

<Tabs groupId="database">
<TabItem value="mysql" label="MySQL" default>

MySQL 8.0: [`mysql-connector-j-9.2.0.jar`](https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.2.0/mysql-connector-j-9.2.0.jar)

</TabItem>
<TabItem value="postgresql" label="PostgreSQL">

PostgreSQL 15, 16 or 17: [`postgresql-42.7.13.jar`](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.13/postgresql-42.7.13.jar)

</TabItem>
</Tabs>

---

## Next steps

After setting up the databases, executing the scripts, and configuring datasources:

- Continue with [Configuring the Accelerator](configuring-the-accelerator.md) to set up server hostnames, administrator credentials, and encrypt passwords with the Cipher Tool.
