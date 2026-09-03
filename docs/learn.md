# Learn the WSO2 DPDP Accelerator through real stories

This guide explains the accelerator through the people and systems that use it.
Each story starts with a real-world need, follows the decision a user makes,
and then shows which part of the accelerator supports that decision. The
stories are intentionally connected: a consent decision can create an event,
and a complaint can reveal what happened after that event was delivered.

These are product examples, not legal advice. Your organization must decide
which purposes, notices, retention rules, roles, and response times apply to
its processing activities.

## Meet the people in the stories

- **Priya**, a Data Principal, uses an online healthcare service.
- **CarePulse**, the Data Fiduciary, decides why her data is processed.
- **MedExpress** and **CloudEngage**, Data Processors, perform delivery and
  marketing work for CarePulse.
- **Anika**, a privacy administrator, maintains the catalog and monitors
  tenant-wide records.
- **Ravi**, a grievance officer, investigates complaints for CarePulse.

The Consent Portal is where Priya, Anika, and Ravi review or manage their
permitted tasks. A connected application creates the initial consent, and
external processors consume events through a webhook or polling client.

## Story 1: Describe what data is needed

CarePulse wants to send optional product updates. Before it asks Priya for
permission, Anika models the request in the catalog:

1. In **Elements**, she creates `contact-email` with a clear display name and
   description.
2. In **Purposes**, she creates `marketing-email` version `1.0` and associates
   the email element with it.
3. CarePulse's consent experience can now explain what the data is and why it
   is needed instead of presenting an unexplained field request.

The catalog describes the request; it does not create a consent by itself.
The connected application or Consent Management API creates the consent using
the catalog definition.

**Try it:** Follow [Flow 1 in the Tryout Flows guide](tryout-flows.md#flow-1-define-a-purpose-and-its-data-element).

## Story 2: Give consent with a clear choice

Priya books a consultation in CarePulse. The application presents separate
choices for consultation, prescription delivery, and optional wellness tips.
She approves the first two and leaves wellness tips unchecked.

The accelerator records the consent decision and its snapshot. Priya can open
**My Consents** to see what she agreed to, which service requested it, its
state, and when it expires. An administrator with the appropriate scope can
review the tenant-wide record in **All Consents**.

The portal is a review and management surface. It does not decide CarePulse's
lawful purpose or replace the application's notice and consent-collection
experience.

## Story 3: Change or withdraw the decision

Several weeks later, Priya no longer wants wellness messages. She opens her
consent history, reviews the current state, and withdraws the relevant
authorization. CarePulse can inspect the status history to answer what changed,
when it changed, and which version of the purpose was in force.

Withdrawal changes the consent record; it does not automatically erase every
copy held by a processor. That follow-up is coordinated through the lifecycle
event in the next story and through CarePulse's contracts and retention rules.

**Try it:** See the consent lifecycle and history flows in
[Tryout Flows](tryout-flows.md#consent-management).

## Story 4: Keep processors in sync with an event

Priya's withdrawal publishes a `consent.revoke` event. CloudEngage has a
subscription for that topic, so it receives the event and stops using her
contact details in its campaign pipeline.

An administrator configures the delivery contract in **Event Notifications**:

- **Topics** identify the kind of change, such as `consent.revoke` or
  `user.account.delete`.
- **Subscriptions** identify the receiving processor and choose **Webhook** or
  **Poll** delivery.
- **Events** and the subscription details show delivery attempts, responses,
  retries, and the final state.

A webhook receiver is responsible for authenticating and processing the
request. A polling receiver claims and acknowledges work through the polling
API. Creating a topic does not publish an event, and registering a subscription
does not make a processor enforce the decision; those systems must implement
their own action after receiving the notification.

**Try it:** Follow the [Event Notification Guide](event-notification-guide.md)
and the automatic lifecycle flow in [Tryout Flows](tryout-flows.md).

## Story 5: Investigate a failed delivery

CloudEngage's endpoint is temporarily unavailable when the revoke event is
published. The delivery appears as failed or stale, and the subscription's
delivery history records the attempts and response information. Anika can use
the event and subscription views to determine whether the receiver was
unreachable, rejected the request, or failed to complete it.

This evidence helps CarePulse coordinate with CloudEngage, but it does not
silently mark the processor as compliant. The receiver must recover and apply
the change, and the organization's operational policy determines when a
delivery is escalated.

## Story 6: Raise and resolve a grievance

Priya receives one promotional SMS after the withdrawal and believes the
processor used her data incorrectly. She opens **My Complaints**, describes the
issue, and attaches the message if needed. Ravi opens the case in **Complaints**
and can assign it, exchange messages, add internal handling information, and
move it through the configured status workflow.

Ravi checks the consent status history and the `consent.revoke` delivery
history together. That gives him one narrative: Priya withdrew permission,
CarePulse published the event, CloudEngage was unavailable, and the message
was sent before the processor applied the change. Ravi records the resolution
and communicates it to Priya.

The grievance tools support intake and accountability; they do not determine
the legal outcome of a complaint.

## Story 7: Delete an account and coordinate the aftermath

Priya later requests self-service account deletion. After the account is
deleted, the `user.account.delete` event can notify connected systems such as
CloudEngage and MedExpress. Each receiver applies the organization's deletion,
retention, and audit policy.

The accelerator provides the account action and notification building blocks.
It does not promise that every downstream copy is automatically erased or that
retention exceptions are resolved without a policy decision.

## Story 8: Give each person only the work they need

Anika receives the `dpdp-consent-admin` role for catalog, tenant-wide consent,
and Event Notification administration. Ravi receives `dpdp-consent-dpo` for
tenant-wide complaint handling. Priya receives `dpdp-consent-user` for her own
consents, complaints, and optional account deletion.

The portal hides navigation that the current token cannot use, while the API
still enforces the scopes. After a role assignment, the user must sign in
again so the new scopes are present in the access token.

Read the [Role Management Guide](role-guide.md) before assigning production
access, and use a dedicated integration role when a processor should only
publish or consume events.

## Follow the complete journey

The stories form one continuous flow:

```text
Catalog definition
      -> consent choice
      -> update or withdrawal
      -> lifecycle event
      -> processor delivery
      -> delivery evidence
      -> grievance investigation
      -> account lifecycle and downstream action
```

Use the [Quickstart](quickstart.md) to prepare a tenant, then use
[Tryout Flows](tryout-flows.md) for executable portal and API walkthroughs.
For deployment decisions, see the [Setup Guide](setup-guide.md) and
[Configuration Guide](configuration-guide.md).
