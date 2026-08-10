#!/bin/bash
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
#
# Registers the consent portal as an OAuth application on a RUNNING Identity
# Server, authorizes it for the consent management v2 APIs and a custom
# complaint management API, creates the portal administrator and complaint
# officer roles, and writes the client credentials into the portal
# configuration. Safe to re-run.

set -e

WSO2_IS_HOME=$1
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACCELERATOR_HOME="$(dirname "${SCRIPT_DIR}")"

if [ -z "${WSO2_IS_HOME}" ]; then
  WSO2_IS_HOME="$(dirname "${ACCELERATOR_HOME}")"
fi

# shellcheck source=/dev/null
source "${ACCELERATOR_HOME}/repository/conf/configure.properties"

command -v python3 >/dev/null 2>&1 || { echo "ERROR: python3 is required."; exit 2; }

BASE="https://${IS_HOSTNAME}:${IS_PORT}"
AUTH="${IS_ADMIN_USERNAME}:${IS_ADMIN_PASSWORD}"
APP_NAME="DPDP Consent Portal"
REDIRECT_URI="${BASE}/consent-portal/auth/callback"
PORTAL_PROPERTIES="${WSO2_IS_HOME}/repository/conf/dpdp-portal.properties"

# -k is required because the shipped Identity Server certificate is self-signed.
CURL="curl -sk -u ${AUTH}"

json() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)" 2>/dev/null || true; }

echo "Identity Server: ${BASE}"
if ! ${CURL} "${BASE}/api/server/v1/api-resources?limit=1" -o /dev/null -w '' 2>/dev/null; then
  echo "ERROR: cannot reach the Identity Server. Is it running?"
  exit 2
fi

# ------------------------------------------------------------------ application
echo "[1/6] Registering the OAuth application"
EXISTING=$(${CURL} --get --data-urlencode "filter=name eq ${APP_NAME}" \
  "${BASE}/api/server/v1/applications" | json "d['applications'][0]['id'] if d.get('applications') else ''")

if [ -n "${EXISTING}" ]; then
  echo "      Application already exists (${EXISTING}); reusing it."
  APP_ID="${EXISTING}"
  CLIENT_ID=$(${CURL} "${BASE}/api/server/v1/applications/${APP_ID}/inbound-protocols/oidc" | json "d.get('clientId','')")
  CLIENT_SECRET=$(${CURL} "${BASE}/api/server/v1/applications/${APP_ID}/inbound-protocols/oidc" | json "d.get('clientSecret','')")
else
  DCR=$(${CURL} -H 'Content-Type: application/json' -d "{
      \"client_name\": \"${APP_NAME}\",
      \"grant_types\": [\"authorization_code\", \"refresh_token\"],
      \"redirect_uris\": [\"${REDIRECT_URI}\"],
      \"token_type_extension\": \"JWT\"
    }" "${BASE}/api/identity/oauth2/dcr/v1.1/register")
  CLIENT_ID=$(echo "${DCR}" | json "d.get('client_id','')")
  CLIENT_SECRET=$(echo "${DCR}" | json "d.get('client_secret','')")
  if [ -z "${CLIENT_ID}" ]; then
    echo "ERROR: application registration failed: ${DCR}"
    exit 2
  fi
  APP_ID=$(${CURL} --get --data-urlencode "filter=clientId eq ${CLIENT_ID}" \
    "${BASE}/api/server/v1/applications" | json "d['applications'][0]['id']")
  echo "      Registered client ${CLIENT_ID}"
fi

# A first-party self-care portal should not prompt the user to consent to its
# own scopes on every login. The username claim has to be requested explicitly:
# the openid and profile scopes alone leave the ID token carrying only "sub",
# and the portal would then greet everyone as "Unknown user".
${CURL} -X PATCH -H 'Content-Type: application/json' -d '{
    "advancedConfigurations": {"skipLoginConsent": true, "skipLogoutConsent": true},
    "claimConfiguration": {
      "dialect": "LOCAL",
      "requestedClaims": [{"claim": {"uri": "http://wso2.org/claims/username"}, "mandatory": true}]
    }
  }' "${BASE}/api/server/v1/applications/${APP_ID}" -o /dev/null

# ------------------------------------------------------------- API authorization
echo "[2/6] Authorizing the consent management v2 APIs"
ALL_SCOPES=""
for IDENTIFIER in \
  "/api/identity/consent-mgt/v2.0/consents" \
  "/api/identity/consent-mgt/v2.0/purposes" \
  "/api/identity/consent-mgt/v2.0/elements"; do

  RESOURCE=$(${CURL} --get --data-urlencode "filter=identifier eq ${IDENTIFIER}" \
    "${BASE}/api/server/v1/api-resources" | json "d['apiResources'][0]['id'] if d.get('apiResources') else ''")
  if [ -z "${RESOURCE}" ]; then
    echo "ERROR: API resource ${IDENTIFIER} is not registered."
    echo "       Confirm [consent_mgt] enable_v2_api = true is set and the server was restarted."
    exit 2
  fi

  SCOPES=$(${CURL} "${BASE}/api/server/v1/api-resources/${RESOURCE}" \
    | json "json.dumps([s['name'] for s in d.get('scopes',[])])")
  ALL_SCOPES="${ALL_SCOPES}${SCOPES}"

  BODY=$(python3 -c "import json;print(json.dumps({'id':'${RESOURCE}','policyIdentifier':'RBAC','scopes':json.loads('''${SCOPES}''')}))")
  ${CURL} -H 'Content-Type: application/json' -d "${BODY}" \
    "${BASE}/api/server/v1/applications/${APP_ID}/authorized-apis" -o /dev/null
  echo "      ${IDENTIFIER}"
done

# ------------------------------------------------------------------------- role
echo "[3/6] Creating the ${PORTAL_ADMIN_ROLE} role"
# Scope authorization alone is not enough: with an RBAC policy the Identity
# Server only puts a scope in a token when the user holds a role granting it.
# Roles are scoped to an audience, so a same-named role belonging to a different
# application is useless here: its scopes would never reach this app's tokens.
# Match on the application id, not just the name.
ROLE_ID=$(${CURL} --get --data-urlencode "filter=displayName eq ${PORTAL_ADMIN_ROLE}" \
  "${BASE}/scim2/v2/Roles" \
  | json "next((r['id'] for r in d.get('Resources',[]) if r.get('audience',{}).get('value')=='${APP_ID}'), '')")

if [ -n "${ROLE_ID}" ]; then
  echo "      Role already exists (${ROLE_ID}); leaving its members unchanged."
else
  ROLE_BODY=$(python3 -c "
import json
scopes = json.loads('''${ALL_SCOPES}'''.replace('][', ','))
print(json.dumps({
  'schemas': ['urn:ietf:params:scim:schemas:extension:2.0:Role'],
  'displayName': '${PORTAL_ADMIN_ROLE}',
  'audience': {'value': '${APP_ID}', 'type': 'application'},
  'permissions': [{'value': s} for s in scopes],
}))")
  ROLE_ID=$(${CURL} -H 'Content-Type: application/json' -d "${ROLE_BODY}" \
    "${BASE}/scim2/v2/Roles" | json "d.get('id','')")
  echo "      Created role ${ROLE_ID}"
  echo "      Assign users to it to grant portal administration access."
fi

# --------------------------------------------------------- complaint management
echo "[4/6] Authorizing the complaint management API"
# Unlike the consent-mgt v2 resources above, this API resource does not ship
# with the Identity Server - the complaint management webapp is a custom
# accelerator component, so its scopes have to be registered here before any
# role can carry them.
COMPLAINT_API_IDENTIFIER="dpdp-complaint-mgt"
COMPLAINT_RESOURCE=$(${CURL} --get --data-urlencode "filter=identifier eq ${COMPLAINT_API_IDENTIFIER}" \
  "${BASE}/api/server/v1/api-resources" | json "d['apiResources'][0]['id'] if d.get('apiResources') else ''")

if [ -n "${COMPLAINT_RESOURCE}" ]; then
  echo "      API resource already exists (${COMPLAINT_RESOURCE}); reusing it."
  COMPLAINT_SCOPES=$(${CURL} "${BASE}/api/server/v1/api-resources/${COMPLAINT_RESOURCE}" \
    | json "json.dumps([s['name'] for s in d.get('scopes',[])])")
else
  COMPLAINT_RESOURCE_BODY='{
    "name": "DPDP Complaint Management",
    "identifier": "'"${COMPLAINT_API_IDENTIFIER}"'",
    "requiresAuthorization": true,
    "scopes": [
      {"name": "portal_complaint_read_any", "displayName": "View complaints",
       "description": "View complaints filed by any data principal"},
      {"name": "portal_complaint_write_any", "displayName": "Manage complaints",
       "description": "Respond to and change the status of complaints filed by any data principal"}
    ]
  }'
  CREATED=$(${CURL} -H 'Content-Type: application/json' -d "${COMPLAINT_RESOURCE_BODY}" \
    "${BASE}/api/server/v1/api-resources")
  COMPLAINT_RESOURCE=$(echo "${CREATED}" | json "d.get('id','')")
  if [ -z "${COMPLAINT_RESOURCE}" ]; then
    echo "ERROR: failed to create the complaint management API resource: ${CREATED}"
    exit 2
  fi
  COMPLAINT_SCOPES='["portal_complaint_read_any","portal_complaint_write_any"]'
  echo "      Created API resource ${COMPLAINT_RESOURCE}"
fi

# A silent parse failure anywhere above would otherwise surface only as an empty
# authorization/role - i.e. exactly the "everything looks configured but tokens
# never carry the scope" failure mode this script is meant to prevent.
if [ -z "${COMPLAINT_SCOPES}" ] || [ "${COMPLAINT_SCOPES}" = "[]" ]; then
  echo "ERROR: could not determine the complaint management API's scopes."
  exit 2
fi

# POST only authorizes an API the app doesn't already have a (possibly stale or
# empty) authorization entry for; it does not update an existing one. Reconcile
# via PATCH instead when the entry already exists, so a re-run always converges
# the live scope list to match COMPLAINT_SCOPES rather than leaving it as-is.
COMPLAINT_ALREADY_AUTHORIZED=$(${CURL} "${BASE}/api/server/v1/applications/${APP_ID}/authorized-apis" \
  | json "'true' if any(a.get('id')=='${COMPLAINT_RESOURCE}' for a in d) else ''")

if [ -n "${COMPLAINT_ALREADY_AUTHORIZED}" ]; then
  COMPLAINT_PATCH_BODY=$(python3 -c "import json;print(json.dumps({'addedScopes':json.loads('''${COMPLAINT_SCOPES}'''),'removedScopes':[]}))")
  ${CURL} -X PATCH -H 'Content-Type: application/json' -d "${COMPLAINT_PATCH_BODY}" \
    "${BASE}/api/server/v1/applications/${APP_ID}/authorized-apis/${COMPLAINT_RESOURCE}" -o /dev/null
else
  COMPLAINT_AUTH_BODY=$(python3 -c "import json;print(json.dumps({'id':'${COMPLAINT_RESOURCE}','policyIdentifier':'RBAC','scopes':json.loads('''${COMPLAINT_SCOPES}''')}))")
  ${CURL} -H 'Content-Type: application/json' -d "${COMPLAINT_AUTH_BODY}" \
    "${BASE}/api/server/v1/applications/${APP_ID}/authorized-apis" -o /dev/null
fi

echo "[5/6] Creating the ${PORTAL_COMPLAINT_OFFICER_ROLE} role"
OFFICER_ROLE_ID=$(${CURL} --get --data-urlencode "filter=displayName eq ${PORTAL_COMPLAINT_OFFICER_ROLE}" \
  "${BASE}/scim2/v2/Roles" \
  | json "next((r['id'] for r in d.get('Resources',[]) if r.get('audience',{}).get('value')=='${APP_ID}'), '')")

if [ -n "${OFFICER_ROLE_ID}" ]; then
  echo "      Role already exists (${OFFICER_ROLE_ID}); leaving its members unchanged."
else
  OFFICER_ROLE_BODY=$(python3 -c "
import json
scopes = json.loads('''${COMPLAINT_SCOPES}''')
print(json.dumps({
  'schemas': ['urn:ietf:params:scim:schemas:extension:2.0:Role'],
  'displayName': '${PORTAL_COMPLAINT_OFFICER_ROLE}',
  'audience': {'value': '${APP_ID}', 'type': 'application'},
  'permissions': [{'value': s} for s in scopes],
}))")
  OFFICER_ROLE_ID=$(${CURL} -H 'Content-Type: application/json' -d "${OFFICER_ROLE_BODY}" \
    "${BASE}/scim2/v2/Roles" | json "d.get('id','')")
  echo "      Created role ${OFFICER_ROLE_ID}"
  echo "      Assign users to it to grant complaint officer access."
fi

# Membership is left alone above, but permissions are reconciled unconditionally:
# a role created (or left over) with an empty/stale permission set would otherwise
# never self-heal on a later re-run, the same failure mode fixed for the API above.
COMPLAINT_ROLE_PATCH_BODY=$(python3 -c "
import json
scopes = json.loads('''${COMPLAINT_SCOPES}''')
print(json.dumps({
  'schemas': ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
  'Operations': [{'op': 'add', 'value': {'permissions': [{'value': s} for s in scopes]}}],
}))")
${CURL} -X PATCH -H 'Content-Type: application/json' -d "${COMPLAINT_ROLE_PATCH_BODY}" \
  "${BASE}/scim2/v2/Roles/${OFFICER_ROLE_ID}" -o /dev/null

# ---------------------------------------------------------------- portal config
echo "[6/6] Writing client credentials to dpdp-portal.properties"
python3 - "${PORTAL_PROPERTIES}" "${CLIENT_ID}" "${CLIENT_SECRET}" <<'PY'
import sys
path, client_id, client_secret = sys.argv[1], sys.argv[2], sys.argv[3]
lines = []
with open(path) as handle:
    for line in handle:
        if line.startswith('oauth.client.id='):
            line = 'oauth.client.id=%s\n' % client_id
        elif line.startswith('oauth.client.secret='):
            line = 'oauth.client.secret=%s\n' % client_secret
        lines.append(line)
with open(path, 'w') as handle:
    handle.writelines(lines)
PY

echo
echo "Done. The portal is at ${BASE}/consent-portal/"
echo "Restart the Identity Server so the portal picks up the new credentials."
