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
const { createCookieConsentService, StaleRevisionError, DecisionNotSavedError } = require('../src/cookieConsentService');
const { IsApiError } = require('../src/isClient');

const VERSIONS = { 'p-a': 'pv-a1', 'p-m': 'pv-m1' };
const CATALOG = {
  revision: 42,
  categories: [
    { key: 'analytics', title: 'Analytics cookies', purposeId: 'p-a', versionId: 'pv-a1', description: 'Counts visits.',
      elements: [{ id: 'e-a', name: '_demo_analytics', displayName: 'Demo analytics cookie', description: 'Counts page views.' }] },
    { key: 'marketing', title: 'Marketing cookies', purposeId: 'p-m', versionId: 'pv-m1', description: 'Offers.', elements: [] },
  ],
};

/** Behaves like IS for the calls the service makes. */
function fakeIs() {
  const is = { records: [] };
  let clock = 1000;
  let nextId = 1;
  is.lists = 0;
  is.listCookieConsents = async (subject, serviceId) => (is.lists++, is.records)
    .filter((r) => r.subjectId === subject && r.serviceId === serviceId)
    .map((r) => ({ ...r }));
  is.createConsent = async (body) => {
    const record = {
      id: `c${nextId++}`, subjectId: body.subjectId, serviceId: body.serviceId, state: body.state, timestamp: clock++,
      purposes: body.purposes.map((p) => ({ id: p.id, versionId: VERSIONS[p.id] })), properties: body.properties, body,
    };
    is.records.push(record);
    return { id: record.id };
  };
  is.revokeConsent = async (id) => {
    is.records.find((r) => r.id === id).state = 'REVOKED';
  };
  // As IS 7.3.0 does: an APPROVED authorization makes the consent ACTIVE again, keeping its
  // creation timestamp; properties are replaced as a whole.
  is.updates = [];
  is.updateConsent = async (id, body) => {
    is.updates.push({ id, body });
    const record = is.records.find((r) => r.id === id);
    record.authorizations = body.authorizations.map((a) => ({ userId: a.userId, state: a.state, updatedTime: clock++ }));
    if (body.authorizations.some((a) => a.state === 'APPROVED')) record.state = 'ACTIVE';
    record.expiryTime = body.expiryTime;
    record.properties = body.properties;
    return { id };
  };
  return is;
}

function setup() {
  const is = fakeIs();
  const errors = [];
  let visitors = 0;
  const service = createCookieConsentService({
    isClient: is, catalog: { get: async () => CATALOG }, serviceId: 'svc', siteName: 'Demo Insurance',
    texts: { bannerTitle: 'We use cookies' }, basisSecret: 'b'.repeat(32), now: () => 0, newVisitorId: () => `v-${++visitors}`,
    onError: (e) => errors.push(e),
  });
  return { is, service, errors };
}

const states = (is, subject) => is.records.filter((r) => r.subjectId === subject).map((r) => [r.purposes[0].id, r.state]);

const ACCEPT_ANALYTICS = { analytics: true, marketing: false };

test('a browser with no cookie gets the banner config and no decision', async () => {
  const { service } = setup();
  const state = await service.getState({ username: null, cookie: null });
  assert.equal(state.hasDecision, false);
  assert.deepEqual(state.categories, {});
  assert.equal(state.revision, 42);
  assert.deepEqual(state.config.texts, { bannerTitle: 'We use cookies' });
  assert.deepEqual(state.config.categories[0], {
    key: 'analytics', title: 'Analytics cookies', description: 'Counts visits.',
    cookies: [{ name: '_demo_analytics', description: 'Counts page views.' }],
  });
});

test('a first decision creates a visitor and one consent per category', async () => {
  const { is, service } = setup();
  const { cookie, state } = await service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  assert.deepEqual(cookie, { v: 'v-1', u: null });
  assert.deepEqual(is.records.map((r) => [r.subjectId, r.purposes[0].id, r.state]), [['v-1', 'p-a', 'ACTIVE'], ['v-1', 'p-m', 'REJECTED']]);
  assert.deepEqual(state.categories, ACCEPT_ANALYTICS);
  assert.equal(state.hasDecision, true);
});

test('consents are recorded with the SDK properties, language, expiry and all category cookies', async () => {
  const { is, service } = setup();
  await service.saveDecision({ username: null, cookie: null, categories: { analytics: true }, revision: 42 });
  assert.deepEqual(is.records[0].body, {
    subjectId: 'v-1', serviceId: 'svc', language: 'en',
    purposes: [{ id: 'p-a', elements: [{ id: 'e-a' }] }], state: 'ACTIVE', expiryTime: 365 * 86400000,
    properties: { consentType: 'cookie', category: 'analytics', siteName: 'Demo Insurance', subjectRef: 'v-1', decidedAt: '0' },
  });
});

test('rejects a decision made against an old revision without writing anything', async () => {
  const { is, service } = setup();
  await assert.rejects(service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 41 }), StaleRevisionError);
  assert.equal(is.records.length, 0);
});

test('a first decision that fails part way still names its visitor, and the retry completes it', async () => {
  const { is, service } = setup();
  const create = is.createConsent;
  is.createConsent = async (body) => { if (body.purposes[0].id === 'p-m') throw new Error('IS down'); return create(body); };
  let failure;
  await assert.rejects(
    service.saveDecision({ username: null, cookie: null, categories: { analytics: true, marketing: true }, revision: 42 }),
    (e) => { failure = e; return e instanceof DecisionNotSavedError && e.cause.message === 'IS down'; },
  );
  assert.deepEqual(failure.cookie, { v: 'v-1', u: null });
  assert.deepEqual(states(is, 'v-1'), [['p-a', 'ACTIVE']]);

  is.createConsent = create;
  await service.saveDecision({ username: null, cookie: failure.cookie, categories: { analytics: true, marketing: true }, revision: 42 });
  assert.deepEqual(states(is, 'v-1'), [['p-a', 'ACTIVE'], ['p-m', 'ACTIVE']]);
  assert.equal(is.records.filter((r) => r.subjectId !== 'v-1').length, 0);
});

test('saving the same decision twice creates nothing new', async () => {
  const { is, service } = setup();
  const first = await service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  await service.saveDecision({ username: null, cookie: first.cookie, categories: ACCEPT_ANALYTICS, revision: 42 });
  assert.equal(is.records.length, 2);
});

test('a first decision is not read back from Identity Server', async () => {
  const { is, service } = setup();
  await service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  assert.equal(is.lists, 0);
});

test('a change reads the current consents once and returns the new state without reading back', async () => {
  const { is, service } = setup();
  const first = await service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  const second = await service.saveDecision({ username: null, cookie: first.cookie, categories: { analytics: false }, revision: 42 });
  assert.equal(is.lists, 1);
  assert.deepEqual(second.state.categories, { analytics: false, marketing: false });
  assert.equal(second.state.hasDecision, true);
  assert.equal(second.state.revision, 42);
  assert.ok(second.state.config.categories.length === 2);
});

test('a decision on some categories leaves the others undecided', async () => {
  const { service } = setup();
  const { state } = await service.saveDecision({ username: null, cookie: null, categories: { analytics: true, unknown: true }, revision: 42 });
  assert.deepEqual(state.categories, { analytics: true });
  assert.equal(state.hasDecision, false);
});

test('the returned state matches what a fresh read of Identity Server gives', async () => {
  const { service } = setup();
  const first = await service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  const second = await service.saveDecision({ username: null, cookie: first.cookie, categories: { marketing: true }, revision: 42 });
  assert.deepEqual(second.state, await service.getState({ username: null, cookie: second.cookie }));
});

test('the basis changes with the decision or the subject, and nothing else', async () => {
  const { is, service } = setup();
  const empty = await service.getState({ username: null, cookie: null });
  assert.equal(typeof empty.basis, 'string');
  const saved = await service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  assert.notEqual(saved.state.basis, empty.basis);
  assert.equal((await service.getState({ username: null, cookie: saved.cookie })).basis, saved.state.basis);

  is.records.find((r) => r.purposes[0].id === 'p-a').state = 'REVOKED';
  assert.notEqual((await service.getState({ username: null, cookie: saved.cookie })).basis, saved.state.basis);

  const bob = await service.saveDecision({ username: 'bob', cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  assert.deepEqual(bob.state.categories, saved.state.categories);
  assert.notEqual(bob.state.basis, saved.state.basis);
  assert.ok(!bob.state.basis.includes('bob'));
});

test('changing a choice back and forth keeps one consent per category', async () => {
  const { is, service } = setup();
  let { cookie } = await service.saveDecision({ username: null, cookie: null, categories: { analytics: true, marketing: false }, revision: 42 });
  for (const analytics of [false, true, false, true]) {
    ({ cookie } = await service.saveDecision({ username: null, cookie, categories: { analytics, marketing: !analytics }, revision: 42 }));
  }
  assert.deepEqual(states(is, 'v-1'), [['p-a', 'ACTIVE'], ['p-m', 'REVOKED']]);
  assert.equal(is.records.length, 2);
  assert.deepEqual((await service.getState({ username: null, cookie })).categories, { analytics: true, marketing: false });
});

test('a reactivation approves as the subject, refreshes the expiry and resends every property', async () => {
  const { is, service } = setup();
  const { cookie } = await service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  await service.saveDecision({ username: null, cookie, categories: { analytics: false }, revision: 42 });
  await service.saveDecision({ username: null, cookie, categories: { analytics: true }, revision: 42 });
  assert.deepEqual(is.updates, [{
    id: 'c1',
    body: {
      authorizations: [{ userId: 'v-1', type: 'USER', state: 'APPROVED' }],
      expiryTime: 365 * 86400000,
      properties: { consentType: 'cookie', category: 'analytics', siteName: 'Demo Insurance', subjectRef: 'v-1', decidedAt: '0' },
    },
  }]);
});

test('the state returned after a reactivation matches a fresh read', async () => {
  const { service } = setup();
  const { cookie } = await service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  await service.saveDecision({ username: null, cookie, categories: { analytics: false }, revision: 42 });
  const saved = await service.saveDecision({ username: null, cookie, categories: { analytics: true }, revision: 42 });
  assert.deepEqual(saved.state, await service.getState({ username: null, cookie }));
});

test('a consent IS refuses to reactivate is replaced by a new one, but an outage is not', async () => {
  const { is, service, errors } = setup();
  const { cookie } = await service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  await service.saveDecision({ username: null, cookie, categories: { analytics: false }, revision: 42 });
  is.updateConsent = async () => { throw new IsApiError('PATCH /consents/c1 failed with HTTP 400', 400); };
  await service.saveDecision({ username: null, cookie, categories: { analytics: true }, revision: 42 });
  assert.deepEqual(states(is, 'v-1'), [['p-a', 'REVOKED'], ['p-m', 'REJECTED'], ['p-a', 'ACTIVE']]);
  assert.equal(errors.length, 1);

  await service.saveDecision({ username: null, cookie, categories: { analytics: false }, revision: 42 });
  is.updateConsent = async () => { throw new IsApiError('PATCH /consents/c3 failed with HTTP 503', 503); };
  await assert.rejects(service.saveDecision({ username: null, cookie, categories: { analytics: true }, revision: 42 }),
    (e) => e instanceof DecisionNotSavedError && e.cause.status === 503);
  assert.equal(is.records.length, 3);
});

test('withdrawing analytics revokes its consent', async () => {
  const { is, service } = setup();
  const first = await service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  const second = await service.saveDecision({ username: null, cookie: first.cookie, categories: { analytics: false, marketing: false }, revision: 42 });
  assert.equal(is.records.length, 2);
  assert.equal(is.records[0].state, 'REVOKED');
  assert.deepEqual(second.state.categories, { analytics: false, marketing: false });
});

test('a returning visitor gets their stored decision', async () => {
  const { service } = setup();
  const { cookie } = await service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  const state = await service.getState({ username: null, cookie });
  assert.equal(state.hasDecision, true);
  assert.deepEqual(state.categories, ACCEPT_ANALYTICS);
});

test('a logged-in user decides for themself and the cookie is linked to them', async () => {
  const { is, service } = setup();
  const { cookie } = await service.saveDecision({ username: 'alice', cookie: { v: 'v-9', u: null }, categories: ACCEPT_ANALYTICS, revision: 42 });
  assert.deepEqual(cookie, { v: 'v-9', u: 'alice' });
  assert.ok(is.records.every((r) => r.subjectId === 'alice'));
});

test('a logged-out browser linked to a user reads that user\'s decision', async () => {
  const { service } = setup();
  await service.saveDecision({ username: 'alice', cookie: { v: 'v-9', u: null }, categories: ACCEPT_ANALYTICS, revision: 42 });
  const state = await service.getState({ username: null, cookie: { v: 'v-9', u: 'alice' } });
  assert.deepEqual(state.categories, ACCEPT_ANALYTICS);
});

test('a logged-out change on a linked browser starts a new visitor and leaves the user untouched', async () => {
  const { is, service } = setup();
  await service.saveDecision({ username: 'alice', cookie: { v: 'v-9', u: null }, categories: ACCEPT_ANALYTICS, revision: 42 });
  const { cookie } = await service.saveDecision({ username: null, cookie: { v: 'v-9', u: 'alice' }, categories: { analytics: false, marketing: false }, revision: 42 });
  assert.deepEqual(cookie, { v: 'v-1', u: null });
  assert.deepEqual(is.records.filter((r) => r.subjectId === 'alice').map((r) => r.state), ['ACTIVE', 'REJECTED']);
  assert.deepEqual(is.records.filter((r) => r.subjectId === 'v-1').map((r) => r.state), ['REJECTED', 'REJECTED']);
});

test('linkOnLogin copies the visitor decision to a user who has none', async () => {
  const { is, service } = setup();
  const { cookie } = await service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  const linked = await service.linkOnLogin({ username: 'alice', cookie });
  assert.deepEqual(linked, { v: 'v-1', u: 'alice' });
  assert.deepEqual(states(is, 'alice'), [['p-a', 'ACTIVE'], ['p-m', 'REJECTED']]);
});

test('linkOnLogin records which visitor consent each copied decision came from', async () => {
  const { is, service } = setup();
  const { cookie } = await service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  const [visitorAnalytics, visitorMarketing] = is.records.map((r) => r.id);
  await service.linkOnLogin({ username: 'alice', cookie });
  const copied = is.records.filter((r) => r.subjectId === 'alice').map((r) => r.properties);
  assert.equal(copied[0].linkedFrom, visitorAnalytics);
  assert.equal(copied[1].linkedFrom, visitorMarketing);
  assert.equal(copied[0].subjectRef, 'alice');
});

test('linkOnLogin revokes the active visitor consents and keeps the refusals', async () => {
  const { is, service } = setup();
  const { cookie } = await service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  await service.linkOnLogin({ username: 'alice', cookie });
  assert.deepEqual(states(is, 'v-1'), [['p-a', 'REVOKED'], ['p-m', 'REJECTED']]);
});

test('linkOnLogin revokes a visitor consent even when the user kept a newer decision', async () => {
  const { is, service } = setup();
  const { cookie } = await service.saveDecision({ username: null, cookie: null, categories: { analytics: true, marketing: true }, revision: 42 });
  await service.saveDecision({ username: 'alice', cookie: { v: 'v-other', u: null }, categories: { analytics: true, marketing: false }, revision: 42 });
  await service.linkOnLogin({ username: 'alice', cookie });
  assert.deepEqual(states(is, 'v-1'), [['p-a', 'REVOKED'], ['p-m', 'REVOKED']]);
  assert.deepEqual(states(is, 'alice'), [['p-a', 'ACTIVE'], ['p-m', 'REJECTED']]);
});

test('linkOnLogin leaves the visitor consents active when copying fails', async () => {
  const { is, service } = setup();
  const { cookie } = await service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  is.createConsent = async () => { throw new Error('IS down'); };
  await assert.rejects(service.linkOnLogin({ username: 'alice', cookie }), /IS down/);
  assert.deepEqual(states(is, 'v-1'), [['p-a', 'ACTIVE'], ['p-m', 'REJECTED']]);
});

test('linkOnLogin still links the browser when a visitor revoke fails', async () => {
  const { is, service, errors } = setup();
  const { cookie } = await service.saveDecision({ username: null, cookie: null, categories: { analytics: true, marketing: true }, revision: 42 });
  const revoke = is.revokeConsent;
  is.revokeConsent = async (id) => { if (id === 'c1') throw new Error('revoke failed'); return revoke(id); };
  const linked = await service.linkOnLogin({ username: 'alice', cookie });
  assert.deepEqual(linked, { v: 'v-1', u: 'alice' });
  assert.deepEqual(states(is, 'v-1'), [['p-a', 'ACTIVE'], ['p-m', 'REVOKED']]);
  assert.deepEqual(errors.map((e) => e.message), ['revoke failed']);
});

test('a visitor revoke that failed at login is retried at the same user\'s next login', async () => {
  const { is, service } = setup();
  const { cookie } = await service.saveDecision({ username: null, cookie: null, categories: { analytics: true, marketing: true }, revision: 42 });
  const revoke = is.revokeConsent;
  is.revokeConsent = async (id) => { if (id === 'c1') throw new Error('revoke failed'); return revoke(id); };
  const linked = await service.linkOnLogin({ username: 'alice', cookie });
  is.revokeConsent = revoke;
  const userRecords = states(is, 'alice').length;
  assert.deepEqual(await service.linkOnLogin({ username: 'alice', cookie: linked }), { v: 'v-1', u: 'alice' });
  assert.deepEqual(states(is, 'v-1'), [['p-a', 'REVOKED'], ['p-m', 'REVOKED']]);
  assert.equal(states(is, 'alice').length, userRecords);
});

test('linkOnLogin reactivates the user\'s own earlier consent and names the visitor consent', async () => {
  const { is, service } = setup();
  await service.saveDecision({ username: 'alice', cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  await service.saveDecision({ username: 'alice', cookie: null, categories: { analytics: false }, revision: 42 });
  const visitor = await service.saveDecision({ username: null, cookie: null, categories: { analytics: true }, revision: 42 });
  await service.linkOnLogin({ username: 'alice', cookie: visitor.cookie });
  assert.deepEqual(states(is, 'alice'), [['p-a', 'ACTIVE'], ['p-m', 'REJECTED']]);
  const update = is.updates.find((u) => u.body.properties.subjectRef === 'alice');
  assert.equal(update.body.properties.linkedFrom, is.records.find((r) => r.subjectId === visitor.cookie.v).id);
  assert.deepEqual(states(is, visitor.cookie.v), [['p-a', 'REVOKED']]);
});

test("linkOnLogin keeps the user's newer decision", async () => {
  const { is, service } = setup();
  const { cookie } = await service.saveDecision({ username: null, cookie: null, categories: ACCEPT_ANALYTICS, revision: 42 });
  await service.saveDecision({ username: 'alice', cookie: { v: 'v-other', u: null }, categories: { analytics: false, marketing: true }, revision: 42 });
  await service.linkOnLogin({ username: 'alice', cookie });
  const state = await service.getState({ username: 'alice', cookie: null });
  assert.deepEqual(state.categories, { analytics: false, marketing: true });
  assert.equal(is.records.filter((r) => r.subjectId === 'alice').length, 2);
});

test('linkOnLogin does not copy decisions from a browser linked to another user', async () => {
  const { is, service } = setup();
  await service.saveDecision({ username: 'alice', cookie: { v: 'v-9', u: null }, categories: ACCEPT_ANALYTICS, revision: 42 });
  await service.saveDecision({ username: null, cookie: { v: 'v-9', u: null }, categories: ACCEPT_ANALYTICS, revision: 42 });
  const linked = await service.linkOnLogin({ username: 'bob', cookie: { v: 'v-9', u: 'alice' } });
  assert.deepEqual(linked, { v: 'v-9', u: 'bob' });
  assert.equal(is.records.filter((r) => r.subjectId === 'bob').length, 0);
  assert.deepEqual(states(is, 'v-9'), [['p-a', 'ACTIVE'], ['p-m', 'REJECTED']]);
});

test('linkOnLogin without a cookie starts a visitor linked to the user', async () => {
  const { is, service } = setup();
  assert.deepEqual(await service.linkOnLogin({ username: 'alice', cookie: null }), { v: 'v-1', u: 'alice' });
  assert.equal(is.records.length, 0);
});
