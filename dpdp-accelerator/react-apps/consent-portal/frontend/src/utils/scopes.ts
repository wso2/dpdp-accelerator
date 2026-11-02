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
 * The Identity Server scopes the portal runs on.
 *
 * The token carries the scopes the signed-in user's roles actually grant, so
 * the UI hides whatever the server would refuse anyway. These are the server's
 * own scope names - the portal does not invent a vocabulary of its own.
 */
export const IS_SCOPES = {
  LOGIN: 'internal_login',
  CONSENT_VIEW: 'internal_consent_mgt_consent_view',
  CONSENT_CREATE: 'internal_consent_mgt_consent_create',
  CONSENT_UPDATE: 'internal_consent_mgt_consent_update',
  PURPOSE_VIEW: 'internal_consent_mgt_purpose_view',
  PURPOSE_CREATE: 'internal_consent_mgt_purpose_create',
  PURPOSE_UPDATE: 'internal_consent_mgt_purpose_update',
  PURPOSE_DELETE: 'internal_consent_mgt_purpose_delete',
  ELEMENT_VIEW: 'internal_consent_mgt_element_view',
  ELEMENT_CREATE: 'internal_consent_mgt_element_create',
  ELEMENT_DELETE: 'internal_consent_mgt_element_delete',
} as const

export const COMPLAINT_SCOPES = {
  COMPLAINTS_READ_SELF: 'complaints:read:self',
  COMPLAINTS_WRITE_SELF: 'complaints:write:self',
  COMPLAINTS_READ_ANY: 'complaints:read:any',
  COMPLAINTS_WRITE_ANY: 'complaints:write:any',
} as const
export const EVENT_SCOPES = {
  EVENT_SUBSCRIPTIONS_READ: 'notifications:subscriptions:read',
  EVENT_SUBSCRIPTIONS_WRITE: 'notifications:subscriptions:write',
  EVENT_TOPICS_READ: 'notifications:topics:read',
  EVENT_TOPICS_WRITE: 'notifications:topics:write',
  EVENTS_READ: 'notifications:events:read',
  EVENTS_WRITE: 'notifications:events:write',
} as const

/**
 * Scopes for the accelerator's own {@code /api/dpdp/consent-mgt/v1} history API - the
 * server registers these via DPDPApiResourceProvisioningUtil, not IS itself.
 * ConsentLifecycleSection gates on STATUS_HISTORY_VIEW_ANY/SELF. HISTORY_VIEW_ANY/SELF guard
 * the full per-snapshot history endpoint, which has no UI consumer - the portal only ever
 * shows the status-audit timeline.
 */
export const CONSENT_HISTORY_SCOPES = {
  STATUS_HISTORY_VIEW_ANY: 'consent:status-history:view:any',
  STATUS_HISTORY_VIEW_SELF: 'consent:status-history:view:self',
  HISTORY_VIEW_ANY: 'consent:history:view:any',
  HISTORY_VIEW_SELF: 'consent:history:view:self',
} as const

/**
 * Guards DELETE /scim2/Me. Granted through the dpdp-consent-user role only, so
 * administrators never see (or pass) the self-service account deletion.
 */
export const ACCOUNT_SCOPES = {
  SELF_DELETE: 'account:self:delete',
} as const

/** Any one of these scopes is enough to unlock the area it guards. */
export type ScopeRequirement = readonly string[]

/**
 * What each area of the portal needs. Managing one's own consents only needs
 * a login: the self-service API scopes every call to the caller.
 */
export const REQUIRED_SCOPES = {
  CONSENTS_READ_SELF: [IS_SCOPES.LOGIN],
  CONSENTS_WRITE_SELF: [IS_SCOPES.LOGIN],
  CONSENTS_READ_ANY: [IS_SCOPES.CONSENT_VIEW],
  CONSENTS_WRITE_ANY: [IS_SCOPES.CONSENT_UPDATE, IS_SCOPES.CONSENT_CREATE],
  PURPOSES_READ: [IS_SCOPES.PURPOSE_VIEW],
  PURPOSES_WRITE: [IS_SCOPES.PURPOSE_CREATE, IS_SCOPES.PURPOSE_UPDATE, IS_SCOPES.PURPOSE_DELETE],
  ELEMENTS_READ: [IS_SCOPES.ELEMENT_VIEW],
  ELEMENTS_WRITE: [IS_SCOPES.ELEMENT_CREATE, IS_SCOPES.ELEMENT_DELETE],
  EVENT_SUBSCRIPTIONS_READ: [EVENT_SCOPES.EVENT_SUBSCRIPTIONS_READ],
  EVENT_SUBSCRIPTIONS_WRITE: [EVENT_SCOPES.EVENT_SUBSCRIPTIONS_WRITE],
  EVENT_TOPICS_READ: [EVENT_SCOPES.EVENT_TOPICS_READ],
  EVENT_TOPICS_WRITE: [EVENT_SCOPES.EVENT_TOPICS_WRITE],
  EVENTS_READ: [EVENT_SCOPES.EVENTS_READ],
  EVENTS_WRITE: [EVENT_SCOPES.EVENTS_WRITE],
  COMPLAINTS_READ_SELF: [COMPLAINT_SCOPES.COMPLAINTS_READ_SELF],
  COMPLAINTS_WRITE_SELF: [COMPLAINT_SCOPES.COMPLAINTS_WRITE_SELF],
  COMPLAINTS_READ_ANY: [COMPLAINT_SCOPES.COMPLAINTS_READ_ANY],
  COMPLAINTS_WRITE_ANY: [COMPLAINT_SCOPES.COMPLAINTS_WRITE_ANY],
  ACCOUNT_SELF_DELETE: [ACCOUNT_SCOPES.SELF_DELETE],
} as const satisfies Record<string, ScopeRequirement>

/**
 * True for a DPO - a complaint officer (holds either "any" complaint scope, matching
 * AppSidebar's isComplaintOfficer) who has neither CONSENTS_READ_ANY nor COMPLAINTS_READ_SELF.
 * internal_login (CONSENTS_READ_SELF) can't be used to spot this: it is granted to every
 * signed-in user regardless of role, DPO included, so on its own it says nothing about whether
 * someone is a genuine consent subject. A DPO-only profile has no consents of their own and no
 * complaints filed as a Data Principal, so anything gated on "am I a consent/complaint subject"
 * (e.g. the Dashboard) should treat this profile as having none.
 */
export function isDpoOnlyProfile(hasScope: (requirement: ScopeRequirement) => boolean): boolean {
  return (
    (hasScope(REQUIRED_SCOPES.COMPLAINTS_READ_ANY) ||
      hasScope(REQUIRED_SCOPES.COMPLAINTS_WRITE_ANY)) &&
    !hasScope(REQUIRED_SCOPES.CONSENTS_READ_ANY) &&
    !hasScope(REQUIRED_SCOPES.COMPLAINTS_READ_SELF)
  )
}

/** Splits the space separated scope string an access token carries. */
export function parseScopes(granted: string | string[] | undefined): string[] {
  if (!granted) {
    return []
  }
  const values = Array.isArray(granted) ? granted : granted.split(/\s+/)
  return values.map((scope) => scope.trim()).filter(Boolean)
}
