---
title: Configuring Users and Roles
---

# Configuring Users and Roles

When WSO2 Identity Server starts with the DPDP Accelerator, and whenever a new tenant is created, the accelerator automatically provisions three predefined roles: `dpdp-consent-admin`, `dpdp-consent-dpo`, and `dpdp-consent-user`.

This guide walks you through:
1. [Creating a new user](#1-creating-a-user)
2. [Understanding accelerator roles](#2-accelerator-roles-overview)
3. [Assigning accelerator roles](#3-assigning-accelerator-roles)

---

## 1. Creating a User

Follow these steps to create a user in the Identity Server Console.

### Step 1: Sign in to the Console

Open your browser and navigate to the WSO2 Identity Server Console:
```text
https://<host>:9443/console
```
Log in with your administrator account (e.g., `admin@wso2.com`).

![Sign in to WSO2 Identity Server Console](../../assets/images/install-and-setup/sign-in-console.png)

### Step 2: Navigate to Users

In the left navigation sidebar, expand **User Management** and click **Users**.

![Navigate to User Management > Users](../../assets/images/install-and-setup/navigate-to-users.png)

### Step 3: Add a new user

On the top-right of the Users page, click **+ Add User** and select **Single User**.

![Click Add User and select Single User](../../assets/images/install-and-setup/add-single-user.png)

### Step 4: Fill in basic user details

Enter the user's details in the **Basic Details** step:

- **Username (Email):** Enter the user's email address (for example, `janedoe@wso2.com`).

:::info Note
Under the accelerator's default configuration, usernames are email addresses.
:::

- **First Name:** Enter the user's first name (e.g., `Jane`).
- **Last Name:** Enter the user's last name (e.g., `Doe`).
- **Password Method:** Select **Set a password for the user** and enter a password that meets the displayed complexity requirements (or click **Generate**).

Click **Next** to proceed. You can optionally assign user groups on the next step or click **Next** to skip.

![Fill in basic user details and password](../../assets/images/install-and-setup/create-user-details.png)

### Step 5: Finish creation

On the final **Invitation** step:

1. Click **Close** to complete user creation.

![Complete user creation](../../assets/images/install-and-setup/user-creation-credentials.png)

---

## 2. Accelerator Roles Overview

The accelerator provides three predefined roles designed to separate user, administrative, and data protection responsibilities:

| Role | Target Persona | Key Capabilities |
|---|---|---|
| **`dpdp-consent-admin`** | System & Organization Administrators | Full oversight over consents, purpose/element catalog management, and event notification subscriptions. Complaint handling is not included; it belongs to `dpdp-consent-dpo`. |
| **`dpdp-consent-dpo`** | Data Protection Officers (DPO) | Organization-wide complaint inspection, review, internal notes, and complaint resolution without administrative consent privileges. |
| **`dpdp-consent-user`** | Data Principals / End Users | Self-service account deletion (`DELETE /scim2/Me`), personal complaint filing and tracking, and personal consent history audit views. |

:::tip
- **Basic Consent Access:** End users can log into the Consent Portal, view their active consents, and authorize or revoke consents without any role assigned. Assign `dpdp-consent-user` when they need to file complaints, view audit history, or delete their accounts.
- **Role Assignment:** `dpdp-consent-admin` is not automatically assigned to `dpdp-consent-dpo`. If a user requires both administrative oversight and DPO complaint handling capabilities, assign both roles explicitly.
:::

For detailed information on permissions and OAuth scopes associated with each role, see the [Role Management Guide](../role-guide.md).

---

## 3. Assigning Accelerator Roles

After creating the user, assign them to the appropriate DPDP role.

### Step 1: Navigate to Roles

In the left navigation sidebar, expand **User Management** and click **Roles**.

You will see the accelerator's predefined roles:
- `dpdp-consent-admin`
- `dpdp-consent-dpo`
- `dpdp-consent-user`

Select the role you want to assign (for example, click the edit icon on **`dpdp-consent-admin`**).

![Navigate to User Management > Roles and select a role](../../assets/images/install-and-setup/navigate-to-roles.png)

### Step 2: Open the Users tab

In the role details view:
1. Click the **Users** tab from the top tab menu.
2. Click the **+ Assign User** button.

![Open the Users tab and click Assign User](../../assets/images/install-and-setup/role-users-tab.png)

### Step 3: Select and assign the user

In the **Assign Users** dialog:
1. Search for or locate the user (e.g. `janedoe@wso2.com`).
2. Check the box next to the user's name.
3. Click **Save**.

![Select user and save role assignment](../../assets/images/install-and-setup/assign-user-to-role.png)

The user is now assigned to the role and can access the corresponding features in the Consent Portal and APIs.
