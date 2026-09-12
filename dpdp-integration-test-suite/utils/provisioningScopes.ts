/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * What the provisioning client is authorized for - the single definition shared by the script
 * that creates it (scripts/bootstrap-provisioning-app.ts) and the helper that mints tokens from
 * it (utils/provisioningClient.ts). Kept in its own module with no imports so the bootstrap can
 * use it without pulling in utils/env.ts, which `required()`s test-account variables that do not
 * exist yet when the bootstrap runs.
 *
 * Identifiers and scope names were read out of the shipped WSO2IDENTITY_DB's API_RESOURCE/SCOPE
 * tables (TYPE='TENANT'), not guessed. Least privilege: every scope here is needed by either
 * scripts/provision-test-users.sh or utils/throwawayUser.ts.
 */
export const PROVISIONING_APIS: { identifier: string; scopes: string[] }[] = [
  {
    identifier: '/scim2/Users',
    scopes: [
      // provision-test-users.sh: find and create the three suite accounts.
      'internal_user_mgt_list',
      'internal_user_mgt_create',
      // throwawayUser.ts: read back and remove the disposable account tests/06-account creates.
      'internal_user_mgt_view',
      'internal_user_mgt_delete',
    ],
  },
  {
    identifier: '/scim2/Roles',
    scopes: [
      // Both callers look a role up by display name, then append a member to it.
      'internal_role_mgt_view',
      'internal_role_mgt_users_update',
    ],
  },
]

export const PROVISIONING_SCOPES: string[] = PROVISIONING_APIS.flatMap((api) => api.scopes)
