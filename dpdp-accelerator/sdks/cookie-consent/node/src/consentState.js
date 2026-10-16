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

const DECIDED_STATES = new Set(['ACTIVE', 'REJECTED', 'REVOKED']);
// States a consent can be brought back to ACTIVE from with PATCH, instead of creating a new one.
const REUSABLE_STATES = new Set(['REJECTED', 'REVOKED', 'EXPIRED']);

/**
 * When the record last changed. A reused consent keeps the timestamp it was created with, so the
 * later decision time comes from its decidedAt property or its authorization's updatedTime.
 */
function decisionTime(record) {
  const times = [record.timestamp, Number(record.properties && record.properties.decidedAt)];
  for (const authorization of record.authorizations || []) times.push(authorization.updatedTime);
  return Math.max(...times.filter((t) => Number.isFinite(t)), 0);
}

function recordsForCategory(records, category) {
  return records
    .filter((r) => (r.purposes || []).some((p) => p.id === category.purposeId))
    .sort((a, b) => decisionTime(b) - decisionTime(a));
}

function isCurrentVersion(record, category) {
  return (record.purposes || []).some((p) => p.id === category.purposeId && p.versionId === category.versionId);
}

/** {accepted, timestamp, consentId} for the category's current purpose version, or null if undecided. */
function currentDecision(records, category) {
  const latest = recordsForCategory(records, category)[0];
  if (!latest || !DECIDED_STATES.has(latest.state) || !isCurrentVersion(latest, category)) return null;
  return { accepted: latest.state === 'ACTIVE', timestamp: decisionTime(latest), consentId: latest.id };
}

function resolveState(records, catalog) {
  const categories = {};
  let hasDecision = true;
  for (const category of catalog.categories) {
    const decision = currentDecision(records, category);
    if (decision) categories[category.key] = decision.accepted;
    else hasDecision = false;
  }
  return { revision: catalog.revision, hasDecision, categories };
}

/** The state once planChanges(records, catalog, desired) has been carried out. */
function resolveAfter(records, catalog, desired) {
  const state = resolveState(records, catalog);
  for (const category of catalog.categories) {
    if (typeof desired[category.key] === 'boolean') state.categories[category.key] = desired[category.key];
  }
  state.hasDecision = catalog.categories.every((c) => typeof state.categories[c.key] === 'boolean');
  return state;
}

/**
 * Declining an accepted category is a revoke; declining an undecided one is a REJECTED consent,
 * so a refusal is on record too. Accepting reactivates the category's latest consent for the
 * current purpose version when there is one, so a subject keeps one consent per category and
 * purpose version however often they change their mind; only a new purpose version, which PATCH
 * cannot set, needs a new consent. Every ACTIVE consent of a changed category is revoked, older
 * purpose versions included, so a subject never holds two live consents for one category.
 */
function planChanges(records, catalog, desired) {
  const ops = [];
  for (const category of catalog.categories) {
    const want = desired[category.key];
    if (typeof want !== 'boolean') continue;
    const decision = currentDecision(records, category);
    if (decision && decision.accepted === want) continue;
    const active = recordsForCategory(records, category).filter((r) => r.state === 'ACTIVE');
    for (const record of active) ops.push({ op: 'revoke', category: category.key, consentId: record.id });
    if (want) {
      const reusable = recordsForCategory(records, category)
        .find((r) => REUSABLE_STATES.has(r.state) && isCurrentVersion(r, category));
      if (reusable) ops.push({ op: 'reactivate', category: category.key, consentId: reusable.id });
      else ops.push({ op: 'create', category: category.key, state: 'ACTIVE' });
    } else if (!active.some((r) => isCurrentVersion(r, category))) {
      ops.push({ op: 'create', category: category.key, state: 'REJECTED' });
    }
  }
  return ops;
}

function decisionsToLink(visitorRecords, userRecords, catalog) {
  const desired = {};
  for (const category of catalog.categories) {
    const visitor = currentDecision(visitorRecords, category);
    if (!visitor) continue;
    const user = currentDecision(userRecords, category);
    if (!user || visitor.timestamp > user.timestamp) desired[category.key] = visitor.accepted;
  }
  return desired;
}

/** Category key -> ID of the consent holding the current decision, for categories that have one. */
function decisionConsentIds(records, catalog) {
  const ids = {};
  for (const category of catalog.categories) {
    const decision = currentDecision(records, category);
    if (decision) ids[category.key] = decision.consentId;
  }
  return ids;
}

module.exports = { resolveState, resolveAfter, planChanges, decisionsToLink, decisionConsentIds };
