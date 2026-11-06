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

'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { resolveState, planChanges, decisionsToLink, decisionConsentIds } = require('../src/consentState');

const CATALOG = {
  revision: 7,
  categories: [
    { key: 'analytics', purposeId: 'p-a', versionId: 'pv-a2' },
    { key: 'marketing', purposeId: 'p-m', versionId: 'pv-m1' },
  ],
};

function rec(id, purposeId, versionId, state, timestamp) {
  return { id, state, timestamp, purposes: [{ id: purposeId, versionId }] };
}

test('no records means no decision', () => {
  assert.deepEqual(resolveState([], CATALOG), { revision: 7, hasDecision: false, categories: {} });
});

test('the latest record of the current version decides each category', () => {
  const records = [
    rec('c1', 'p-a', 'pv-a2', 'REJECTED', 1),
    rec('c2', 'p-a', 'pv-a2', 'ACTIVE', 2),
    rec('c3', 'p-m', 'pv-m1', 'REVOKED', 3),
  ];
  assert.deepEqual(resolveState(records, CATALOG), { revision: 7, hasDecision: true, categories: { analytics: true, marketing: false } });
});

test('a record for an older purpose version or an expired one is not a decision', () => {
  const records = [rec('c1', 'p-a', 'pv-a1', 'ACTIVE', 5), rec('c2', 'p-m', 'pv-m1', 'EXPIRED', 6)];
  assert.deepEqual(resolveState(records, CATALOG), { revision: 7, hasDecision: false, categories: {} });
});

test('a partial decision keeps the known category but still asks', () => {
  const state = resolveState([rec('c1', 'p-a', 'pv-a2', 'ACTIVE', 1)], CATALOG);
  assert.equal(state.hasDecision, false);
  assert.deepEqual(state.categories, { analytics: true });
});

test('plans one consent per category for a first decision', () => {
  assert.deepEqual(planChanges([], CATALOG, { analytics: true, marketing: false }), [
    { op: 'create', category: 'analytics', state: 'ACTIVE' },
    { op: 'create', category: 'marketing', state: 'REJECTED' },
  ]);
});

test('plans nothing when the decision is unchanged', () => {
  const records = [rec('c1', 'p-a', 'pv-a2', 'ACTIVE', 1), rec('c2', 'p-m', 'pv-m1', 'REJECTED', 2)];
  assert.deepEqual(planChanges(records, CATALOG, { analytics: true, marketing: false }), []);
});

test('withdrawing an accepted category is a revoke only', () => {
  const records = [rec('c1', 'p-a', 'pv-a2', 'ACTIVE', 1)];
  assert.deepEqual(planChanges(records, CATALOG, { analytics: false }), [{ op: 'revoke', category: 'analytics', consentId: 'c1' }]);
});

test('accepting a rejected, revoked or expired category reactivates its consent', () => {
  for (const state of ['REJECTED', 'REVOKED', 'EXPIRED']) {
    const records = [rec('c2', 'p-m', 'pv-m1', state, 2)];
    assert.deepEqual(planChanges(records, CATALOG, { marketing: true }), [{ op: 'reactivate', category: 'marketing', consentId: 'c2' }], state);
  }
});

test('reactivates the latest consent of the current version, never one of an older version', () => {
  const records = [rec('c1', 'p-a', 'pv-a2', 'REVOKED', 1), rec('c2', 'p-a', 'pv-a2', 'REJECTED', 3), rec('c3', 'p-a', 'pv-a1', 'REVOKED', 5)];
  assert.deepEqual(planChanges(records, CATALOG, { analytics: true }), [{ op: 'reactivate', category: 'analytics', consentId: 'c2' }]);
  assert.deepEqual(planChanges([rec('c3', 'p-a', 'pv-a1', 'REVOKED', 5)], CATALOG, { analytics: true }), [
    { op: 'create', category: 'analytics', state: 'ACTIVE' },
  ]);
});

test('a reused consent is decided at its decidedAt or authorization time, not its creation', () => {
  const reused = { ...rec('c1', 'p-a', 'pv-a2', 'ACTIVE', 1), properties: { decidedAt: '50' } };
  const newer = rec('c2', 'p-a', 'pv-a2', 'REVOKED', 10);
  assert.deepEqual(resolveState([newer, reused], CATALOG).categories, { analytics: true });
  const approved = { ...rec('c3', 'p-a', 'pv-a2', 'ACTIVE', 1), authorizations: [{ userId: 'v-1', state: 'APPROVED', updatedTime: 60 }] };
  assert.deepEqual(resolveState([newer, approved], CATALOG).categories, { analytics: true });
  assert.deepEqual(decisionsToLink([approved], [newer], CATALOG), { analytics: true });
});

test('re-consenting to a new purpose version revokes the old ACTIVE consent first', () => {
  const records = [rec('c1', 'p-a', 'pv-a1', 'ACTIVE', 1)];
  assert.deepEqual(planChanges(records, CATALOG, { analytics: true }), [
    { op: 'revoke', category: 'analytics', consentId: 'c1' },
    { op: 'create', category: 'analytics', state: 'ACTIVE' },
  ]);
  assert.deepEqual(planChanges(records, CATALOG, { analytics: false }), [
    { op: 'revoke', category: 'analytics', consentId: 'c1' },
    { op: 'create', category: 'analytics', state: 'REJECTED' },
  ]);
});

test('ignores unknown categories and non-boolean values', () => {
  assert.deepEqual(planChanges([], CATALOG, { necessary: true, analytics: 'yes' }), []);
});

test('login copies visitor decisions the user has no newer decision for', () => {
  const visitor = [rec('v1', 'p-a', 'pv-a2', 'ACTIVE', 10), rec('v2', 'p-m', 'pv-m1', 'REJECTED', 11)];
  assert.deepEqual(decisionsToLink(visitor, [], CATALOG), { analytics: true, marketing: false });
  const user = [rec('u1', 'p-a', 'pv-a2', 'REJECTED', 20), rec('u2', 'p-m', 'pv-m1', 'ACTIVE', 5)];
  assert.deepEqual(decisionsToLink(visitor, user, CATALOG), { marketing: false });
});

test('decisionConsentIds names the consent holding each current decision', () => {
  const records = [rec('v1', 'p-a', 'pv-a2', 'REVOKED', 10), rec('v3', 'p-a', 'pv-a2', 'ACTIVE', 12), rec('v2', 'p-m', 'pv-m1', 'EXPIRED', 11)];
  assert.deepEqual(decisionConsentIds(records, CATALOG), { analytics: 'v3' });
});

test('login copies nothing when the visitor has not decided', () => {
  assert.deepEqual(decisionsToLink([], [rec('u1', 'p-a', 'pv-a2', 'ACTIVE', 1)], CATALOG), {});
});
