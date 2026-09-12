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

import { config, requireConfigured, trimTrailingSlash } from './config'

export interface Persona {
  username: string
  password: string
}

/**
 * Everything below is read from e2e-config.json (plus the optional, gitignored
 * e2e-config.local.json) - see utils/config.ts. There are no environment-variable fallbacks and
 * no defaults inlined here: a missing value fails with the key that is missing.
 *
 * This object's shape is what the whole suite consumes, so it is kept stable independently of
 * how the configuration is stored.
 */

function persona(which: 'user' | 'user2' | 'consentAdmin'): Persona {
  const configured = config.personas[which]
  return {
    username: requireConfigured(configured.username, `personas.${which}.username`),
    password: requireConfigured(configured.password, `personas.${which}.password`),
  }
}

const rawPortalBaseUrl = trimTrailingSlash(config.identityServer.portalBaseUrl)

export const env = {
  portalBaseUrl: rawPortalBaseUrl,
  // Playwright's `baseURL` resolves a leading-slash relative goto() (e.g. page.goto('/consents'),
  // used throughout pages/) per the WHATWG URL spec: a leading slash REPLACES the base's own path
  // rather than appending to it. Without the trailing slash here, page.goto('/consents') against
  // baseURL "https://host:9443/consent-portal" resolves to "https://host:9443/consents" - outside
  // the portal entirely, and outside the deployment.toml rule that exempts "(.*)/consent-portal(.*)"
  // from the Identity Server's own auth valve - which is exactly the confusing 401 this cost hours
  // chasing as an OAuth/IS problem before landing here. portalBaseUrl itself stays slash-free since
  // the consent API URL helpers below need that form.
  portalNavigationBaseUrl: `${rawPortalBaseUrl}/`,
  identityServerBaseUrl: trimTrailingSlash(config.identityServer.baseUrl),
  ignoreHttpsErrors: config.identityServer.ignoreHttpsErrors,

  user: persona('user'),

  // Must be a real user assigned the dpdp-consent-admin role. The accelerator provisions the role
  // itself automatically, but never role membership - run scripts/provision-test-users.sh to
  // create this account with its role already assigned. Grants every internal_consent_mgt_*
  // scope, so this single persona both drives the admin consent registry UI and creates
  // Purposes/Elements/Consents via the API as test setup for the UI layer.
  consentAdmin: persona('consentAdmin'),

  /**
   * Optional: a second user account, used only by ownership-isolation tests that
   * need two distinct real users. Those tests skip themselves when this isn't configured,
   * since a real environment can't fabricate extra user accounts the way a stubbed IdP could.
   */
  secondUser: (): Persona | undefined => {
    const { username, password } = config.personas.user2
    return username && password ? { username, password } : undefined
  },

  /**
   * The super-tenant admin. Used by scripts/bootstrap-provisioning-app.ts for its one-time
   * Console sign-in, and by tests/05-multi-tenancy to create a throwaway tenant through the
   * Console's "New Root Organization" flow - see fixtures/tenant.fixtures.ts. Whatever is in
   * e2e-config.json must match the account the target deployment actually has.
   */
  superAdmin: {
    username: requireConfigured(config.superAdmin.username, 'superAdmin.username'),
    password: requireConfigured(config.superAdmin.password, 'superAdmin.password'),
  } satisfies Persona,
}

// The portal has no backend of its own any more (see docs/configuration-guide.md) - the frontend
// calls these WSO2 IS-native REST APIs directly from the browser, so tests do the same. Self-service
// consents live under the User Consent Management API (org.wso2.carbon.identity.rest.api.user.consent.v1,
// unversioned base); admin consents/purposes/elements live under consent-mgt v2
// (org.wso2.carbon.identity.api.server.consent.management.v2, see clients/ConsentApiClient.ts for the
// full contract). `tenantDomain` prefixes `/t/<tenant>` - confirmed live (see
// fixtures/tenant.fixtures.ts) that a real OAuth2 bearer token reaches both surfaces fine
// tenant-qualified; omit it (or pass undefined) for the super-tenant paths every other test uses.
function tenantSegment(tenantDomain?: string): string {
  return tenantDomain ? `/t/${tenantDomain}` : ''
}

export function myConsentsApiUrl(path: string, tenantDomain?: string): string {
  return `${env.identityServerBaseUrl}${tenantSegment(tenantDomain)}/api/users/v1/me/consents${path}`
}

export function adminConsentsApiUrl(path: string, tenantDomain?: string): string {
  return `${env.identityServerBaseUrl}${tenantSegment(tenantDomain)}/api/identity/consent-mgt/v2.0/consents${path}`
}

export function consentPurposesApiUrl(path: string, tenantDomain?: string): string {
  return `${env.identityServerBaseUrl}${tenantSegment(tenantDomain)}/api/identity/consent-mgt/v2.0/purposes${path}`
}

export function consentElementsApiUrl(path: string, tenantDomain?: string): string {
  return `${env.identityServerBaseUrl}${tenantSegment(tenantDomain)}/api/identity/consent-mgt/v2.0/elements${path}`
}

// Full navigation targets for tests/05-multi-tenancy - both are absolute URLs to a different app
// than the portal (Console, not consent-portal), so page objects there use page.goto() with these
// directly rather than the portal-relative baseURL every other page object relies on.
export function consoleRootOrganizationsUrl(): string {
  return `${env.identityServerBaseUrl}/t/carbon.super/console/root/organizations`
}

export function tenantConsoleUrl(tenantDomain: string): string {
  return `${env.identityServerBaseUrl}/t/${tenantDomain}/console`
}

export function tenantPortalUrl(tenantDomain: string): string {
  return `${env.identityServerBaseUrl}/t/${tenantDomain}/consent-portal`
}

/** SCIM2 user management, used for the throwaway account the deletion test creates. */
export function scim2UsersUrl(path: string): string {
  return `${env.identityServerBaseUrl}/scim2/Users${path}`
}

// The accelerator's own complaint-server webapp (org.wso2.dpdp.accelerator.complaint.mgt.endpoint,
// finalName "api#dpdp#complaints#v1") - unlike consent-mgt, this is NOT an IS-native API, so there
// is no tenant-qualification concern to mirror from consentPurposesApiUrl et al.
const COMPLAINT_SERVER_BASE = '/api/dpdp/complaints/v1'

/** Officer/admin surface: `/complaints/*`, requiring a portal:complaints:* (non-self) scope. */
export function complaintsApiUrl(path: string): string {
  return `${env.identityServerBaseUrl}${COMPLAINT_SERVER_BASE}/complaints${path}`
}

/** Data Principal self-service surface: `/me/complaints/*`, requiring portal:complaints:*:self. */
export function meComplaintsApiUrl(path: string): string {
  return `${env.identityServerBaseUrl}${COMPLAINT_SERVER_BASE}/me/complaints${path}`
}

// The accelerator's own event-notification webapp (org.wso2.dpdp.accelerator.event.notifications.endpoint,
// finalName "api#dpdp#event-notifications#v1"). Unlike the complaint-server, this IS
// tenant-qualified (see docs/event-notification-guide.md) - every path goes through
// tenantSegment the same way the IS-native consent APIs above do.
export function eventNotificationsApiUrl(path: string, tenantDomain?: string): string {
  return `${env.identityServerBaseUrl}${tenantSegment(tenantDomain)}/api/dpdp/event-notifications/v1${path}`
}

// The accelerator's own consent-history webapp (org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint,
// see consent-history.yaml) - status-audit/full-snapshot reads, tenant-qualified the same way as
// eventNotificationsApiUrl above. Not wired into the consent-portal frontend yet (see CLAUDE.md),
// but deployment.config.json already requests the consent:history:view:*/consent:status-history:view:*
// scopes, so a real persona login already carries them.
export function consentHistoryApiUrl(path: string, tenantDomain?: string): string {
  return `${env.identityServerBaseUrl}${tenantSegment(tenantDomain)}/api/dpdp/consent-mgt/v1${path}`
}

/**
 * Opt-in only (see README.md, "Configuration"): the real ConsentExpiryJob's default daily cron makes waiting on
 * it impractical for an automated run, so the one test that actually waits on the live scheduler
 * (rather than triggering DPDPConsentExpiryReconciler via a mutation) needs the operator to have
 * both shortened [dpdp_accelerator.consent_expiry].cron_value in deployment.toml and restarted the
 * server, then set this to a timeout comfortably larger than that interval. Undefined means "not
 * configured" - that test skips itself, mirroring hasSecondUser()/webhookReceiverConfig() above.
 */
export function consentExpirySchedulerPollTimeoutMs(): number | undefined {
  return config.consentExpiry.schedulerPollTimeoutMs ?? undefined
}

/**
 * A real webhook end-to-end round trip (subscription verification, signed delivery, retries)
 * needs a receiver process the WSO2 IS host can actually reach over the network to POST/GET
 * against - EventNotificationUrlValidator rejects loopback/127.0.0.1 unconditionally (see
 * AGENTS.md, "Webhook-dependent tests"), so a receiver bound to
 * this machine's own loopback interface can never pass callback-URL validation no matter what
 * deployment.toml says. Tests that need this skip themselves (mirroring hasSecondUser()) unless
 * both a receiver host and confirmation that the deployment allows it are explicitly configured.
 */
export function webhookReceiverConfig(): { host: string; allowPrivateNetwork: boolean } | undefined {
  const { receiverHost, allowPrivateNetwork } = config.webhook
  return receiverHost ? { host: receiverHost, allowPrivateNetwork } : undefined
}

export type PersonaName = 'user' | 'user-2' | 'consent-admin'
