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
const {
  COOKIE_NAME, newVisitorId, seal, unseal, parseCookies, readSubjectCookie, writeSubjectCookie,
} = require('../src/subjectCookie');

const SECRET = 'a'.repeat(32);

test('newVisitorId returns a lowercase v- prefixed UUID', () => {
  const id = newVisitorId();
  assert.match(id, /^v-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
  assert.notEqual(newVisitorId(), id);
});

test('seal and unseal round-trip the visitor and username', () => {
  assert.deepEqual(unseal(SECRET, seal(SECRET, { v: 'v-1', u: 'alice@example.com' })), { v: 'v-1', u: 'alice@example.com' });
  assert.deepEqual(unseal(SECRET, seal(SECRET, { v: 'v-1', u: null })), { v: 'v-1', u: null });
});

test('the sealed value does not reveal the username', () => {
  const raw = Buffer.from(seal(SECRET, { v: 'v-1', u: 'alice@example.com' }), 'base64url').toString('latin1');
  assert.ok(!raw.includes('alice'));
});

test('unseal rejects a tampered value', () => {
  const chars = seal(SECRET, { v: 'v-1', u: null }).split('');
  chars[30] = chars[30] === 'A' ? 'B' : 'A';
  assert.equal(unseal(SECRET, chars.join('')), null);
});

test('unseal rejects a value sealed with another secret', () => {
  assert.equal(unseal('b'.repeat(32), seal(SECRET, { v: 'v-1', u: null })), null);
});

test('unseal rejects missing, garbage and non-visitor values', () => {
  assert.equal(unseal(SECRET, undefined), null);
  assert.equal(unseal(SECRET, ''), null);
  assert.equal(unseal(SECRET, 'not-a-cookie'), null);
  assert.equal(unseal(SECRET, seal(SECRET, { v: 'admin', u: null })), null);
});

test('parseCookies reads several cookies and decodes values', () => {
  assert.deepEqual(parseCookies('a=1; dpdp_cc=x%3Dy; b'), { a: '1', dpdp_cc: 'x=y' });
  assert.deepEqual(parseCookies(undefined), {});
});

test('writeSubjectCookie sets an HttpOnly first-party cookie that readSubjectCookie reads back', () => {
  let written;
  const res = { cookie: (name, value, options) => { written = { name, value, options }; } };
  writeSubjectCookie(res, SECRET, { v: 'v-1', u: 'bob' }, { secure: true, maxAgeDays: 365 });
  assert.equal(written.name, COOKIE_NAME);
  assert.deepEqual(written.options, { httpOnly: true, sameSite: 'lax', secure: true, path: '/', maxAge: 365 * 86400000 });
  assert.deepEqual(readSubjectCookie({ headers: { cookie: `${COOKIE_NAME}=${written.value}` } }, SECRET), { v: 'v-1', u: 'bob' });
});
