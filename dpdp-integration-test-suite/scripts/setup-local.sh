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

# One-time local setup against a freshly installed, already-running Identity Server: generates the
# test-account password, mints the provisioning client, and creates the three accounts. Run this
# once per fresh pack, then use ./run-e2e.sh for every run after it.
#
# Does NOT start or stop a server - like run-e2e.sh, the target is expected to be up already.
#
# Takes no arguments and reads no environment variables. Everything it needs comes from
# e2e-config.json; point that (or e2e-config.local.json beside it) at a different host or a
# different super admin before running this. See README.md, "Configuration".
#
# Safe to re-run: the generated password, the provisioning client and the accounts are all
# idempotent, and an existing e2e-config.local.json is never overwritten.

set -euo pipefail

SUITE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${SUITE_DIR}"

LOCAL_CONFIG="e2e-config.local.json"

if [ ! -d node_modules ]; then
  echo "==> Installing dependencies"
  npm install
fi

# The bootstrap below signs in through a real browser, so Chromium has to be present before it
# runs - run-e2e.sh installs it too, but that runs after this script, not before.
echo "==> Ensuring the Chromium browser is installed"
npx playwright install chromium

# Keyed on the password itself, not on the file: the bootstrap step below writes
# provisioningClient into this same file, so running that first leaves the file present with no
# password in it. Testing for the file would then skip generation for good - provisioning would
# fail pointing back here, and re-running would skip again.
if [ -f "${LOCAL_CONFIG}" ] && python3 -c '
import json, sys
config = json.load(open(sys.argv[1]))
sys.exit(0 if (config.get("personas", {}).get("user") or {}).get("password") else 1)
' "${LOCAL_CONFIG}" 2>/dev/null; then
  echo "==> ${LOCAL_CONFIG} already carries the test-account password - leaving it alone"
else
  echo "==> Generating the test-account password into ${LOCAL_CONFIG}"
  # Not committed, and never reused across environments: the accounts exist only for this
  # deployment, so the password is generated here rather than being a shared constant.
  password="$(openssl rand -base64 18 | tr -dc 'A-Za-z0-9')Aa1!"
  PASSWORD="${password}" python3 - "${LOCAL_CONFIG}" <<'PYEOF'
import json, os, sys

path = sys.argv[1]
password = os.environ['PASSWORD']

# Merged into whatever is already there rather than written fresh, so a provisioningClient the
# bootstrap wrote earlier survives.
try:
    with open(path) as handle:
        config = json.load(handle)
except (FileNotFoundError, ValueError):
    config = {}

personas = config.setdefault('personas', {})
for name in ('user', 'user2', 'consentAdmin'):
    personas.setdefault(name, {})['password'] = password

with open(path, 'w') as handle:
    json.dump(config, handle, indent=2)
    handle.write('\n')
PYEOF
  chmod 600 "${LOCAL_CONFIG}"
fi

echo "==> Bootstrapping the provisioning client"
npm run --silent bootstrap:provisioning-app

echo "==> Provisioning test accounts"
bash scripts/provision-test-users.sh

echo
echo "Setup complete. Run the suite with:"
echo "  ./run-e2e.sh"
