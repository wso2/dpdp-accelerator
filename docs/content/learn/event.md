# Understanding Event Notifications

Giving someone the ability to revoke consent or delete their account is only part of the job. Other systems that hold or process that person's data also need to know when something changes so they can take the appropriate action.

**Event Notifications** helps the accelerator keep connected systems informed when important changes occur. Instead of relying on someone to manually notify each system, the accelerator publishes an event that subscribed systems can receive and act on.

For example, Priya may withdraw her consent for marketing communications. CarePulse needs to know about the change, but so does **CloudEngage**, the Data Processor that sends marketing emails on CarePulse's behalf. When the consent is revoked, the accelerator publishes an event so CloudEngage can receive the notification and take the required action.

The same approach applies to other lifecycle changes, such as account deletion or changes to a person's data.

This page explains how Event Notifications work through the example below. To try the flows yourself, see [Try Out → Event Notifications](../try-out/event.md).

## The people and systems in this example

- **Priya** is a Data Principal who uses CarePulse, an online healthcare service.
- **CarePulse** is the Data Fiduciary. It decides why Priya's data is processed.
- **CloudEngage** is a Data Processor that sends marketing emails for CarePulse.
- **MedExpress** is a Data Processor that delivers CarePulse's orders.
- **Anika** is CarePulse's **Data Fiduciary administrator**. She manages Event Notifications in the Consent Portal.

## How it works

![Consent lifecycle and custom event-publication paths converging on the Event Notification service](../../assets/images/diagrams/dpdp-consent-event-flow.svg)

Four ideas cover everything on this page:

- **Topic:** a kind of change, such as `consent.revoke`. The accelerator
  provides topics for consent and account changes, and CarePulse can add its
  own.
- **Subscription:** a processor's request to hear about a topic. It says where
  to send messages and how, either by calling the processor's webhook or by
  letting the processor collect them.
- **Event:** one change that happened, such as Priya revoking a particular
  consent.
- **Delivery:** one event on its way to one subscriber. An event with three
  subscribers has three deliveries, each tracked on its own.

## Priya withdraws consent, and CloudEngage stops emailing her

Priya opens the Consent Portal and revokes her consent for marketing emails.
The accelerator publishes a `consent.revoke` event straight away. Nobody at
CarePulse has to do anything.

CloudEngage subscribed to `consent.revoke` with a webhook, so the accelerator
calls CloudEngage's server with the event. The event names the revoked
consent but carries none of Priya's personal data. CloudEngage looks up the
consent and removes Priya's email address from its campaigns.

**Try it:** [Notify a processor when a consent is revoked](../try-out/event.md#notify-a-processor-when-a-consent-is-revoked)

## Only the right processors hear about it

MedExpress delivers Priya's orders, which has nothing to do with marketing. It
shouldn't learn that she stopped marketing emails.

Each consent event carries the consent's purposes, such as `marketing-email`.
Anika set up CloudEngage's subscription to receive revocations only for that
purpose, so MedExpress never sees this one. A subscription can listen to all
purposes, to specific purposes, or to all purposes except some. Account events
aren't tied to a purpose, so their subscriptions always listen to all of them.

**Try it:** [Subscribe the listener to consent revocations](../try-out/event.md#step-3-subscribe-the-listener-to-consent-revocations)

## MedExpress can't accept webhooks, so it collects its events

MedExpress runs behind a firewall that blocks incoming calls, so a webhook
can't reach it. Instead, its subscription uses polling. Its system asks the
accelerator for new events whenever it's ready, processes them, and then
acknowledges each one so it isn't sent again.

The events MedExpress needs aren't about consent. When a customer updates their
delivery preferences, CarePulse's order system publishes its own event on a
topic Anika created, `delivery.preferences.update`. Custom events travel
through exactly the same subscriptions and deliveries as the built-in ones.

**Try it:** [Publish your own event and poll for it](../try-out/event.md#publish-your-own-event-and-poll-for-it)

## CloudEngage checks that the message is genuine

Anyone who finds CloudEngage's webhook address could send it a fake "consent
revoked" message. So CloudEngage checks every message before acting on it, in
two ways:

- The accelerator signs each event with CarePulse's key, and CloudEngage
  verifies that signature. It proves the event came from CarePulse's Identity
  Server and wasn't changed on the way.
- CloudEngage and the accelerator share a secret for the subscription. Each
  delivery carries a signature made with that secret, which proves the message
  was meant for CloudEngage's own subscription.

Events are signed, not encrypted, so anyone holding one can read it. That's
why HTTPS protects them on the way, and why events should carry references
like a consent ID rather than personal data.

**Try it:** [What the shared secret protects](../try-out/event.md#step-1-start-the-webhook-listener)

## CloudEngage's server was down

CloudEngage's server is offline for maintenance when Priya revokes her consent.
The delivery fails, so the accelerator tries again after 5 seconds, then 15,
then 45, waiting three times longer each time. After five retries it marks the
delivery **Failed**. Every attempt and the response it got is recorded.

Anika opens the event in **Event Notifications → Events** and sees the failed
delivery. The subscription's delivery history shows each attempt, so she can
tell whether CloudEngage's server couldn't be reached or rejected the request.
Once CloudEngage is back up, she selects **Retry** on the delivery to send it
again.

A subscription can also be marked **Stale**. That happens when its webhook
address never answered the verification check that confirms it's ready to
receive events. It's a problem with the address, not with any one delivery.

**Try it:** [Confirm that the processor was notified](../try-out/event.md#step-5-confirm-that-the-processor-was-notified)
shows where these records live. For every delivery state, see
[Inspect events and delivery history](../event-notification-guide.md#8-inspect-events-and-delivery-history).

## Priya deletes her account

Later, Priya deletes her CarePulse account. The accelerator publishes a
`user.account.delete` event, which tells subscribed processors that the account
is gone so they can clean up what they hold. The event carries only her user
ID.

Deleting the account removes Priya's Identity Server user. It doesn't
automatically erase her consent history, complaints, or past events. CarePulse
decides how long to keep those records under its own retention rules.

## What events don't do

An event tells a processor that something changed. It doesn't make the
processor act. **Delivered** only means CloudEngage's server accepted the
message. Removing Priya's address is still CloudEngage's job.

When CarePulse needs proof that the work was done, the processor can report
back once it has finished processing, and the delivery then shows
**Completed**. Deliveries can also arrive out of order, so processors should
handle each one on its own.

## Next Steps

- [Try Out → Event Notifications](../try-out/event.md): run these flows yourself, step by
  step.
- [Event Notification Guide](../event-notification-guide.md): the full
  reference for topics, subscriptions, receivers, signatures, and
  troubleshooting.
