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
const express = require('express');
const { createRouter } = require('../src/router');
const { seal, COOKIE_NAME } = require('../src/subjectCookie');
const { StaleRevisionError, DecisionNotSavedError } = require('../src/cookieConsentService');

const SECRET = 's'.repeat(32);
const STATE = { revision: 1, hasDecision: false, categories: {}, config: { texts: {}, categories: [] } };

async function withServer(service, run, username = null) {
  const errors = [];
  const app = express();
  app.use('/cookie-consent', createRouter({
    service, cookieSecret: SECRET, cookieOptions: { secure: false, maxAgeDays: 365 },
    getUsername: typeof username === 'function' ? username : () => username, onError: (e) => errors.push(e),
  }));
  const server = app.listen(0);
  await new Promise((resolve) => server.once('listening', resolve));
  try {
    await run(`http://127.0.0.1:${server.address().port}/cookie-consent`, errors);
  } finally {
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
  }
}

function postJson(url, body, headers = {}) {
  return fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) });
}

test('GET /state passes the username and the unsealed cookie, and is never cached', async () => {
  let seen;
  const service = { getState: async (args) => { seen = args; return STATE; } };
  await withServer(service, async (base) => {
    const response = await fetch(`${base}/state`, { headers: { cookie: `${COOKIE_NAME}=${seal(SECRET, { v: 'v-1', u: null })}` } });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get('cache-control'), 'no-store');
    assert.deepEqual(await response.json(), STATE);
    assert.deepEqual(seen, { username: 'alice', cookie: { v: 'v-1', u: null } });
  }, 'alice');
});

test('a tampered cookie reaches the service as null', async () => {
  let seen;
  const service = { getState: async (args) => { seen = args; return STATE; } };
  await withServer(service, async (base) => {
    await fetch(`${base}/state`, { headers: { cookie: `${COOKIE_NAME}=tampered` } });
    assert.equal(seen.cookie, null);
  });
});

test('GET /state answers 502 and reports the error when IS fails', async () => {
  const service = { getState: async () => { throw new Error('IS down'); } };
  await withServer(service, async (base, errors) => {
    const response = await fetch(`${base}/state`);
    assert.equal(response.status, 502);
    assert.equal(errors[0].message, 'IS down');
  });
});

test('POST /decision rejects malformed bodies with 400', async () => {
  const service = { saveDecision: async () => assert.fail('must not be called') };
  await withServer(service, async (base) => {
    for (const body of [{}, { revision: 1 }, { revision: '1', categories: {} }, { revision: 1, categories: [] }, { revision: 1, categories: { analytics: 'yes' } }]) {
      assert.equal((await postJson(`${base}/decision`, body)).status, 400, JSON.stringify(body));
    }
  });
});

test('POST /decision saves the decision and sets the HttpOnly subject cookie', async () => {
  let seen;
  const service = { saveDecision: async (args) => { seen = args; return { cookie: { v: 'v-2', u: null }, state: STATE }; } };
  await withServer(service, async (base) => {
    const response = await postJson(`${base}/decision`, { revision: 1, categories: { analytics: true } });
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), STATE);
    assert.deepEqual(seen, { username: null, cookie: null, categories: { analytics: true }, revision: 1 });
    const setCookie = response.headers.get('set-cookie');
    assert.match(setCookie, new RegExp(`^${COOKIE_NAME}=`));
    assert.match(setCookie, /HttpOnly/);
    assert.match(setCookie, /SameSite=Lax/);
  });
});

test('POST /decision answers 409 for a stale revision without reporting an error', async () => {
  const service = { saveDecision: async () => { throw new StaleRevisionError(); } };
  await withServer(service, async (base, errors) => {
    assert.equal((await postJson(`${base}/decision`, { revision: 1, categories: {} })).status, 409);
    assert.equal(errors.length, 0);
  });
});

test('POST /decision answers 502 when IS fails', async () => {
  const service = { saveDecision: async () => { throw new Error('IS down'); } };
  await withServer(service, async (base, errors) => {
    assert.equal((await postJson(`${base}/decision`, { revision: 1, categories: {} })).status, 502);
    assert.equal(errors.length, 1);
  });
});

test('POST /decision still sets the subject cookie when a save fails part way', async () => {
  const service = { saveDecision: async () => { throw new DecisionNotSavedError(new Error('IS down'), { v: 'v-3', u: null }); } };
  await withServer(service, async (base, errors) => {
    const response = await postJson(`${base}/decision`, { revision: 1, categories: { analytics: true } });
    assert.equal(response.status, 502);
    assert.match(response.headers.get('set-cookie'), new RegExp(`^${COOKIE_NAME}=`));
    assert.deepEqual(errors.map((e) => e.message), ['IS down']);
  });
});

test('awaits an async getUsername in both routes', async () => {
  const seen = [];
  const service = {
    getState: async (args) => { seen.push(args.username); return STATE; },
    saveDecision: async (args) => { seen.push(args.username); return { cookie: { v: 'v-1', u: args.username }, state: STATE }; },
  };
  await withServer(service, async (base) => {
    assert.equal((await fetch(`${base}/state`)).status, 200);
    assert.equal((await postJson(`${base}/decision`, { revision: 1, categories: {} })).status, 200);
    assert.deepEqual(seen, ['alice', 'alice']);
  }, async () => 'alice');
});

test('a rejected async getUsername answers 502 and reports the error', async () => {
  const service = { getState: async () => assert.fail('must not be called'), saveDecision: async () => assert.fail('must not be called') };
  await withServer(service, async (base, errors) => {
    assert.equal((await fetch(`${base}/state`)).status, 502);
    assert.equal((await postJson(`${base}/decision`, { revision: 1, categories: {} })).status, 502);
    assert.deepEqual(errors.map((e) => e.message), ['session store down', 'session store down']);
  }, async () => { throw new Error('session store down'); });
});

test('serves the loader and the library', async () => {
  await withServer({}, async (base) => {
    const loader = await fetch(`${base}/loader.js`);
    assert.match(loader.headers.get('content-type'), /javascript/);
    const source = await loader.text();
    assert.ok(source.includes('DPDPCookieConsentCore') && source.includes("'/state'"));
    const vendor = await fetch(`${base}/vendor/cookieconsent.umd.js`);
    assert.equal(vendor.status, 200);
    assert.ok((await vendor.text()).includes('CookieConsent'));
    const css = await fetch(`${base}/vendor/cookieconsent.css`);
    assert.match(css.headers.get('content-type'), /css/);
    assert.equal((await fetch(`${base}/vendor/cookieconsent.esm.js`)).status, 404);
  });
});
