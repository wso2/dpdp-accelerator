#!/usr/bin/env bash
# Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
#
# WSO2 LLC. licenses this file to you under the Apache License,
# Version 2.0 (the "License"); you may not use this file except
# in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.

# Creates the three accounts the integration suite needs and assigns their roles.
#
# The accelerator provisions the DPDP Consent Portal application and both roles
# automatically, but never any user and never role membership - see
# docs/configuration-guide.md, which documents membership as a manual Console step.
# This script is that manual step, automated, so CI and a fresh local install can
# reach a runnable state the same way.
#
# Idempotent: safe to re-run against a long-lived server. Existing users are left
# alone and re-adding an existing role member is a no-op (verified: SCIM2
# `op: add` on `path: users` appends rather than replacing, so other members of
# dpdp-consent-admin are never evicted).
#
# Usage:
#   bash scripts/provision-test-users.sh
#
# Every setting - server URL, the three usernames, their password, the roles to assign - comes
# from e2e-config.json plus the optional e2e-config.local.json beside it, the same single
# configuration the TypeScript side reads (see utils/config.ts). There are no environment
# variables and no defaults inlined here. ./scripts/setup-local.sh generates the password into
# the local file; without it this script says which key is missing.
#
# Authenticates as the provisioning client recorded in that same config - see AUTHENTICATION
# below. Mint it with: npm run bootstrap:provisioning-app

set -euo pipefail

SUITE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# The persona usernames must stay identical to what the TypeScript side signs in as, so both
# read them from the same file rather than each carrying its own copy. They are email-shaped
# because the accelerator enforces it: the username regex is
# ^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}$, so a bare "dpdp-ci-user" is rejected with
# SCIM2 31301 and provisioning aborts.
#
# The plain user persona must NOT be an administrator: tests/04-authorization asserts it holds
# only internal_login. dpdp-consent-user carries zero permissions (it is created with an empty
# permission list), so assigning it grants no scopes and cannot perturb those assertions - it is
# assigned only to mirror the documented setup.
CONFIG_EXPORTS="$(python3 - "${SUITE_DIR}" <<'PYEOF'
import json, os, shlex, sys

root = sys.argv[1]


def load(name):
    try:
        with open(os.path.join(root, name)) as handle:
            return json.load(handle)
    except FileNotFoundError:
        return {}


def merge(base, override):
    merged = dict(base)
    for key, value in override.items():
        if isinstance(value, dict) and isinstance(merged.get(key), dict):
            merged[key] = merge(merged[key], value)
        else:
            merged[key] = value
    return merged


config = merge(load('e2e-config.json'), load('e2e-config.local.json'))


def require(value, key):
    if value in (None, ''):
        sys.exit(
            'ERROR: "%s" is not configured. Set it in e2e-config.local.json, or run\n'
            '       ./scripts/setup-local.sh, which generates everything this script needs.' % key
        )
    return value


emit = {
    'IS_BASE_URL': require(config['identityServer']['baseUrl'], 'identityServer.baseUrl').rstrip('/'),
    'IGNORE_HTTPS_ERRORS': 'true' if config['identityServer']['ignoreHttpsErrors'] else 'false',
    'USER_ROLE': require(config['personaRoles']['user'], 'personaRoles.user'),
    'PROVISIONING_CLIENT_ID': require(
        (config.get('provisioningClient') or {}).get('clientId'), 'provisioningClient.clientId'
    ),
    'PROVISIONING_CLIENT_SECRET': require(
        (config.get('provisioningClient') or {}).get('clientSecret'), 'provisioningClient.clientSecret'
    ),
    'ADMIN_ROLE': require(config['personaRoles']['consentAdmin'], 'personaRoles.consentAdmin'),
}
for shell_name, persona in (('USER', 'user'), ('USER_2', 'user2'), ('ADMIN', 'consentAdmin')):
    entry = config['personas'][persona]
    emit['%s_NAME' % shell_name] = require(entry['username'], 'personas.%s.username' % persona)
    emit['%s_PASSWORD' % shell_name] = require(entry['password'], 'personas.%s.password' % persona)

for name, value in emit.items():
    print('%s=%s' % (name, shlex.quote(value)))
PYEOF
)"
eval "${CONFIG_EXPORTS}"

CURL_OPTS=(-s --max-time 30)
if [ "${IGNORE_HTTPS_ERRORS}" = "true" ]; then
  CURL_OPTS+=(-k)
fi

# --- helpers ----------------------------------------------------------------

# Reads a value out of a JSON document on stdin. Keeps jq out of the dependency
# list, since python3 is present on GitHub runners and on developer machines.
json() {
  python3 -c "$1"
}

api() {
  curl "${CURL_OPTS[@]}" "$@"
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

# Prints the SCIM2 id of a user, or an empty string when the user does not exist.
find_user_id() {
  api "${IS_BASE_URL}/scim2/Users?filter=userName+eq+${1}" | json '
import json, sys
d = json.load(sys.stdin)
res = d.get("Resources") or []
print(res[0]["id"] if res else "")
'
}

# Prints the SCIM2 id of a role by display name, or an empty string.
find_role_id() {
  api "${IS_BASE_URL}/scim2/v2/Roles?filter=displayName+eq+${1}" | json '
import json, sys
d = json.load(sys.stdin)
res = d.get("Resources") or []
print(res[0]["id"] if res else "")
'
}

create_user() {
  local username="$1" password="$2" body response status
  body=$(TP="${password}" UN="${username}" python3 -c '
import json, os
print(json.dumps({
    "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
    "userName": os.environ["UN"],
    "password": os.environ["TP"],
    "name": {"givenName": "DPDP", "familyName": "CI"},
    "emails": [{"primary": True, "value": os.environ["UN"]}],
}))
')
  response=$(api -X POST "${IS_BASE_URL}/scim2/Users" \
    -H 'Content-Type: application/json' -d "${body}" -w '\n%{http_code}')
  status=$(printf '%s' "${response}" | tail -1)
  if [ "${status}" != "201" ]; then
    # The body can carry a password-policy message, which is the usual cause.
    printf '%s\n' "${response}" | sed '$d' >&2
    fail "creating ${username} returned HTTP ${status}"
  fi
  printf '%s' "${response}" | sed '$d' | json '
import json, sys
print(json.load(sys.stdin)["id"])
'
}

# Adds a user to a role. Appends - existing members are preserved - and is a no-op
# when the user is already a member.
assign_role() {
  local role_id="$1" user_id="$2" status
  status=$(api -X PATCH "${IS_BASE_URL}/scim2/v2/Roles/${role_id}" \
    -H 'Content-Type: application/json' \
    -d "{\"Operations\":[{\"op\":\"add\",\"path\":\"users\",\"value\":[{\"value\":\"${user_id}\"}]}]}" \
    -o /dev/null -w '%{http_code}')
  [ "${status}" = "200" ] || fail "assigning role ${role_id} returned HTTP ${status}"
}

# Creates the user if absent, then ensures the role membership.
provision() {
  local username="$1" password="$2" role_name="$3" user_id role_id

  role_id=$(find_role_id "${role_name}")
  if [ -z "${role_id}" ]; then
    fail "role ${role_name} does not exist. The accelerator creates it at startup, so this
       usually means provisioning failed silently - check for 'Error provisioning the DPDP
       Consent Portal' in repository/logs/wso2carbon.log, and confirm
       [dpdp_accelerator.consent_portal] auto_provisioning_enabled = true."
  fi

  user_id=$(find_user_id "${username}")
  if [ -n "${user_id}" ]; then
    echo "  ${username}: already exists (${user_id})"
  else
    user_id=$(create_user "${username}" "${password}")
    echo "  ${username}: created (${user_id})"
  fi

  assign_role "${role_id}" "${user_id}"
  echo "  ${username}: member of ${role_name}"
}

# --- authentication ---------------------------------------------------------
#
# Identity Server 7.3 disables HTTP Basic auth on protected resources for any deployment whose
# root organization was created on or after the cutoff in
# repository/conf/compatibility-settings-metadata.json (`basicAuth.disableBasicAuth`,
# 2026-08-13). The shipped H2 database carries a root org stamped at WSO2's own build time
# (2026-04-23), which is why Basic auth still works there; every other backend writes
# CURRENT_TIMESTAMP at install (see the UM_ORG seed row in dbscripts/mysql.sql) and so lands
# past the cutoff. A client_credentials token is the one approach that behaves identically on
# every database type, so it is the only one this script supports - a Basic-auth fallback would
# work on exactly one backend and fail confusingly everywhere else.

SCOPES="internal_user_mgt_list internal_user_mgt_create internal_role_mgt_view internal_role_mgt_users_update"

# Exchanges the client credentials for an access token, printing it. Non-zero on any failure.
mint_token() {
  local response
  response=$(curl "${CURL_OPTS[@]}" -X POST "${IS_BASE_URL}/oauth2/token" \
    -u "${PROVISIONING_CLIENT_ID}:${PROVISIONING_CLIENT_SECRET}" \
    -d grant_type=client_credentials --data-urlencode "scope=${SCOPES}") || return 1
  printf '%s' "${response}" | json '
import json, sys
try:
    print(json.load(sys.stdin)["access_token"])
except Exception:
    raise SystemExit(1)
'
}

TOKEN="$(mint_token)" || fail "the provisioning client ${PROVISIONING_CLIENT_ID} could not obtain a
       token from ${IS_BASE_URL}/oauth2/token. Re-mint it with:
         npm run bootstrap:provisioning-app"
CURL_OPTS+=(-H "Authorization: Bearer ${TOKEN}")
echo "Authenticating as the provisioning client ${PROVISIONING_CLIENT_ID}."

# --- main -------------------------------------------------------------------

echo "Provisioning integration-test accounts on ${IS_BASE_URL}"

api -o /dev/null -f "${IS_BASE_URL}/scim2/Users?count=1" \
  || fail "cannot reach ${IS_BASE_URL}/scim2 with the provisioning client's token. Is the server
       running? A 403 here means the client is missing a scope - re-run
       npm run bootstrap:provisioning-app, which re-authorizes it."

provision "${USER_NAME}"   "${USER_PASSWORD}"   "${USER_ROLE}"
provision "${USER_2_NAME}" "${USER_2_PASSWORD}" "${USER_ROLE}"
provision "${ADMIN_NAME}"  "${ADMIN_PASSWORD}"  "${ADMIN_ROLE}"

echo "Done."
