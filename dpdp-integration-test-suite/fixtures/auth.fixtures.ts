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

import { mkdir, open, readFile, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { test as base, type APIRequestContext, type Browser, type Page, type Request } from '@playwright/test'
import { ComplaintApiClient } from '../clients/ComplaintApiClient'
import { ConsentApiClient } from '../clients/ConsentApiClient'
import { EventNotificationApiClient } from '../clients/EventNotificationApiClient'
import { LoginPage } from '../pages/LoginPage'
import {
  authHeadersFromPersonaState,
  type PersonaAuthState,
  type PersonaStorageState,
} from '../utils/authStorage'
import { consentPurposesApiUrl, env, type Persona, type PersonaName } from '../utils/env'

/**
 * Every fixture here represents an already-authenticated "state" (per the fixtures/ folder's job
 * in this suite), built from a real login driven against the real Identity Server - never a
 * stub. A given persona only ever logs in once for the whole run, the first time any test needs
 * it - see getPersonaState below for how that one login's result is cached to a gitignored file
 * under `.auth/` and read back by every later test/worker that needs the same persona, instead of
 * each one logging in for itself.
 */

/**
 * A place for a test to register the id of an Element or Purpose it created through the UI, so
 * it gets deleted again once the test finishes - without this, every regression run only adds
 * data.
 */
export interface ConsentCleanupTracker {
  trackElement: (id: string) => void
  trackPurpose: (id: string) => void
}

interface Fixtures {
  userConsentApi: ConsentApiClient
  consentAdminConsentApi: ConsentApiClient
  consentCleanupTracker: ConsentCleanupTracker
  // "Officer" here is any dpdp-consent-admin holder (see AGENTS.md's
  // Personas section) - reuses the same consent-admin persona/login as consentAdminConsentApi,
  // just wrapped in the complaint client instead of the consent one.
  userComplaintApi: ComplaintApiClient
  officerComplaintApi: ComplaintApiClient
  // dpdp-consent-admin holds every notifications:* scope (see
  // AGENTS.md), so this one persona doubles as the admin, the
  // event publisher, and the webhook-verification actor - same "one role covers every surface"
  // rationale as officerComplaintApi above.
  consentAdminEventApi: EventNotificationApiClient
  // dpdp-consent-user holds NO notifications:* scope - used only to prove every event-notification
  // endpoint rejects a token that lacks the relevant scope.
  userEventApi: EventNotificationApiClient
}

/**
 * WSO2 IS invalidates a session when the same account logs in again elsewhere (e.g. a real
 * browser tab left open, or a previous run's session that never got closed) - which shows up as
 * unexplained timeouts partway through a run. `/api/users/v1/me/sessions` is IS's own
 * self-service session-management API: plain HTTP Basic auth with the account's own credentials
 * is enough (verified live - no OAuth token, no special role needed), and DELETE terminates every
 * active session for that account, including ones from a real browser. Only called once, right
 * before a persona's one real login for the whole run (see getPersonaState) - it can't stop a
 * *new* login (e.g. someone signing into the portal manually) from colliding with this run's own
 * session once it's underway.
 */
async function terminateAllSessions(persona: Persona): Promise<void> {
  const credentials = Buffer.from(`${persona.username}:${persona.password}`).toString('base64')
  try {
    const response = await fetch(`${env.identityServerBaseUrl}/api/users/v1/me/sessions`, {
      method: 'DELETE',
      headers: { Authorization: `Basic ${credentials}` },
      signal: AbortSignal.timeout(10_000),
    })
    if (response.status !== 204) {
      console.warn(
        `Could not terminate ${persona.username}'s existing sessions (status ${String(response.status)}) - proceeding anyway.`,
      )
    }
  } catch (error) {
    console.warn(
      `Could not terminate ${persona.username}'s existing sessions - proceeding anyway. Cause: ${(error as Error).message}`,
    )
  }
}

/**
 * A successful login only proves the consent-admin persona's credentials are valid, not that the
 * account actually holds the `dpdp-consent-admin` role - that role assignment is a manual Console
 * step (see docs/configuration-guide.md, "Grant administration access") that's easy to forget for
 * a freshly created test account. Without this check, a missing role surfaces as dozens of
 * unrelated, confusing assertion failures scattered across the suite (every seeded
 * Purpose/Element/Consent creation silently 401s/403s) instead of one clear error naming the
 * actual problem, right at this persona's one login for the run.
 */
async function verifyConsentAdminAuthorized(state: PersonaAuthState): Promise<void> {
  const response = await fetch(consentPurposesApiUrl(''), {
    headers: { ...authHeadersFromPersonaState(state) },
    signal: AbortSignal.timeout(10_000),
  })
  if (response.status === 401 || response.status === 403) {
    throw new Error(
      `The consent admin ("${env.consentAdmin.username}") logged in successfully but ` +
        `is not authorized for the consent-management admin API (got ${String(response.status)} ` +
        `from ${consentPurposesApiUrl('')}). Assign this account the dpdp-consent-admin role in ` +
        `the Console - see docs/configuration-guide.md, "Grant administration access".`,
    )
  }
}

/**
 * Waits for `persona` to reach a signed-in state on `page`, filling in the real Identity Server
 * login form if (and only if) it actually appears, and returns the request that proved sign-in
 * completed. The portal has no backend of its own any more (see docs/configuration-guide.md): the
 * SPA keeps its access token inside its auth SDK's own web worker, never in a cookie or anywhere
 * else `storageState` or page JS can read directly (see utils/authStorage.ts) - so "signed in" has
 * to be observed off the wire instead, as the first outgoing request that actually carries a
 * `Bearer` token, which every authenticated view eventually makes.
 *
 * The login form itself only appears when there's no valid IS-side SSO session yet - the very
 * first login of a run (see loginAndCaptureState), or if a persona's session was somehow
 * invalidated - since a context reused from a cached `storageState` (see loginAs) already carries
 * IS's own session cookies and the SPA's normal sign-in redirect is satisfied silently. Either way
 * this polls for the form and fills it in at most once, racing that against the error banner a
 * wrong password renders inline, same as a failed login always has here. `Promise.race` never
 * cancels its loser, so the poll below just stops checking (not throws) once the request side has
 * already settled, rather than surfacing a spurious rejection after the fact.
 */
async function ensureSignedIn(page: Page, persona: Persona): Promise<Request> {
  const outcome = { settled: false }
  const authenticatedRequest = page
    .waitForRequest((request) => Boolean(request.headers().authorization?.startsWith('Bearer ')), {
      timeout: 30_000,
    })
    .finally(() => {
      outcome.settled = true
    })

  const loginPage = new LoginPage(page)
  const formPoll = (async () => {
    let submitted = false
    while (!outcome.settled) {
      if (!submitted && (await page.locator('#usernameUserInput').isVisible().catch(() => false))) {
        submitted = true
        await loginPage.signIn(persona)
      }
      if (await loginPage.errorMessage.isVisible().catch(() => false)) {
        const message = (await loginPage.errorMessage.textContent())?.trim()
        throw new Error(`Login failed for persona "${persona.username}": ${message ?? 'Login failed.'}`)
      }
      await new Promise((resolve) => {
        setTimeout(resolve, 250)
      })
    }
  })()
  await Promise.race([authenticatedRequest, formPoll])

  return authenticatedRequest
}

/**
 * Drives the real Identity Server login form once and captures the resulting session -
 * getPersonaState is the one place that persists it, to `.auth/<persona>.json`. Reuses the
 * worker's own `browser` (Playwright's built-in `browser` fixture is worker-scoped, i.e. one
 * instance per worker process already) for a throwaway context/page rather than launching a
 * separate browser just for this.
 *
 * This is the one place in the whole suite that actually exercises the real login page - every
 * other fixture/test just reuses the state captured here (see getPersonaState), so the login form
 * itself is verified as part of this already-existing flow rather than by a separate, dedicated
 * login test.
 */
async function loginAndCaptureState(browser: Browser, persona: Persona): Promise<PersonaAuthState> {
  const context = await browser.newContext({ ignoreHTTPSErrors: env.ignoreHttpsErrors })
  try {
    const page = await context.newPage()
    await page.goto(`${env.portalBaseUrl}/`, { waitUntil: 'networkidle' })
    const authenticatedRequest = await ensureSignedIn(page, persona)

    const authorization = authenticatedRequest.headers().authorization
    if (!authorization) {
      // ensureSignedIn's own wait predicate already requires this header to be present - this is
      // just keeping TypeScript honest about headers() being a plain string-keyed record.
      throw new Error(`No Authorization header found on persona "${persona.username}"'s sign-in request.`)
    }

    return {
      storageState: await context.storageState(),
      bearerToken: authorization.replace(/^Bearer\s+/i, ''),
    }
  } finally {
    // Closed on every path, including a failed/timed-out login - without this, a misconfigured
    // persona leaks one browser context per attempt (and getPersonaState may retry this from
    // several call sites over the run).
    await context.close()
  }
}

/**
 * Cross-process cache: one gitignored JSON file per persona under `.auth/`, holding exactly the
 * object `context.storageState()` returns. A file can be read by any worker regardless of which
 * one happens to run first, so a persona logs in at most once for the whole run, not once per
 * test or per worker. `global-teardown.ts` deletes this directory at the end of every run, so the
 * next run always starts with a fresh login.
 */
const AUTH_DIR = path.resolve(import.meta.dirname, '..', '.auth')

function authFilePath(personaName: PersonaName): string {
  return path.join(AUTH_DIR, `${personaName}.json`)
}

function lockFilePath(personaName: PersonaName): string {
  return path.join(AUTH_DIR, `${personaName}.lock`)
}

async function readCachedState(personaName: PersonaName): Promise<PersonaAuthState | undefined> {
  try {
    return JSON.parse(await readFile(authFilePath(personaName), 'utf-8')) as PersonaAuthState
  } catch {
    return undefined
  }
}

/**
 * Cross-process mutex for "who gets to log in": `open(path, 'wx')` is an atomic, exclusive
 * filesystem create that fails with EEXIST if the lock file already exists, so at most one
 * worker process ever wins this race for a given persona. Only held for the brief moment of the
 * actual login and file write, not for a whole test - once the file exists, every later caller
 * just reads it, no locking involved. Without this lock, two tests that both find no cached
 * session yet (the very first use of a persona in the run) would both call terminateAllSessions
 * and log in, and whichever finished second would invalidate the first one's session out from
 * under whatever test was already using it.
 */
async function acquireLoginLock(personaName: PersonaName): Promise<boolean> {
  await mkdir(AUTH_DIR, { recursive: true })
  try {
    await (await open(lockFilePath(personaName), 'wx')).close()
    return true
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === 'EEXIST') {
      return false
    }
    throw error
  }
}

async function releaseLoginLock(personaName: PersonaName): Promise<void> {
  await rm(lockFilePath(personaName), { force: true })
}

/**
 * Polls for the cache file a different worker's in-flight login (see acquireLoginLock) is about
 * to write, instead of attempting a second login of its own. If that other login fails outright,
 * its own test reports the real error; a test blocked here instead times out with the generic
 * message below - check the run for a failed login test first.
 */
async function waitForCachedState(personaName: PersonaName): Promise<PersonaAuthState> {
  const deadline = Date.now() + 60_000
  while (Date.now() < deadline) {
    const cached = await readCachedState(personaName)
    if (cached) {
      return cached
    }
    await new Promise((resolve) => {
      setTimeout(resolve, 500)
    })
  }
  throw new Error(
    `Timed out waiting for another worker to finish logging in as "${personaName}" - check the ` +
      'run for a failed login test, which is the likely real cause.',
  )
}

/**
 * Returns the cached session for `personaName`, logging in for real (after terminating any
 * session left over from elsewhere) only the first time any test in the run needs it - every
 * later call, from any fixture, any test, any worker, just reads the file this first call wrote.
 * Shared by every fixture below, and by the one place outside the fixtures that needs a persona
 * this suite has no always-on fixture for - the ownership-isolation test in
 * `tests/03-consents/03.02-user-viewing-consents.spec.ts` calls this directly for
 * `user-2`.
 */
export async function getPersonaState(
  browser: Browser,
  personaName: PersonaName,
  persona: Persona,
): Promise<PersonaAuthState> {
  const cached = await readCachedState(personaName)
  if (cached) {
    return cached
  }

  const acquiredLock = await acquireLoginLock(personaName)
  if (!acquiredLock) {
    return waitForCachedState(personaName)
  }

  try {
    // Another worker may have finished writing the file between our read above and acquiring
    // the lock just now - re-check before logging in again.
    const cachedAfterLock = await readCachedState(personaName)
    if (cachedAfterLock) {
      return cachedAfterLock
    }

    await terminateAllSessions(persona)
    const state = await loginAndCaptureState(browser, persona)
    if (personaName === 'consent-admin') {
      await verifyConsentAdminAuthorized(state)
    }
    await writeFile(authFilePath(personaName), JSON.stringify(state))
    return state
  } finally {
    await releaseLoginLock(personaName)
  }
}

/**
 * Drops the servlet container's own `JSESSIONID` cookies from a captured session, keeping
 * everything else (notably `commonAuthId`, IS's SSO session, which is what lets a reused context
 * sign in silently).
 *
 * This is what makes the suite safe to run in parallel. The portal's sign-in handoff parks the
 * authorization code in the webapp's HTTP SESSION - index.jsp forwards the code to /authenticate,
 * home.jsp parks it, auth.jsp hands it over once and clears it so a reload cannot replay it (see
 * the portal's web.xml). That is correct anti-replay behaviour for a real browser, which never
 * shares one JSESSIONID across two windows mid-sign-in. But `storageState` captures
 * `JSESSIONID path=/consent-portal`, so without this filter every context built here replayed the
 * SAME server-side session: N concurrent contexts meant N concurrent callbacks writing and
 * clearing one shared parked code, and whichever ones lost that race came back without a usable
 * code and silently landed on the default route instead of the one the test asked for. Serially
 * the flows never overlap, which is why it only ever showed up with more than one worker.
 *
 * Each context now gets its own HTTP session and does its own handoff, while still reusing the
 * IS-side SSO session so no credentials are re-entered.
 */
function withoutServletSessionCookies(state: PersonaStorageState): PersonaStorageState {
  return {
    ...state,
    cookies: state.cookies.filter((cookie) => cookie.name !== 'JSESSIONID'),
  }
}

/**
 * Builds an already-authenticated page for a persona whose `PersonaAuthState` the caller already
 * has - split out of `loginAs` below for the one place outside these fixtures that calls
 * `getPersonaState` directly (the ownership-isolation test in
 * `tests/03-consents/03.02-user-viewing-consents.spec.ts`, for `user-2`, which has no always-on
 * fixture of its own). The caller owns the returned page's context and must close it itself
 * (`await page.context().close()`) once done with it.
 *
 * Seeding the new context's `storageState` from the cached login only carries over IS's own SSO
 * session cookies (and the token-binding cookie, see utils/authStorage.ts) - it can't carry over
 * the actual access token, since that never lives anywhere but the *original* login's in-memory
 * auth SDK worker (see ensureSignedIn). So every call still drives the SPA's normal sign-in
 * redirect once; with a live SSO session already in the reused cookies, IS satisfies it silently
 * (no visible form, no credentials re-entered) and the SPA ends up with its own fresh token.
 */
export async function pageForPersonaState(
  browser: Browser,
  personaState: PersonaAuthState,
  persona: Persona,
): Promise<Page> {
  // browser.newContext() here bypasses playwright.config.ts's `use` block entirely (that's only
  // auto-applied to the base test's own default context/page) - baseURL and ignoreHTTPSErrors have
  // to be passed explicitly or relative goto() calls break and the self-signed cert kills every
  // navigation.
  const context = await browser.newContext({
    storageState: withoutServletSessionCookies(personaState.storageState),
    baseURL: env.portalNavigationBaseUrl,
    ignoreHTTPSErrors: env.ignoreHttpsErrors,
  })
  const page = await context.newPage()

  // ensureSignedIn is armed BEFORE navigating, not after. Reusing a cached storageState means IS
  // satisfies the SPA's sign-in redirect silently, so the token arrives and the first Bearer
  // request goes out while the goto below is still settling to networkidle. Starting the watch
  // afterwards would miss it and then wait 30s for a request that has already been made.
  // loginAndCaptureState is the opposite case and needs no such care: on the run's one real login
  // the page parks on the login form, so nothing can be missed while it navigates.
  const signedIn = ensureSignedIn(page, persona)
  // Keeps a goto failure from surfacing as an unhandled rejection on this promise; the await
  // below is still what reports it.
  signedIn.catch(() => undefined)

  // "./", never "/" - a leading slash REPLACES baseURL's path (see the long note in utils/env.ts),
  // so goto('/') here landed on the Identity Server root, which redirects to the CONSOLE rather
  // than to the portal. That masked itself serially, because the Console emits Bearer requests
  // too: the wait was satisfied by the wrong application, and the real portal sign-in happened
  // later on the test's own first goto(). For a persona with no Console access it was worse than
  // wasteful - the Console bounced to /console/unauthorized without ever issuing a Bearer request,
  // so the wait never settled and the test died on its timeout. That race gets much easier to lose
  // under worker contention, which is what made parallel runs flaky.
  await page.goto('./', { waitUntil: 'networkidle' })
  await signedIn
  return page
}

/**
 * Explicit, per-test alternative to a `userPage`/`consentAdminPage` fixture: each test calls this
 * itself instead of Playwright silently injecting an already-authenticated page. Still goes
 * through getPersonaState, so it's exactly as cheap as a fixture would be - the persona logs in
 * for real only the first time any test in the run calls this, every later call (from any test,
 * any file, any worker) just reuses the cached state.
 */
async function loginAs(browser: Browser, personaName: PersonaName, persona: Persona): Promise<Page> {
  const personaState = await getPersonaState(browser, personaName, persona)
  return pageForPersonaState(browser, personaState, persona)
}

export interface ThrowawaySession {
  page: Page
  /**
   * The session's own access token, lifted off the first authenticated request the way
   * loginAndCaptureState does. Needed because the auth SDK keeps the token in a web worker the
   * page cannot read, and the account-deletion test has to call SCIM2 directly with exactly this
   * user's token to prove what its scopes do and don't authorize.
   */
  bearerToken: string
}

/**
 * Signs an account in on a context of its own, with nothing cached or reused. For the one
 * account this suite must not share: the throwaway user the account-deletion test creates and
 * then destroys (see utils/throwawayUser.ts). Every persona above logs in once and is reused for
 * the whole run, which an account that stops existing partway through cannot be.
 *
 * The caller owns the returned page's context and must close it itself.
 */
export async function loginAsThrowawayUser(
  browser: Browser,
  persona: Persona,
): Promise<ThrowawaySession> {
  const context = await browser.newContext({
    baseURL: env.portalNavigationBaseUrl,
    ignoreHTTPSErrors: env.ignoreHttpsErrors,
  })
  const page = await context.newPage()
  await page.goto('/', { waitUntil: 'networkidle' })
  const authenticatedRequest = await ensureSignedIn(page, persona)

  const authorization = authenticatedRequest.headers().authorization
  if (!authorization) {
    throw new Error(`No Authorization header found on "${persona.username}"'s sign-in request.`)
  }
  return { page, bearerToken: authorization.replace(/^Bearer\s+/i, '') }
}

export async function loginAsUser(browser: Browser): Promise<Page> {
  return loginAs(browser, 'user', env.user)
}

export async function loginAsConsentAdmin(browser: Browser): Promise<Page> {
  return loginAs(browser, 'consent-admin', env.consentAdmin)
}

export const test = base.extend<Fixtures>({
  userConsentApi: async ({ browser, request }, use) => {
    const personaState = await getPersonaState(browser, 'user', env.user)
    await use(new ConsentApiClient(request, authHeadersFromPersonaState(personaState)))
  },

  consentAdminConsentApi: async ({ browser, request }, use) => {
    const personaState = await getPersonaState(browser, 'consent-admin', env.consentAdmin)
    await use(new ConsentApiClient(request, authHeadersFromPersonaState(personaState)))
  },

  userComplaintApi: async ({ browser, request }, use) => {
    const personaState = await getPersonaState(browser, 'user', env.user)
    await use(new ComplaintApiClient(request, authHeadersFromPersonaState(personaState)))
  },

  officerComplaintApi: async ({ browser, request }, use) => {
    const personaState = await getPersonaState(browser, 'consent-admin', env.consentAdmin)
    await use(new ComplaintApiClient(request, authHeadersFromPersonaState(personaState)))
  },

  consentAdminEventApi: async ({ browser, request }, use) => {
    const personaState = await getPersonaState(browser, 'consent-admin', env.consentAdmin)
    await use(new EventNotificationApiClient(request, authHeadersFromPersonaState(personaState)))
  },

  userEventApi: async ({ browser, request }, use) => {
    const personaState = await getPersonaState(browser, 'user', env.user)
    await use(new EventNotificationApiClient(request, authHeadersFromPersonaState(personaState)))
  },

  consentCleanupTracker: async ({ consentAdminConsentApi }, use) => {
    const elementIds: string[] = []
    const purposeIds: string[] = []
    await use({
      trackElement: (id) => elementIds.push(id),
      trackPurpose: (id) => purposeIds.push(id),
    })
    // Sequential cleanup, not perf-sensitive.
    for (const id of purposeIds) {
      await consentAdminConsentApi.deletePurpose(id).catch(() => undefined)
    }
    for (const id of elementIds) {
      await consentAdminConsentApi.deleteElement(id).catch(() => undefined)
    }
  },
})

export { expect } from '@playwright/test'

/**
 * Ownership-isolation tests need a second, distinct real user account, which a real
 * environment can't fabricate on demand the way a stubbed IdP could. Those tests call this to
 * decide whether to run at all, and skip themselves with a clear reason when it's false. The
 * actual login-success check for this persona happens lazily on first use inside
 * `getPersonaState`, the same way every other persona's fixture implicitly relies on its own
 * login succeeding.
 */
export function hasSecondUser(): boolean {
  return Boolean(env.secondUser())
}

/**
 * Same rationale as hasSecondUser/env.secondUser(): the ownership-isolation tests in
 * the complaint ownership-isolation tests need a second real user's ComplaintApiClient, and there is no
 * always-on fixture for it since most runs don't configure personas.user2.
 * Returns undefined when it isn't configured; callers check hasSecondUser() first and skip
 * themselves, same pattern as the consent-side ownership tests.
 */
export async function getSecondUserComplaintApi(
  browser: Browser,
  request: APIRequestContext,
): Promise<ComplaintApiClient | undefined> {
  const secondUser = env.secondUser()
  if (!secondUser) {
    return undefined
  }
  const personaState = await getPersonaState(browser, 'user-2', secondUser)
  return new ComplaintApiClient(request, authHeadersFromPersonaState(personaState))
}
