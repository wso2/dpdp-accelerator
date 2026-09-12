#!/bin/bash
# Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
#
# WSO2 LLC. licenses this file to you under the Apache License,
# Version 2.0 (the "License"); you may not use this file except
# in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
#
# Installs the accelerator's deployment.toml over an Identity Server and prepares
# its databases.
#
#   ./configure.sh <WSO2_IS_HOME>

set -e

WSO2_IS_HOME=$1
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACCELERATOR_HOME="$(dirname "${SCRIPT_DIR}")"

if [ -z "${WSO2_IS_HOME}" ]; then
  WSO2_IS_HOME="$(dirname "${ACCELERATOR_HOME}")"
fi

if [ ! -d "${WSO2_IS_HOME}/repository/components" ]; then
  printf '\nERROR: %s is not a valid Carbon product path.\n\n' "${WSO2_IS_HOME}"
  exit 2
fi

# This script replaces deployment.toml and runs a schema migration against the
# embedded database, neither of which is safe while the server holds it open.
if pgrep -f "carbon.home=${WSO2_IS_HOME}" > /dev/null 2>&1; then
  printf '\nERROR: the Identity Server at %s is still running.\n' "${WSO2_IS_HOME}"
  printf '       Stop it first: sh %s/bin/wso2server.sh stop\n\n' "${WSO2_IS_HOME}"
  exit 2
fi

# shellcheck source=/dev/null
source "${ACCELERATOR_HOME}/repository/conf/configure.properties"
# shellcheck source=/dev/null
source "${ACCELERATOR_HOME}/repository/conf/dbprofiles.properties"

DEPLOYMENT_TOML="${WSO2_IS_HOME}/repository/conf/deployment.toml"
TOML_TEMPLATE="${ACCELERATOR_HOME}/${PRODUCT_CONF_PATH}"
TOML_STAGING="${ACCELERATOR_HOME}/repository/resources/deployment.toml"

# The credentials baked into the stock deployment.toml for the embedded databases.
# H2 ignores DB_USER/DB_PASS so that an operator switching DB_TYPE back to h2 does
# not have to remember to restore them too.
H2_USER=wso2carbon
H2_PASS=wso2carbon

# --------------------------------------------------------------- database profile
# Selects one block out of dbprofiles.properties by uppercased DB_TYPE. Indirect
# expansion (rather than an associative array) keeps this working on the bash 3.2
# that macOS still ships.
resolve_db_profile() {
  local prefix key var port_override
  prefix="$(echo "${DB_TYPE}" | tr '[:lower:]' '[:upper:]')"

  # DB_PORT is an operator override in configure.properties; the profile also
  # carries a <TYPE>_PORT default, so the override is saved before the lookup and
  # restored after it.
  port_override="${DB_PORT-}"

  for key in PORT DRIVER URL_TEMPLATE SCRIPT_SUFFIX DRIVER_URL CLIENT \
             CLIENT_ARGS CLIENT_QUERY_ARGS PW_ENV ADMIN_DB \
             DB_EXISTS_SQL CREATE_DB_SQL DROP_DB_SQL IS_CHARSET DPDP_CHARSET; do
    var="${prefix}_${key}"
    eval "DB_${key}=\${${var}-}"
  done

  if [ -z "${DB_SCRIPT_SUFFIX}" ]; then
    printf '\nERROR: DB_TYPE "%s" has no block in repository/conf/dbprofiles.properties.\n' "${DB_TYPE}"
    printf '       Define a %s_* block there, or pick a type that already has one.\n\n' "${prefix}"
    exit 2
  fi

  # An if, not `[ ... ] && ...`: as the function's last command the latter would
  # return non-zero whenever the override is empty, which under `set -e` aborts the
  # whole script.
  if [ -n "${port_override}" ]; then
    DB_PORT="${port_override}"
  fi
}

resolve_db_profile

# Expands {host}/{port}/{db}/{user} in a profile template, plus {charset} when a third
# argument is given (only the CREATE DATABASE template uses it).
expand_template() {
  local template="$1" db="$2" charset="${3-}"
  printf '%s' "${template}" \
    | sed -e "s|{host}|${DB_HOST}|g" -e "s|{port}|${DB_PORT}|g" -e "s|{db}|${db}|g" \
          -e "s|{user}|${DB_USER}|g" -e "s|{charset}|${charset}|g"
}

echo "Product home: ${WSO2_IS_HOME}"
echo "Database type: ${DB_TYPE}"
echo

# --------------------------------------------------------------- SQL execution
# H2 is embedded, so it is driven through the engine jar already in the pack. Every
# other type is driven through its own client, whose invocation comes from the
# profile - so a new server-based type needs a profile block, not a code change.
H2_JAR=""
locate_h2_jar() {
  [ -n "${H2_JAR}" ] && return 0
  H2_JAR=$(find "${WSO2_IS_HOME}/repository/components/plugins" -name "h2-engine_*.jar" | head -1)
  [ -n "${H2_JAR}" ]
}

# run_client <database> <extra args...>  - stdin is forwarded to the client.
run_client() {
  local db="$1"; shift
  local args
  args="$(expand_template "${DB_CLIENT_ARGS}" "${db}")"
  if [ -n "${DB_PW_ENV}" ]; then
    export "${DB_PW_ENV}=${DB_PASS}"
  fi
  # Word splitting is intentional: the profile supplies a flag string.
  # shellcheck disable=SC2086
  "${DB_CLIENT}" ${args} "$@"
}

# apply_sql_file <database> <path to .sql>
apply_sql_file() {
  local db="$1" file="$2"
  if [ "${DB_TYPE}" = "h2" ]; then
    java -cp "${H2_JAR}" org.h2.tools.RunScript \
      -url "jdbc:h2:${WSO2_IS_HOME}/repository/database/${db}" \
      -user "${H2_USER}" -password "${H2_PASS}" -script "${file}"
  else
    run_client "${db}" < "${file}"
  fi
}

# run_admin_sql <sql>  - executed against the server-level database, for CREATE/DROP.
run_admin_sql() {
  run_client "${DB_ADMIN_DB}" -e "$1" > /dev/null
}

# database_exists <name>
# Only empty output means "no such database". A failed query is reported and aborts,
# because silently treating it as absent would send the product's non-idempotent DDL
# at a database that may already hold data. The `if` form is required: a failing
# command substitution in a plain assignment aborts under `set -e` before the status
# can be read.
database_exists() {
  local sql out args
  sql="$(expand_template "${DB_DB_EXISTS_SQL}" "$1")"
  args="$(expand_template "${DB_CLIENT_ARGS}" "${DB_ADMIN_DB}")"
  if [ -n "${DB_PW_ENV}" ]; then
    export "${DB_PW_ENV}=${DB_PASS}"
  fi
  # shellcheck disable=SC2086
  if ! out="$("${DB_CLIENT}" ${args} ${DB_CLIENT_QUERY_ARGS} -e "${sql}" 2>&1)"; then
    printf '\nERROR: could not query %s on %s:%s for database "%s".\n' \
      "${DB_ADMIN_DB}" "${DB_HOST}" "${DB_PORT}" "$1"
    printf '       %s\n' "${out}"
    printf '       Check DB_HOST, DB_PORT, DB_USER and DB_PASS in repository/conf/configure.properties.\n\n'
    exit 2
  fi
  [ -n "${out}" ]
}

# ---------------------------------------------------------------- deployment.toml
# One deployment.toml template with placeholders in its four datasource blocks; the
# values below are filled in for whichever DB_TYPE is selected. The template is the
# stock IS file except that those blocks are tokenised and carry a uniform
# driver + pool_options (validationQuery guards a pooled connection the server has
# already dropped from serving a stale/failed read).
if [ ! -f "${TOML_TEMPLATE}" ]; then
  echo "ERROR: no deployment.toml template at ${TOML_TEMPLATE}"
  echo "       Check PRODUCT_CONF_PATH in repository/conf/configure.properties."
  exit 2
fi

# One global sed substitution on the staging copy. The value is escaped for the sed
# replacement (& means the whole match, \ escapes, | is our delimiter), which also
# lets a URL carry &amp; and a password carry those characters safely.
subst_token() {
  local token="$1" value
  value="$(printf '%s' "$2" | sed -e 's/[\\&|]/\\&/g')"
  # sed -i needs a backup suffix to be portable across GNU and BSD; remove it after.
  sed -i.tmp "s|${token}|${value}|g" "${TOML_STAGING}"
  rm -f "${TOML_STAGING}.tmp"
}

# h2 authenticates with the fixed embedded-database credentials; every other type
# uses the operator's connection account from configure.properties.
if [ "${DB_TYPE}" = "h2" ]; then
  CONN_USER="${H2_USER}"; CONN_PASS="${H2_PASS}"
else
  CONN_USER="${DB_USER}"; CONN_PASS="${DB_PASS}"
fi

echo "[1/4] Installing deployment.toml for ${DB_TYPE}"
cp "${TOML_TEMPLATE}" "${TOML_STAGING}"

# No token is a substring of another, so order is free.
subst_token IS_HOSTNAME "${IS_HOSTNAME}"
subst_token IS_ADMIN_USERNAME "${IS_ADMIN_USERNAME}"
subst_token IS_ADMIN_PASSWORD "${IS_ADMIN_PASSWORD}"
subst_token DB_DIALECT "${DB_TYPE}"
subst_token DB_DRIVER_CLASS "${DB_DRIVER}"
subst_token DB_CONN_USERNAME "${CONN_USER}"
subst_token DB_CONN_PASSWORD "${CONN_PASS}"
subst_token DB_IDENTITY_URL "$(expand_template "${DB_URL_TEMPLATE}" "${DB_IDENTITY}")"
subst_token DB_SHARED_URL "$(expand_template "${DB_URL_TEMPLATE}" "${DB_SHARED}")"
subst_token DB_AGENT_URL "$(expand_template "${DB_URL_TEMPLATE}" "${DB_AGENT_IDENTITY}")"
subst_token DB_DPDP_URL "$(expand_template "${DB_URL_TEMPLATE}" "${DB_DPDP}")"

if [ -f "${DEPLOYMENT_TOML}" ]; then
  BACKUP="${DEPLOYMENT_TOML}.bak-$(date +%Y%m%d%H%M%S)"
  cp "${DEPLOYMENT_TOML}" "${BACKUP}"
  echo "      Previous deployment.toml backed up to $(basename "${BACKUP}")"
fi

cp "${TOML_STAGING}" "${DEPLOYMENT_TOML}"
rm -f "${TOML_STAGING}"
echo "      deployment.toml was REPLACED, not merged - re-apply any local"
echo "      customisation from the backup before starting the server."

# ------------------------------------------------------------------- JDBC driver
# Licensing keeps vendor drivers out of the distribution, so the pinned jar named by
# the profile is fetched on demand - the same version for operators and for CI.
LIB_DIR="${WSO2_IS_HOME}/repository/components/lib"
if [ "${DB_TYPE}" = "h2" ]; then
  echo "[2/4] No JDBC driver needed for the embedded database."
elif [ -z "${DB_DRIVER_URL}" ]; then
  echo "[2/4] No driver URL for ${DB_TYPE}; place the JDBC driver in ${LIB_DIR} yourself."
else
  DRIVER_JAR="${LIB_DIR}/$(basename "${DB_DRIVER_URL}")"
  if [ -f "${DRIVER_JAR}" ]; then
    echo "[2/4] JDBC driver already present: $(basename "${DRIVER_JAR}")"
  else
    echo "[2/4] Downloading $(basename "${DRIVER_JAR}")"
    # Downloaded beside the target and moved into place only once complete: an
    # interrupted transfer would otherwise leave a truncated jar that the next run
    # reports as "already present", and the server then fails to load the driver.
    DRIVER_TMP="${DRIVER_JAR}.part"
    if command -v curl > /dev/null 2>&1; then
      curl -fsSL "${DB_DRIVER_URL}" -o "${DRIVER_TMP}" || { rm -f "${DRIVER_TMP}"; exit 2; }
    elif command -v wget > /dev/null 2>&1; then
      wget -q "${DB_DRIVER_URL}" -O "${DRIVER_TMP}" || { rm -f "${DRIVER_TMP}"; exit 2; }
    else
      echo "      ERROR: neither curl nor wget is available; download ${DB_DRIVER_URL}"
      echo "             into ${LIB_DIR} manually."
      exit 2
    fi
    mv "${DRIVER_TMP}" "${DRIVER_JAR}"
    echo "      Driver installed."
  fi
fi

# ------------------------------------------------- Identity Server databases/schema
# On h2 there is nothing to create: WSO2's build bakes a fully populated
# WSO2IDENTITY_DB into the pack, so only the post-update consent migration is
# outstanding. Every other type starts empty and needs the product's own schema.
IS_DB_CREATED=""

create_is_databases() {
  local db charset
  for db in "${DB_SHARED}" "${DB_IDENTITY}" "${DB_AGENT_IDENTITY}" "${DB_DPDP}"; do
    # WSO2DPDP_DB holds our own, potentially multilingual data; the product's own
    # databases must match the latin1 their DDL hardcodes. See dbprofiles.properties.
    if [ "${db}" = "${DB_DPDP}" ]; then
      charset="${DB_DPDP_CHARSET}"
    else
      charset="${DB_IS_CHARSET}"
    fi
    if [ "${RECREATE_DATABASES}" = "true" ]; then
      run_admin_sql "$(expand_template "${DB_DROP_DB_SQL}" "${db}")"
      run_admin_sql "$(expand_template "${DB_CREATE_DB_SQL}" "${db}" "${charset}")"
      echo "      Recreated ${db} (${charset})"
      IS_DB_CREATED="${IS_DB_CREATED} ${db}"
    elif database_exists "${db}"; then
      echo "      ${db} already exists; leaving its data alone"
    else
      run_admin_sql "$(expand_template "${DB_CREATE_DB_SQL}" "${db}" "${charset}")"
      echo "      Created ${db} (${charset})"
      IS_DB_CREATED="${IS_DB_CREATED} ${db}"
    fi
  done
}

# The product's own DDL is not idempotent - consent/mysql.sql guards none of its 10
# CREATE TABLEs, agent none of 4 - so it is applied only to a database this run
# created. Re-running against an existing database would error and, worse, is exactly
# where a careless DROP would destroy real data.
was_created() {
  case " ${IS_DB_CREATED} " in *" $1 "*) return 0 ;; *) return 1 ;; esac
}

apply_is_schema() {
  local suffix="${DB_SCRIPT_SUFFIX}"
  if was_created "${DB_SHARED}"; then
    echo "      Applying dbscripts/${suffix}.sql to ${DB_SHARED}"
    apply_sql_file "${DB_SHARED}" "${WSO2_IS_HOME}/dbscripts/${suffix}.sql"
  fi
  if was_created "${DB_IDENTITY}"; then
    echo "      Applying dbscripts/identity/${suffix}.sql to ${DB_IDENTITY}"
    apply_sql_file "${DB_IDENTITY}" "${WSO2_IS_HOME}/dbscripts/identity/${suffix}.sql"
    echo "      Applying dbscripts/consent/${suffix}.sql to ${DB_IDENTITY}"
    apply_sql_file "${DB_IDENTITY}" "${WSO2_IS_HOME}/dbscripts/consent/${suffix}.sql"
  fi
  if was_created "${DB_AGENT_IDENTITY}"; then
    echo "      Applying dbscripts/identity/agent/${suffix}.sql to ${DB_AGENT_IDENTITY}"
    apply_sql_file "${DB_AGENT_IDENTITY}" "${WSO2_IS_HOME}/dbscripts/identity/agent/${suffix}.sql"
  fi
}

# MySQL's InnoDB rejects a standalone "ADD COLUMN ... AUTO_INCREMENT" - the column
# has to be indexed in the same ALTER. WSO2's migration adds the key in the very next
# statement, which is fine on H2 but errors on MySQL (1075), so the two consecutive
# ALTERs on one table are folded into one. Left untouched for H2, whose own migration
# file works as shipped.
# This is an upstream Identity Server bug: wso2/product-is#28412. Remove this workaround
# once the shipped mysql-migration.txt applies cleanly to MySQL 8.
#
# Assumes the ALTER that adds the key immediately follows the one adding the column, as it
# does in the shipped migration - the fold does not compare table names. If a future
# migration puts an unrelated ALTER TABLE next, the second clause would be applied to the
# first table. Re-check this if the workaround outlives the upstream fix.
mysqlify_migration() {
  awk '
    held != "" {
      if ($0 ~ /^[[:space:]]*$/) { next }
      if (match($0, /^[[:space:]]*ALTER TABLE[[:space:]]+[^[:space:]]+[[:space:]]+/)) {
        print held ", " substr($0, RLENGTH + 1)
      } else {
        print held ";"; print $0
      }
      held = ""; next
    }
    /ADD COLUMN/ && /AUTO_INCREMENT[[:space:]]*;[[:space:]]*$/ {
      held = $0; sub(/[[:space:]]*;[[:space:]]*$/, "", held); next
    }
    { print }
    END { if (held != "") print held ";" }
  '
}

# The consent v2 tables ship in neither the base scripts nor the pre-built H2 file -
# they exist only in this migration, which arrives with the U2 updates.
apply_consent_migration() {
  local target="$1" migration tmp_sql
  migration="${WSO2_IS_HOME}/dbscripts/migrations/consent/${DB_SCRIPT_SUFFIX}-migration.txt"
  if [ ! -f "${migration}" ]; then
    echo "      WARNING: no migration script at ${migration}; skipping."
    return 0
  fi
  echo "      Applying the consent schema migration to ${target}"
  tmp_sql="$(mktemp)"
  if [ "${DB_TYPE}" = "h2" ]; then
    grep -v '^#' "${migration}" > "${tmp_sql}"
  else
    grep -v '^#' "${migration}" | mysqlify_migration > "${tmp_sql}"
  fi
  apply_sql_file "${target}" "${tmp_sql}"
  rm -f "${tmp_sql}"
}

if [ "${APPLY_IS_CONSENT_MGT_V2_MIGRATION}" != "true" ] && [ "${DB_TYPE}" = "h2" ]; then
  echo "[3/4] Skipping the consent schema migration (APPLY_IS_CONSENT_MGT_V2_MIGRATION is not true)."
elif [ "${DB_TYPE}" = "h2" ]; then
  # The embedded database is the file WSO2 ships pre-populated; there is nothing to drop
  # or create, so RECREATE_DATABASES has no meaning here. Say so rather than ignore it
  # silently, or a reader expecting a clean reset gets one with no explanation.
  if [ "${RECREATE_DATABASES}" = "true" ]; then
    echo "[3/4] NOTE: RECREATE_DATABASES is ignored for the embedded h2 database."
  fi
  if ! locate_h2_jar; then
    echo "[3/4] WARNING: could not locate the H2 engine jar; apply the consent migration manually."
  else
    echo "[3/4] Preparing the embedded Identity Server database"
    apply_consent_migration "${DB_IDENTITY}"
  fi
else
  echo "[3/4] Preparing the ${DB_TYPE} Identity Server databases"
  if [ "${RECREATE_DATABASES}" = "true" ]; then
    echo "      RECREATE_DATABASES=true - dropping and recreating all four databases"
  fi
  create_is_databases
  apply_is_schema
  if [ "${APPLY_IS_CONSENT_MGT_V2_MIGRATION}" != "true" ]; then
    echo "      Skipping the consent schema migration (APPLY_IS_CONSENT_MGT_V2_MIGRATION is not true)."
  elif was_created "${DB_IDENTITY}"; then
    apply_consent_migration "${DB_IDENTITY}"
  else
    echo "      ${DB_IDENTITY} pre-existed; not re-applying the consent migration."
  fi
fi

# ------------------------------------------------------------- DPDP DB schema creation
# WSO2DPDP_DB is not part of the stock distribution, so unlike the Identity Server's
# own databases there is nothing pre-built to migrate - H2 creates it fresh on first
# connection, RunScript included, and the block above creates it for other types.
# Every DPDP feature has its own subdirectory under dbscripts/dpdp-accelerator/ - each
# one's ${DB_SCRIPT_SUFFIX}.sql is applied in turn, so a new feature directory needs no
# edit here. These scripts are all CREATE TABLE IF NOT EXISTS, so re-running is safe.
if [ "${APPLY_DPDP_DB_MIGRATION}" != "true" ]; then
  echo "[4/4] Skipping the DPDP schema creation (APPLY_DPDP_DB_MIGRATION is not true)."
else
  DPDP_DBSCRIPTS_DIR="${ACCELERATOR_HOME}/carbon-home/dbscripts/dpdp-accelerator"
  if [ "${DB_TYPE}" = "h2" ] && ! locate_h2_jar; then
    echo "[4/4] WARNING: could not locate the H2 engine jar; apply each feature's ${DB_SCRIPT_SUFFIX}.sql under ${DPDP_DBSCRIPTS_DIR}/<feature>/ manually."
  else
    echo "[4/4] Creating the DPDP schema in ${DB_DPDP}"
    for FEATURE_DIR in "${DPDP_DBSCRIPTS_DIR}"/*/; do
      FEATURE_NAME="$(basename "${FEATURE_DIR}")"
      FEATURE_SCRIPT="${FEATURE_DIR}${DB_SCRIPT_SUFFIX}.sql"
      if [ ! -f "${FEATURE_SCRIPT}" ]; then
        echo "      WARNING: no ${DB_SCRIPT_SUFFIX}.sql for feature '${FEATURE_NAME}'; skipping."
        continue
      fi
      echo "      Applying ${FEATURE_NAME}/${DB_SCRIPT_SUFFIX}.sql"
      apply_sql_file "${DB_DPDP}" "${FEATURE_SCRIPT}"
    done
    echo "      Schema created."
  fi
fi

echo
echo "Configuration complete. Now:"
echo "  1. Start the Identity Server."
echo "  2. Follow docs/configuration-guide.md to register the portal application."
