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

import type { NomineePermission } from '../../../types/nominee'

/**
 * Access rules applied while a nominee is acting on an owner's account.
 *
 * A nominee exercises the owner's data rights, never the owner's account
 * control. Two rules follow from that:
 *
 *  1. Account-control areas are fenced off permanently — no granted
 *     permission can unlock them.
 *  2. Everything else is denied unless a route is explicitly allowlisted and
 *     the owner granted the permission it requires.
 */

/**
 * Areas a nominee can never reach, whatever the owner granted. Changing
 * credentials or nominating someone would hand over the account itself.
 */
export const FENCED_PREFIXES: string[] = [
  '/nominations',
  '/nominee',
  '/profile',
  '/settings',
  '/security',
  '/account/delete',
  '/admin',
]

interface RouteRule {
  prefix: string
  permission: NomineePermission
}

/** Routes reachable while acting, and the permission each one requires. */
/**
 * Routes that read and write the OWNER's data while acting.
 *
 * The first-party pages are deliberately absent. They resolve their data from
 * the signed-in user, so while acting they would show the nominee's own records
 * under a banner naming the owner - the reader would have no way to tell whose
 * data they were changing.
 */
/**
 * Routes a nominee may open, and the permission each one needs.
 *
 * These pages resolve their data from the acting session rather than from the
 * signed-in user, so a nominee opening one sees the OWNER's records - which is
 * what the banner above them states.
 */
const ROUTE_RULES: RouteRule[] = [
  { prefix: '/consents', permission: 'CONSENT_VIEW' },
  { prefix: '/nominee/manage', permission: 'CONSENT_VIEW' },
  { prefix: '/dashboard', permission: 'ACCOUNT_VIEW' },
]

export function isFencedRoute(pathname: string): boolean {
  // The acting pages live under /nominee, which is otherwise fenced off.
  if (pathname.startsWith('/nominee/manage')) {
    return false
  }
  return FENCED_PREFIXES.some((prefix) => pathname.startsWith(prefix))
}

/** Deny-by-default: a route is reachable only if allowlisted and in scope. */
export function canAccessRoute(pathname: string, scope: NomineePermission[]): boolean {
  if (isFencedRoute(pathname)) {
    return false
  }

  const rule = ROUTE_RULES.find((item) => pathname.startsWith(item.prefix))
  if (!rule) {
    return false
  }

  return scope.includes(rule.permission)
}

/**
 * Where to send a nominee who opened something they cannot use, or null when
 * their grant covers nothing.
 *
 * Never returns a fenced route. Doing so sends the reader to a page that refuses
 * them and offers to send them onwards to the same place, which cannot be
 * escaped except by leaving nominee access.
 */
export function defaultRouteForScope(scope: NomineePermission[]): string | null {
  const reachable = ROUTE_RULES.find((rule) => scope.includes(rule.permission))
  return reachable ? reachable.prefix : null
}

export function hasPermission(scope: NomineePermission[], permission: NomineePermission): boolean {
  return scope.includes(permission)
}
