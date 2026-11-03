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
# Copies the DPDP accelerator artifacts over a WSO2 Identity Server
# distribution. Run configure.sh afterwards to apply deployment settings.

set -e

WSO2_IS_HOME=$1
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACCELERATOR_HOME="$(dirname "${SCRIPT_DIR}")"

if [ -z "${WSO2_IS_HOME}" ]; then
  # Default layout: the accelerator was unzipped inside <IS_HOME>.
  WSO2_IS_HOME="$(dirname "${ACCELERATOR_HOME}")"
fi

echo "Product home     : ${WSO2_IS_HOME}"
echo "Accelerator home : ${ACCELERATOR_HOME}"

if [ ! -d "${WSO2_IS_HOME}/repository/components" ]; then
  printf '\nERROR: %s is not a valid Carbon product path.\n\n' "${WSO2_IS_HOME}"
  exit 2
fi

WEBAPPS_PATH="${WSO2_IS_HOME}/repository/deployment/server/webapps"

# Remove stale exploded webapps from older versions to ensure a clean deployment,
# as `cp -r` only adds/overwrites and never removes files.
for webapp in "${ACCELERATOR_HOME}"/carbon-home/repository/deployment/server/webapps/*/; do
  name="$(basename "${webapp}")"
  target="${WEBAPPS_PATH}/${name}"
  if [ -d "${target}" ]; then
    echo "Removing the previously deployed ${name}"
    rm -rf "${target}"
  fi
  rm -f "${WEBAPPS_PATH}/${name}.war"
done

# Explicitly remove the legacy version-suffixed event-notifications webapp
# (renamed from api#dpdp#event-notifications#v1 to api#dpdp#event-notifications).
LEGACY_EVENT_WEBAPP="api#dpdp#event-notifications#v1"
if [ -d "${WEBAPPS_PATH}/${LEGACY_EVENT_WEBAPP}" ]; then
  echo "Removing the previously deployed ${LEGACY_EVENT_WEBAPP}"
  rm -rf "${WEBAPPS_PATH:?}/${LEGACY_EVENT_WEBAPP}"
fi
rm -f "${WEBAPPS_PATH}/${LEGACY_EVENT_WEBAPP}.war"

# Remove stale dropins to prevent duplicate bundle loading.
echo "Removing old DPDP accelerator artifacts from the product"
find "${WSO2_IS_HOME}/repository/components/dropins" -name "org.wso2.dpdp.accelerator.*" -exec rm -f {} \;

echo "Copying accelerator artifacts"
cp -r "${ACCELERATOR_HOME}"/carbon-home/* "${WSO2_IS_HOME}/"

printf '\nMerge complete. Next: bash bin/configure.sh %s\n\n' "${WSO2_IS_HOME}"
