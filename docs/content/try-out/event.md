# Event Notifications

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

Event Notifications tell connected systems when something changes, such as a consent being revoked or a user account being deleted. Subscribers receive each event at a webhook listener or by polling for it.

This guide continues the **CarePulse** (Data Fiduciary) story and walks you through two flows:

- **[Notify a processor when a consent is revoked](#notify-a-processor-when-a-consent-is-revoked):** subscribe a webhook listener, revoke a consent, verify the delivery, and report that the processor acted on it.
- **[Publish your own event and poll for it](#publish-your-own-event-and-poll-for-it):** register a custom topic, publish an event through the REST API, and retrieve and acknowledge it by polling.

They use three building blocks:

- **Topic:** the kind of change you want to hear about, such as `consent.revoke`. The accelerator ships system topics for consent and user lifecycle changes.
- **Subscription:** a processor signing up for a topic. Each event reaches the processor either at its webhook listener or when it polls for it.
- **Event and delivery history:** a record of every event published and every attempt to deliver it, which you can look through in the Consent Portal.

Here are the system topics and what triggers each one:

| System topic | Triggered when |
|---|---|
| `consent.update` | A consent is updated, or an authorizer approves or rejects it |
| `consent.revoke` | A consent is revoked |
| `consent.expire` | The expiry reconciler records a consent as expired |
| `user.data.change` | A user's profile claims are updated |
| `user.account.delete` | A user account is deleted |

For the complete list of endpoints and schemas, see the [Event Notification API specification](https://github.com/wso2/dpdp-accelerator/blob/v1.0.0/dpdp-accelerator/internal-webapps/org.wso2.dpdp.accelerator.event.notifications.endpoint/src/main/resources/event-notifications.yaml).

## Before you begin

- Ensure WSO2 Identity Server with the DPDP Accelerator is installed and running.
- Create these users and assign their roles, as described in [Configuring Users and Roles](../install-and-setup/configuring-users-and-roles.md):
  - **Priya** (Data Principal), username `priya@example.com`: `dpdp-consent-user`
  - **Anika** (Data Fiduciary Administrator): `dpdp-consent-admin`
- [Create an application](../learn/managing-access.md#step-1-create-an-application) for each system below and [authorize it](../learn/managing-access.md#step-2-authorize-the-required-scopes) for the listed APIs and scopes:

  | Application | Role in this guide | APIs and scopes |
  |---|---|---|
  | **CarePulse** | Data Fiduciary: creates the test consent and publishes the custom event | **Consent Management V2 – Consents** (`internal_consent_mgt_consent_create`), **DPDP Event Notification API subscriptions** (`notifications:subscriptions:write`), **DPDP Event Notification API events** (`notifications:events:write`) |
  | **CloudEngage** | Data Processor: reports completion of a webhook delivery | **DPDP Event Notification API deliveries** (`notifications:event-deliveries:complete`) |
  | **MedExpress** | Data Processor: polls for and acknowledges events | **DPDP Event Notification API poll** (`notifications:events:poll`) |

  Giving each application only the scopes it needs keeps the publisher and the receivers separate.

Before running the API examples, set the base URL, the group ID, and the **CarePulse** application's access token. [Obtain a client-credentials token](../learn/managing-access.md#step-3-get-an-access-token) using the **CarePulse** application's credentials, with the scopes listed above. Each step that needs the **CloudEngage** or **MedExpress** token says so.

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```bash
export BASE_URL="https://localhost:9443"
export API_BASE="${BASE_URL}/api/dpdp/event-notifications/v1"
export GROUP_ID="carbon.super"
export ACCESS_TOKEN="<carepulse-client-credentials-token>"
```

</TabItem>
<TabItem value="macos" label="macOS">

```bash
export BASE_URL="https://localhost:9443"
export API_BASE="${BASE_URL}/api/dpdp/event-notifications/v1"
export GROUP_ID="carbon.super"
export ACCESS_TOKEN="<carepulse-client-credentials-token>"
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
$env:BASE_URL = "https://localhost:9443"
$env:API_BASE = "$env:BASE_URL/api/dpdp/event-notifications/v1"
$env:GROUP_ID = "carbon.super"
$env:ACCESS_TOKEN = "<carepulse-client-credentials-token>"
```

</TabItem>
</Tabs>

These values are for the super tenant. For a specific tenant, use `https://localhost:9443/t/<tenant-domain>` as the base URL and the tenant domain as the group ID, and get every token from that same tenant.

:::note Local testing with a self-signed certificate
Add `-k` to the `curl` commands if your local Identity Server uses the default self-signed certificate. On Windows, add `-SkipCertificateCheck` to `Invoke-RestMethod`; this needs PowerShell 7 or later, so with Windows PowerShell 5.1, trust the server's certificate instead. Never skip certificate checks in production — configure a trusted CA certificate, or pass it with `--cacert`.
:::

## Notify a processor when a consent is revoked

When a Data Principal revokes their consent, every Data Processor holding their data has to stop using it. In this flow:

- **Priya** (Data Principal) revokes a consent in **My Consents**.
- **Anika** (Data Fiduciary Administrator) registers the subscription under **Event Notifications → Subscriptions** and checks the delivery under **Events**.
- **CloudEngage** (Data Processor) receives the `consent.revoke` event at its webhook listener, which runs outside the portal.

### Step 1: Start the webhook listener

A webhook listener receives the subscription verification and each event delivery from Identity Server. Its API contract is in [`webhook-listener.openapi.yaml`](pathname:///examples/webhook-listener.openapi.yaml). For this try-out, use one of the sample listeners below.

Each sample listener is a single file, with nothing to install. It answers the verification challenge, checks every delivery's signature with the shared secret, and prints whatever it receives.

**Node.js** ([`webhook-listener.mjs`](pathname:///examples/webhook-listener.mjs)) needs Node.js 18 or later:

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```bash
node --version

curl -O https://raw.githubusercontent.com/wso2/dpdp-accelerator/main/docs/static/examples/webhook-listener.mjs

export SHARED_SECRET="carepulse-sample-secret-9d3e7b12"
node webhook-listener.mjs
```

</TabItem>
<TabItem value="macos" label="macOS">

```bash
node --version

curl -O https://raw.githubusercontent.com/wso2/dpdp-accelerator/main/docs/static/examples/webhook-listener.mjs

export SHARED_SECRET="carepulse-sample-secret-9d3e7b12"
node webhook-listener.mjs
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
node --version

curl.exe -O https://raw.githubusercontent.com/wso2/dpdp-accelerator/main/docs/static/examples/webhook-listener.mjs

$env:SHARED_SECRET = "carepulse-sample-secret-9d3e7b12"
node webhook-listener.mjs
```

</TabItem>
</Tabs>

**Python** ([`webhook-listener.py`](pathname:///examples/webhook-listener.py)) needs Python 3.9 or later:

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```bash
python3 --version

curl -O https://raw.githubusercontent.com/wso2/dpdp-accelerator/main/docs/static/examples/webhook-listener.py

export SHARED_SECRET="carepulse-sample-secret-9d3e7b12"
python3 webhook-listener.py
```

</TabItem>
<TabItem value="macos" label="macOS">

```bash
python3 --version

curl -O https://raw.githubusercontent.com/wso2/dpdp-accelerator/main/docs/static/examples/webhook-listener.py

export SHARED_SECRET="carepulse-sample-secret-9d3e7b12"
python3 webhook-listener.py
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
python --version

curl.exe -O https://raw.githubusercontent.com/wso2/dpdp-accelerator/main/docs/static/examples/webhook-listener.py

$env:SHARED_SECRET = "carepulse-sample-secret-9d3e7b12"
python webhook-listener.py
```

</TabItem>
</Tabs>

Once it's running, the listener prints its local address and confirms that HMAC signature checks are enabled:

```text
=============================================================
  DPDP Reference Webhook Listener (Node.js)
  Listening on: http://127.0.0.1:8443/dpdp/events
  HMAC Verification: ENABLED (shared secret configured)
  Press Ctrl+C to stop.
=============================================================
```

The address shown in the terminal is local to your machine. Identity Server must be able to reach the listener, so the subscription needs a callback URL it can reach. For local testing, see **Exposing a listener locally** below. Make sure to use a strong shared secret in a production environment.

:::info Understand the two signature checks

Event contents are signed, not encrypted. HTTPS protects the contents in transit. Two separate signatures have different purposes:

- **Event JWT signature:** Identity Server signs the event JWT with its tenant private key. A receiver should verify the JWT signature using the matching public key published through Identity Server's JWKS endpoint. This verifies the event's authenticity and integrity.
- **Delivery HMAC (`event-signature`):** webhook deliveries also carry an HMAC of the request body, calculated with the subscription's shared secret. The sample listener checks this HMAC, so someone who finds your callback URL can't send it fake events.

The shared secret is also used for the signed event's `payloadHash`, and to sign poll requests and completion reports. These checks don't encrypt event data or replace validating the event JWT signature. Avoid putting unnecessary personal data in payloads; use references, such as a consent ID, wherever possible.

:::

<details>
<summary><strong>Exposing a listener locally</strong></summary>

No public server to run the listener on? Try one of these:

- **An ngrok tunnel.** Set up [ngrok](https://ngrok.com/docs/start), run `ngrok http 8443` in a second terminal, then add `/dpdp/events` to the `https://` forwarding address it prints. That's your callback URL for Step 3. Your test events travel through ngrok, so stick to sample data, and don't use a tunnel for a real receiver.
- **Your machine's network IP**, if Identity Server can't reach the internet. Start the listener with `HOST` set to that IP, and use `http://<ip>:8443/dpdp/events` as the callback URL. Identity Server turns away private network addresses by default, so also set `allow_private_network_callback_targets = true` under `[dpdp_accelerator.event_notifications.webhook]` in `deployment.toml` and restart it. If you've set `allow_http_callback_url = false` in the same section, set it back to `true` for this test, because the callback uses HTTP. Keep `allow_private_network_callback_targets` set to `false` in production.

</details>

### Step 2: Create a consent to revoke

The portal can't create consents, so **CarePulse** creates one through the API, and **Priya** revokes it in the portal in Step 4.

1. **Get a purpose ID and an element ID.** Sign in to the Consent Portal as **Anika**, go to **Definitions → Purposes**, and open the `marketing-email` purpose. No purpose yet? Create one with an element first, as described in [Define a purpose and its data element](consent.md#define-a-purpose-and-its-data-element). Copy the **Purpose ID** from the purpose page, then open one of its elements and copy its ID as well.

   ![Purpose details page with the Purpose ID and its Contact email element](../../assets/images/try-out/event/00-purpose-id.png)

2. **Create an active consent for Priya** with the **CarePulse** token you set earlier. The token needs the `internal_consent_mgt_consent_create` scope. `subjectId` must belong to a user in the same tenant. On the highlighted line, swap in the **`<purpose-id>`** and **`<element-id>`** you copied:

   <Tabs groupId="operating-systems">
   <TabItem value="linux" label="Linux" default>

   ```bash {9}
   curl -X POST "${BASE_URL}/api/identity/consent-mgt/v2.0/consents" \
     -H "Authorization: Bearer ${ACCESS_TOKEN}" \
     -H "Content-Type: application/json" \
     -d '{
       "subjectId": "priya@example.com",
       "serviceId": "CarePulse-client",
       "language": "en",
       "purposes": [
         { "id": "<purpose-id>", "elements": [ { "id": "<element-id>" } ] }
       ]
     }'
   ```

   </TabItem>
   <TabItem value="macos" label="macOS">

   ```bash {9}
   curl -X POST "${BASE_URL}/api/identity/consent-mgt/v2.0/consents" \
     -H "Authorization: Bearer ${ACCESS_TOKEN}" \
     -H "Content-Type: application/json" \
     -d '{
       "subjectId": "priya@example.com",
       "serviceId": "CarePulse-client",
       "language": "en",
       "purposes": [
         { "id": "<purpose-id>", "elements": [ { "id": "<element-id>" } ] }
       ]
     }'
   ```

   </TabItem>
   <TabItem value="windows" label="Windows">

   ```powershell {7}
   $Body = @'
   {
     "subjectId": "priya@example.com",
     "serviceId": "CarePulse-client",
     "language": "en",
     "purposes": [
       { "id": "<purpose-id>", "elements": [ { "id": "<element-id>" } ] }
     ]
   }
   '@

   Invoke-RestMethod -Method Post `
     -Uri "$env:BASE_URL/api/identity/consent-mgt/v2.0/consents" `
     -Headers @{ Authorization = "Bearer $env:ACCESS_TOKEN" } `
     -ContentType "application/json" `
     -Body $Body | ConvertTo-Json -Depth 10
   ```

   </TabItem>
   </Tabs>

   There are no other authorizers, so the consent is `ACTIVE` straight away. The response includes its `id`; use your own ID rather than the example IDs shown later.

### Step 3: Subscribe the listener to consent revocations

1. Sign in to the Consent Portal as **Anika**.
2. Open **Event Notifications → Subscriptions** and select **Register Subscription**.
3. Enter a **Subscription Name**, such as `CloudEngage - consent revocations`.
4. Set **Topic Category** to **Consent Topics**, and select `consent.revoke` under **Topics**.
5. Set **Consent Purpose Filter Mode** to **Specific Purposes**, and select `marketing-email` under **Consent Purposes**. The listener then hears only about revocations of consents that include this purpose. To hear about every revocation, choose **All Purposes** instead.
6. Set **Delivery Mode** to **Webhook**, enter your callback URL from Step 1 as the **Webhook Callback URL** (for example, `https://<your-ngrok-address>/dpdp/events`), and enter `carepulse-sample-secret-9d3e7b12` as the **Shared Secret**.
7. Select **Register Subscription**.

![Register Subscription dialog filled in for consent.revoke with the marketing-email purpose](../../assets/images/try-out/event/01-register-subscription.png)

Right away, Identity Server sends the listener a verification challenge. As soon as the listener answers it, the subscription turns **Active**:

![Subscriptions list with the new consent.revoke subscription in Active status](../../assets/images/try-out/event/02-subscription-active.png)

Over in the listener's terminal, you'll see the challenge it answered:

```text
[INFO] ================ [SUBSCRIPTION VERIFICATION] ================
[INFO] Verification Payload:
{
  "type": "subscription.verification",
  "subscriptionId": "e4e10144-e76a-4d4b-9e80-70747df4f650",
  "topics": [
    "consent.revoke"
  ],
  "challenge": "ec09e7a8-fb5b-4eec-9670-c3c1fbe529b9"
}
[INFO] Verified subscription 'e4e10144-e76a-4d4b-9e80-70747df4f650' for topics: ["consent.revoke"]
[INFO] <-- Returned HTTP 200 OK with challenge: 'ec09e7a8-fb5b-4eec-9670-c3c1fbe529b9'
```

#### Using the REST API (optional)

To register the subscription through the API instead, use the **CarePulse** token. It must include the `notifications:subscriptions:write` scope.

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```bash
curl -X POST "${API_BASE}/subscriptions" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "CloudEngage - consent revocations",
    "topics": ["consent.revoke"],
    "filter": {
      "type": "specific",
      "purposes": ["marketing-email"]
    },
    "delivery": {
      "mode": "webhook",
      "callbackUrl": "https://<your-ngrok-address>/dpdp/events",
      "sharedSecret": "carepulse-sample-secret-9d3e7b12"
    }
  }'
```

</TabItem>
<TabItem value="macos" label="macOS">

```bash
curl -X POST "${API_BASE}/subscriptions" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "CloudEngage - consent revocations",
    "topics": ["consent.revoke"],
    "filter": {
      "type": "specific",
      "purposes": ["marketing-email"]
    },
    "delivery": {
      "mode": "webhook",
      "callbackUrl": "https://<your-ngrok-address>/dpdp/events",
      "sharedSecret": "carepulse-sample-secret-9d3e7b12"
    }
  }'
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
$Body = @'
{
  "name": "CloudEngage - consent revocations",
  "topics": ["consent.revoke"],
  "filter": {
    "type": "specific",
    "purposes": ["marketing-email"]
  },
  "delivery": {
    "mode": "webhook",
    "callbackUrl": "https://<your-ngrok-address>/dpdp/events",
    "sharedSecret": "carepulse-sample-secret-9d3e7b12"
  }
}
'@

Invoke-RestMethod -Method Post `
  -Uri "$env:API_BASE/subscriptions" `
  -Headers @{ Authorization = "Bearer $env:ACCESS_TOKEN" } `
  -ContentType "application/json" `
  -Body $Body | ConvertTo-Json -Depth 10
```

</TabItem>
</Tabs>

The subscription name must be unique in the tenant, so use a different name if you already registered one in the portal.

### Step 4: Revoke the consent

1. Sign in to the Consent Portal as **Priya**.
2. Open **My Consents** and select the consent you created. It should show **Active**.

   ![Consent details page for an active marketing-email consent with the Revoke button](../../assets/images/try-out/event/03-consent-active.png)

3. Select **Revoke**, then **Revoke Consent** to confirm.

   ![Confirm Revocation dialog](../../assets/images/try-out/event/04-confirm-revocation.png)

The consent now shows **Revoked**. An application can also revoke a consent on **Priya**'s behalf, using her own user token (not the **CarePulse** token), through `POST /api/users/v1/me/consents/<consent-id>/revoke`.

### Step 5: Confirm that the processor was notified

Within a few seconds, the event reaches the listener. The sample checks the delivery HMAC and prints the decoded event, including the consent ID **Priya** just revoked:

```text
[INFO] ==================== [EVENT DELIVERY] ====================
[INFO] Delivery-Id:     58328a80-4e74-44c8-80cc-927414e4159a
[INFO] Event-Signature: sha256=e8c0443706bca2452875971e20bdc909867a12298bbd99907d36edcbf4092245
[INFO] HMAC-SHA256 signature verified successfully.
[INFO] Decoded Event Payload:
{
  "iss": "https://localhost:9443/oauth2/token",
  "sub": "carbon.super",
  "aud": "dpdp-event-notifications",
  ...
  "payload": {
    "deliveryId": "58328a80-4e74-44c8-80cc-927414e4159a",
    "eventId": "131b9ab4-c6f2-4652-ae70-023dafc35c8f",
    "subscriptionId": "e4e10144-e76a-4d4b-9e80-70747df4f650",
    "orgId": "carbon.super",
    "groupId": "carbon.super",
    "topic": "consent.revoke",
    "eventPayload": {
      "consentId": "c540bcee-c9d8-4919-8fdc-e2b9723971f5",
      "previousStatus": "ACTIVE"
    }
  }
}
[INFO] <-- Returned HTTP 202 Accepted
```

You can find the same event in the portal too. As **Anika**, open **Event Notifications → Events**, and search for the `eventId` from the listener output, or look for the newest `consent.revoke` event:

![Events list showing the consent.revoke event for the marketing-email purpose with one subscriber](../../assets/images/try-out/event/06-events-list.png)

Open it to see the payload and the delivery to your subscription, marked **Delivered**:

![Event details page with the consent.revoke payload and a Delivered webhook delivery](../../assets/images/try-out/event/07-event-delivered.png)

Notice that the event carries only the consent ID and its previous status, none of **Priya**'s personal data. A real processor would use `consentId` to find the data it holds under that consent and stop using it.

Expected result: revoking the consent publishes a `consent.revoke` event, and **CloudEngage**'s listener receives it because the subscription matches both the topic and the `marketing-email` purpose. **Delivered** means the listener accepted the notification; **CloudEngage** is still responsible for acting on it. For more on this, see [receiver responsibilities](../event-notification-guide.md#acceptance-retries-and-processing-responsibilities).

### Step 6: Report completion (optional)

You can stop after Step 5 if you only want to verify webhook delivery. Continue here to see how a processor reports that it has acted on an event.

A `2xx` response from the listener confirms receipt, not completion. After **CloudEngage** has updated **Priya**'s marketing preferences, it can submit a signed completion report, so the accelerator records that confirmation alongside the delivery.

[Obtain a client-credentials token](../learn/managing-access.md#step-3-get-an-access-token) using the **CloudEngage** application's credentials, with the `notifications:event-deliveries:complete` scope. Use the `deliveryId` from your Step 5 listener output, and the same shared secret you used for the subscription.

The signature is an HMAC-SHA256, made with the shared secret, over `v1`, `completion`, the delivery ID, and the exact request body, each on its own line. This binds the report to one delivery. `completionEvidence` must be an HTTPS URL.

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```bash
export CLOUDENGAGE_TOKEN="<cloudengage-access-token>"
export SHARED_SECRET="carepulse-sample-secret-9d3e7b12"
DELIVERY_ID="<delivery-id-from-step-5>"
COMPLETION_BODY='{"completionStatus":"completed","completionEvidence":"https://cloudengage.example/evidence/receipt.pdf"}'
COMPLETION_SIGNATURE="sha256=$(printf 'v1\ncompletion\n%s\n%s' "${DELIVERY_ID}" "${COMPLETION_BODY}" \
  | openssl dgst -sha256 -hmac "${SHARED_SECRET}" | awk '{print $NF}')"

curl -X POST "${API_BASE}/deliveries/${DELIVERY_ID}/completion" \
  -H "Authorization: Bearer ${CLOUDENGAGE_TOKEN}" \
  -H "group-id: ${GROUP_ID}" \
  -H "event-signature: ${COMPLETION_SIGNATURE}" \
  -H "Content-Type: application/json" \
  -d "${COMPLETION_BODY}"
```

</TabItem>
<TabItem value="macos" label="macOS">

```bash
export CLOUDENGAGE_TOKEN="<cloudengage-access-token>"
export SHARED_SECRET="carepulse-sample-secret-9d3e7b12"
DELIVERY_ID="<delivery-id-from-step-5>"
COMPLETION_BODY='{"completionStatus":"completed","completionEvidence":"https://cloudengage.example/evidence/receipt.pdf"}'
COMPLETION_SIGNATURE="sha256=$(printf 'v1\ncompletion\n%s\n%s' "${DELIVERY_ID}" "${COMPLETION_BODY}" \
  | openssl dgst -sha256 -hmac "${SHARED_SECRET}" | awk '{print $NF}')"

curl -X POST "${API_BASE}/deliveries/${DELIVERY_ID}/completion" \
  -H "Authorization: Bearer ${CLOUDENGAGE_TOKEN}" \
  -H "group-id: ${GROUP_ID}" \
  -H "event-signature: ${COMPLETION_SIGNATURE}" \
  -H "Content-Type: application/json" \
  -d "${COMPLETION_BODY}"
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
$env:CLOUDENGAGE_TOKEN = "<cloudengage-access-token>"
$env:SHARED_SECRET = "carepulse-sample-secret-9d3e7b12"
$DeliveryId = "<delivery-id-from-step-5>"
$CompletionBody = '{"completionStatus":"completed","completionEvidence":"https://cloudengage.example/evidence/receipt.pdf"}'
$Hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($env:SHARED_SECRET))
$SigningInput = "v1`ncompletion`n$DeliveryId`n$CompletionBody"
$CompletionSignature = 'sha256=' + [BitConverter]::ToString($Hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($SigningInput))).Replace('-', '').ToLower()

Invoke-RestMethod -Method Post `
  -Uri "$env:API_BASE/deliveries/$DeliveryId/completion" `
  -Headers @{
    Authorization     = "Bearer $env:CLOUDENGAGE_TOKEN"
    "group-id"        = $env:GROUP_ID
    "event-signature" = $CompletionSignature
  } `
  -ContentType "application/json" `
  -Body $CompletionBody
```

</TabItem>
</Tabs>

A successful call returns `204 No Content`. Reopen the event in **Event Notifications → Events**: the delivery still shows **Delivered**, now with **Completion Evidence** recorded alongside it. Submitting a second completion for the same delivery returns `409 Conflict` (`EN-4090`). For the other completion statuses and error responses, see [Submit webhook delivery completion](../event-notification-guide.md#submit-webhook-delivery-completion).

## Publish your own event and poll for it

The accelerator publishes consent and user lifecycle events on its own. Your applications can publish events too, about their own business changes, on topics you define.

Say **CarePulse**'s order system publishes an event whenever a customer updates their delivery preferences. **MedExpress**, the delivery processor (Data Processor), sits behind a firewall and can't expose a webhook. Instead, it polls for new events and acknowledges each one once it has processed it. In this flow:

- **Anika** (Data Fiduciary Administrator) registers the custom topic and the poll subscription in the Consent Portal, and checks the result.
- **CarePulse** publishes the event through the REST API, using its own token.
- **MedExpress** polls for the event and acknowledges it through the REST API, using its own token.

[Obtain a client-credentials token](../learn/managing-access.md#step-3-get-an-access-token) using the **MedExpress** application's credentials, with the `notifications:events:poll` scope:

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```bash
export MEDEXPRESS_TOKEN="<medexpress-access-token>"
```

</TabItem>
<TabItem value="macos" label="macOS">

```bash
export MEDEXPRESS_TOKEN="<medexpress-access-token>"
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
$env:MEDEXPRESS_TOKEN = "<medexpress-access-token>"
```

</TabItem>
</Tabs>

### Step 1: Register a custom topic

1. Sign in to the Consent Portal as **Anika**.
2. Open **Event Notifications → Topics** and select **Register Topic**.
3. Enter `delivery.preferences.update` as the **Topic Name**, add a **Description**, and select **Register Topic**.

![Register Topic dialog for delivery.preferences.update](../../assets/images/try-out/event/10-register-topic.png)

### Step 2: Subscribe a poll receiver

1. Open **Event Notifications → Subscriptions** and select **Register Subscription**.
2. Enter a **Subscription Name**, such as `MedExpress - delivery preferences`.
3. Set **Topic Category** to **Custom Topics**, and select `delivery.preferences.update` under **Topics**.
4. Keep **Consent Purpose Filter Mode** as **All Purposes**.
5. Set **Delivery Mode** to **Poll**.
6. Enter `medexpress-sample-secret-4f8a2c91` as the **Shared Secret**. **MedExpress** signs its poll requests with it. We use a sample value so the commands below work as they are. For a real receiver, select the generate icon at the end of the field instead, and keep the secret somewhere safe.
7. Select **Register Subscription**.

![Register Subscription dialog for a poll subscription with the sample shared secret](../../assets/images/try-out/event/11-register-poll-subscription.png)

Poll subscriptions don't need verification, so this one is **Active** straight away. Copy its **Subscription ID** with the copy icon in the list:

![Subscriptions list with the active poll subscription](../../assets/images/try-out/event/12-poll-subscription-active.png)

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```bash
export SUBSCRIPTION_ID="<subscription-id>"
export SHARED_SECRET="medexpress-sample-secret-4f8a2c91"
```

</TabItem>
<TabItem value="macos" label="macOS">

```bash
export SUBSCRIPTION_ID="<subscription-id>"
export SHARED_SECRET="medexpress-sample-secret-4f8a2c91"
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
$env:SUBSCRIPTION_ID = "<subscription-id>"
$env:SHARED_SECRET = "medexpress-sample-secret-4f8a2c91"
```

</TabItem>
</Tabs>

### Step 3: Publish an event

Now play **CarePulse**'s order system and publish the change with the **CarePulse** token. It must include the `notifications:events:write` scope. The `group-id` header has to match the subscription's group, which is the tenant domain (`carbon.super` for the super tenant). An event with any other group ID is stored, but reaches no subscriber.

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```bash
curl -X POST "${API_BASE}/events" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "group-id: ${GROUP_ID}" \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "delivery.preferences.update",
    "payload": {
      "customerReference": "cust-0001",
      "change": "DELIVERY_WINDOW_UPDATED"
    }
  }'
```

</TabItem>
<TabItem value="macos" label="macOS">

```bash
curl -X POST "${API_BASE}/events" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "group-id: ${GROUP_ID}" \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "delivery.preferences.update",
    "payload": {
      "customerReference": "cust-0001",
      "change": "DELIVERY_WINDOW_UPDATED"
    }
  }'
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
$Body = @'
{
  "topic": "delivery.preferences.update",
  "payload": {
    "customerReference": "cust-0001",
    "change": "DELIVERY_WINDOW_UPDATED"
  }
}
'@

Invoke-RestMethod -Method Post -Uri "$env:API_BASE/events" `
  -Headers @{
    Authorization = "Bearer $env:ACCESS_TOKEN"
    "group-id"    = $env:GROUP_ID
  } `
  -ContentType "application/json" `
  -Body $Body | ConvertTo-Json -Depth 10
```

</TabItem>
</Tabs>

You'll get back the stored event and its `eventId`:

```json
{
  "eventId": "f0b66a0d-edcb-4d94-94ee-70ce33b34f20",
  "orgId": "carbon.super",
  "groupId": "carbon.super",
  "topicId": "3064830b-7249-4fa6-9b83-59d7215af1c2",
  "payload": "{\"customerReference\":\"cust-0001\",\"change\":\"DELIVERY_WINDOW_UPDATED\"}",
  "occurredAt": 1791199075832,
  "createdAt": 1791199075832
}
```

Try to keep personal data out of the payload. Send a reference the receiver can look up instead, the way `customerReference` does here.

### Step 4: Poll for the event

Next, switch to **MedExpress** and poll with the **MedExpress** token. The first poll can have an empty body:

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```bash
POLL_BODY=''
POLL_SIGNATURE="sha256=$(printf %s "${POLL_BODY}" | openssl dgst -sha256 -hmac "${SHARED_SECRET}" | awk '{print $NF}')"

curl -X POST "${API_BASE}/events/poll" \
  -H "Authorization: Bearer ${MEDEXPRESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "group-id: ${GROUP_ID}" \
  -H "subscription-id: ${SUBSCRIPTION_ID}" \
  -H "event-signature: ${POLL_SIGNATURE}" \
  -d "${POLL_BODY}"
```

</TabItem>
<TabItem value="macos" label="macOS">

```bash
POLL_BODY=''
POLL_SIGNATURE="sha256=$(printf %s "${POLL_BODY}" | openssl dgst -sha256 -hmac "${SHARED_SECRET}" | awk '{print $NF}')"

curl -X POST "${API_BASE}/events/poll" \
  -H "Authorization: Bearer ${MEDEXPRESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "group-id: ${GROUP_ID}" \
  -H "subscription-id: ${SUBSCRIPTION_ID}" \
  -H "event-signature: ${POLL_SIGNATURE}" \
  -d "${POLL_BODY}"
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
$PollBody = ''
$Hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($env:SHARED_SECRET))
$PollSignature = 'sha256=' + [BitConverter]::ToString($Hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($PollBody))).Replace('-', '').ToLower()

Invoke-RestMethod -Method Post -Uri "$env:API_BASE/events/poll" `
  -Headers @{
    Authorization     = "Bearer $env:MEDEXPRESS_TOKEN"
    "group-id"        = $env:GROUP_ID
    "subscription-id" = $env:SUBSCRIPTION_ID
    "event-signature" = $PollSignature
  } `
  -ContentType "application/json" `
  -Body $PollBody | ConvertTo-Json -Depth 10
```

</TabItem>
</Tabs>

`event-signature` is an HMAC-SHA256 of the exact request body, made with the subscription's shared secret. For an empty body, it's calculated over zero bytes. The server only checks it when `request_hmac_validation_enabled = true` under `[dpdp_accelerator.event_notifications.polling]`, and that's off by default. Sign every request anyway, so your receiver keeps working when someone turns the check on.

The response holds pending deliveries in the `sets` object. Each key is a delivery ID, and its value is a signed event (a compact JWS) carrying the same contents as a webhook delivery. Save the delivery ID, because you'll need it in Step 5 to acknowledge the event:

```json
{
  "moreAvailable": false,
  "sets": {
    "07f813ee-391a-4cd2-aa70-2ebe420fc0d0": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIs..."
  }
}
```

Before you trust an event, verify its signature against Identity Server's JWKS endpoint. Once it checks out, you'll find your event in the `payload` claim, under `eventPayload`:

```json
"eventPayload": {
  "change": "DELIVERY_WINDOW_UPDATED",
  "customerReference": "cust-0001"
}
```

### Step 5: Acknowledge the event

Once **MedExpress** has processed the event, it sends the delivery ID back in the next poll's `ack` array, signing the new body the same way:

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```bash
DELIVERY_ID="<delivery-id-from-step-4>"
POLL_BODY="{\"ack\": [\"${DELIVERY_ID}\"], \"maxEvents\": 20}"
POLL_SIGNATURE="sha256=$(printf %s "${POLL_BODY}" | openssl dgst -sha256 -hmac "${SHARED_SECRET}" | awk '{print $NF}')"

curl -X POST "${API_BASE}/events/poll" \
  -H "Authorization: Bearer ${MEDEXPRESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "group-id: ${GROUP_ID}" \
  -H "subscription-id: ${SUBSCRIPTION_ID}" \
  -H "event-signature: ${POLL_SIGNATURE}" \
  -d "${POLL_BODY}"
```

</TabItem>
<TabItem value="macos" label="macOS">

```bash
DELIVERY_ID="<delivery-id-from-step-4>"
POLL_BODY="{\"ack\": [\"${DELIVERY_ID}\"], \"maxEvents\": 20}"
POLL_SIGNATURE="sha256=$(printf %s "${POLL_BODY}" | openssl dgst -sha256 -hmac "${SHARED_SECRET}" | awk '{print $NF}')"

curl -X POST "${API_BASE}/events/poll" \
  -H "Authorization: Bearer ${MEDEXPRESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "group-id: ${GROUP_ID}" \
  -H "subscription-id: ${SUBSCRIPTION_ID}" \
  -H "event-signature: ${POLL_SIGNATURE}" \
  -d "${POLL_BODY}"
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
$DeliveryId = "<delivery-id-from-step-4>"
$PollBody = '{"ack": ["' + $DeliveryId + '"], "maxEvents": 20}'
$Hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($env:SHARED_SECRET))
$PollSignature = 'sha256=' + [BitConverter]::ToString($Hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($PollBody))).Replace('-', '').ToLower()

Invoke-RestMethod -Method Post -Uri "$env:API_BASE/events/poll" `
  -Headers @{
    Authorization     = "Bearer $env:MEDEXPRESS_TOKEN"
    "group-id"        = $env:GROUP_ID
    "subscription-id" = $env:SUBSCRIPTION_ID
    "event-signature" = $PollSignature
  } `
  -ContentType "application/json" `
  -Body $PollBody | ConvertTo-Json -Depth 10
```

</TabItem>
</Tabs>

An acknowledged event isn't returned again, so with nothing else pending, the response comes back empty:

```json
{"moreAvailable":false,"sets":{}}
```

If the receiver couldn't process an event, report it in `setErrs` instead of `ack`. A delivery ID goes in one or the other, never both.

### Step 6: Check the result in the portal

Back in the Consent Portal as **Anika**, open **Event Notifications → Events** and find the `delivery.preferences.update` event. Its delivery to the poll subscription now shows **Acknowledged**:

![Event details page with the custom event payload and an Acknowledged poll delivery](../../assets/images/try-out/event/13-poll-event-acknowledged.png)

When you're done, tidy up by deleting the subscription and deregistering the topic in the portal.

Expected result: the published event is queued for the poll subscription, **MedExpress** picks it up on its first poll, and once it's acknowledged it no longer shows up in later polls. For the full request options, errors, and HMAC settings, see [Register a poll subscription](../event-notification-guide.md#register-a-poll-subscription) and [Poll event deliveries](../event-notification-guide.md#poll-event-deliveries).

## Next Steps

- **[Learn: Understanding Event Notifications](../learn/event.md):** the concepts and example behind these flows.
- **[Event Notification Guide](../event-notification-guide.md):** the full reference for topics, subscriptions, receivers, signatures, and troubleshooting.
