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
const { createCatalog, revisionOf } = require('../src/catalog');

const DEFINITIONS = [
  { key: 'analytics', purposeName: 'cookie_analytics', title: 'Analytics cookies' },
  { key: 'marketing', purposeName: 'cookie_marketing', title: 'Marketing cookies' },
];

function purposes(overrides = {}) {
  return {
    cookie_analytics: {
      id: 'p-a', name: 'cookie_analytics', type: 'Cookie', description: 'Counts visits.',
      latestVersion: { id: 'pv-a1', version: 'v1' },
      elements: [{ id: 'e-a', name: '_demo_analytics', displayName: 'Demo analytics cookie', description: 'Counts page views.' }],
    },
    cookie_marketing: {
      id: 'p-m', name: 'cookie_marketing', type: 'Cookie', description: 'Offers.',
      latestVersion: { id: 'pv-m1', version: 'v1' }, elements: [],
    },
    ...overrides,
  };
}

/** Counts IS calls: a lookup by name costs two (search, then detail), a get by id costs one. */
function fakeIsClient(byName) {
  const client = { lookups: 0, byId: 0 };
  client.findPurposeByName = async (name) => {
    client.lookups += 1;
    return Object.values(byName).find((p) => p.name === name) || null;
  };
  client.getPurpose = async (id) => {
    client.byId += 1;
    return Object.values(byName).find((p) => p.id === id) || null;
  };
  return client;
}

test('loads the configured categories in order with their cookies', async () => {
  const catalog = await createCatalog({ isClient: fakeIsClient(purposes()), categories: DEFINITIONS }).get();
  assert.deepEqual(catalog.categories.map((c) => c.key), ['analytics', 'marketing']);
  assert.deepEqual(catalog.categories[0], {
    key: 'analytics', title: 'Analytics cookies', purposeId: 'p-a', purposeName: 'cookie_analytics',
    versionId: 'pv-a1', description: 'Counts visits.',
    elements: [{ id: 'e-a', name: '_demo_analytics', displayName: 'Demo analytics cookie', description: 'Counts page views.' }],
  });
  assert.ok(Number.isInteger(catalog.revision) && catalog.revision >= 0);
});

test('the revision changes only when a purpose version changes', () => {
  const a = [{ key: 'analytics', versionId: 'pv-a1' }];
  assert.equal(revisionOf(a), revisionOf([{ key: 'analytics', versionId: 'pv-a1' }]));
  assert.notEqual(revisionOf(a), revisionOf([{ key: 'analytics', versionId: 'pv-a2' }]));
});

test('fails clearly when a purpose is missing', async () => {
  const byName = purposes();
  delete byName.cookie_marketing;
  await assert.rejects(createCatalog({ isClient: fakeIsClient(byName), categories: DEFINITIONS }).get(), /cookie_marketing/);
});

test('fails clearly when a purpose is not of type Cookie', async () => {
  const byName = purposes({ cookie_marketing: { ...purposes().cookie_marketing, type: 'marketing' } });
  await assert.rejects(createCatalog({ isClient: fakeIsClient(byName), categories: DEFINITIONS }).get(), /expected "Cookie"/);
});

test('caches within the TTL and reloads after it', async () => {
  let clock = 0;
  const isClient = fakeIsClient(purposes());
  const catalog = createCatalog({ isClient, categories: DEFINITIONS, ttlMs: 1000, now: () => clock });
  await catalog.get();
  clock = 999;
  await catalog.get();
  assert.equal(isClient.lookups, 2);
  clock = 1000;
  await catalog.get();
  assert.equal(isClient.lookups + isClient.byId, 4);
});

test('concurrent requests on an empty cache share one load', async () => {
  const isClient = fakeIsClient(purposes());
  const catalog = createCatalog({ isClient, categories: DEFINITIONS });
  const [a, b, c] = await Promise.all([catalog.get(), catalog.get(), catalog.get()]);
  assert.equal(isClient.lookups, 2);
  assert.equal(a, b);
  assert.equal(b, c);
});

test('a failed load is not cached, so the next request tries again', async () => {
  const byName = purposes();
  const isClient = fakeIsClient(byName);
  const realFind = isClient.findPurposeByName;
  isClient.findPurposeByName = async () => { throw new Error('IS down'); };
  const catalog = createCatalog({ isClient, categories: DEFINITIONS });
  await assert.rejects(catalog.get(), /IS down/);
  isClient.findPurposeByName = realFind;
  assert.equal((await catalog.get()).categories.length, 2);
});

test('a reload gets each purpose by the id it found last time, not by name', async () => {
  let clock = 0;
  const isClient = fakeIsClient(purposes());
  const catalog = createCatalog({ isClient, categories: DEFINITIONS, ttlMs: 1000, now: () => clock });
  await catalog.get();
  clock = 1000;
  await catalog.get();
  assert.deepEqual([isClient.lookups, isClient.byId], [2, 2]);
});

test('a reload picks up a new purpose version through the remembered id', async () => {
  let clock = 0;
  const byName = purposes();
  const catalog = createCatalog({ isClient: fakeIsClient(byName), categories: DEFINITIONS, ttlMs: 1000, now: () => clock });
  const before = await catalog.get();
  byName.cookie_analytics = { ...byName.cookie_analytics, latestVersion: { id: 'pv-a2', version: 'v2' } };
  clock = 1000;
  const after = await catalog.get();
  assert.equal(after.categories[0].versionId, 'pv-a2');
  assert.notEqual(after.revision, before.revision);
});

test('a purpose deleted and recreated under the same name is found again by name', async () => {
  let clock = 0;
  const byName = purposes();
  const isClient = fakeIsClient(byName);
  const catalog = createCatalog({ isClient, categories: DEFINITIONS, ttlMs: 1000, now: () => clock });
  await catalog.get();
  byName.cookie_analytics = { ...byName.cookie_analytics, id: 'p-a-new' };
  clock = 1000;
  const after = await catalog.get();
  assert.equal(after.categories[0].purposeId, 'p-a-new');
  assert.equal(isClient.lookups, 3);
});

test('an id that now belongs to a differently named purpose is not used', async () => {
  let clock = 0;
  const byName = purposes();
  const isClient = fakeIsClient(byName);
  const catalog = createCatalog({ isClient, categories: DEFINITIONS, ttlMs: 1000, now: () => clock });
  await catalog.get();
  byName.cookie_analytics = { ...byName.cookie_analytics, name: 'renamed' };
  clock = 1000;
  await assert.rejects(catalog.get(), /cookie_analytics/);
});
