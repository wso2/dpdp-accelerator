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

import { createHmac, timingSafeEqual } from 'node:crypto';
import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';

function log(level, message) {
  const ts = new Date().toISOString().replace('T', ' ').substring(0, 19);
  console.log(`[${ts}] [${level}] ${message}`);
}

/**
 * Creates the HTTP request handler for subscription verification and event delivery.
 * @param {object} config - Configuration object { secret?, path? }
 */
export function createReceiver(config = {}) {
  const sharedSecret = config.secret ?? process.env.SHARED_SECRET ?? '';
  const callbackPath = config.path ?? process.env.CALLBACK_PATH ?? '/dpdp/events';

  return createServer(async (req, res) => {
    const reply = (status, body = '', contentType = 'text/plain') => {
      res.writeHead(status, { 'Content-Type': contentType, 'Cache-Control': 'no-store' });
      res.end(body);
    };

    const url = new URL(req.url, 'http://receiver.invalid');
    log('INFO', `--> Incoming ${req.method} ${url.pathname} from ${req.socket.remoteAddress || 'client'}`);

    if (url.pathname !== callbackPath) {
      log('WARN', `<-- Route not found: '${url.pathname}' (expected '${callbackPath}'). Returning HTTP 404.`);
      return reply(404, 'Not Found');
    }
    if (req.method !== 'POST') {
      log('WARN', `<-- Method '${req.method}' not allowed. Returning HTTP 405.`);
      return reply(405, 'Method Not Allowed');
    }
    if (req.headers['content-type']?.split(';')[0].trim() !== 'application/json') {
      log('WARN', `<-- Invalid Content-Type '${req.headers['content-type']}' (expected application/json). Returning HTTP 415.`);
      return reply(415, 'Unsupported Media Type');
    }

    try {
      const chunks = [];
      let size = 0;
      for await (const chunk of req) {
        size += chunk.length;
        if (size > 1024 * 1024) {
          log('WARN', '<-- Request body exceeds 1 MiB limit. Returning HTTP 413.');
          return reply(413, 'Payload Too Large');
        }
        chunks.push(chunk);
      }
      const raw = Buffer.concat(chunks);
      const message = JSON.parse(raw.toString('utf8'));

      // 1. Subscription Verification Handshake
      if (message.type === 'subscription.verification') {
        log('INFO', '================ [SUBSCRIPTION VERIFICATION] ================');
        log('INFO', `Verification Payload:\n${JSON.stringify(message, null, 2)}`);

        if (!message.challenge || typeof message.challenge !== 'string') {
          log('WARN', '<-- Verification rejected: missing challenge. Returning HTTP 400.');
          return reply(400, 'Invalid challenge');
        }

        log('INFO', `Verified subscription '${message.subscriptionId}' for topics: ${JSON.stringify(message.topics)}`);
        log('INFO', `<-- Returned HTTP 200 OK with challenge: '${message.challenge}'`);
        log('INFO', '=============================================================');
        return reply(200, message.challenge, 'text/plain; charset=utf-8');
      }

      // 2. Event Delivery
      const deliveryId = req.headers['delivery-id'] ?? 'unknown';
      const eventSignature = req.headers['event-signature'] ?? '';

      log('INFO', '==================== [EVENT DELIVERY] ====================');
      log('INFO', `Delivery-Id:     ${deliveryId}`);
      log('INFO', `Event-Signature: ${eventSignature || '(none)'}`);

      // Verify HMAC-SHA256 signature if shared secret is provided
      if (sharedSecret) {
        if (!eventSignature.startsWith('sha256=')) {
          log('ERROR', '<-- Missing or invalid Event-Signature header format. Returning HTTP 401.');
          return reply(401, 'Invalid Event-Signature header');
        }
        const expectedHmac = `sha256=${createHmac('sha256', sharedSecret).update(raw).digest('hex')}`;
        const bufExpected = Buffer.from(expectedHmac, 'utf8');
        const bufReceived = Buffer.from(eventSignature, 'utf8');

        if (bufExpected.length !== bufReceived.length || !timingSafeEqual(bufExpected, bufReceived)) {
          log('ERROR', '<-- HMAC-SHA256 signature verification failed. Returning HTTP 401.');
          return reply(401, 'HMAC-SHA256 signature mismatch');
        }
        log('INFO', 'HMAC-SHA256 signature verified successfully.');
      } else {
        log('INFO', 'SHARED_SECRET not set; skipped HMAC signature check.');
      }

      // Decode compact JWS claims if signedPayload is present
      if (message.signedPayload && typeof message.signedPayload === 'string') {
        log('INFO', `Raw Signed JWS:\n${message.signedPayload}`);
        try {
          const parts = message.signedPayload.split('.');
          if (parts.length >= 2) {
            const claims = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
            log('INFO', `Decoded Event Payload:\n${JSON.stringify(claims, null, 2)}`);
          }
        } catch (e) {
          log('WARN', `Could not decode JWS claims: ${e.message}`);
        }
      } else {
        log('INFO', `Event Body:\n${JSON.stringify(message, null, 2)}`);
      }

      log('INFO', '<-- Returned HTTP 202 Accepted');
      log('INFO', '==========================================================');
      return reply(202, JSON.stringify({ status: 'accepted' }), 'application/json');

    } catch (err) {
      log('ERROR', `<-- Malformed JSON body or request error: ${err.message}. Returning HTTP 400.`);
      return reply(400, 'Bad Request');
    }
  });
}

function main() {
  const port = Number(process.env.PORT || 8443);
  const host = process.env.HOST || '127.0.0.1';
  const callbackPath = process.env.CALLBACK_PATH || '/dpdp/events';
  const sharedSecret = process.env.SHARED_SECRET || '';

  const server = createReceiver({ secret: sharedSecret, path: callbackPath });

  server.listen(port, host, () => {
    console.log(`=============================================================`);
    console.log(`  DPDP Reference Webhook Listener (Node.js)`);
    console.log(`  Listening on: http://${host}:${port}${callbackPath}`);
    if (sharedSecret) {
      console.log(`  HMAC Verification: ENABLED (shared secret configured)`);
    } else {
      console.log(`  HMAC Verification: DISABLED (set SHARED_SECRET to enable)`);
    }
    console.log(`  Press Ctrl+C to stop.`);
    console.log(`=============================================================`);
  });

  for (const signal of ['SIGINT', 'SIGTERM']) {
    process.on(signal, () => {
      console.log('\nShutting down listener.');
      server.close();
    });
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
