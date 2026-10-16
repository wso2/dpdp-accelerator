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

const COOKIE_PURPOSE_TYPE = 'Cookie';
const DEFAULT_TTL_MS = 5 * 60 * 1000;

/** vanilla-cookieconsent's revision must be a number; this one changes with any purpose version. */
function revisionOf(categories) {
  const digest = crypto.createHash('sha256')
    .update(categories.map((c) => `${c.key}:${c.versionId}`).join('|'))
    .digest();
  return digest.readUInt32BE(0) & 0x7fffffff;
}

function toCategory(definition, purpose) {
  if (!purpose) throw new Error(`Cookie purpose not found in Identity Server: ${definition.purposeName}`);
  if (purpose.type !== COOKIE_PURPOSE_TYPE) {
    throw new Error(`Purpose ${definition.purposeName} has type "${purpose.type}", expected "${COOKIE_PURPOSE_TYPE}"`);
  }
  if (!purpose.latestVersion || !purpose.latestVersion.id) {
    throw new Error(`Purpose ${definition.purposeName} has no latest version`);
  }
  return {
    key: definition.key,
    title: definition.title,
    purposeId: purpose.id,
    purposeName: purpose.name,
    versionId: purpose.latestVersion.id,
    description: purpose.description || '',
    elements: (purpose.elements || []).map((e) => ({
      id: e.id, name: e.name, displayName: e.displayName || e.name, description: e.description || '',
    })),
  };
}

function createCatalog({ isClient, categories, ttlMs = DEFAULT_TTL_MS, now = Date.now }) {
  let cached = null;
  let loadedAt = 0;
  let loading = null;
  // Purpose ids found by name on an earlier load. A purpose keeps its id across versions, so a
  // reload costs one call per category instead of two; the name is checked on every load, and a
  // purpose deleted and recreated under the same name is found by name again.
  const knownIds = new Map();

  async function resolve(definition) {
    const knownId = knownIds.get(definition.key);
    const byId = knownId ? await isClient.getPurpose(knownId) : null;
    const purpose = byId && byId.name === definition.purposeName
      ? byId
      : await isClient.findPurposeByName(definition.purposeName);
    const category = toCategory(definition, purpose);
    knownIds.set(definition.key, category.purposeId);
    return category;
  }

  async function load() {
    const loaded = await Promise.all(categories.map(resolve));
    return { categories: loaded, revision: revisionOf(loaded) };
  }

  return {
    async get() {
      if (cached && now() - loadedAt < ttlMs) return cached;
      // Requests that arrive while the catalog loads wait for that load instead of starting their own.
      if (!loading) {
        loading = load()
          .then((loaded) => {
            cached = loaded;
            loadedAt = now();
            return loaded;
          })
          .finally(() => { loading = null; });
      }
      return loading;
    },
  };
}

module.exports = { createCatalog, revisionOf, COOKIE_PURPOSE_TYPE };
