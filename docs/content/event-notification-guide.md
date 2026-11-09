---
title: Using Event Notifications
sidebar_position: 3
---

# Using Event Notifications

This reference covers the Event Notification API: topics, subscriptions, publishing, the webhook receiver contract, polling, and completion reports. For the concepts, see [Understanding Event Notifications](learn/event.md). For a step-by-step walkthrough with the Consent Portal and the sample webhook listener, see [Try Out: Event Notifications](try-out/event.md). For the server settings, see [Configure Event Notifications](install-and-setup/configuring-the-accelerator.md#configure-event-notifications).

For every endpoint and schema, see the [Event Notification API specification](https://github.com/wso2/dpdp-accelerator/blob/v1.0.0/dpdp-accelerator/internal-webapps/org.wso2.dpdp.accelerator.event.notifications.endpoint/src/main/resources/event-notifications.yaml).

## 1. Before you begin

The API base URL is:

```text
https://<host>:9443/api/dpdp/event-notifications/v1
```

For a specific tenant, add `/t/<tenant-domain>` after the port. Topics, subscriptions, events, and deliveries are isolated by tenant, and the tenant always comes from the URL and the token, never from a request body.

**Scopes:** each operation needs one `notifications:*` scope, listed in [Managing Access](learn/managing-access.md#event-notifications). The `dpdp-consent-admin` role holds all eight, but the Consent Portal doesn't request the polling and completion scopes, so a portal token never carries them. Give each publishing or receiving system its own application with only the scopes it needs.

The examples use these variables:

```bash
export API_BASE="https://localhost:9443/api/dpdp/event-notifications/v1"
export GROUP_ID="carbon.super"   # the tenant domain
export ACCESS_TOKEN="<token-with-topic-and-subscription-scopes>"
export PUBLISHER_TOKEN="<token-with-notifications:events:write>"
export RECEIVER_TOKEN="<token-with-notifications:events:poll-or-event-deliveries:complete>"
```

For a local Identity Server with a self-signed certificate, add `-k` to a `curl` command. Never use `-k` in production; configure a trusted CA, or pass it with `--cacert`.

## 2. Topics

### System topics

The accelerator creates these topics for every tenant when `system_topics_auto_create_enabled = true` (the default). It publishes to them automatically when `lifecycle_events.publishing_enabled = true` (the default), so don't publish to them yourself.

| Topic | Published when | Carries a purpose? |
|---|---|---|
| `consent.create` | A consent is created | Yes |
| `consent.update` | A consent is updated, or an authorization is approved or rejected | Yes |
| `consent.revoke` | A consent or its authorization is revoked | Yes |
| `consent.expire` | The expiry reconciler records a consent as expired | Yes |
| `user.data.change` | A user's profile claims are updated | No |
| `user.account.delete` | A user account is deleted | No |

System topics can't be deregistered. The two user topics also need the `dpdpUserLifecycleEventHandler` event handler, which `configure.sh` adds; see [Configure lifecycle event publishing](install-and-setup/configuring-the-accelerator.md#configure-lifecycle-event-publishing).

When both `system_topics_auto_create_enabled` and `system_topics_verify_on_startup_enabled` are `true` (the default), the server reconciles system topics at startup across every active tenant (plus `carbon.super`), including tenants with no topic rows yet:

- An **active** user-created topic whose name matches a system topic is promoted in-place to a system topic (`initiatedBy = system`), receives automatic lifecycle events, and can no longer be deregistered.
- A **deregistered** user topic with the same name is left `deregistered`, and a new active system topic is created alongside it.

### Register a user topic

```bash
curl -X POST "${API_BASE}/topics" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "delivery.preferences.update",
    "description": "A customer updated their delivery preferences"
  }'
```

`name` is required and must be unique in the tenant. A new topic is `active`. To list topics, call `GET /topics?status=active&limit=20&offset=0`. List responses return `items` and `total`.

To deregister a user topic, call `DELETE /topics/<topic-id>`. A topic can't be deregistered while any subscription that references it is `active`, `pending`, or `stale`.

## 3. Subscriptions

A subscription links one or more topics to a purpose filter and a delivery mode: `webhook` (pushed to a callback URL) or `poll` (fetched by the receiver).

| Filter `type` | Matching events |
|---|---|
| `all` | Every event on the subscribed topics |
| `specific` | Events that carry at least one of the listed `purposes` |
| `all_except` | Events that carry at least one purpose that isn't listed |

- `name` and `delivery.sharedSecret` are required for both modes. Store the secret before you submit it; responses never return it.
- A subscription covers 1 to 100 active topics, fixed at creation.
- `specific` and `all_except` need at least one purpose. Purpose matching is case-insensitive.
- A subscription that includes `user.data.change` or `user.account.delete` must use `all`, because user events carry no purpose. Any other filter is rejected with `422`.
- Active subscription names must be unique within a tenant (`409`). When `[dpdp_accelerator.event_notifications.subscription] conflict_checks_enabled = true` (off by default), a tenant also can't mix webhook and poll subscriptions on the same topic or create overlapping webhook subscriptions to the same callback URL.

### Register a webhook subscription

```bash
curl -X POST "${API_BASE}/subscriptions" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "CloudEngage - consent changes",
    "topics": ["consent.update", "consent.revoke"],
    "filter": {
      "type": "specific",
      "purposes": ["marketing-email"]
    },
    "delivery": {
      "mode": "webhook",
      "callbackUrl": "https://subscriber.example.com/dpdp/events",
      "sharedSecret": "<strong-random-secret>"
    }
  }'
```

The subscription starts as `pending`, and becomes `active` once the callback answers the verification challenge (see [Respond to verification](#respond-to-verification)). If verification attempts run out, it becomes `stale`. After fixing the receiver, retry with `POST /subscriptions/<subscription-id>/verify`, or select **Re-verify webhook** in the portal.

The callback URL must use an allowed port (by default, none or 80, 443, or 8443) and can't point to a private network address unless `allow_private_network_callback_targets = true`. HTTP callback URLs are allowed by default (`allow_http_callback_url = true`); set it to `false` in production so only HTTPS is accepted. See [Configure webhook delivery](install-and-setup/configuring-the-accelerator.md#configure-webhook-delivery).

### Register a poll subscription

```bash
curl -X POST "${API_BASE}/subscriptions" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "MedExpress - delivery preferences",
    "topics": ["delivery.preferences.update"],
    "filter": { "type": "all" },
    "delivery": {
      "mode": "poll",
      "sharedSecret": "<strong-random-secret>"
    }
  }'
```

A poll subscription has no callback URL and is `active` immediately. Keep the returned `subscriptionId` and the shared secret; the receiver needs both to poll.

To delete a subscription, call `DELETE /subscriptions/<subscription-id>`, or use the portal. Its status becomes `deleted`, and its event and delivery history are kept.

## 4. Publish an event

Publish to an active user topic with a token that has `notifications:events:write`:

```bash
curl -X POST "${API_BASE}/events" \
  -H "Authorization: Bearer ${PUBLISHER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "group-id: ${GROUP_ID}" \
  -d '{
    "topic": "delivery.preferences.update",
    "purposes": ["order-delivery"],
    "payload": {
      "customerReference": "cust-0001",
      "change": "DELIVERY_WINDOW_UPDATED"
    }
  }'
```

- The `group-id` header is required. It must equal the tenant domain (`carbon.super` for the super tenant), because every subscription's group is the tenant domain. An event with any other group ID is stored but reaches no subscriber.
- `purposes` is optional. It's what `specific` and `all_except` filters match against.
- The event and its deliveries are created together. Only `active` subscriptions on the same tenant, group, and topic whose filter matches get a delivery.
- Keep personal data out of the payload. Send a reference the receiver can look up instead.

## 5. Build a webhook receiver

Identity Server sends every request for a subscription to its `callbackUrl` as an HTTP `POST`: first the verification challenge, then each event delivery. The [sample listeners](try-out/event.md#step-1-start-the-webhook-listener) implement this contract, as described in [`webhook-listener.openapi.yaml`](pathname:///examples/webhook-listener.openapi.yaml).

![Webhook receiver request routing flow for verification handshake and event delivery](../assets/images/diagrams/dpdp-webhook-receiver-flow.svg)

### Respond to verification

```json
{
  "type": "subscription.verification",
  "subscriptionId": "<subscription-id>",
  "topics": ["consent.update", "consent.revoke"],
  "challenge": "<one-time-challenge>"
}
```

When `type` is `subscription.verification`, check that `topics` matches what the receiver expects, then respond with HTTP `200`, `Content-Type: text/plain`, and the exact `challenge` string as the body. Surrounding whitespace is ignored, but any other content fails verification.

### Receive and verify deliveries

An event delivery has `Content-Type: application/json` and these headers:

| Header | Description |
|---|---|
| `Delivery-Id` | Stable delivery identifier. Use it to reject duplicates. |
| `event-signature` | `sha256=<hex HMAC-SHA256>` of the raw request body, made with the subscription's shared secret |

With payload signing enabled (the default), the body contains only a compact JWS signed with the tenant's key:

```json
{
  "signedPayload": "<base64url-header>.<base64url-claims>.<base64url-signature>"
}
```

Its decoded claims look like this:

```json
{
  "iss": "https://localhost:9443/oauth2/token",
  "sub": "carbon.super",
  "aud": "dpdp-event-notifications",
  "iat": 1787651970,
  "jti": "<delivery-id>",
  "txn": "<event-id>",
  "payloadHash": "<HMAC-SHA256 of the embedded payload>",
  "payload": {
    "deliveryId": "<delivery-id>",
    "eventId": "<event-id>",
    "subscriptionId": "<subscription-id>",
    "orgId": "carbon.super",
    "groupId": "carbon.super",
    "topic": "consent.revoke",
    "eventPayload": {
      "consentId": "c7c6b814-ef76-4eb4-9494-8af1b98a9ed1",
      "previousStatus": "ACTIVE"
    }
  }
}
```

To verify a delivery:

1. Compute HMAC-SHA256 over the exact raw body bytes with the shared secret, and compare `sha256=<lowercase hex>` with `event-signature` in constant time. Don't re-serialize the JSON first.
2. Verify the JWS with `RS256` only, using the key whose `kid` matches the tenant's trusted JWKS endpoint. Keep the issuer-to-JWKS mapping in your own configuration; never fetch keys from a location taken from an unverified token. The super tenant uses `/oauth2/token` and `/oauth2/jwks`, without `/t/carbon.super`.
3. Check `iss`, `sub`, `aud`, and `iat`. Check that `jti`, the `Delivery-Id` header, and `payload.deliveryId` agree, and that `txn` matches `payload.eventId`.
4. Check the expected tenant, group, topic, and subscription, then process `payload.eventPayload`.

### Acceptance, retries, and processing responsibilities

Return a `2xx` only after durably accepting the event, for example after committing it to an inbox table or queue. The sender waits up to five seconds, so do the business work asynchronously afterwards.

| Receiver outcome | Response | What happens next |
|---|---|---|
| Verified event committed | `202` or another `2xx` | The delivery becomes `delivered`. |
| The same delivery was already accepted | `2xx` | Acknowledge it again without repeating the work. |
| Invalid signature, or unexpected tenant or subscription | `401` or `403` | Retried like any failure; investigate the configuration. |
| Storage unavailable | `503` | Retried. |

- Every non-`2xx` response, and every transport failure, is retried. With `max_retries = 5`, there are at most six attempts, with a backoff of `base_backoff_seconds × 3^(retry number - 1)`. A delivery that runs out of attempts becomes `failed`. See [Retry a failed webhook delivery](#retry-a-failed-webhook-delivery).
- Deduplicate by `Delivery-Id` with a durable uniqueness constraint. A delivery can arrive again after a timeout or recovery, and the body may be signed again on retry, so don't deduplicate by body. Deliveries can arrive out of order.
- A `2xx` proves acceptance, not that the work is done. Make the business effects idempotent, and retry or quarantine failures on your side. To record that the work is done, submit a [completion report](#submit-webhook-delivery-completion).

## 6. Process deliveries

### Submit webhook delivery completion

After processing a webhook delivery, a receiver can submit a signed completion report with a token that has `notifications:event-deliveries:complete`. The signature is an HMAC-SHA256, made with the subscription's shared secret, over:

```text
v1\ncompletion\n<deliveryId>\n<exact-request-body>
```

This binds the report to one delivery. Send the body bytes exactly as signed.

```bash
DELIVERY_ID="<delivery-id>"
SHARED_SECRET="<subscription-shared-secret>"
COMPLETION_BODY='{"completionStatus":"completed","completionEvidence":"https://processor.example/evidence/receipt.pdf"}'
COMPLETION_SIGNATURE="sha256=$(printf 'v1\ncompletion\n%s\n%s' "${DELIVERY_ID}" "${COMPLETION_BODY}" \
  | openssl dgst -sha256 -hmac "${SHARED_SECRET}" | awk '{print $NF}')"

curl -X POST "${API_BASE}/deliveries/${DELIVERY_ID}/completion" \
  -H "Authorization: Bearer ${RECEIVER_TOKEN}" \
  -H "group-id: ${GROUP_ID}" \
  -H "event-signature: ${COMPLETION_SIGNATURE}" \
  -H "Content-Type: application/json" \
  -d "${COMPLETION_BODY}"
```

- `completionStatus` is one of `completed`, `ack`, `disputed`, or `partial`.
- `completionEvidence` is required. It must be an absolute HTTPS URL of up to 512 characters, with no credentials or fragment.
- `completedAt` is optional, in epoch milliseconds.
- A successful report returns `204 No Content`. The delivery stays `delivered`, and the report is shown under **Completion Evidence** in the portal.

| Response | Cause |
|---|---|
| `401` (`EN-4010`) | Invalid signature, unknown delivery, or group mismatch. All three return the same response, so a caller can't discover whether a delivery exists. |
| `409` (`EN-4003`) | The delivery isn't `delivered` yet. The sender can still have it in flight briefly after your HTTP response. |
| `409` (`EN-4090`) | A completion report already exists for this delivery. |

### Poll event deliveries

A poll request is scoped to one subscription. In one call, it can acknowledge deliveries, report errors, and fetch pending deliveries. Long polling isn't supported, so `"returnImmediately": false` is rejected.

```bash
SUBSCRIPTION_ID="<poll-subscription-id>"
SHARED_SECRET="<poll-subscription-shared-secret>"
POLL_BODY='{
  "maxEvents": 20,
  "ack": ["<delivery-id-processed>"],
  "setErrs": {
    "<delivery-id-failed>": {
      "err": "authentication_failed",
      "description": "Unable to authenticate the signed event"
    }
  }
}'
POLL_SIGNATURE="sha256=$(printf %s "${POLL_BODY}" | openssl dgst -sha256 -hmac "${SHARED_SECRET}" | awk '{print $NF}')"

curl -X POST "${API_BASE}/events/poll" \
  -H "Authorization: Bearer ${RECEIVER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "group-id: ${GROUP_ID}" \
  -H "subscription-id: ${SUBSCRIPTION_ID}" \
  -H "event-signature: ${POLL_SIGNATURE}" \
  -d "${POLL_BODY}"
```

- `ack` lists delivery IDs, and `setErrs` is keyed by delivery ID, not event ID. A delivery ID can't appear in both.
- The first poll can have an empty body. Its signature is then calculated over zero bytes, not over `{}`.
- An omitted `maxEvents` uses `default_max_events` (20). `0` makes an acknowledge-only request, and values above `max_events_limit` (100) are rejected.
- The server checks `event-signature` only when `request_hmac_validation_enabled = true` (off by default), but sign every request anyway.

The response holds pending deliveries in `sets`, keyed by delivery ID. Each value is a compact RS256 JWS with the same claims as a webhook delivery. Verify it as in [Receive and verify deliveries](#receive-and-verify-deliveries), then acknowledge it in a later poll:

```json
{
  "moreAvailable": false,
  "sets": {
    "f5f37c64-6130-4d44-9463-136c8bca4278": "<compact-RS256-JWS>"
  }
}
```

### Retry a failed webhook delivery

When a webhook delivery has used all its automatic retries and is `failed`, fix the receiver and queue it again with a token that has `notifications:subscriptions:write`:

```bash
curl -X POST "${API_BASE}/subscriptions/<subscription-id>/events/<delivery-id>/retry" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

The response is `202 Accepted`. A delivery that isn't `failed` with its retries exhausted returns `409`.

## 7. Inspect events and delivery history

In the portal, open **Event Notifications → Events** to see each event's payload and its deliveries, or open a subscription to see its deliveries. The read APIs need `notifications:events:read` or `notifications:subscriptions:read`:

| Operation | Method and path |
|---|---|
| List events | `GET /events` |
| Get an event | `GET /events/{eventId}` |
| List deliveries for an event | `GET /events/{eventId}/deliveries` |
| Get a delivery's attempt history | `GET /events/{deliveryId}/history` |
| List deliveries for a subscription | `GET /subscriptions/{subscriptionId}/events` |
| Get a subscription delivery's history | `GET /subscriptions/{subscriptionId}/events/{deliveryId}` |

## 8. Error codes

| Code | Meaning |
|---|---|
| `EN-4001` | Invalid request, for example a missing `group-id` or an invalid field |
| `EN-4002` | A required parameter is missing |
| `EN-4003` | The resource isn't in a state that allows the operation |
| `EN-4010` | Invalid signature, or a delivery or group that doesn't match |
| `EN-4040` | Resource not found |
| `EN-4041` | Topic not found |
| `EN-4042` | Delivery not found |
| `EN-4043` | Event not found |
| `EN-4090` | The resource already exists, or conflicts with an existing one |
| `EN-4220` | Webhook verification failed |
| `EN-5000` | Internal server error |
| `EN-5001` | The event couldn't be published |

## 9. Troubleshooting

| Symptom | Check |
|---|---|
| Event Notification pages aren't visible in the portal | Assign `dpdp-consent-admin` in the current tenant, then sign in again. |
| `401` or `403` from the API | The token must come from the URL's tenant and carry the operation's scope. Get a new token after changing scopes or roles. |
| Subscription creation returns `409` | An active subscription with the same name already exists in the tenant, or (when `conflict_checks_enabled = true`) an existing subscription has a conflicting delivery mode or overlapping purpose filter. |
| Subscription creation returns `422` | A subscription with a user topic must use the `all` filter. |
| Subscription stays `pending` or becomes `stale` | The callback must be reachable from Identity Server, on an allowed port, and return the exact challenge with HTTP `200`. Then retry verification. |
| A system topic gets no events | Check `lifecycle_events.publishing_enabled = true` and `consent_history.enabled = true`; consent expiry also needs `consent_expiry.enabled = true`. User topics need the `dpdpUserLifecycleEventHandler` event handler. |
| An event is created but has no deliveries | The subscription must be `active`, its topic and filter must match, and `group-id` must equal the tenant domain. |
| Webhook signature doesn't match | Compute the HMAC over the unmodified raw body, not only the nested payload. On macOS, use `awk '{print $NF}'` with `openssl dgst`, because LibreSSL prints only the hash. |
