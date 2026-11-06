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
const { createIsClient, IsApiError } = require('../src/isClient');

function response(status, body) {
  const text = body === undefined ? '' : JSON.stringify(body);
  return { ok: status >= 200 && status < 300, status, text: async () => text, json: async () => JSON.parse(text) };
}

function client(handler, extra = {}) {
  const calls = [];
  const logged = [];
  const fetch = async (url, options) => {
    calls.push({ url, options });
    if (url.endsWith('/oauth2/token')) return response(200, { access_token: 'tok-1', expires_in: 3600 });
    return handler(url, options);
  };
  const c = createIsClient({
    baseUrl: 'https://is.test', clientId: 'cid', clientSecret: 'sec', fetch, onApiCall: (e) => logged.push(e), ...extra,
  });
  return { c, calls, logged };
}

test('gets a client_credentials token once and reuses it', async () => {
  const { c, calls } = client(async () => response(200, { Consents: [] }));
  await c.listCookieConsents('v-1', 'svc');
  await c.listCookieConsents('v-1', 'svc');
  const tokenCalls = calls.filter((x) => x.url.endsWith('/oauth2/token'));
  assert.equal(tokenCalls.length, 1);
  assert.equal(tokenCalls[0].options.headers.Authorization, `Basic ${Buffer.from('cid:sec').toString('base64')}`);
  const form = new URLSearchParams(tokenCalls[0].options.body);
  assert.equal(form.get('grant_type'), 'client_credentials');
  assert.ok(form.get('scope').split(' ').includes('internal_consent_mgt_consent_update'));
});

test('refreshes the token shortly before it expires', async () => {
  let clock = 0;
  const { c, calls } = client(async () => response(200, { Consents: [] }), { now: () => clock });
  await c.listCookieConsents('v-1', 'svc');
  clock = 3600 * 1000 - 10 * 1000;
  await c.listCookieConsents('v-1', 'svc');
  assert.equal(calls.filter((x) => x.url.endsWith('/oauth2/token')).length, 2);
});

test('fails with IsApiError when the token request is rejected', async () => {
  const fetch = async () => response(401, { error: 'invalid_client' });
  const c = createIsClient({ baseUrl: 'https://is.test', clientId: 'cid', clientSecret: 'bad', fetch });
  await assert.rejects(c.listCookieConsents('v-1', 'svc'), (e) => e instanceof IsApiError && e.status === 401);
});

test("lists a subject's cookie consents by userId", async () => {
  const { c, calls } = client(async () => response(200, { Consents: [{ id: 'c1' }] }));
  assert.deepEqual(await c.listCookieConsents('v-1', 'svc'), [{ id: 'c1' }]);
  const url = new URL(calls[1].url);
  assert.equal(url.pathname, '/api/identity/consent-mgt/v2.0/consents');
  assert.equal(url.searchParams.get('userId'), 'v-1');
  assert.equal(url.searchParams.get('relation'), 'SUBJECT');
  assert.equal(url.searchParams.get('serviceId'), 'svc');
  assert.equal(url.searchParams.get('filter'), 'properties.consentType eq cookie');
  assert.equal(url.searchParams.get('attributes'), 'purposes,properties,authorizations');
  assert.equal(calls[1].options.headers.Authorization, 'Bearer tok-1');
});

test('lists by the subjectRef property when configured to', async () => {
  const { c, calls } = client(async () => response(200, { Consents: [] }), { subjectQuery: 'property' });
  await c.listCookieConsents('v-1', 'svc');
  const url = new URL(calls[1].url);
  assert.equal(url.searchParams.get('userId'), null);
  assert.equal(url.searchParams.get('filter'), 'properties.consentType eq cookie and properties.subjectRef eq v-1');
});

test('follows the next cursor until the last page, keeping the query', async () => {
  const pages = {
    '': { Consents: [{ id: 'c1' }], links: [{ rel: 'next', href: 'https://is.internal:9443/api/identity/consent-mgt/v2.0/consents?limit=100&after=MQ==' }] },
    'MQ==': { Consents: [{ id: 'c2' }], links: [{ rel: 'previous', href: 'x' }, { rel: 'next', href: '/consents?after=Mg==' }] },
    'Mg==': { Consents: [{ id: 'c3' }], links: [{ rel: 'previous', href: 'x' }] },
  };
  const { c, calls } = client(async (url) => response(200, pages[new URL(url).searchParams.get('after') || '']));
  assert.deepEqual(await c.listCookieConsents('v-1', 'svc'), [{ id: 'c1' }, { id: 'c2' }, { id: 'c3' }]);
  const urls = calls.slice(1).map((x) => new URL(x.url));
  assert.equal(urls.length, 3);
  for (const url of urls) {
    assert.equal(url.origin, 'https://is.test');
    assert.equal(url.searchParams.get('userId'), 'v-1');
    assert.equal(url.searchParams.get('filter'), 'properties.consentType eq cookie');
  }
  assert.deepEqual(urls.map((u) => u.searchParams.get('after')), [null, 'MQ==', 'Mg==']);
});

test('stops paging on an empty page or a repeated cursor', async () => {
  const looping = { Consents: [{ id: 'c1' }], links: [{ rel: 'next', href: '/consents?after=MQ==' }] };
  const { c, calls } = client(async () => response(200, looping));
  assert.deepEqual(await c.listCookieConsents('v-1', 'svc'), [{ id: 'c1' }, { id: 'c1' }]);
  assert.equal(calls.length, 3);
  const empty = client(async () => response(200, { Consents: [], links: [{ rel: 'next', href: '/consents?after=MQ==' }] }));
  assert.deepEqual(await empty.c.listCookieConsents('v-1', 'svc'), []);
  assert.equal(empty.calls.length, 2);
});

test('treats a list response without Consents as empty', async () => {
  const { c } = client(async () => response(200, {}));
  assert.deepEqual(await c.listCookieConsents('v-1', 'svc'), []);
});

test('finds a purpose by exact name and returns its detail', async () => {
  const { c, calls } = client(async (url) => (url.includes('/purposes?')
    ? response(200, { Purposes: [{ id: 'p0', name: 'cookie_analytics_old' }, { id: 'p1', name: 'cookie_analytics' }] })
    : response(200, { id: 'p1', name: 'cookie_analytics', type: 'Cookie' })));
  const purpose = await c.findPurposeByName('cookie_analytics');
  assert.equal(purpose.id, 'p1');
  assert.equal(new URL(calls[1].url).searchParams.get('filter'), 'name eq "cookie_analytics"');
  assert.ok(calls[2].url.endsWith('/purposes/p1'));
});

test('returns null when no purpose has that name', async () => {
  const { c } = client(async () => response(200, { Purposes: [] }));
  assert.equal(await c.findPurposeByName('missing'), null);
});

test('gets a purpose by id, or null when Identity Server no longer has it', async () => {
  const { c, calls } = client(async (url) => (url.endsWith('/purposes/p1')
    ? response(200, { id: 'p1', name: 'cookie_analytics', type: 'Cookie' })
    : response(404, { code: 'CM-60001' })));
  assert.equal((await c.getPurpose('p1')).name, 'cookie_analytics');
  assert.equal(calls.length, 2);
  assert.equal(await c.getPurpose('gone'), null);
});

test('getPurpose still throws on errors other than not found', async () => {
  const { c } = client(async () => response(500, {}));
  await assert.rejects(c.getPurpose('p1'), IsApiError);
});

test('creates and revokes consents and reports each call', async () => {
  const { c, calls, logged } = client(async (url, options) => (options.method === 'POST' && url.endsWith('/consents')
    ? response(201, { id: 'c9' })
    : response(200)));
  assert.equal((await c.createConsent({ subjectId: 'v-1' })).id, 'c9');
  await c.revokeConsent('c9');
  assert.deepEqual(JSON.parse(calls[1].options.body), { subjectId: 'v-1' });
  assert.equal(calls[1].options.headers['Content-Type'], 'application/json');
  assert.ok(calls[2].url.endsWith('/consents/c9/revoke'));
  assert.equal(calls[2].options.body, undefined);
  assert.deepEqual(logged.map((e) => [e.method, e.status]), [['POST', 201], ['POST', 200]]);
});

test('a failed call throws IsApiError whose message leaves out the query string', async () => {
  const { c } = client(async () => response(500, { code: 'CM_00084' }));
  await assert.rejects(c.listCookieConsents('alice@example.com', 'svc'), (e) => {
    assert.ok(e instanceof IsApiError);
    assert.equal(e.status, 500);
    assert.ok(!e.message.includes('alice'));
    return true;
  });
});

const URL_ERROR = () => new Error('request to https://is.test/api/identity/consent-mgt/v2.0/consents?userId=alice%40example.com failed');

test('a network failure on the token call does not leak the request URL', async () => {
  const fetch = async () => { throw URL_ERROR(); };
  const c = createIsClient({ baseUrl: 'https://is.test', clientId: 'cid', clientSecret: 'sec', fetch });
  await assert.rejects(c.listCookieConsents('alice@example.com', 'svc'),
    (e) => e instanceof IsApiError && e.status === 0 && !e.message.includes('alice'));
});

test('a network failure on a consent call does not leak the request URL', async () => {
  const { c } = client(async () => { throw URL_ERROR(); });
  await assert.rejects(c.listCookieConsents('alice@example.com', 'svc'),
    (e) => e instanceof IsApiError && e.status === 0 && !e.message.includes('alice') && !e.message.includes('userId'));
});
