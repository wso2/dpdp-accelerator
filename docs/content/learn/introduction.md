# Learn the WSO2 DPDP Accelerator

This guide helps you understand the WSO2 DPDP Accelerator through practical examples and common use cases.

Each section focuses on a specific need and shows how the accelerator supports it. The sections are connected, so you can see how different capabilities work together.

:::note
The examples in this guide are intended to help you understand how the accelerator can be used. You can adapt the configuration and workflows to suit your organization's requirements.
:::

## Start with your running instance

Once you have set up the DPDP Accelerator, you can start exploring it using the following URLs.

| Component                   | URL |
|-----------------------------|---|
| **Identity Server Console** | `https://<HOST>/console` |
| **Consent Portal**          | `https://<HOST>/consent-portal/` |

These are the main starting points for exploring the DPDP Accelerator. You can use the sections in this guide to understand what you can do with these components and how the different capabilities work together.

:::note Using a tenant?
The URLs above are for the super tenant. If you're working within a specific tenant, insert `/t/<tenant-domain>` before the path, for example `https://<HOST>/t/<tenant-domain>/console`. See WSO2's [multi-tenancy guide](https://is.docs.wso2.com/en/latest/guides/multitenancy/) for more on how tenants work.
:::

## What you'll learn

### Managing API Access

Learn how access is managed for portal users, administrators, and API integrations.


### Consent

Learn how users can give, review, and revoke consent, and how these actions are managed by the accelerator.

### Complaint

Learn how a Data Principal can raise a complaint and how an organization can manage and respond to it.

### Event Notifications

Learn how connected systems are notified when important changes occur, such as consent being updated or revoked, or user data changing.

### Federated Login

Learn how users can sign in to the Consent Portal with their existing accounts from an identity provider such as Microsoft Entra ID or Asgardeo, and how their groups determine their DPDP roles.

## Next Steps

- [Managing Access](managing-access.md) — roles, permissions, and direct API access.
- [Consent](consent.md) — the consent lifecycle, from defining a purpose to withdrawing consent.
- [Complaint](complaint.md) — how grievances are raised, tracked, and resolved.
- [Event Notifications](event.md) — how connected systems stay informed of important changes.
- [Federated Login](federated-login/introduction.md) — sign-in with existing identity provider accounts.
