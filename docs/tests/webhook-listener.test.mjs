// Copyright (c) 2026, WSO2 LLC. Licensed under the Apache License, Version 2.0.
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { once } from 'node:events';
import test from 'node:test';
import { createReceiver } from '../static/examples/webhook-listener.mjs';

const secret = 'test-only-secret';

function createSignedDelivery(payload, sharedSecret = secret) {
  const raw = Buffer.from(JSON.stringify(payload));
  const signature = `sha256=${createHmac('sha256', sharedSecret).update(raw).digest('hex')}`;
  return {
    raw,
    headers: {
      'content-type': 'application/json',
      'delivery-id': 'del-test-1',
      'event-signature': signature,
    },
  };
}

test('Webhook Listener verification handshake and event delivery', async () => {
  const server = createReceiver({ secret, path: '/dpdp/events' });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  const baseUrl = `http://127.0.0.1:${server.address().port}/dpdp/events`;

  try {
    // 1. Rejects non-POST methods with 405
    const getRes = await fetch(baseUrl);
    assert.equal(getRes.status, 405);

    // 2. Rejects non-JSON content-type with 415
    const nonJsonRes = await fetch(baseUrl, {
      method: 'POST',
      headers: { 'content-type': 'text/plain' },
      body: 'hello',
    });
    assert.equal(nonJsonRes.status, 415);

    // 3. Verification handshake succeeds and echoes challenge with 200
    const verification = {
      type: 'subscription.verification',
      subscriptionId: 'sub-12345',
      topics: ['user.data.change'],
      challenge: 'test-challenge-uuid-123',
    };
    const verifyRes = await fetch(baseUrl, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(verification),
    });
    assert.equal(verifyRes.status, 200);
    assert.equal(await verifyRes.text(), 'test-challenge-uuid-123');

    // 4. Verification with missing challenge fails with 400
    const invalidVerifyRes = await fetch(baseUrl, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ type: 'subscription.verification' }),
    });
    assert.equal(invalidVerifyRes.status, 400);

    // 5. Signed event delivery with valid HMAC returns 202
    const eventPayload = {
      eventId: 'evt-1',
      topic: 'user.data.change',
      signedPayload: 'eyJhbGciOiJSUzI1NiJ9.eyJ1c2VySWQiOiJwb3J0YWwtdXNlciJ9.signature',
    };
    const delivery = createSignedDelivery(eventPayload, secret);
    const deliveryRes = await fetch(baseUrl, {
      method: 'POST',
      headers: delivery.headers,
      body: delivery.raw,
    });
    assert.equal(deliveryRes.status, 202);

    // 6. Delivery with invalid HMAC returns 401
    const badDeliveryRes = await fetch(baseUrl, {
      method: 'POST',
      headers: { ...delivery.headers, 'event-signature': 'sha256=invalid-signature' },
      body: delivery.raw,
    });
    assert.equal(badDeliveryRes.status, 401);

  } finally {
    server.close();
    await once(server, 'close');
  }
});
