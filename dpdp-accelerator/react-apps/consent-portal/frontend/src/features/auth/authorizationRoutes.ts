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

import { REQUIRED_SCOPES, isDpoOnlyProfile, type ScopeRequirement } from '../../utils/scopes'

const AUTHORIZED_DESTINATIONS: ReadonlyArray<{ path: string; requirement: ScopeRequirement }> = [
  { path: '/dashboard', requirement: REQUIRED_SCOPES.CONSENTS_READ_SELF },
  { path: '/consents', requirement: REQUIRED_SCOPES.CONSENTS_READ_SELF },
  { path: '/purposes', requirement: REQUIRED_SCOPES.PURPOSES_READ },
  { path: '/elements', requirement: REQUIRED_SCOPES.ELEMENTS_READ },
  { path: '/events', requirement: REQUIRED_SCOPES.EVENTS_READ },
  { path: '/events/topics', requirement: REQUIRED_SCOPES.EVENT_TOPICS_READ },
  { path: '/events/subscriptions', requirement: REQUIRED_SCOPES.EVENT_SUBSCRIPTIONS_READ },
  { path: '/administration/consents', requirement: REQUIRED_SCOPES.CONSENTS_READ_ANY },
  { path: '/complaints', requirement: REQUIRED_SCOPES.COMPLAINTS_READ_SELF },
  { path: '/complaint-management', requirement: REQUIRED_SCOPES.COMPLAINTS_READ_ANY },
]

/** The first landing page the session's scopes actually allow. */
export default function firstAuthorizedPath(scopes: readonly string[]): string | undefined {
  const granted = new Set(scopes)
  const hasScope = (requirement: ScopeRequirement): boolean =>
    requirement.some((scope) => granted.has(scope))

  // A DPO's token carries internal_login like everyone else's, so CONSENTS_READ_SELF alone would
  // still route them to the Dashboard or My Consents - both of which hide themselves from a DPO
  // precisely because they have nothing non-duplicate to show them (see isDpoOnlyProfile,
  // AppSidebar, DashboardPage). Landing them there anyway on sign-in, only to have no link back to
  // it, would be worse than skipping both here in favor of Complaint Management further down this
  // list.
  const isDpoOnly = isDpoOnlyProfile(hasScope)
  const dpoSkippedPaths = new Set(['/dashboard', '/consents'])

  return AUTHORIZED_DESTINATIONS.find(
    ({ path, requirement }) => !(isDpoOnly && dpoSkippedPaths.has(path)) && hasScope(requirement),
  )?.path
}
