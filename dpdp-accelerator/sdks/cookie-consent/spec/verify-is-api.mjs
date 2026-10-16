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

// Checks, against a live IS, the consent-mgt v2 behaviour the cookie consent SDK relies on.
// Usage: IS_BASE_URL=https://localhost:9443 COOKIE_CONSENT_CLIENT_ID=... COOKIE_CONSENT_CLIENT_SECRET=... \
//        NODE_TLS_REJECT_UNAUTHORIZED=0 node spec/verify-is-api.mjs
import { randomUUID } from 'node:crypto';

const base = (process.env.IS_BASE_URL || 'https://localhost:9443').replace(/\/+$/, '');
const clientId = process.env.COOKIE_CONSENT_CLIENT_ID;
const clientSecret = process.env.COOKIE_CONSENT_CLIENT_SECRET;
if (!clientId || !clientSecret) {
  console.error('Set COOKIE_CONSENT_CLIENT_ID and COOKIE_CONSENT_CLIENT_SECRET');
  process.exit(2);
}

const SCOPES = [
  'internal_consent_mgt_purpose_view',
  'internal_consent_mgt_consent_create',
  'internal_consent_mgt_consent_view',
  'internal_consent_mgt_consent_update',
];
const CM = `${base}/api/identity/consent-mgt/v2.0`;
const results = [];

function check(name, ok, detail) {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${ok || !detail ? '' : `\n      ${detail}`}`);
}

async function api(token, method, path, body) {
  const headers = { Authorization: `Bearer ${token}`, Accept: 'application/json' };
  if (body) headers['Content-Type'] = 'application/json';
  const response = await fetch(`${CM}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const text = await response.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  return { status: response.status, data };
}

const tokenResponse = await fetch(`${base}/oauth2/token`, {
  method: 'POST',
  headers: {
    Authorization: `Basic ${Buffer.from(`${clientId}:${clientSecret}`).toString('base64')}`,
    'Content-Type': 'application/x-www-form-urlencoded',
  },
  body: new URLSearchParams({ grant_type: 'client_credentials', scope: SCOPES.join(' ') }),
});
const tokenData = await tokenResponse.json();
const granted = String(tokenData.scope || '').split(' ');
check('1. Token carries all four scopes', SCOPES.every((s) => granted.includes(s)), `granted: ${tokenData.scope}`);
const token = tokenData.access_token;

const purposeList = await api(token, 'GET', `/purposes?filter=${encodeURIComponent('name eq "cookie_analytics"')}`);
const summary = purposeList.data && purposeList.data.Purposes && purposeList.data.Purposes[0];
const purpose = summary ? (await api(token, 'GET', `/purposes/${summary.id}`)).data : null;
check(
  '2. cookie_analytics has type Cookie, a latest version and the _demo_analytics element',
  Boolean(purpose && purpose.type === 'Cookie' && purpose.latestVersion && purpose.latestVersion.id
    && (purpose.elements || []).some((e) => e.name === '_demo_analytics')),
  JSON.stringify(purpose),
);
if (!purpose) process.exit(1);

const subject = `v-${randomUUID()}`;
const serviceId = clientId;
const purposes = [{ id: purpose.id, elements: purpose.elements.map((e) => ({ id: e.id })) }];
const properties = { consentType: 'cookie', category: 'analytics', siteName: 'verify-is-api', subjectRef: subject };
const body = (state) => ({ subjectId: subject, serviceId, language: 'en', purposes, state, expiryTime: Date.now() + 86400000, properties });

const rejected = await api(token, 'POST', '/consents', body('REJECTED'));
check('3. Create a REJECTED consent for a subject that is not an IS user', rejected.status === 201, JSON.stringify(rejected));
const active = await api(token, 'POST', '/consents', body('ACTIVE'));
check('4. Create an ACTIVE consent for the same subject', active.status === 201, JSON.stringify(active));

const byUserQuery = new URLSearchParams({ userId: subject, relation: 'SUBJECT', serviceId, filter: 'properties.consentType eq cookie', attributes: 'purposes,properties', limit: '100' });
const byUser = await api(token, 'GET', `/consents?${byUserQuery}`);
const byUserIds = new Set(((byUser.data && byUser.data.Consents) || []).map((c) => c.id));
check('5. List by userId=<visitor id> returns exactly the two consents',
  byUser.status === 200 && byUserIds.size === 2 && byUserIds.has(rejected.data && rejected.data.id) && byUserIds.has(active.data && active.data.id),
  JSON.stringify(byUser));

const byPropertyQuery = new URLSearchParams({ serviceId, filter: `properties.consentType eq cookie and properties.subjectRef eq ${subject}`, attributes: 'purposes,properties', limit: '100' });
const byProperty = await api(token, 'GET', `/consents?${byPropertyQuery}`);
const byPropertyIds = new Set(((byProperty.data && byProperty.data.Consents) || []).map((c) => c.id));
check('6. List by properties.subjectRef returns exactly the two consents', byProperty.status === 200 && byPropertyIds.size === 2, JSON.stringify(byProperty));

const sample = ((byUser.data && byUser.data.Consents) || [])[0] || ((byProperty.data && byProperty.data.Consents) || [])[0];
check('7. Summaries carry a ms timestamp, purposes[].versionId and properties',
  Boolean(sample && sample.timestamp > 1e12 && sample.purposes && sample.purposes[0]
    && sample.purposes[0].versionId === purpose.latestVersion.id && sample.properties && sample.properties.category === 'analytics'),
  JSON.stringify(sample),
);

const revoke = await api(token, 'POST', `/consents/${active.data && active.data.id}/revoke`);
const after = await api(token, 'GET', `/consents/${active.data && active.data.id}`);
check('8. M2M revoke turns the ACTIVE consent REVOKED',
  revoke.status >= 200 && revoke.status < 300 && after.data && after.data.state === 'REVOKED',
  JSON.stringify({ revoke, state: after.data && after.data.state }));

const listAfter = await api(token, 'GET', `/consents?${byUserQuery}`);
const revoked = ((listAfter.data && listAfter.data.Consents) || []).find((c) => c.id === (active.data && active.data.id));
check('9. List after revoke still returns the consent, now REVOKED',
  listAfter.status === 200 && Boolean(revoked) && revoked.state === 'REVOKED',
  JSON.stringify({ status: listAfter.status, state: revoked && revoked.state }));

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} checks passed. Test subject: ${subject}`);
process.exit(failed.length ? 1 : 0);
