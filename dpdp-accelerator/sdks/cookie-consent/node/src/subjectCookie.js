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

const crypto = require('crypto');

const COOKIE_NAME = 'dpdp_cc';
const VISITOR_PREFIX = 'v-';
const IV_BYTES = 12;
const TAG_BYTES = 16;
const DAY_MS = 24 * 60 * 60 * 1000;

function newVisitorId() {
  return VISITOR_PREFIX + crypto.randomUUID();
}

function keyFor(secret) {
  return crypto.createHash('sha256').update(secret, 'utf8').digest();
}

/** Encrypted, so the browser can neither read the username nor forge another visitor's ID. */
function seal(secret, value) {
  const iv = crypto.randomBytes(IV_BYTES);
  const cipher = crypto.createCipheriv('aes-256-gcm', keyFor(secret), iv);
  const plain = JSON.stringify({ v: value.v, u: value.u || null });
  const body = Buffer.concat([cipher.update(plain, 'utf8'), cipher.final()]);
  return Buffer.concat([iv, cipher.getAuthTag(), body]).toString('base64url');
}

/** {v, u}, or null for anything missing, tampered with, or sealed with another secret. */
function unseal(secret, token) {
  if (typeof token !== 'string' || token === '') return null;
  try {
    const raw = Buffer.from(token, 'base64url');
    if (raw.length <= IV_BYTES + TAG_BYTES) return null;
    const decipher = crypto.createDecipheriv('aes-256-gcm', keyFor(secret), raw.subarray(0, IV_BYTES));
    decipher.setAuthTag(raw.subarray(IV_BYTES, IV_BYTES + TAG_BYTES));
    const plain = Buffer.concat([decipher.update(raw.subarray(IV_BYTES + TAG_BYTES)), decipher.final()]).toString('utf8');
    const value = JSON.parse(plain);
    if (!value || typeof value.v !== 'string' || !value.v.startsWith(VISITOR_PREFIX)) return null;
    return { v: value.v, u: typeof value.u === 'string' && value.u !== '' ? value.u : null };
  } catch (e) {
    return null;
  }
}

function parseCookies(header) {
  const cookies = {};
  if (typeof header !== 'string') return cookies;
  for (const part of header.split(';')) {
    const index = part.indexOf('=');
    if (index < 1) continue;
    const name = part.slice(0, index).trim();
    let value = part.slice(index + 1).trim();
    try {
      value = decodeURIComponent(value);
    } catch (e) {
      // Keep the raw value; unseal rejects it if it is not ours.
    }
    if (!(name in cookies)) cookies[name] = value;
  }
  return cookies;
}

function readSubjectCookie(req, secret) {
  return unseal(secret, parseCookies(req.headers.cookie)[COOKIE_NAME]);
}

function writeSubjectCookie(res, secret, value, { secure, maxAgeDays }) {
  res.cookie(COOKIE_NAME, seal(secret, value), {
    httpOnly: true,
    sameSite: 'lax',
    secure,
    path: '/',
    maxAge: maxAgeDays * DAY_MS,
  });
}

module.exports = {
  COOKIE_NAME, VISITOR_PREFIX, newVisitorId, seal, unseal, parseCookies, readSubjectCookie, writeSubjectCookie,
};
