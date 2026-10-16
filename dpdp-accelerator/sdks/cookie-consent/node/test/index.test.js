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
const { createCookieConsent } = require('..');
const { seal, COOKIE_NAME } = require('../src/subjectCookie');

const SECRET = 'k'.repeat(32);

function options(overrides = {}) {
  return {
    isBaseUrl: 'https://is.test/', clientId: 'cid', clientSecret: 'sec', serviceId: 'cid', siteName: 'Demo',
    cookieSecret: SECRET, categories: [{ key: 'analytics', purposeName: 'cookie_analytics', title: 'Analytics' }],
    getUsername: () => null, ...overrides,
  };
}

test('rejects missing settings, a short secret and bad categories', () => {
  assert.throws(() => createCookieConsent(options({ clientId: '' })), /"clientId" is required/);
  assert.throws(() => createCookieConsent(options({ cookieSecret: 'short' })), /at least 32/);
  assert.throws(() => createCookieConsent(options({ getUsername: undefined })), /getUsername/);
  assert.throws(() => createCookieConsent(options({ categories: [] })), /at least one/);
  assert.throws(() => createCookieConsent(options({ categories: [{ key: 'necessary', purposeName: 'x', title: 'x' }] })), /not "necessary"/);
});

test('returns a router', () => {
  assert.equal(typeof createCookieConsent(options()).router(), 'function');
});

test('calls Identity Server without the trailing slashes of isBaseUrl', async () => {
  const urls = [];
  const fetch = async (url) => { urls.push(url); return { ok: false, status: 503, json: async () => ({}), text: async () => '' }; };
  const cc = createCookieConsent(options({ isBaseUrl: 'https://is.test///', fetch, onError: () => {} }));
  await cc.linkOnLogin({ headers: { cookie: `${COOKIE_NAME}=${seal(SECRET, { v: 'v-1', u: null })}` } }, { cookie: () => {} }, 'alice');
  assert.equal(urls[0], 'https://is.test/oauth2/token');
});

test('linkOnLogin never throws and reports IS failures', async () => {
  const errors = [];
  const cc = createCookieConsent(options({ fetch: async () => { throw new Error('IS down'); }, onError: (e) => errors.push(e) }));
  let cookieSet = false;
  const req = { headers: { cookie: `${COOKIE_NAME}=${seal(SECRET, { v: 'v-1', u: null })}` } };
  await cc.linkOnLogin(req, { cookie: () => { cookieSet = true; } }, 'alice');
  assert.equal(errors[0].message, 'Could not reach Identity Server for a cookie consent token');
  assert.equal(cookieSet, false);
});

test('linkOnLogin without a visitor cookie links a new visitor without calling IS', async () => {
  const cc = createCookieConsent(options({ fetch: async () => assert.fail('no IS call expected') }));
  let written;
  await cc.linkOnLogin({ headers: {} }, { cookie: (name, value, opts) => { written = { name, opts }; } }, 'alice');
  assert.equal(written.name, COOKIE_NAME);
  assert.equal(written.opts.secure, true);
});
