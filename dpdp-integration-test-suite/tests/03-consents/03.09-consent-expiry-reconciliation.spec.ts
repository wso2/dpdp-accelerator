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

import { test, expect, loginAsConsentAdmin } from '../../fixtures/auth.fixtures'
import { consentExpirySchedulerPollTimeoutMs, env } from '../../utils/env'
import { seedConsent } from '../../utils/consentSetup'

interface StatusAuditEntry {
  actionType: string
  actionBy: string
  currentStatus: string
  actionTime: number
}

interface HistoryEntry {
  actionType: string
  actionBy: string
  actionTime: number
}

/**
 * Exercises DPDPConsentExpiryReconciler - the accelerator's reconciliation of a lapsed consent's
 * expiry into DPDP_CONSENT_STATUS_AUDIT/DPDP_CONSENT_HISTORY (ActionType.EXPIRE, actionBy=SYSTEM).
 *
 * The real ConsentExpiryJob runs on its own Quartz cron (`dpdp_accelerator.consent_expiry` in
 * deployment.toml, daily by default) with no manual-trigger endpoint, so waiting on it for real
 * needs the operator to have shortened that cron and restarted the server first.
 * `DPDPConsentExpiryReconciler.expireConsentIfDue` is shared code, though: its own class doc says
 * it's called identically from the scheduled job's batch path AND from every consent-mutation
 * `pre*` hook (DPDPConsentHistoryListener), so a lapsed consent gets reconciled the moment
 * anything touches it again, whichever happens first.
 *
 * 03.09.01/03.09.02 below trigger that shared logic via a mutation (no server config needed, run
 * always). 03.09.03 instead waits on the literal scheduled job with no mutation at all, so it only
 * runs when consentExpiry.schedulerPollTimeoutMs is configured (see README.md) - it skips
 * itself otherwise.
 *
 * Confirmed live: revoking a consent whose expiry has already passed 409s (CM_00112 - stock
 * carbon-consent-management resolves the receipt's state to EXPIRED before validating the revoke,
 * and rejects any non-PENDING/ACTIVE-as-persisted transition). That 409 is a real, separate product
 * behaviour 03.09.02 doesn't assert on either way - preRevokeConsent's call to
 * DPDPConsentExpiryReconciler.expireConsentIfDue runs, and the EXPIRE row is written, before that
 * later validation ever gets to run and reject the revoke itself.
 */
test.describe('Consent expiry reconciliation (API)', () => {
  test('03.09.01 - A consent whose expiry time has not yet passed has no EXPIRE entry in its history', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    const futureExpiry = Date.now() + 24 * 60 * 60 * 1000
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'ACTIVE',
      undefined,
      futureExpiry,
    )

    const historyResponse = await consentAdminConsentApi.getConsentHistory(consentId)
    expect(historyResponse.status()).toBe(200)
    const { history } = (await historyResponse.json()) as { history: Array<{ actionType: string }> }
    expect(history.some((entry) => entry.actionType === 'EXPIRE')).toBe(false)

    await consentAdminPage.context().close()
  })

  test('03.09.02 - Revoking a consent past its expiry time first reconciles the lapse into an EXPIRE history entry', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const consentAdminPage = await loginAsConsentAdmin(browser)
    // A full minute in the past so the reconciler's own now-vs-expiryTime comparison (evaluated at
    // revoke time below, not at seed time) is unambiguously due regardless of the gap between this
    // seed call and the revoke call that follows it.
    const pastExpiry = Date.now() - 60_000
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'ACTIVE',
      undefined,
      pastExpiry,
    )

    // Response status intentionally not asserted - see the describe-block comment above.
    await consentAdminConsentApi.revokeAdminConsent(consentId)

    const statusHistoryResponse = await consentAdminConsentApi.getConsentStatusHistory(consentId)
    expect(statusHistoryResponse.status()).toBe(200)
    const { statusHistory } = (await statusHistoryResponse.json()) as {
      statusHistory: Array<{ actionType: string; actionBy: string; currentStatus: string }>
    }
    const expireEntry = statusHistory.find((entry) => entry.actionType === 'EXPIRE')
    expect(expireEntry).toBeDefined()
    expect(expireEntry?.actionBy).toBe('SYSTEM')
    expect(expireEntry?.currentStatus).toBe('EXPIRED')

    const historyResponse = await consentAdminConsentApi.getConsentHistory(consentId)
    expect(historyResponse.status()).toBe(200)
    const { history } = (await historyResponse.json()) as { history: Array<{ actionType: string; actionBy: string }> }
    const expireSnapshot = history.find((entry) => entry.actionType === 'EXPIRE')
    expect(expireSnapshot).toBeDefined()
    expect(expireSnapshot?.actionBy).toBe('SYSTEM')

    await consentAdminPage.context().close()
  })

  test('03.09.03 - The background ConsentExpiryJob reconciles a lapsed consent within one scheduler cycle, with an accurate history timestamp', async ({
    browser,
    consentAdminConsentApi,
    consentCleanupTracker,
  }) => {
    const pollTimeout = consentExpirySchedulerPollTimeoutMs()
    test.skip(
      !pollTimeout,
      'consentExpiry.schedulerPollTimeoutMs is not configured - see README.md. Requires ' +
        "shortening deployment.toml's [dpdp_accelerator.consent_expiry].cron_value and restarting " +
        'the server, so this is opt-in rather than run by default.',
    )
    // Playwright's own default test timeout (30s) is shorter than any sane poll window for a
    // scheduler test - extend it to comfortably cover the poll plus setup/teardown.
    test.setTimeout((pollTimeout ?? 0) + 30_000)

    const consentAdminPage = await loginAsConsentAdmin(browser)
    const dueSince = Date.now()
    // Already due at creation, so the only thing this test waits on is the job noticing it, not
    // it also becoming due first.
    const { consentId } = await seedConsent(
      consentAdminPage,
      consentAdminConsentApi,
      consentCleanupTracker,
      env.user.username,
      'ACTIVE',
      undefined,
      dueSince,
    )

    // No mutation is performed on this consent anywhere in this test - unlike 03.09.02, the only
    // thing that can produce the EXPIRE entry below is the real ConsentExpiryJob's own batch pass
    // finding and claiming it on its own schedule.
    await expect
      .poll(
        async () => {
          const response = await consentAdminConsentApi.getConsentStatusHistory(consentId)
          if (response.status() !== 200) {
            return undefined
          }
          const { statusHistory } = (await response.json()) as { statusHistory: StatusAuditEntry[] }
          return statusHistory.find((entry) => entry.actionType === 'EXPIRE')
        },
        { timeout: pollTimeout, message: 'ConsentExpiryJob never recorded an EXPIRE status-audit entry' },
      )
      .toBeDefined()
    const observedAt = Date.now()

    const statusHistoryResponse = await consentAdminConsentApi.getConsentStatusHistory(consentId)
    const { statusHistory } = (await statusHistoryResponse.json()) as { statusHistory: StatusAuditEntry[] }
    const expireEntry = statusHistory.find((entry) => entry.actionType === 'EXPIRE')
    expect(expireEntry?.actionBy).toBe('SYSTEM')
    expect(expireEntry?.currentStatus).toBe('EXPIRED')
    // Timestamp accuracy: the job can only have recorded this between the consent becoming due
    // (dueSince - expiryTime === dueSince here) and the moment the poll above observed it.
    expect(expireEntry?.actionTime).toBeGreaterThanOrEqual(dueSince)
    expect(expireEntry?.actionTime).toBeLessThanOrEqual(observedAt)

    const historyResponse = await consentAdminConsentApi.getConsentHistory(consentId)
    const { history } = (await historyResponse.json()) as { history: HistoryEntry[] }
    const expireSnapshot = history.find((entry) => entry.actionType === 'EXPIRE')
    expect(expireSnapshot?.actionBy).toBe('SYSTEM')
    expect(expireSnapshot?.actionTime).toBeGreaterThanOrEqual(dueSince)
    expect(expireSnapshot?.actionTime).toBeLessThanOrEqual(observedAt)

    await consentAdminPage.context().close()
  })
})
