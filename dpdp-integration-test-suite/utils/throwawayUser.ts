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

import { env, scim2UsersUrl, type Persona } from './env'
import { provisioningHeaders } from './provisioningClient'

/**
 * Creates and removes disposable user accounts through SCIM2, for the account-deletion test.
 *
 * That test destroys the account it signs in as, so it cannot use any of the shared personas in
 * fixtures/auth.fixtures.ts - those log in once and are reused by every later test in the run.
 * The account here exists for one test and is gone by the end of it, either because the test
 * deleted it through the portal (the point of the test) or because the cleanup below caught
 * what a failure left behind.
 */

const SCIM2_USER_SCHEMA = 'urn:ietf:params:scim:schemas:core:2.0:User'

/**
 * The approval-task endpoints below are `/me/` resources - they act as the signed-in user, so a
 * client_credentials token (which carries no user) cannot address them and this stays on Basic
 * auth. That path is best-effort cleanup and only runs where a Delete User approval workflow is
 * configured, which is not the default; on a deployment with Basic auth disabled it simply
 * no-ops, exactly as it already does when the admin holds no matching task.
 */
function basicAdminHeaders(admin: Persona): Record<string, string> {
  const credentials = Buffer.from(`${admin.username}:${admin.password}`).toString('base64')
  return {
    Authorization: `Basic ${credentials}`,
    'Content-Type': 'application/json',
    Accept: 'application/json',
  }
}

export interface ThrowawayUser extends Persona {
  /** SCIM2 resource id, for the cleanup path. */
  id: string
}

/**
 * Creates a user and assigns it the portal's regular-user role, so the session it signs in with
 * carries `account:self:delete` exactly the way a real portal user's does.
 */
export async function createThrowawayUser(
  roleName: string,
  usernamePrefix: string,
): Promise<ThrowawayUser> {
  // Unique per run: a leftover account from an interrupted run must not collide with this one.
  // Email-shaped because the accelerator enforces it - SCIM2 rejects a bare name with 31301.
  const username = `${usernamePrefix}-${Date.now().toString(36)}@dpdp.test`
  const password = `Throwaway#${Math.random().toString(36).slice(2, 10)}A1`

  const response = await fetch(scim2UsersUrl(''), {
    method: 'POST',
    headers: await provisioningHeaders(),
    body: JSON.stringify({
      schemas: [SCIM2_USER_SCHEMA],
      // Unqualified: SCIM2 puts the user in the primary user store. Prefixing a store
      // name that doesn't exist on the server fails with "Invalid user store name".
      userName: username,
      password,
      name: { givenName: 'Throwaway', familyName: 'Account' },
      // The username is already an address; appending a domain again is rejected.
      emails: [{ primary: true, value: username }],
    }),
    signal: AbortSignal.timeout(20_000),
  })

  if (response.status !== 201) {
    throw new Error(
      `Could not create the throwaway user "${username}" via SCIM2 (status ${String(response.status)}): ` +
        `${await response.text()}. The provisioning client needs the SCIM2 user-management scopes - `
        + 'run npm run bootstrap:provisioning-app to re-authorize it.',
    )
  }

  const created = (await response.json()) as { id?: string }
  if (!created.id) {
    throw new Error(`SCIM2 created "${username}" but returned no resource id.`)
  }

  await assignRole(created.id, roleName)
  return { id: created.id, username, password }
}

/**
 * Adds the user to an existing application role by patching the role's member list. The role is
 * provisioned per tenant by the accelerator, so this looks it up rather than creating it.
 */
async function assignRole(userId: string, roleName: string): Promise<void> {
  const searchResponse = await fetch(
    `${env.identityServerBaseUrl}/scim2/v2/Roles?filter=${encodeURIComponent(`displayName eq ${roleName}`)}`,
    { headers: await provisioningHeaders(), signal: AbortSignal.timeout(20_000) },
  )
  if (!searchResponse.ok) {
    throw new Error(
      `Could not look up the "${roleName}" role (status ${String(searchResponse.status)}): ` +
        `${await searchResponse.text()}`,
    )
  }

  const found = (await searchResponse.json()) as { Resources?: { id?: string }[] }
  const roleId = found.Resources?.[0]?.id
  if (!roleId) {
    throw new Error(
      `The "${roleName}" role does not exist in this tenant. It is provisioned automatically - ` +
        `see docs/configuration-guide.md, "Recovering a broken tenant".`,
    )
  }

  const patchResponse = await fetch(`${env.identityServerBaseUrl}/scim2/v2/Roles/${roleId}`, {
    method: 'PATCH',
    headers: await provisioningHeaders(),
    body: JSON.stringify({
      schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
      Operations: [{ op: 'add', path: 'users', value: [{ value: userId }] }],
    }),
    signal: AbortSignal.timeout(20_000),
  })
  if (!patchResponse.ok) {
    throw new Error(
      `Could not add the throwaway user to "${roleName}" (status ${String(patchResponse.status)}): ` +
        `${await patchResponse.text()}`,
    )
  }
}

/**
 * Best-effort cleanup: the account is often gone already when the test passed.
 *
 * Where an approval workflow is associated with Delete User the delete only
 * records a request (202) and the account survives, so this approves the
 * resulting task too - otherwise every run would leave a throwaway account and
 * a pending approval behind on the server.
 */
export async function deleteThrowawayUser(
  admin: Persona,
  userId: string,
  username?: string,
): Promise<void> {
  const response = await fetch(scim2UsersUrl(`/${userId}`), {
    method: 'DELETE',
    headers: await provisioningHeaders(),
    signal: AbortSignal.timeout(20_000),
  }).catch(() => undefined)

  // 204 means it is already gone. Anything else under a workflow leaves the
  // account alive behind a pending task: 202 when this delete raised it, 400
  // when the test itself already did ("pending workflow already defined").
  if (response?.status === 204 || !username) {
    return
  }
  await approvePendingDeletion(admin, username)
}

/** Approves the pending Delete User task for one username, if the admin has it. */
async function approvePendingDeletion(admin: Persona, username: string): Promise<void> {
  try {
    const listed = await fetch(`${env.identityServerBaseUrl}/api/users/v2/me/approval-tasks`, {
      headers: basicAdminHeaders(admin),
      signal: AbortSignal.timeout(20_000),
    })
    if (!listed.ok) {
      return
    }
    const tasks = (await listed.json()) as { id: string; approvalStatus: string }[]
    for (const task of tasks.filter((t) => t.approvalStatus === 'READY')) {
      const detailResponse = await fetch(
        `${env.identityServerBaseUrl}/api/users/v2/me/approval-tasks/${task.id}`,
        { headers: basicAdminHeaders(admin), signal: AbortSignal.timeout(20_000) },
      )
      if (!detailResponse.ok) {
        continue
      }
      const detail = (await detailResponse.json()) as { properties?: { key: string; value: string }[] }
      const taskUser = detail.properties?.find((p) => p.key === 'Username')?.value
      if (taskUser !== username) {
        continue
      }
      await fetch(`${env.identityServerBaseUrl}/api/users/v2/me/approval-tasks/${task.id}/state`, {
        method: 'PUT',
        headers: basicAdminHeaders(admin),
        body: JSON.stringify({ action: 'APPROVE' }),
        signal: AbortSignal.timeout(20_000),
      })
      return
    }
  } catch {
    // Cleanup is best effort - a leftover account must not fail the test that
    // already made its assertions.
  }
}

/**
 * Whether the account still exists. Used to prove the deletion actually reached the user store,
 * rather than trusting the portal's own redirect.
 */
export async function userExists(userId: string): Promise<boolean> {
  const response = await fetch(scim2UsersUrl(`/${userId}`), {
    headers: await provisioningHeaders(),
    signal: AbortSignal.timeout(20_000),
  })
  return response.status === 200
}

/**
 * Attempts to delete some *other* user with a portal session's own access token, to prove
 * `account:self:delete` does not authorize it. Returns the status so the caller can assert on it.
 */
export async function attemptDeleteAsUser(bearerToken: string, userId: string): Promise<number> {
  const response = await fetch(scim2UsersUrl(`/${userId}`), {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${bearerToken}`, Accept: 'application/json' },
    signal: AbortSignal.timeout(20_000),
  })
  return response.status
}
