---
title: Configuring Complaint Management
sidebar_position: 4
---
## Customizing complaint notification emails

`ComplaintCreated`, `ComplaintCommentAdded`, and `ComplaintAcknowledged` are standard IS
notification templates — edit them in Console under **Email Templates**, per tenant.

Placeholders: `{{reference-id}}`, `{{message-excerpt}}`, `{{data-principal-name}}`,
`{{actor-name}}`, `{{category-label}}`, `{{priority-label}}`, `{{status-label}}`,
`{{sla-label}}`, `{{action-url}}`, `{{recipient-role-label}}`, `{{headline-html}}`,
`{{footer-text}}`, `{{action-badge-html}}`, `{{logo-url}}`.

A template is written once, on first provisioning, and never rewritten — a Console edit is
permanent. The bundled default comes from
`<IS_HOME>/repository/conf/email/email-dpdp-config.xml` (edit it for new tenants, no rebuild
needed), but only affects tenants provisioned after the edit. The file is read once and cached
for the server's lifetime — **restart the server after editing it**, or new tenants keep
getting the old default until you do.

