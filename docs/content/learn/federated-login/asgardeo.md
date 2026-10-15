# Add Asgardeo Login

Integrate Asgardeo with the DPDP Consent Portal through WSO2 Identity Server to let users sign in with their existing Asgardeo accounts. Their Asgardeo group memberships determine the DPDP roles assigned to them.

:::note
- Before you begin, make sure WSO2 Identity Server 7.3.0 (update level 17 or later) and the DPDP Accelerator are installed. WSO2 Identity Server must also be able to reach `api.asgardeo.io`.
- The examples use the super tenant. Replace `<IS_HOST>` with your WSO2 Identity Server hostname, such as `localhost:9443`, and `<ASGARDEO_ORG>` with your Asgardeo organization name.
:::

## Register WSO2 Identity Server on Asgardeo

First, register WSO2 Identity Server as an OpenID Connect application in Asgardeo.

1. Sign in to the [Asgardeo Console](https://console.asgardeo.io) as an administrator of your organization.

2. Go to **Applications** → **New Application** → **Traditional Web Application**.

3. Enter the following application details.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/asgardeo-register-application.png').default} alt="Traditional Web Application form with the name, OpenID Connect selected and the authorized redirect URL https://localhost:9443/commonauth" />
   </figure>

   | Parameter | Description |
   | :--- | :--- |
   | Name | Enter a descriptive name for the application, such as `WSO2 DPDP Consent Portal`. |
   | Protocol | Select **OpenID Connect**. |
   | Authorized redirect URL | Enter the URL that users should return to after signing in.<br />**Value:** `https://<IS_HOST>/commonauth` |

4. Click **Create** to create the application.

5. On the application's **Protocol** tab, make sure **Code** is selected under **Allowed grant types**, then copy the **Client ID** and **Client secret**. You will need both when configuring the connection in WSO2 Identity Server.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/asgardeo-protocol.png').default} alt="Protocol tab with the client ID and client secret blurred and the Code grant type selected" />
   </figure>

   :::note[Important]
   Store the client secret securely. Do not commit it to source control or share it in screenshots, tickets or chat.
   :::

6. Note the endpoints listed on the application's **Info** tab. They follow this pattern:

   | Endpoint | URL |
   | :--- | :--- |
   | Authorization | `https://api.asgardeo.io/t/<ASGARDEO_ORG>/oauth2/authorize` |
   | Token | `https://api.asgardeo.io/t/<ASGARDEO_ORG>/oauth2/token` |
   | JWKS | `https://api.asgardeo.io/t/<ASGARDEO_ORG>/oauth2/jwks` |
   | Logout | `https://api.asgardeo.io/t/<ASGARDEO_ORG>/oidc/logout` |

Configure the user attributes that Asgardeo shares with WSO2 Identity Server.

1. Open the application's **User Attributes** tab.

2. Select **Email** and mark it as **Mandatory**.

3. Select **Groups**. Leave the other scopes cleared, because DPDP needs only the user's email address and groups.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/asgardeo-user-attributes.png').default} alt="User Attribute Selection with only the Email and Groups scopes selected" />
   </figure>

   :::note
   The `groups` claim lists the names of the user's groups. WSO2 Identity Server uses it to assign the user's DPDP roles.
   :::

4. Under **Subject**, set **Subject attribute** to **Email**. Depending on the Console version, you may first need to select **Assign alternate subject identifier**.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/asgardeo-subject.png').default} alt="Subject section with Assign alternate subject identifier selected and Email as the subject attribute" />
   </figure>

   :::note
   By default, Asgardeo sends an internal user ID as the subject. With Email as the subject, consent records are stored against the user's email address, which people and administrators can recognize.
   :::

5. Click **Update**.

## Select the role groups in Asgardeo

The Consent Portal displays only the pages and actions permitted by a user's DPDP role. In this setup, users are managed in Asgardeo, so access is granted by mapping Asgardeo groups to DPDP roles in WSO2 Identity Server. You can then manage access by adding users to or removing them from the relevant groups in Asgardeo.

| Asgardeo group (example) | DPDP role | For |
| :--- | :--- | :--- |
| `dpdp-consent-users` | `dpdp-consent-user` | Data Principals who manage their own consents and complaints |
| `dpdp-consent-admins` | `dpdp-consent-admin` | Administrators who manage purposes, data elements and consent oversight |
| `dpdp-consent-dpos` | `dpdp-consent-dpo` | Data Protection Officers who handle complaints |

1. Go to **User Management** → **Groups** → **New Group**.

2. Enter a group name, select the users to add, and complete the wizard. Repeat for each role.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/asgardeo-new-group.png').default} alt="Create Group form with the group name dpdp-consent-users and the Add Users list" />
   </figure>

3. Go to **User Management** → **Users**, and add each user to the group that matches the access they need. Each user must have an email address.

## Register the Asgardeo IdP

Next, add Asgardeo as an identity provider (IdP) in WSO2 Identity Server.

1. In the WSO2 Identity Server Console (`https://<IS_HOST>/console`), open **Connections**.

2. Click **New Connection**, select **Standard-Based IdP**, and click **Create**.

3. Select **OpenID Connect** and enter the following values.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/asgardeo-is-connection-configuration.png').default} alt="Configuration step of the Standard based Connections wizard with the client ID, client secret and the two endpoint URLs" />
   </figure>

   | Parameter | Description |
   | :--- | :--- |
   | Identity provider name | `Asgardeo` |
   | Client ID | The client ID obtained from Asgardeo. |
   | Client secret | The client secret obtained from Asgardeo. |
   | Authorization endpoint URL | `https://api.asgardeo.io/t/<ASGARDEO_ORG>/oauth2/authorize` |
   | Token endpoint URL | `https://api.asgardeo.io/t/<ASGARDEO_ORG>/oauth2/token` |
   | Mode of certificate configuration | JWKS endpoint |
   | JWKS endpoint URL | `https://api.asgardeo.io/t/<ASGARDEO_ORG>/oauth2/jwks` |

4. Click **Finish** to create the identity provider connection.

5. Open the new connection, select the **Settings** tab, enter the following values, and click **Update**.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/asgardeo-is-connection-settings.png').default} alt="Connection Settings tab showing the Authorized redirect URL, the Logout Endpoint URL and the openid, email and groups scopes" />
   </figure>

   | Parameter | Description |
   | :--- | :--- |
   | Logout Endpoint URL | `https://api.asgardeo.io/t/<ASGARDEO_ORG>/oidc/logout` |
   | Scopes | `openid email groups`. Add `email` and `groups` next to the default `openid`. |

   :::tip
   The **Authorized redirect URL** shown here must exactly match the redirect URL registered in Asgardeo. If the values differ, update the Asgardeo application to use the URL shown in WSO2 Identity Server.
   :::

6. Leave the **Just-in-Time Provisioning** setting disabled. It is disabled by default, so federated users are not provisioned as local user accounts in WSO2 Identity Server.

## Configure user attributes

Map the claims received from Asgardeo to the corresponding WSO2 Identity Server attributes.

1. On the **Asgardeo** connection, go to the **Attributes** tab.

2. Click **Add Attribute Mapping** and configure the following mappings.

   | Asgardeo claim | WSO2 Identity Server claim | Purpose |
   | :--- | :--- | :--- |
   | `email` | Email Addresses (`http://wso2.org/claims/emailAddresses`) | Identifies the user |
   | `groups` | Groups (`http://wso2.org/claims/groups`) | Role mapping |

3. Set **Subject Attribute** to `email`, then click **Update**. If the **Subject** section doesn't appear, reload the tab after saving the mappings.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/asgardeo-is-connection-attributes.png').default} alt="Attributes tab with the email and groups mappings and the Subject Attribute set to email" />
   </figure>

## Map Asgardeo groups to DPDP roles

Map each Asgardeo group to the corresponding DPDP role.

1. Open the **Asgardeo** connection and select the **Groups** tab.

2. Click **New Group** and create an entry for each role group. Use the Asgardeo group name exactly, because Asgardeo sends the group name in the `groups` claim.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/asgardeo-is-connection-groups.png').default} alt="Connection Groups tab with a group named dpdp-consent-admins" />
   </figure>

3. Go to **User Management** → **Roles** and select `dpdp-consent-user`.

4. On the **Groups** tab, find **External Groups**, expand **Asgardeo**, and select the matching group under **Assign groups**.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/asgardeo-is-role-external-groups.png').default} alt="Groups tab of the dpdp-consent-admin role with the Asgardeo external group dpdp-consent-admins assigned" />
   </figure>

5. Click **Update**.

6. Repeat steps 3–5 for `dpdp-consent-admin` and `dpdp-consent-dpo`.

The accelerator has already attached the right permissions (scopes) to each role.

## Enable Asgardeo login

1. In the WSO2 Identity Server Console, open **Applications** and select **DPDP Consent Portal**.

2. Open the **Login Flow** tab, click **Add Sign In Option**, select **Asgardeo**, and click **Add**.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/asgardeo-is-portal-login-flow.png').default} alt="Login Flow tab of the DPDP Consent Portal with a Sign In With Asgardeo option below the username and password form" />
   </figure>

3. Click **Update** to save the login-flow configuration.

## Try it out

Test the integration with an Asgardeo account that belongs to one of the configured role groups.

1. Open a private or incognito browser window and go to `https://<IS_HOST>/consent-portal`.

2. On the WSO2 Identity Server sign-in page, select **Sign In With Asgardeo**.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/asgardeo-is-portal-sign-in.png').default} alt="WSO2 Identity Server sign-in page for the Consent Portal with a Sign In With Asgardeo button" />
   </figure>

3. Sign in with an Asgardeo account that belongs to one of the configured role groups. If Asgardeo asks for consent to share your attributes, allow it.

4. Confirm the following:
   - The Consent Portal shows the user's email address as the username.
   - The pages for the user's role are available.
   - **My Consents** lists the consents recorded for the user's email address.
   - No new user appears under **User Management** → **Users** in WSO2 Identity Server.

:::note
Group membership is evaluated at sign-in. After changing a user's group membership, have the user sign out and sign in again.
:::

## Next Steps

- [Federated Login](introduction.md) — how federated login works and what applies to every identity provider.
- [Add Microsoft Entra ID Login](entra-id.md) — set up another identity provider.
- [Managing Access](../managing-access.md) — the DPDP roles and the permissions each one grants.
- [Consent](../consent.md) — how users give, review, and revoke consent in the Consent Portal.
