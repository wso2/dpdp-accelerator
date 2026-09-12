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

import type { ComplaintApiClient, ComplaintCategory, ComplaintStatus } from '../clients/ComplaintApiClient'
import { uniqueMarker } from './testData'

export interface SeededComplaint {
  id: string
  referenceId: string
  description: string
}

/**
 * Same rationale as utils/consentSetup.ts's seedConsent: the UI tests in tests/07-complaints care
 * about the detail/reply/attachment/queue *pages*, not about re-proving the create form works on
 * every single test (that's 07.01's own job) - so most of them seed a complaint straight through
 * the real REST API via ComplaintApiClient instead of driving
 * ComplaintSubmitDialog every time. Unlike seedConsent, there's no cleanup tracker: complaints have
 * no delete-by-id endpoint at all (see AGENTS.md).
 */
export async function seedComplaint(
  api: ComplaintApiClient,
  category: ComplaintCategory = 'OTHER',
  labelSuffix = 'ui-seed',
): Promise<SeededComplaint> {
  const description = `Automated regression test: ${uniqueMarker(labelSuffix)}`
  const response = await api.createMyComplaint({ subjectCategory: category, description })
  if (response.status() !== 201) {
    throw new Error(
      `Failed to seed a complaint via the API (status ${String(response.status())}): ${await response.text()}`,
    )
  }
  const body = (await response.json()) as { id: string; referenceId: string }
  return { id: body.id, referenceId: body.referenceId, description }
}

/**
 * Moves a freshly-seeded (OPEN) complaint to `toStatus` via the officer status-only endpoint, as
 * test setup - not itself the thing under test in whichever spec file calls this.
 *
 * OPEN -> AWAITING_INTERNAL_REVIEW is not a direct transition (StatusTransitionValidator.java
 * requires an officer to triage a complaint into WAITING_ON_CLIENT first), so that target hops
 * through WAITING_ON_CLIENT automatically rather than making every caller know the intermediate
 * step.
 *
 * Always sends a `note`, even though ComplaintEventServiceImpl only requires one when
 * toStatus=RESOLVED: with no note, `note` is stored verbatim as the resulting timeline event's
 * `message` (see the constructor call right before complaintDAO.updateStatus in that file), so an
 * omitted note seeds a timeline entry with message=null. ComplaintActivityFeed.tsx's
 * isPlainLogEntry unconditionally calls `entry.message.trim()` on every statusChange/resolution
 * entry with no null guard, which throws and blanks the whole activity feed for any complaint
 * whose timeline contains one - i.e. every complaint this helper seeds, before this fix. A default
 * note here keeps every status-only transition this suite creates well-formed for the app as it
 * actually behaves today, without altering accelerator/frontend source.
 */
export async function moveComplaintToStatus(
  officerApi: ComplaintApiClient,
  complaintId: string,
  toStatus: ComplaintStatus,
  note: string = `Automated test setup: transitioned to ${toStatus}.`,
): Promise<void> {
  if (toStatus === 'AWAITING_INTERNAL_REVIEW') {
    await moveComplaintToStatus(officerApi, complaintId, 'WAITING_ON_CLIENT')
  }
  const response = await officerApi.updateStatus(complaintId, { toStatus, note })
  if (!response.ok()) {
    throw new Error(
      `Failed to move complaint ${complaintId} to ${toStatus} as test setup (status ${String(response.status())}): ${await response.text()}`,
    )
  }
}
