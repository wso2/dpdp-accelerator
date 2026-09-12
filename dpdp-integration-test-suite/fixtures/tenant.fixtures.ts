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

import { type Browser, type Locator, type Page, type Request, request as playwrightRequest } from '@playwright/test'
import { ConsentApiClient } from '../clients/ConsentApiClient'
import { EventNotificationApiClient } from '../clients/EventNotificationApiClient'
import { ConsoleAddUserWizard } from '../pages/ConsoleAddUserWizard'
import { ConsoleRoleAssignment } from '../pages/ConsoleRoleAssignment'
import { ConsoleRootOrganizationWizard } from '../pages/ConsoleRootOrganizationWizard'
import { LoginPage } from '../pages/LoginPage'
import { authHeadersFromPersonaState, type PersonaAuthState } from '../utils/authStorage'
import { consoleRootOrganizationsUrl, env, tenantConsoleUrl, tenantPortalUrl, type Persona } from '../utils/env'
import { uniqueMarker, uniqueTenantDomain } from '../utils/testData'
// Extends auth.fixtures's own `test`, not raw @playwright/test - tests/05-multi-tenancy needs
// the super tenant's consentAdminConsentApi fixture too (for the isolation scenario, which
// compares a tenant-scoped Purpose against a super-tenant one), and Playwright fixtures only
// compose by extending, not by importing two independently-extended `test` objects into one spec.
import { test as base } from './auth.fixtures'

/**
 * Everything tests/05-multi-tenancy needs about the one throwaway tenant this worker created:
 * its domain, its two personas (see the `tenant` fixture below for what each is for), and a
 * ready-made API client bound to the owner's auth, tenant-qualified.
 */
export interface TenantContext {
  domain: string
  /** Created via Console's "New Root Organization" wizard, then explicitly assigned
   * dpdp-consent-admin by this fixture - role membership is never auto-provisioned, being the
   * tenant's owner grants Console/IS-level administration only, nothing about this custom
   * application role (confirmed live: without the explicit assignment below, the owner's sidebar
   * has no admin items at all). */
  owner: Persona
  /** Created via the owner's own Console "Add User" wizard and assigned dpdp-consent-user
   * (no permissions) - the tenant-local equivalent of the super tenant's plain `user` persona. */
  consentUser: Persona
  ownerConsentApi: ConsentApiClient
  // Tenant-qualified the same way ownerConsentApi is - tests/08-event-notifications' tenant
  // isolation file (05.10) uses this directly rather than re-deriving tenant-scoped headers of
  // its own.
  ownerEventApi: EventNotificationApiClient
}

// `tenant`/`tenantB` are worker-scoped (see the `test.extend` call below), which Playwright's
// fixture typing requires declaring as the *second* type parameter, separate from any per-test
// fixtures - there are none needed here, hence the empty first type argument. `tenantB` exists
// only for tests/08-event-notifications/08.11-tenant-isolation-api.spec.ts, which needs two
// distinct tenants live at once (proving tenant A's data never leaks into tenant B's view and
// vice versa) - every other multi-tenancy test in this suite only ever needed one.
interface WorkerFixtures {
  tenant: TenantContext
  tenantB: TenantContext
}

/**
 * Waits for a Console/portal login form to appear and fills it in. Deliberately not a call into
 * fixtures/auth.fixtures.ts's ensureSignedIn: that function is tightly coupled to the super
 * tenant's own portal base URL and to a cross-worker `.auth/` login cache, neither of which
 * applies here - every tenant this fixture creates belongs to exactly one worker for the
 * whole run, so there is nothing to cache and no other worker to race against.
 */
async function fillLoginForm(page: Page, persona: Persona): Promise<void> {
  const loginPage = new LoginPage(page)
  await loginPage.signIn(persona)
  if (await loginPage.errorMessage.isVisible({ timeout: 5_000 }).catch(() => false)) {
    const message = (await loginPage.errorMessage.textContent())?.trim()
    throw new Error(`Sign-in failed for persona "${persona.username}": ${message ?? 'Login failed.'}`)
  }
}

/**
 * Logs into a Console URL as `persona`, in a fresh context. `consoleUrl` is a full absolute URL
 * (the super tenant's root-organizations page, or a specific tenant's own `/console`) - both are
 * different apps than the portal this suite's baseURL points at, so page.goto() here always
 * takes an absolute URL rather than relying on playwright.config.ts's baseURL.
 */
async function loginToConsole(
  browser: Browser,
  consoleUrl: string,
  persona: Persona,
  ready?: (page: Page) => Locator,
): Promise<Page> {
  let lastError: unknown

  // Retried as a whole, with a brand-new context each attempt, because the Console's own token
  // exchange sometimes fails server-side in a way the SPA never recovers from: IS logs
  // "IdentityOAuth2Exception: Token binding reference cannot be retrieved from the token binder:
  // cookie" for client CONSOLE, and the page then sits on its bootstrap spinner indefinitely -
  // no error, no timeout of its own. Only a fresh cookie jar and a fresh authorize round clear
  // it, so reloading the same context is not enough.
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    const context = await browser.newContext({ ignoreHTTPSErrors: env.ignoreHttpsErrors })
    const page = await context.newPage()

    try {
      await page.goto(consoleUrl, { waitUntil: 'domcontentloaded' })
      await page.locator('#usernameUserInput').waitFor({ state: 'visible', timeout: 20_000 })
      await fillLoginForm(page, persona)
      // Deliberately not checking for a specific post-login element (e.g. the sidebar's
      // "Applications" link): confirmed empirically that the super tenant's Root Organizations page
      // renders with no sidebar at all (a different layout than a tenant's own Console shell), so no
      // single element is common to every page this function is asked to land on. The login form
      // disappearing, generically, is what every successful login has in common. A caller that DOES
      // know what it is about to interact with passes `ready`, which is what turns the hang above
      // into a retry instead of a fixture timeout.
      await page.locator('#usernameUserInput').waitFor({ state: 'hidden', timeout: 30_000 })
      await page.waitForLoadState('networkidle', { timeout: 30_000 }).catch(() => undefined)
      if (ready) {
        await ready(page).waitFor({ state: 'visible', timeout: 30_000 })
      }
      return page
    } catch (error) {
      lastError = error
      await context.close()
    }
  }

  throw new Error(
    `Console at ${consoleUrl} never became usable for "${persona.username}" after 3 attempts: ` +
      `${lastError instanceof Error ? lastError.message : String(lastError)}`,
  )
}

/**
 * Logs into a tenant's own consent portal as `persona`, capturing the resulting auth state the
 * same way fixtures/auth.fixtures.ts's loginAndCaptureState does for the super tenant - mirrors
 * its Bearer-request-detection technique (the SPA never exposes the token any other way, see
 * utils/authStorage.ts) rather than reusing that function directly, for the same reason
 * fillLoginForm above doesn't reuse ensureSignedIn.
 */
async function loginToTenantPortal(
  browser: Browser,
  domain: string,
  persona: Persona,
): Promise<{ page: Page; authState: PersonaAuthState }> {
  // baseURL (trailing slash, same reasoning as env.ts's portalNavigationBaseUrl) is set here so
  // that page objects written against the super tenant's relative goto('purposes')-style calls
  // work unmodified against a tenant-qualified page too.
  const context = await browser.newContext({
    ignoreHTTPSErrors: env.ignoreHttpsErrors,
    baseURL: `${tenantPortalUrl(domain)}/`,
  })
  const page = await context.newPage()

  const outcome = { settled: false }
  const authenticatedRequest = page
    .waitForRequest((request) => Boolean(request.headers().authorization?.startsWith('Bearer ')), {
      timeout: 30_000,
    })
    .finally(() => {
      outcome.settled = true
    })

  await page.goto('./', { waitUntil: 'domcontentloaded' })

  const loginPage = new LoginPage(page)
  const formPoll = (async () => {
    let submitted = false
    while (!outcome.settled) {
      if (!submitted && (await page.locator('#usernameUserInput').isVisible().catch(() => false))) {
        submitted = true
        await fillLoginForm(page, persona)
      }
      if (await loginPage.errorMessage.isVisible().catch(() => false)) {
        const message = (await loginPage.errorMessage.textContent())?.trim()
        throw new Error(`Portal sign-in failed for persona "${persona.username}": ${message ?? 'Login failed.'}`)
      }
      await new Promise((resolve) => {
        setTimeout(resolve, 250)
      })
    }
  })()
  const authenticatedReq = (await Promise.race([authenticatedRequest, formPoll])) as Request

  const authorization = authenticatedReq.headers().authorization
  if (!authorization) {
    throw new Error(`No Authorization header found on persona "${persona.username}"'s sign-in request.`)
  }

  return {
    page,
    authState: {
      storageState: await context.storageState(),
      bearerToken: authorization.replace(/^Bearer\s+/i, ''),
    },
  }
}

interface CreatedTenant {
  domain: string
  owner: Persona
  consentUser: Persona
  authState: PersonaAuthState
}

/**
 * The full create-tenant-then-create-second-user-then-assign-role setup, factored out so both
 * the `tenant` and `tenantB` fixtures below can share it - tests/08-event-notifications'
 * tenant-isolation file needs two live tenants at once, everything else in this suite needs one.
 * Returns the owner's captured auth state rather than building API clients itself, so the
 * caller (see the `tenant`/`tenantB` fixtures below) owns and disposes the `APIRequestContext`
 * those clients are bound to.
 */
async function createTenant(browser: Browser): Promise<CreatedTenant> {
  const domain = uniqueTenantDomain()
  // Email-shaped: the accelerator enforces an email-address username.
  const owner: Persona = { username: `${uniqueMarker('tenant-owner')}@dpdp.test`, password: 'TenantOwner@2026!' }
  const consentUser: Persona = { username: `${uniqueMarker('tenant-user')}@dpdp.test`, password: 'TenantUser@2026!' }

  // Step 1: super admin creates the tenant + owner through Console's "New Root Organization"
  // wizard. Confirmed live this is the only tenant-creation path whose password field works
  // immediately - see ConsoleRootOrganizationWizard's own comment for the full comparison
  // against the raw Tenant Management REST API.
  const adminPage = await loginToConsole(
    browser,
    consoleRootOrganizationsUrl(),
    env.superAdmin,
    (page) => new ConsoleRootOrganizationWizard(page).newRootOrganizationButton,
  )
  const rootOrgWizard = new ConsoleRootOrganizationWizard(adminPage)
  await rootOrgWizard.open()
  await rootOrgWizard.createTenant({
    domain,
    firstName: 'Tenant',
    lastName: 'Owner',
    username: owner.username,
    email: owner.username,
    password: owner.password,
  })
  // Provisioning itself is synchronous (confirmed live: the accelerator's onTenantCreate
  // finishes within the same request the dialog's own POST makes), but the dialog's close
  // animation and the underlying list's refresh still need a beat before the context is torn
  // down mid-flight.
  await adminPage.waitForTimeout(2_000)
  await adminPage.context().close()

  // Step 2: the tenant owner logs into their OWN Console (never the super admin - confirmed
  // live that `admin` cannot log into a secondary tenant's Console at all, since classic
  // tenants have fully independent user stores) and creates the second, lower-privilege user.
  // Confirmed live to succeed here even though the identical `POST .../scim2/Users` call
  // 401s when replayed directly via curl - see ConsoleAddUserWizard for the full story; this
  // suite never calls SCIM2 directly as a result.
  // No `ready` locator passed here, unlike step 1: this login has never been observed hanging on
  // the CONSOLE token-binding failure loginToConsole describes, and any locator picked for it
  // would be a guess. If this step ever times out on a blank spinner, that is the same bug -
  // pass the first element the wizard below touches (ConsoleAddUserWizard's addUserButton).
  const ownerConsolePage = await loginToConsole(browser, tenantConsoleUrl(domain), owner)
  await ownerConsolePage.goto(`${tenantConsoleUrl(domain)}/users`, { waitUntil: 'domcontentloaded' })
  const addUserWizard = new ConsoleAddUserWizard(ownerConsolePage)
  await addUserWizard.createUser({
    username: consentUser.username,
    email: consentUser.username,
    firstName: 'Tenant',
    lastName: 'User',
    password: consentUser.password,
  })

  // Role MEMBERSHIP is never auto-provisioned, only the roles themselves - true for the
  // super tenant too (see scripts/provision-test-users.sh and docs/configuration-guide.md's
  // "Recovering a broken tenant" section) and confirmed live here: the freshly created owner
  // has no admin sidebar items at all until explicitly assigned dpdp-consent-admin. Being the
  // tenant's owner only grants Console/IS-level administration, not this custom application
  // role - the two are unrelated.
  const roleAssignment = new ConsoleRoleAssignment(ownerConsolePage)
  await ownerConsolePage.goto(`${tenantConsoleUrl(domain)}/roles`, { waitUntil: 'domcontentloaded' })
  await roleAssignment.openRoleByName('dpdp-consent-admin')
  await roleAssignment.openUsersTab()
  await roleAssignment.assignUser(owner.username)

  await ownerConsolePage.goto(`${tenantConsoleUrl(domain)}/roles`, { waitUntil: 'domcontentloaded' })
  await roleAssignment.openRoleByName('dpdp-consent-user')
  await roleAssignment.openUsersTab()
  await roleAssignment.assignUser(consentUser.username)
  await ownerConsolePage.context().close()

  // Step 3: log the owner into their own tenant-qualified portal for real, the same way
  // fixtures/auth.fixtures.ts does for the super tenant, and keep the resulting auth state
  // around as ready-made, tenant-qualified API clients - tests/05-multi-tenancy and
  // tests/08-event-notifications use these to seed/verify records without needing their own
  // login for every API call.
  const { page: ownerPortalPage, authState } = await loginToTenantPortal(browser, domain, owner)
  await ownerPortalPage.context().close()

  return { domain, owner, consentUser, authState }
}

// Worker-scoped: one throwaway tenant per worker for the whole run, not per test - createTenant's
// chained browser-driven steps are too slow to pay for per test. No teardown/deactivation step:
// every run generates a fresh, unique domain (see uniqueTenantDomain), so there is nothing to
// free up for reuse, and no real tenant delete exists on this product to call anyway.
async function toTenantContext(created: CreatedTenant): Promise<{ context: TenantContext; dispose: () => Promise<void> }> {
  const apiContext = await playwrightRequest.newContext({ ignoreHTTPSErrors: env.ignoreHttpsErrors })
  const headers = authHeadersFromPersonaState(created.authState)
  return {
    context: {
      domain: created.domain,
      owner: created.owner,
      consentUser: created.consentUser,
      ownerConsentApi: new ConsentApiClient(apiContext, headers, created.domain),
      ownerEventApi: new EventNotificationApiClient(apiContext, headers, created.domain),
    },
    dispose: () => apiContext.dispose(),
  }
}

export const test = base.extend<object, WorkerFixtures>({
  tenant: [
    async ({ browser }, use) => {
      const { context, dispose } = await toTenantContext(await createTenant(browser))
      await use(context)
      await dispose()
    },
    // This setup chains three separate browser logins plus several UI wizards - the default
    // fixture timeout (tied to a single test's own timeout, 30s) is nowhere near enough. 120s was
    // enough when 05.10 ran on its own but not with the full suite in flight: the Console login
    // alone budgets 80s of waits, and the worker that owns this fixture also pays for tenantB.
    { scope: 'worker', timeout: 240_000 },
  ],

  // Only tests/08-event-notifications/08.11-tenant-isolation-api.spec.ts requests this fixture
  // (Playwright only runs a worker fixture's setup when some test in that worker actually uses
  // it), so no other spec pays createTenant's cost twice.
  tenantB: [
    async ({ browser }, use) => {
      const { context, dispose } = await toTenantContext(await createTenant(browser))
      await use(context)
      await dispose()
    },
    { scope: 'worker', timeout: 240_000 },
  ],
})

export { expect } from '@playwright/test'

/**
 * Signed-in `Page` for the tenant owner, tenant-qualified. Mirrors loginAsUser/loginAsConsentAdmin
 * from fixtures/auth.fixtures.ts in shape (caller owns the returned page's context and must close
 * it), but always logs in fresh - this persona is used by at most a couple of tests per run, so
 * the cross-worker caching machinery those functions need for the super tenant's shared,
 * many-tests-per-run personas would be pure overhead here.
 */
export async function loginAsTenantOwner(browser: Browser, tenant: TenantContext): Promise<Page> {
  const { page } = await loginToTenantPortal(browser, tenant.domain, tenant.owner)
  return page
}

/**
 * Same login as loginAsTenantOwner, but also returns the owner's own bearer token - needed only
 * by tests/08-event-notifications/08.05-event-notifications-authorization.spec.ts's wrong-tenant-token check,
 * which replays a genuinely valid token for tenant A against tenant B's API base URL. Every other
 * caller just needs the signed-in Page (loginAsTenantOwner above) or the ready-made
 * `ownerEventApi`/`ownerConsentApi` on TenantContext, both already tenant-qualified to the
 * OWNER's own domain - this one exists because the SPA never exposes the token any other way (see
 * utils/authStorage.ts), and arming a Bearer-request watcher AFTER loginAsTenantOwner already
 * resolved is too late (same pitfall fixtures/auth.fixtures.ts's pageForPersonaState documents).
 */
export async function loginAsTenantOwnerWithToken(
  browser: Browser,
  tenant: TenantContext,
): Promise<{ page: Page; bearerToken: string }> {
  const { page, authState } = await loginToTenantPortal(browser, tenant.domain, tenant.owner)
  return { page, bearerToken: authState.bearerToken }
}

/** Signed-in `Page` for the tenant's second, lower-privilege user - see `TenantContext.consentUser`. */
export async function loginAsTenantConsentUser(browser: Browser, tenant: TenantContext): Promise<Page> {
  const { page } = await loginToTenantPortal(browser, tenant.domain, tenant.consentUser)
  return page
}
