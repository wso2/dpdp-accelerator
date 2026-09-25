/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { createHmac, createPublicKey, timingSafeEqual, verify } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { DatabaseSync } from 'node:sqlite';
import { pathToFileURL } from 'node:url';

/** Authenticates the raw HTTP body, then verifies the signed routing envelope. */
export function authenticate(raw, headers, config) {
  const signature = headers['event-signature'];
  if (typeof signature !== 'string' || !/^sha256=[0-9a-f]{64}$/.test(signature)) {
    throw new Error('Invalid HMAC');
  }
  const expected = createHmac('sha256', config.secret).update(raw).digest();
  if (!timingSafeEqual(expected, Buffer.from(signature.slice(7), 'hex'))) {
    throw new Error('Invalid HMAC');
  }
  const token = JSON.parse(raw.toString('utf8')).signedPayload;
  if (typeof token !== 'string' || !/^[\w-]+\.[\w-]+\.[\w-]+$/.test(token)) {
    throw new Error('Signed payload required');
  }
  const [head, body, sig] = token.split('.');
  const protectedHeader = JSON.parse(Buffer.from(head, 'base64url').toString('utf8'));
  if (protectedHeader.alg !== 'RS256' || protectedHeader.crit !== undefined ||
      typeof protectedHeader.kid !== 'string' || !protectedHeader.kid) {
    throw new Error('Unsupported JWS header');
  }
  // Keys come only from the operator's pinned JWKS file, never from token URLs.
  const keys = config.jwks.keys.filter(key => key.kid === protectedHeader.kid &&
    key.kty === 'RSA' && (!key.use || key.use === 'sig') && (!key.alg || key.alg === 'RS256'));
  if (keys.length !== 1 || !verify('RSA-SHA256', Buffer.from(`${head}.${body}`),
      createPublicKey({ key: keys[0], format: 'jwk' }), Buffer.from(sig, 'base64url'))) {
    throw new Error('Invalid JWS');
  }
  const claims = JSON.parse(Buffer.from(body, 'base64url').toString('utf8'));
  const p = claims.payload;
  const text = value => typeof value === 'string' && value.length > 0;
  if (claims.iss !== config.issuer || claims.sub !== config.group || claims.aud !== config.audience ||
      !Number.isSafeInteger(claims.iat) || claims.iat < 0 || claims.iat > Date.now() / 1000 + 60 ||
      !p || !text(claims.jti) || !text(claims.txn) || !text(p.subscriptionId) ||
      claims.jti !== headers['delivery-id'] || claims.jti !== p.deliveryId || claims.txn !== p.eventId ||
      p.orgId !== config.tenant || p.groupId !== config.group || !(config.topics ?? [config.topic]).includes(p.topic) ||
      p.subscriptionId !== config.subscription || p.eventPayload == null) {
    throw new Error('Invalid claims or routing');
  }
  // The verified JWS binds payloadHash and payload. Do not reserialize payload
  // to recompute payloadHash: JSON number/escape formatting can differ from Java.
  return p;
}

/** Stores accepted events durably; a delivery ID can be inserted only once. */
export function openInbox(filename) {
  const db = new DatabaseSync(filename);
  db.exec(`PRAGMA journal_mode=WAL; PRAGMA synchronous=FULL;
    CREATE TABLE IF NOT EXISTS inbox (
      delivery_id TEXT PRIMARY KEY, event_id TEXT NOT NULL, subscription_id TEXT NOT NULL,
      accepted_at TEXT NOT NULL, payload TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'accepted'
    )`);
  return db;
}

/** Implements verification and durable acceptance, without business side effects. */
export function createReceiver(config, db) {
  const topics = config.topics ?? [config.topic];
  if (!Array.isArray(topics) || topics.length < 1 || topics.length > 100 ||
      topics.some(topic => typeof topic !== 'string' || !topic.trim()) ||
      new Set(topics).size !== topics.length) {
    throw new Error('Configure between 1 and 100 distinct expected topics');
  }
  const insert = db.prepare(`INSERT INTO inbox
    (delivery_id, event_id, subscription_id, accepted_at, payload) VALUES (?, ?, ?, ?, ?)
    ON CONFLICT(delivery_id) DO NOTHING`);
  const lookup = db.prepare('SELECT event_id, subscription_id FROM inbox WHERE delivery_id = ?');
  return createServer(async (req, res) => {
    const reply = (status, message = '') => {
      res.writeHead(status, { 'Content-Type': 'text/plain', 'Cache-Control': 'no-store' });
      res.end(message);
    };
    const url = new URL(req.url, 'http://receiver.invalid');
    if (url.pathname !== '/dpdp/events') return reply(404);
    if (req.method === 'GET') {
      const challenge = url.searchParams.get('hub.challenge');
      if (url.searchParams.get('hub.mode') !== 'subscribe' ||
          !(config.topics ?? [config.topic]).includes(url.searchParams.get('hub.topic')) || !challenge || challenge.length > 1024) {
        return reply(400);
      }
      return reply(200, challenge);
    }
    if (req.method !== 'POST') return reply(405);
    if (req.headers['content-type']?.split(';')[0].trim() !== 'application/json') return reply(415);
    let payload;
    try {
      const chunks = [];
      let size = 0;
      for await (const chunk of req) {
        size += chunk.length;
        if (size > 1024 * 1024) return reply(413);
        chunks.push(chunk);
      }
      const raw = Buffer.concat(chunks);
      const message = JSON.parse(raw.toString('utf8'));
      if (message.type === 'subscription.verification') {
        const allowed = config.topics ?? [config.topic];
        if (raw.length > 262144 || !Array.isArray(message.topics) || message.topics.length < 1 ||
            message.topics.length > 100 || new Set(message.topics).size !== message.topics.length ||
            !message.topics.every(topic => typeof topic === 'string' && allowed.includes(topic)) ||
            typeof message.subscriptionId !== 'string' || !message.subscriptionId ||
            (config.subscription && config.subscription !== message.subscriptionId) ||
            typeof message.challenge !== 'string' || !message.challenge || message.challenge.length > 1024) {
          return reply(400);
        }
        return reply(200, message.challenge);
      }
      if (!config.subscription) return reply(503, 'Configure the subscription ID first');
      payload = authenticate(raw, req.headers, config);
    } catch {
      return reply(401, 'Delivery authentication failed');
    }
    try {
      const previous = lookup.get(payload.deliveryId);
      if (previous && (previous.event_id !== payload.eventId ||
          previous.subscription_id !== payload.subscriptionId)) return reply(409);
      insert.run(payload.deliveryId, payload.eventId, payload.subscriptionId,
        new Date().toISOString(), JSON.stringify(payload));
      // The SQLite commit completes before acknowledgement. Duplicates also get 202.
      reply(202);
    } catch {
      reply(503, 'Inbox unavailable');
    }
  });
}

/** Starts the local demo, or lists receipt metadata without printing personal data. */
function main() {
  process.umask(0o077);
  const filename = process.env.INBOX_DB || './webhook-inbox.sqlite';
  if (process.argv.includes('--list')) {
    const db = new DatabaseSync(filename, { readOnly: true });
    console.table(db.prepare('SELECT delivery_id, event_id, accepted_at, status FROM inbox').all());
    db.close();
    return;
  }
  const required = name => {
    if (!process.env[name]) throw new Error(`Set ${name}`);
    return process.env[name];
  };
  const config = {
    secret: required('SHARED_SECRET'), issuer: required('EXPECTED_ISSUER'),
    tenant: required('EXPECTED_TENANT'), group: required('EXPECTED_GROUP'),
    topics: process.env.EXPECTED_TOPICS ? JSON.parse(process.env.EXPECTED_TOPICS) : [required('EXPECTED_TOPIC')], audience: process.env.EXPECTED_AUDIENCE || 'dpdp-event-notifications',
    subscription: process.env.EXPECTED_SUBSCRIPTION_ID || '',
    jwks: JSON.parse(readFileSync(required('JWKS_FILE'), 'utf8')),
  };
  const db = openInbox(filename);
  const server = createReceiver(config, db);
  server.requestTimeout = 4000;
  server.headersTimeout = 4000;
  server.listen(Number(process.env.PORT || 8443), process.env.HOST || '127.0.0.1', () => {
    console.log(`Receiver listening on port ${server.address().port}; ${config.subscription ? 'accepting' : 'verification only'}`);
  });
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close(() => db.close()));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main();
