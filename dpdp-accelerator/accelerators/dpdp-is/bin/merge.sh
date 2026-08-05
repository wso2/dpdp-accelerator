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
  echo -e "\nERROR: ${WSO2_IS_HOME} is not a valid Carbon product path.\n"
  exit 2
fi

WEBAPPS_PATH="${WSO2_IS_HOME}/repository/deployment/server/webapps"
PORTAL_PATH="${WEBAPPS_PATH}/consent-portal"
PORTAL_PROPERTIES="${WSO2_IS_HOME}/repository/conf/dpdp-portal.properties"
BACKUP_PROPERTIES="/tmp/dpdp-portal.properties.$$"

# Preserve the deployment's own portal configuration across an upgrade.
if [ -f "${PORTAL_PROPERTIES}" ]; then
  echo "Backing up existing dpdp-portal.properties"
  cp "${PORTAL_PROPERTIES}" "${BACKUP_PROPERTIES}"
fi

if [ -d "${PORTAL_PATH}" ]; then
  echo "Removing the previously deployed consent portal"
  rm -rf "${PORTAL_PATH}"
fi
# A stale packed WAR would be redeployed over the exploded webapp.
rm -f "${WEBAPPS_PATH}/consent-portal.war"

echo "Copying accelerator artifacts"
cp -r "${ACCELERATOR_HOME}"/carbon-home/* "${WSO2_IS_HOME}/"

if [ -f "${BACKUP_PROPERTIES}" ]; then
  echo "Restoring dpdp-portal.properties"
  cp "${BACKUP_PROPERTIES}" "${PORTAL_PROPERTIES}"
  rm -f "${BACKUP_PROPERTIES}"
fi

echo -e "\nMerge complete. Next: sh bin/configure.sh ${WSO2_IS_HOME}\n"
