/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * 09.12 — MySQL deadlock reproduction for POST /events (EN-5001 on concurrent publish + subscribe)
 *
 * WHY THIS TEST EXISTS
 * --------------------
 * POST /events fails intermittently with EN-5001 on MySQL only. Root cause: lock-order inversion.
 *
 *   Publish tx:    SELECT … FROM TOPIC … FOR UPDATE   ← range scan → gap locks ALL org topics
 *                  → SELECT … FROM SUBSCRIPTION … FOR UPDATE
 *
 *   Subscribe tx:  INSERT INTO SUBSCRIPTION …
 *                  → INSERT INTO SUBSCRIPTION_TOPIC … ← FK_ST_TOPIC → shared lock on TOPIC
 *
 * InnoDB detects the cycle in <50ms and rolls back one side with SQLState 40001 / error 1213.
 * PostgreSQL and H2 are unaffected (single-row index seek, no gap locks).
 *
 * HOW TO RUN
 * ----------
 *   cd dpdp-integration-test-suite
 *   npx playwright test tests/09-event-notifications/09.12-deadlock-repro.spec.ts --reporter=line
 *
 * Expected on unpatched MySQL:        FAIL  (EN-5001 responses collected + printed)
 * Expected on PostgreSQL/H2:          PASS  (no gap locks → no deadlock)
 * Expected after the fix is applied:  PASS  on all databases
 *
 * TUNING
 * ------
 * Raise WORKERS, PUBLISH_ITERATIONS, or SUBSCRIBE_ITERATIONS if the deadlock doesn't
 * appear within the first run on a fast machine. Lower them to reduce runtime after the fix.
 */

import { test, expect } from '../../fixtures/auth.fixtures'
import type { APIResponse } from '@playwright/test'
import type { EventNotificationApiClient } from '../../clients/EventNotificationApiClient'
import { seedActiveTopicViaApi } from '../../utils/eventNotificationSetup'
import { uniqueMarker } from '../../utils/testData'

// ── tuneable constants ─────────────────────────────────────────────────────
/** POST /events calls per worker pair. */
const PUBLISH_ITERATIONS = 30

/** subscribe→delete cycles per worker pair. Each cycle is one FK_ST_TOPIC insertion. */
const SUBSCRIBE_ITERATIONS = 30

/**
 * Number of concurrent publish+subscribe worker *pairs*.
 * 3 pairs is enough to reproduce the deadlock within ~5s on a typical MySQL 8 instance.
 */
const WORKERS = 3
// ──────────────────────────────────────────────────────────────────────────

test.describe('09.12 — MySQL publish/subscribe deadlock reproduction', () => {
  test.use({ eventApiVersion: 'v1.1' })

  /**
   * Fires WORKERS pairs of goroutines simultaneously, all sharing one topic:
   *   • Publish loop A:   POST /events   ×  PUBLISH_ITERATIONS
   *   • Subscribe loop B: createSubscription → deleteSubscription  ×  SUBSCRIBE_ITERATIONS
   *
   * Any 500 with code "EN-5001" is the deadlock signal.
   * The test asserts zero such failures.
   */
  test(
    '09.12.01 - concurrent publish and subscribe must not produce EN-5001 (MySQL deadlock)',
    async ({ consentAdminEventApi }) => {
      const api: EventNotificationApiClient = consentAdminEventApi

      // One shared topic — all workers publish to it to maximise lock contention on the same org.
      const topic = await seedActiveTopicViaApi(api, 'deadlock-repro')

      // The server silently forces groupId = orgId regardless of what the caller sends.
      // Read it back from the topic record the same way every other spec does.
      const groupId: string | undefined = (topic as unknown as Record<string, string>).groupId

      const deadlockFailures: Array<{ attempt: number; worker: number; body: unknown }> = []
      let successfulSubscriptionCreates = 0

      // ── worker factory ─────────────────────────────────────────────────
      async function runWorkerPair(workerId: number): Promise<void> {
        /**
         * Publish loop — sequential requests so each sits in its own DB transaction.
         * We intentionally do NOT fire them in parallel inside the loop: we want
         * concurrent *transactions*, not concurrent requests within one transaction.
         */
        async function publishLoop(): Promise<void> {
          for (let i = 0; i < PUBLISH_ITERATIONS; i++) {
            const response: APIResponse = await api.publishEvent(groupId, {
              topic: topic.name,
              purposes: [],
              payload: { marker: uniqueMarker(`dl-w${workerId}-p${i}`) },
            })
            if (response.status() === 500) {
              let body: unknown
              try {
                body = await response.json()
              } catch {
                body = await response.text()
              }
              deadlockFailures.push({ attempt: i, worker: workerId, body })
            } else {
              expect(response.status(), await response.text()).toBe(201)
            }
            // Do NOT throw here for 500 — collect all failures so both loops run to completion.
          }
        }

        /**
         * Subscribe loop — each iteration creates a new poll subscription on the shared topic
         * then immediately deletes it. The create inserts into SUBSCRIPTION_TOPIC, which causes
         * InnoDB to take a shared lock on the TOPIC row via FK_ST_TOPIC. When a concurrent
         * publish holds the TOPIC range lock and is waiting for the SUBSCRIPTION lock, this
         * creates the classic AB-BA deadlock.
         */
        async function subscribeLoop(): Promise<void> {
          for (let i = 0; i < SUBSCRIBE_ITERATIONS; i++) {
            const createResp: APIResponse = await api.createSubscription({
              // Prefix with workerId + iteration so names are unique across concurrent workers
              // even when uniqueMarker's timestamp component has the same millisecond value.
              name: `w${workerId}-i${i}-${uniqueMarker('dl-sub')}`,
              topic: topic.name,
              filter: { type: 'specific', purposes: [uniqueMarker(`dl-p-w${workerId}-i${i}`)] },
              delivery: { mode: 'poll', sharedSecret: uniqueMarker('secret') },
            })

            if (createResp.status() !== 201) {
              const body = await createResp.text()
              if (createResp.status() === 409) {
                // EN-4090: expected noise. The server rejects a new subscription when the
                // previous iteration's delete hasn't committed yet (the create and the delete
                // race each other). This does NOT affect the publish loop and is intentional
                // — the subscribe loop keeps cycling to maximise FK_ST_TOPIC lock contention.
                console.warn(
                  `[worker ${workerId}] subscribe iteration ${i}: 409 EN-4090 (expected race noise — ` +
                  `previous subscription delete not yet committed; continuing)`,
                )
              } else {
                // Any other non-201 is unexpected and worth noting, but still not a test failure —
                // the test only fails if the *publish* loop sees EN-5001.
                console.warn(
                  `[worker ${workerId}] subscribe iteration ${i}: unexpected HTTP ${createResp.status()}: ${body}`,
                )
              }
              continue
            }

            successfulSubscriptionCreates++
            const sub = await createResp.json()
            // Delete immediately so we don't accumulate subscriptions that widen fan-out.
            const deleteResp: APIResponse = await api.deleteSubscription(sub.subscriptionId)
            if (deleteResp.status() !== 204 && deleteResp.status() !== 200 && deleteResp.status() !== 409) {
              // Delete failure (other than 409 in-flight/pending delivery conflict) is unexpected.
              console.warn(
                `[worker ${workerId}] subscribe iteration ${i}: delete returned unexpected HTTP ${deleteResp.status()}`,
              )
            }
          }
        }

        // Publish and subscribe run concurrently within each worker pair.
        await Promise.all([publishLoop(), subscribeLoop()])
      }

      // ── launch all worker pairs simultaneously ─────────────────────────
      await Promise.all(Array.from({ length: WORKERS }, (_, i) => runWorkerPair(i + 1)))

      // ── assert zero deadlock failures ──────────────────────────────────
      if (deadlockFailures.length > 0) {
        console.error(
          `\n❌  Deadlock CONFIRMED: ${deadlockFailures.length} POST /events request(s) returned 500` +
          ` during concurrent subscribe operations.\n` +
          `Root cause: MySQL InnoDB lock-order inversion (SQLState 40001 / error 1213).\n` +
          `Look for "Failed to publish event" in wso2carbon.log to see the underlying exception.\n` +
          `Failures:\n` +
          deadlockFailures
            .map(
              (f) =>
                `  worker=${f.worker} attempt=${f.attempt} body=${JSON.stringify(f.body)}`,
            )
            .join('\n'),
        )
      }

      expect(
        deadlockFailures.length,
        `Expected 0 deadlock failures but got ${deadlockFailures.length}. ` +
        `See console output above for details.\n` +
        `Root cause: getActiveTopicByOrgAndNameForUpdateQuery uses LOWER(NAME) which bypasses ` +
        `UQ_TOPIC_ORG_ACTIVE_NAME, causing a range scan + gap locks on all org topics. ` +
        `Fix: use ACTIVE_NAME = LOWER(?) in a MySQL dialect override.`,
      ).toBe(0)

      // ── assert subscription churn actually occurred ──────────────────
      const expectedMinCreates = Math.floor(WORKERS * SUBSCRIBE_ITERATIONS * 0.7)
      expect(
        successfulSubscriptionCreates,
        `Expected at least ${expectedMinCreates} successful subscription creates to exercise ` +
        `concurrent churn, but only got ${successfulSubscriptionCreates}.`,
      ).toBeGreaterThanOrEqual(expectedMinCreates)
    },
  )
})
