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
const core = require('../loader-core');

// Every text the loader reads from state.config.texts; each SDK supplies its own defaults.
const TEXTS = {
  bannerTitle: 'We use cookies',
  bannerDescription: 'Optional cookies need your permission.',
  acceptAll: 'Accept all',
  rejectAll: 'Reject all',
  managePreferences: 'Manage preferences',
  preferencesTitle: 'Cookie preferences',
  savePreferences: 'Save preferences',
  close: 'Close',
  necessaryTitle: 'Strictly necessary cookies',
  necessaryDescription: 'Always on.',
  cookieName: 'Cookie',
  cookieDescription: 'Purpose',
};

const STATE = {
  revision: 42,
  basis: 'b-1',
  hasDecision: true,
  categories: { analytics: true, marketing: false },
  config: {
    texts: TEXTS,
    categories: [
      { key: 'analytics', title: 'Analytics cookies', description: 'Counts visits.', cookies: [{ name: '_demo_analytics', description: 'Counts page views.' }] },
      { key: 'marketing', title: 'Marketing cookies', description: 'Offers.', cookies: [] },
    ],
  },
};

test('floatingButtonPosition accepts the two bottom corners and nothing else', () => {
  assert.equal(core.floatingButtonPosition('bottom-left'), 'bottom-left');
  assert.equal(core.floatingButtonPosition(' Bottom-Right '), 'bottom-right');
  assert.equal(core.floatingButtonPosition(null), null);
  assert.equal(core.floatingButtonPosition(''), null);
  assert.equal(core.floatingButtonPosition('top-left'), null);
});

test('buildRunConfig makes an opt-in config that waits for the server before showing anything', () => {
  const config = core.buildRunConfig(STATE);
  assert.equal(config.mode, 'opt-in');
  assert.equal(config.autoShow, false);
  assert.equal(config.revision, 42);
  assert.deepEqual(config.cookie, { name: 'dpdp_cc_prefs', expiresAfterDays: 365 });
  assert.deepEqual(config.categories, {
    necessary: { enabled: true, readOnly: true },
    analytics: { autoClear: { cookies: [{ name: '_demo_analytics' }] } },
    marketing: { autoClear: { cookies: [] } },
  });
});

test('buildRunConfig puts the server texts and cookie table into the English translation', () => {
  const en = core.buildRunConfig(STATE).language.translations.en;
  assert.equal(en.consentModal.title, 'We use cookies');
  assert.equal(en.consentModal.acceptNecessaryBtn, 'Reject all');
  assert.deepEqual(en.preferencesModal.sections.map((s) => s.linkedCategory), ['necessary', 'analytics', 'marketing']);
  assert.deepEqual(en.preferencesModal.sections[1].cookieTable, {
    headers: { name: 'Cookie', desc: 'Purpose' },
    body: [{ name: '_demo_analytics', desc: 'Counts page views.' }],
  });
  assert.equal(en.preferencesModal.sections[2].cookieTable, undefined);
});

test('acceptedFromState lists accepted optional categories', () => {
  assert.deepEqual(core.acceptedFromState(STATE), ['analytics']);
});

test('decisionFromAccepted gives a boolean for every configured category', () => {
  assert.deepEqual(core.decisionFromAccepted(['necessary', 'marketing'], STATE), { analytics: false, marketing: true });
});

test('sameOptionalCategories ignores necessary and order', () => {
  assert.equal(core.sameOptionalCategories(['necessary', 'b', 'a'], ['a', 'b']), true);
  assert.equal(core.sameOptionalCategories(['necessary', 'a'], ['a', 'b']), false);
});

test('startAction retries a pending decision made on the current state even when the server has one', () => {
  const pending = { revision: 42, basis: 'b-1', categories: { analytics: false, marketing: true } };
  assert.deepEqual(core.startAction(STATE, pending), { type: 'retryPending', accepted: ['marketing'] });
});

test('startAction ignores a pending decision made for another revision', () => {
  const pending = { revision: 41, basis: 'b-1', categories: { analytics: false, marketing: true } };
  assert.deepEqual(core.startAction(STATE, pending), { type: 'applyServer', accepted: ['analytics'] });
});

test('startAction drops a pending decision once the state it was made on has changed', () => {
  // Its save went through, a withdrawal overtook it, or another account signed in.
  const pending = { revision: 42, basis: 'b-0', categories: { analytics: false, marketing: true } };
  assert.deepEqual(core.startAction(STATE, pending), { type: 'applyServer', accepted: ['analytics'] });
  assert.equal(core.pendingApplies(pending, STATE), false);
});

test('pendingApplies rejects a pending decision with no basis', () => {
  assert.equal(core.pendingApplies({ revision: 42, categories: {} }, STATE), false);
  assert.equal(core.pendingApplies({ revision: 42, categories: {} }, { ...STATE, basis: undefined }), false);
  assert.equal(core.pendingApplies(null, STATE), false);
  assert.equal(core.pendingApplies({ revision: 42, basis: 'b-1', categories: {} }, STATE), true);
});

test('startAction applies the server decision when nothing is pending', () => {
  assert.deepEqual(core.startAction(STATE, null), { type: 'applyServer', accepted: ['analytics'] });
});

test('startAction asks when nothing is pending and the server has no decision', () => {
  assert.deepEqual(core.startAction({ ...STATE, hasDecision: false }, null), { type: 'ask' });
  assert.equal(core.startAction.length, 2);
});

test('consentCookieDeletions expires host-only for a dotless hostname', () => {
  const out = core.consentCookieDeletions('dpdp_cc_prefs', 'localhost');
  assert.equal(out.length, 1);
  assert.ok(out[0].includes('dpdp_cc_prefs=;'));
  assert.ok(out[0].includes('Max-Age=0'));
  assert.ok(out[0].includes('path=/'));
  assert.ok(!out[0].includes('Domain='));
});

test('consentCookieDeletions also expires the Domain cookie when the hostname has a dot', () => {
  const out = core.consentCookieDeletions('dpdp_cc_prefs', 'www.example.com');
  assert.equal(out.length, 2);
  assert.ok(!out[0].includes('Domain='));
  assert.ok(out[1].includes('Domain=www.example.com'));
  out.forEach((s) => {
    assert.ok(s.includes('Max-Age=0'));
    assert.ok(s.includes('path=/'));
  });
});

test('storedConsent reads categories and revision from the library cookie among other cookies', () => {
  const value = encodeURIComponent(JSON.stringify({ categories: ['necessary', 'analytics'], revision: 42, consentId: 'x' }));
  const header = `a=1; dpdp_cc_prefs=${value}; _demo_analytics=zzz`;
  assert.deepEqual(core.storedConsent(header, 'dpdp_cc_prefs'), { categories: ['necessary', 'analytics'], revision: 42 });
});

test('storedConsent returns null for a missing, garbage or malformed cookie', () => {
  assert.equal(core.storedConsent('', 'dpdp_cc_prefs'), null);
  assert.equal(core.storedConsent('a=1', 'dpdp_cc_prefs'), null);
  assert.equal(core.storedConsent('dpdp_cc_prefs=%E0%A4%A', 'dpdp_cc_prefs'), null);
  assert.equal(core.storedConsent('dpdp_cc_prefs=notjson', 'dpdp_cc_prefs'), null);
  assert.equal(core.storedConsent('dpdp_cc_prefs=' + encodeURIComponent('{"revision":1}'), 'dpdp_cc_prefs'), null);
  assert.equal(core.storedConsent(undefined, 'dpdp_cc_prefs'), null);
});

test('needsReset is true unless the stored consent matches the target and revision', () => {
  const stored = { categories: ['necessary', 'analytics', 'marketing'], revision: 42 };
  assert.equal(core.needsReset(null, ['analytics'], 42), true);
  assert.equal(core.needsReset({ categories: ['necessary'], revision: 41 }, [], 42), true);
  assert.equal(core.needsReset(stored, ['analytics'], 42), true);
  assert.equal(core.needsReset(stored, ['marketing', 'analytics'], 42), false);
  assert.equal(core.needsReset({ categories: ['necessary'], revision: 42 }, [], 42), false);
});

test('declinedCookieNames lists cookies of categories outside the target', () => {
  const state = JSON.parse(JSON.stringify(STATE));
  state.config.categories[1].cookies = [{ name: '_demo_marketing', description: 'x' }];
  assert.deepEqual(core.declinedCookieNames(state, ['analytics']), ['_demo_marketing']);
  assert.deepEqual(core.declinedCookieNames(STATE, []), ['_demo_analytics']);
  assert.deepEqual(core.declinedCookieNames(state, ['analytics', 'marketing']), []);
});

test('declinedCookieNames with empty accepted array returns all catalog cookies', () => {
  const state = JSON.parse(JSON.stringify(STATE));
  state.config.categories[1].cookies = [{ name: '_demo_marketing', description: 'x' }];
  assert.deepEqual(core.declinedCookieNames(state, []), ['_demo_analytics', '_demo_marketing']);
});
