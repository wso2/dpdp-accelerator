# Role Management Guide

This guide outlines the roles provided by the DPDP Accelerator, their intended use cases, the permissions they grant, and how to manage them.

## Overview

The DPDP Accelerator introduces three roles to manage access to the Consent Portal and its associated APIs. These roles separate regular user privileges, complaint-handling duties, and administrative control.

**Important:** While the roles themselves are created automatically by the system, **membership is not**. You must manually assign these roles to users via the Identity Server Console.

---

## 👥 Available Roles

### 1. `dpdp-consent-user`

**Intended For:** Regular end-users of the Consent Portal.

| Use Case | Actions / Permissions |
| :--- | :--- |
| **Self-Service Account Deletion** | Grants the `account:self:delete` scope, allowing the user to delete their own account via the portal profile menu. |
| **Personal Complaint Management** | Grants `complaints:read:self` and `complaints:write:self`, allowing users to file and track their own complaints. |
| **Personal Consent History** | Grants `consent:status-history:view:self` and `consent:history:view:self`, allowing users to view the status and snapshot history of their own consents. |

> [!NOTE]
> **Basic Consent Management requires no role.** Users can sign in, view their dashboard, and authorize or revoke their own consents without any specific portal role. Assign `dpdp-consent-user` when they also need personal consent history, account deletion, or complaint features.

### 2. `dpdp-consent-admin`

**Intended For:** Organization administrators.

| Use Case | Actions / Permissions |
| :--- | :--- |
| **Administrative Consent Management** | Ability to view and manage consents for other users within the tenant. |
| **Catalog Management** | Ability to create, edit, and manage the Purposes and Elements catalog. |
| **Consent History Oversight** | Grants all `consent:status-history:view:*` and `consent:history:view:*` scopes for viewing both personal and tenant-wide consent history. |
| **Event Notification Management** | Grants read and write access to topics, subscriptions, and events. Polling and delivery completion are receiver operations and are not permissions of this portal role. |
| **Global Complaint Oversight** | Grants `complaints:read:any` and `complaints:write:any`, allowing the admin to view every complaint in the organization, including internal notes and status transitions. |

> [!TIP]
> Administrators do **not** automatically receive the `dpdp-consent-user` role. If an administrator needs to be able to delete their own account, you must assign both `dpdp-consent-admin` and `dpdp-consent-user` to them.

### 3. `dpdp-consent-dpo`

**Intended For:** Data Protection Officers and complaint-handling personnel who require organization-wide complaint access without full administrative access.

| Use Case | Actions / Permissions |
| :--- | :--- |
| **Global Complaint Oversight** | Grants `complaints:read:any` and `complaints:write:any`, allowing the DPO to view and manage every complaint in the organization, including internal notes and status transitions. |

The DPO role does not grant Consent Management, consent-history, Event Notification, catalog-management, or self-service account-deletion permissions.

### Consent Management roles and scopes

These are Identity Server's native Consent Management scopes. Basic
self-service consent access is scoped to the signed-in user and only requires
`internal_login`. Administrative catalog and consent operations require the
scopes below:

| Consent Management operation | Required scope(s) | Admin | User | DPO |
| :--- | :--- | :---: | :---: | :---: |
| Sign in and manage own consents | `internal_login` | ✅ | ✅ | ✅ |
| View or manage other users' consents | `internal_consent_mgt_consent_view`, `internal_consent_mgt_consent_create`, `internal_consent_mgt_consent_update` | ✅ | ❌ | ❌ |
| View purposes | `internal_consent_mgt_purpose_view` | ✅ | ❌ | ❌ |
| Create, update, or delete purposes | `internal_consent_mgt_purpose_create`, `internal_consent_mgt_purpose_update`, `internal_consent_mgt_purpose_delete` | ✅ | ❌ | ❌ |
| View elements | `internal_consent_mgt_element_view` | ✅ | ❌ | ❌ |
| Create or delete elements | `internal_consent_mgt_element_create`, `internal_consent_mgt_element_delete` | ✅ | ❌ | ❌ |

The automatically provisioned **DPDP Consent API Invoker** application is
authorized only for the native consents resource. It is not a portal role and
does not receive the purposes or elements scopes.

### Consent History roles and scopes

Consent history is exposed through the accelerator's separate history API:

| Consent-history operation | Required scope | Admin | User | DPO |
| :--- | :--- | :---: | :---: | :---: |
| View own status-audit history | `consent:status-history:view:self` | ✅ | ✅ | ❌ |
| View own full snapshot history | `consent:history:view:self` | ✅ | ✅ | ❌ |
| View tenant-wide status-audit history | `consent:status-history:view:any` | ✅ | ❌ | ❌ |
| View tenant-wide full snapshot history | `consent:history:view:any` | ✅ | ❌ | ❌ |

### Grievance roles and scopes

The complaint API separates self-service access from organization-wide
Complaint Management access:

| Grievance operation | Required scope(s) | Admin | User | DPO |
| :--- | :--- | :---: | :---: | :---: |
| Create, view, reply to, or transition own complaints | `complaints:read:self`, `complaints:write:self` (user) or `complaints:read:any`, `complaints:write:any` (DPO/admin) | ✅ | ✅ | ✅ |
| View or manage any tenant complaint | `complaints:read:any`, `complaints:write:any` | ✅ | ❌ | ✅ |

The `dpdp-consent-admin` and `dpdp-consent-dpo` roles use the management
surface for complaints, including internal notes and organization-wide
search. A dedicated integration can be assigned only the complaint scopes it
needs.

### Account self-service roles and scopes

| Account operation | Required scope | Admin | User | DPO |
| :--- | :--- | :---: | :---: | :---: |
| Delete the signed-in user's own account | `account:self:delete` | ❌ | ✅ | ❌ |

Administrators do not receive this scope unless `dpdp-consent-user` is also
assigned to the same user.

### Event Notification roles and scopes

Of the three automatically provisioned portal roles, only `dpdp-consent-admin` receives Event Notification management permissions. Identity Server also registers the polling and delivery-completion scopes, but they are not assigned to any of these portal roles:

| Event Notification operation | Required scope | Admin | User | DPO |
| :--- | :--- | :---: | :---: | :---: |
| View topics | `notifications:topics:read` | ✅ | ❌ | ❌ |
| Create or deregister topics | `notifications:topics:write` | ✅ | ❌ | ❌ |
| View subscriptions and their delivery history | `notifications:subscriptions:read` | ✅ | ❌ | ❌ |
| Create, verify, or delete subscriptions | `notifications:subscriptions:write` | ✅ | ❌ | ❌ |
| View events and delivery history | `notifications:events:read` | ✅ | ❌ | ❌ |
| Publish events | `notifications:events:write` | ✅ | ❌ | ❌ |
| Poll event deliveries | `notifications:events:poll` | ❌ | ❌ | ❌ |
| Submit delivery completion evidence | `notifications:event-deliveries:complete` | ❌ | ❌ | ❌ |

The final two scopes exist in Identity Server so a dedicated receiver client can use the corresponding APIs. Their existence does not grant them to `dpdp-consent-admin`, `dpdp-consent-user`, or `dpdp-consent-dpo`.

The five predefined lifecycle topics are a separate system-publishing path.
When automatic lifecycle publication is enabled, the accelerator publishes
their events as the matching consent or user lifecycle actions occur. This does
not require a user holding `notifications:events:write`; that scope controls
explicit calls to `POST /events`, not internal lifecycle publication.

For application-to-application integrations, do not give a publisher or receiver the full `dpdp-consent-admin` role. Instead, manually create least-privilege roles such as:

| Suggested role | Assign these scopes |
| :--- | :--- |
| `event-publisher` | `notifications:events:write` |
| `event-receiver` | `notifications:events:poll`, `notifications:event-deliveries:complete` |

The `event-publisher` and `event-receiver` roles are recommendations and are **not** automatically provisioned by the accelerator. See the [Event Notification Guide](event-notification-guide.md) for the API workflow and token usage.

---

## 🛠️ Management Workflow

### What is automatically created?

The accelerator automatically provisions the following for every tenant (including the super tenant):

- [x] The **DPDP Consent Portal** OIDC application.
- [x] The **DPDP Consent API Invoker** machine-to-machine application when its
  separate provisioning setting is enabled.
- [x] The `dpdp-consent-user` role.
- [x] The `dpdp-consent-admin` role.
- [x] The `dpdp-consent-dpo` role.
- [x] Necessary API authorizations and scopes for these roles.

### What must the user create/do?

The following steps must be performed manually by the operator in the **WSO2 IS Console**:

1. **Assign Roles to Users**:
   - Navigate to **User Management** $\rightarrow$ **Users**.
   - Select a user $\rightarrow$ **Roles**.
   - Add `dpdp-consent-user` for regular users, `dpdp-consent-dpo` for Data Protection Officers, or `dpdp-consent-admin` for administrators.

2. **Verify Permissions**:
   If you have updated the accelerator to a new version, you can force the system to add any newly introduced permissions to existing roles by updating the tenant properties in the Console (**Tenant Management** $\rightarrow$ **Update**).

---

## 🔍 Feature availability and required scopes

| Feature | Required scope(s) | No Role | `dpdp-consent-user` | `dpdp-consent-dpo` | `dpdp-consent-admin` |
| :--- | :--- | :---: | :---: | :---: | :---: |
| Sign in to Portal | `internal_login` | ✅ | ✅ | ✅ | ✅ |
| Manage own consents | `internal_login` | ✅ | ✅ | ✅ | ✅ |
| View own consent history | `consent:status-history:view:self`, `consent:history:view:self` | ❌ | ✅ | ❌ | ✅ |
| View tenant-wide consent history | `consent:status-history:view:any`, `consent:history:view:any` | ❌ | ❌ | ❌ | ✅ |
| Delete own account | `account:self:delete` | ❌ | ✅ | ❌ | ❌ |
| Manage own complaints | `complaints:read:self`, `complaints:write:self` (user) or `complaints:read:any`, `complaints:write:any` (DPO/admin) | ❌ | ✅ | ✅ | ✅ |
| Manage others' consents | `internal_consent_mgt_consent_view`, `internal_consent_mgt_consent_create`, `internal_consent_mgt_consent_update` | ❌ | ❌ | ❌ | ✅ |
| Manage Purposes catalog | `internal_consent_mgt_purpose_view`, `internal_consent_mgt_purpose_create`, `internal_consent_mgt_purpose_update`, `internal_consent_mgt_purpose_delete` | ❌ | ❌ | ❌ | ✅ |
| Manage Elements catalog | `internal_consent_mgt_element_view`, `internal_consent_mgt_element_create`, `internal_consent_mgt_element_delete` | ❌ | ❌ | ❌ | ✅ |
| View topics | `notifications:topics:read` | ❌ | ❌ | ❌ | ✅ |
| Manage topics | `notifications:topics:write` | ❌ | ❌ | ❌ | ✅ |
| View subscriptions | `notifications:subscriptions:read` | ❌ | ❌ | ❌ | ✅ |
| Manage subscriptions | `notifications:subscriptions:write` | ❌ | ❌ | ❌ | ✅ |
| View events and delivery history | `notifications:events:read` | ❌ | ❌ | ❌ | ✅ |
| Publish events | `notifications:events:write` | ❌ | ❌ | ❌ | ✅ |
| Poll event deliveries | `notifications:events:poll` | ❌ | ❌ | ❌ | ❌ |
| Submit delivery completion | `notifications:event-deliveries:complete` | ❌ | ❌ | ❌ | ❌ |
| Manage all complaints | `complaints:read:any`, `complaints:write:any` | ❌ | ❌ | ✅ | ✅ |
