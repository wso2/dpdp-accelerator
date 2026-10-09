---
title: Complaints
---

# Understanding Complaints

Privacy compliance isn't just about recording consent checkboxes-it's about responding responsibly when things go wrong. In real-world enterprise architectures, third-party sync failures, network timeouts, and misunderstandings can happen. When an individual raises a concern about how their data is being handled, organizations need a transparent, auditable process to investigate, communicate, and fix the issue.

The WSO2 DPDP Accelerator provides a built-in grievance handling system that connects Data Principals directly with grievance officers while linking complaints to underlying consent history and event audit trails.

---

## The People Involved

To understand grievance handling in practice, consider two key personas:

- **Priya (Data Principal):** An everyday user whose personal data is managed by CarePulse. She is assigned the `dpdp-consent-user` role (which provides the `complaints:read:self` and `complaints:write:self` scopes). When she notices an issue: such as receiving promotional messages after opting out-she uses the **My Complaints** section of the Consent Portal to file a complaint, upload evidence, and track updates directly.
- **Ravi (Grievance Officer):** CarePulse's Data Protection Officer, assigned the `dpdp-consent-dpo` role. Ravi manages incoming cases under **Complaint Management**, investigates root causes, exchanges messages with complainants, and drives cases to resolution.

---

## Real-World Scenario: When Revocation Fails Downstream

### The Background
Priya previously withdrew her consent for optional marketing communications in the Consent Portal. CarePulse recorded her revocation and published a `consent.revoke` event to notify its external email provider (`CloudEngage`).

However, due to a transient network outage, the event delivery to `CloudEngage` timed out after retries were exhausted. A couple of days later, Priya receives a marketing email. Naturally, she assumes her request was ignored: *"I already opted out of marketing emails, but I'm still receiving them."*

### 1. Submitting the Grievance
Rather than searching for buried support emails or submitting generic helpdesk tickets, Priya signs in to the Consent Portal:

1. Under **My Complaints**, she clicks **+ Submit a complaint**.
2. She selects **Consent Withdrawal Issue** and writes a clear summary of what happened.
3. She attaches a screenshot of the unwanted email showing the receipt timestamp.
4. Upon submission, the portal generates a unique Reference ID (e.g., `CMP-2026-00001`) and begins tracking the statutory response deadline.

Priya receives immediate confirmation with an assigned tracking number and a clear deadline, ensuring her issue won't be lost in an unmonitored queue.

### 2. Investigating Complaint
When Ravi opens the queue in **Complaint Management**, he doesn't treat Priya's complaint as an isolated ticket. Instead, he investigates by coordinating with the administrator to examine the accelerator's audit trails:

- **Consent Status History:** Verifies that Priya successfully revoked her consent.
- **Event Delivery Logs:** Shows that the `consent.revoke` event was published by CarePulse, but failed delivery to `CloudEngage` due to an HTTP connection timeout.

This gives Ravi an immediate, verifiable factual timeline: Priya correctly opted out, CarePulse generated the event, but the downstream processor missed the update. With these facts confirmed alongside the administrator, Ravi now has the exact technical context needed to resolve the problem.

### 3. Public Dialogue vs. Internal Notes
Investigating a grievance often requires collaboration between technical teams without confusing the complainant with internal jargon. The accelerator handles this by strictly separating public communication from internal investigation notes:

- **Public Messages (Shared Activity):** Ravi sends an immediate, clear response that Priya can see in her portal timeline:  
  > *"Hello Priya, we verified that your consent was revoked as intended. We identified a sync delay with our downstream email service and are manually updating your preferences now."*
- **Internal Notes (Officer-Only):** On the same complaint, Ravi adds an internal note visible strictly to users with the `dpdp-consent-dpo` role:  
  > *"CloudEngage webhook timed out. Contacted vendor ops to purge email from active campaign lists and verified delivery endpoint health."*

This allows the team to document technical root causes and vendor interactions without cluttering the Data Principal's view.

### 4. Resolution and Accountability
Once `CloudEngage` confirms that Priya's contact details have been purged from marketing lists:

1. Ravi transitions the complaint status to **`RESOLVED`** and leaves a closing explanation.
2. The complaint view locks against accidental edits, while allowing the Data Principal to reopen the case if a new message is sent.
3. The full history, including timestamps, attachments, comments, and status transitions, remains permanently archived for compliance reporting and regulatory audits.

---

:::note Accountability Principle
The accelerator's grievance handling tools provide the intake mechanisms, deadline tracking, role separation, and evidence trails necessary for compliance; they provide the factual record so organizations can investigate and resolve complaints fairly and transparently.
:::

---

## Role Separation for Complaints

Grievance handling enforces strict role boundaries between complainants and investigators:

| Role | Persona | Permissions and Scope |
|---|---|---|
| **`dpdp-consent-user`** | Data Principal (Priya) | **Self-service access only:** Can file, view, reply to, and track only their own complaints via `/me/complaints/*`. Cannot access complaints belonging to any other user. |
| **`dpdp-consent-dpo`** | Grievance Officer / DPO (Ravi) | **Tenant-wide oversight:** Can view and manage all complaints across the organization, send public replies, add internal notes, and update statuses via `/complaints/*`. |

---

## Next Steps

- **Hands-on Walkthrough:** Follow the step-by-step Try Out guide in [Try Out: Complaints](../try-out/complaint.md).
- **Configuration & APIs:** Learn about statutory deadlines, attachment limits, and email notification templates in the [Developer Grievance Guide](../grievances-guide.md).
