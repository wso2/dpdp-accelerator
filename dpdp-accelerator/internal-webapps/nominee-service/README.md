# Nominee Service

Owns nominations under DPDP Act section 14: an owner appoints one or more
nominees who may exercise the owner's data rights on their behalf.

This service holds the record of that authority and the record of its exercise.
It answers, for one owner-nominee pair, whether the nomination is active and
which permissions it carries. The WSO2 Identity Server consults it before issuing
an impersonation token, and the portal backend consults it again on every request
made while acting, so a withdrawn permission takes effect on the next request
rather than at token expiry.

For why it is built this way — the lifecycle rules, the permission freeze, the
acting-token guard and the audit chain — see [DESIGN.md](DESIGN.md). The database
tables are described in [DATABASE.md](DATABASE.md), and the HTTP contract in
[`api/nominee-management-API.yaml`](api/nominee-management-API.yaml).

It also holds the audit chain. A nominee reading an owner's records, or being
refused an action, reaches no other system: the Consent Server observes only
successful writes.

## Prerequisites

- Java 21
- Maven 3.9+
- One of MySQL 8, PostgreSQL 13+ or SQLite 3

## Quick Start

### 1. Build

```bash
mvn clean package
```

The runnable jar is `target/nominee-service-0.1.0.jar`.

### 2. Setup Database

Pick one of the three supported databases and create its schema from the
matching script in `dbscripts/`.

This step is required. The service starts with `ddl-auto: validate` and refuses
to run if the schema and the entity mappings disagree, so the script must be
applied before the first start. It never creates or alters tables itself.

**MySQL:**

```bash
# Create database
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS nominee_mgt;"

# Import schema
mysql -u root -p nominee_mgt < dbscripts/db_schema_mysql.sql
```

**PostgreSQL:**

```bash
# Create database
psql -U postgres -c "CREATE DATABASE nominee_mgt;"

# Import schema
psql -U postgres -d nominee_mgt -f dbscripts/db_schema_postgres.sql
```

**SQLite:**

```bash
# Create the database directory
mkdir -p data

# Initialize the SQLite database with the schema
sqlite3 data/nominee-service.db < dbscripts/db_schema_sqlite.sql
```

### 3. Configure Application

Configuration is by environment variable. `NOMINEE_DB_TYPE` selects the
database and must be one of `mysql`, `postgres` or `sqlite`.

**MySQL:**

```bash
export NOMINEE_DB_TYPE=mysql
export NOMINEE_DB_HOST=localhost
export NOMINEE_DB_PORT=3306
export NOMINEE_DB_NAME=nominee_mgt
export NOMINEE_DB_USER=root
export NOMINEE_DB_PASSWORD=<password>
```

**PostgreSQL:**

```bash
export NOMINEE_DB_TYPE=postgres
export NOMINEE_DB_HOST=localhost
export NOMINEE_DB_PORT=5432
export NOMINEE_DB_NAME=nominee_mgt
export NOMINEE_DB_USER=postgres
export NOMINEE_DB_PASSWORD=<password>
```

**SQLite:**

```bash
export NOMINEE_DB_TYPE=sqlite
export NOMINEE_DB_PATH=./data/nominee-service.db
```

| Variable | Applies to | Default |
|---|---|---|
| `NOMINEE_DB_TYPE` | all | `mysql` |
| `NOMINEE_DB_HOST` | mysql, postgres | `localhost` |
| `NOMINEE_DB_PORT` | mysql, postgres | `3306` / `5432` |
| `NOMINEE_DB_NAME` | mysql, postgres | `nominee_mgt` |
| `NOMINEE_DB_USER` | mysql, postgres | required |
| `NOMINEE_DB_PASSWORD` | mysql, postgres | required |
| `NOMINEE_DB_PATH` | sqlite | `./data/nominee-service.db` |

The Identity Server settings in `src/main/resources/application.yml` must match
the portal backend's `BFF_AUTH__*` values, since both validate the same tokens.

### 4. Run

```bash
java -jar target/nominee-service-0.1.0.jar
```

Starts on port 8082.

## Database schema

`dbscripts/` holds one script per database. They are **generated from the entity
definitions**, not maintained by hand. Regenerate after changing any entity:

```bash
mvn -q test-compile exec:java \
  -Dexec.mainClass=com.openfgc.nomineeservice.GenerateSchemaScripts \
  -Dexec.classpathScope=test
```

Generating rather than hand-editing is what keeps the scripts and the mappings
from drifting. A mapping change missing from a script would otherwise surface
only as a startup failure, and only on whichever database the script forgot.

`DATABASE.md` documents the tables, the audit chain and its known limitations.

## API

The full contract is in `api/nominee-management-API.yaml` (OpenAPI 3.0).

**Owner facing** — `portal:profile:read:self` / `portal:profile:write:self`

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/me/nominees` | Appoint a nominee |
| `GET` | `/me/nominees` | List the nominees I appointed |
| `PATCH` | `/me/nominees/{id}` | Change what one nominee may do |
| `DELETE` | `/me/nominees/{id}` | Remove a nominee |

**Nominee facing** — same scopes

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/nominations/{id}/accept` | Accept a nomination naming me |
| `GET` | `/nominated-for` | Accounts I have been nominated for |

**Administrative** — `portal:profile:read:any` / `portal:profile:write:any`

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/admin/nominations?ownerId=` | One owner's nominations |
| `GET` | `/admin/nominations/pending` | The activation queue |
| `POST` | `/admin/nominations/{id}/activate` | Bring a nomination into force |
| `POST` | `/admin/nominations/{id}/deactivate` | Withdraw it |

**Internal** — authenticated by `X-Internal-Key`, called by infrastructure

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/internal/nominations/active` | Is this pair active? |
| `GET` | `/internal/nominations/permissions` | What may this nominee do? |
| `POST` | `/internal/nominations/audit` | Append an acting event |

Callers present the portal's split access token: the first part as
`Authorization: Bearer`, the second as an HttpOnly cookie. Neither half is a
valid token on its own.

Every owner- and nominee-facing endpoint refuses a token that is acting on
another user's behalf. Managing nominations is a right the owner exercises
personally, and delegation is not transitive.

## Lifecycle

```
PENDING ──accept──▶ ACCEPTED ──activate──▶ ACTIVE ──deactivate──▶ DEACTIVATED
```

Acceptance grants nothing. A nomination confers no authority until an
administrator activates it against a recorded ticket reference, which is where
the manual verification is evidenced.

Withdrawal takes effect immediately. An impersonation token already issued stays
cryptographically valid, so it is the gate rather than expiry that stops the
nominee, on their next request.

## Permissions

`CONSENT_VIEW`, `CONSENT_REVOKE`, `CONSENT_APPROVE`, `ACCOUNT_VIEW`,
`ACCOUNT_UPDATE`, `ACCOUNT_DELETE`.

Some require another and are stored together with it: `CONSENT_REVOKE` and
`CONSENT_APPROVE` each imply `CONSENT_VIEW`, since a consent must be found
before it can be acted on, and `ACCOUNT_UPDATE` and `ACCOUNT_DELETE` imply
`ACCOUNT_VIEW`. A stored grant therefore always describes something the nominee
can actually carry out.

Revocation and approval are separate grants and neither implies the other:
revoking withdraws processing the owner already chose, while approving
authorises new processing in their name.

`CONSENT_VIEW`, `CONSENT_REVOKE` and `CONSENT_APPROVE` are enforced by an
endpoint today. The others are accepted, stored and carried in the token, but
nothing acts on them yet.

## Tests

The tests run against MySQL — the same engine the service runs on — in a
database of their own, so they never touch real data. Create it once:

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS nominee_mgt_test;"
```

The schema is created and dropped per run, so no script has to be applied. Then:

```bash
export NOMINEE_DB_USER=root
export NOMINEE_DB_PASSWORD=<password>
mvn test
```

Without those two variables the connection is attempted with no password and
every test fails to start with `Access denied for user 'root'@'localhost'`.
