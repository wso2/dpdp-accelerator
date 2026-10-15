# Add Microsoft Entra ID Login

This section illustrates how to integrate Microsoft Entra ID with the DPDP Consent Portal through WSO2 Identity Server to let users sign in with their existing Entra ID accounts. Their Entra ID group memberships determine the DPDP roles assigned to them.

:::note
- Before you begin, make sure WSO2 Identity Server 7.3.0 (update level 17 or later) and the DPDP Accelerator are installed. WSO2 Identity Server must also be able to reach `login.microsoftonline.com`.
- The examples use the super tenant. Replace `<IS_HOST>` with your WSO2 Identity Server hostname, such as `localhost:9443`.
  :::

## Register WSO2 Identity Server on Microsoft Entra ID

First, register WSO2 Identity Server as an OpenID Connect application in Microsoft Entra ID.

1. Sign in to the [Microsoft Entra admin center](https://entra.microsoft.com) with an account assigned the Application Administrator or Cloud Application Administrator role.

2. Go to **Entra ID** → **App registrations** → **New registration**.

3. Enter the following application registration details.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/entra-register-application.png').default} alt="Register an application form with the name, the single tenant account type and a Web redirect URI ending in /commonauth" width="765" />
   </figure>

   | Parameter | Description |
   | :--- | :--- |
   | Name | Enter a descriptive name for the application, such as `WSO2 DPDP Consent Portal`. |
   | Supported account types | Select who can sign in. This guide uses **Accounts in this organizational directory only (Single tenant)**, which is recommended. |
   | Redirect URI | Select **Web** as the platform and enter the redirect URI that users should return to after signing in.<br />**Value:** `https://<IS_HOST>/commonauth` |

   :::info
   Select **Web**, not **Single-page application (SPA)**. After the user signs in, Entra ID redirects back to WSO2 Identity Server with an authorization code. WSO2 Identity Server then exchanges that code for tokens directly from its own server, using the client secret. Entra ID allows this server-side exchange only for redirect URIs registered under **Web**.
   :::

4. Click **Register** to create the application.

5. On the application **Overview** page, copy the **Application (client) ID** and **Directory (tenant) ID**. You will need both when configuring the connection in WSO2 Identity Server.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/entra-app-overview.png').default} alt="App registration Overview page showing the Application (client) ID, Object ID and Directory (tenant) ID fields" width="774" />
   </figure>

Next, create a client secret that WSO2 Identity Server can use to authenticate to the application.

1. Go to **Manage** → **Certificates & secrets** → **Client secrets** and click **New client secret**.

2. Enter a description and select an expiry period that aligns with your organization's secret-rotation policy.

3. Click **Add** to add the client secret.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/entra-client-secret.png').default} alt="Certificates and secrets page with the Client secrets tab, showing the Value and Secret ID columns" width="788" />
   </figure>

   :::note[Important]
   Copy the generated **Value**, not the **Secret ID**. Entra ID displays the secret value only once, so store it securely. Record the expiry date and replace the secret before it expires.
   :::

Configure the claims that Microsoft Entra ID includes in the ID token sent to WSO2 Identity Server.

1. Go to **Manage** → **Token configuration**.

2. Click **Add optional claim**, select the **ID** token type, choose `upn` (User Principal Name), and click **Add**. If prompted, select **Turn on the Microsoft Graph profile permission** and click **Add**.

3. Click **Add groups claim**, select **Security groups**, choose **Group ID** as the claim type for the ID token, and click **Add**.

   :::note
   The `groups` claim lists the IDs of the user's security groups. WSO2 Identity Server uses it to assign the user's DPDP roles.
   :::

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/entra-groups-claim.png').default} alt="Edit groups claim panel with Security groups selected and Group ID chosen for the ID token" width="526" />
   </figure>

   Confirm that `groups` and `upn` appear in the token configuration. `preferred_username` needs no configuration, because Entra ID includes it whenever the `profile` scope is requested.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/entra-token-configuration.png').default} alt="Token configuration page listing the groups and upn optional claims" width="792" />
   </figure>

## Select the role groups in Microsoft Entra ID

The Consent Portal displays only the pages and actions permitted by a user's DPDP role. For example, Data Principals manage their own consents, while administrators can also manage purposes and data elements. In this setup, users are managed in Entra ID, so access is granted by mapping Entra ID security groups to DPDP roles in WSO2 Identity Server.

Group-based role assignment works as follows:

1. Map each Entra ID security group to its corresponding DPDP role in WSO2 Identity Server, as described in [Map Entra ID groups to DPDP roles](#map-entra-id-groups-to-dpdp-roles).
2. When a user signs in, Entra ID includes the user's group IDs in the `groups` claim, provided the user is within the group-claim limit.
3. WSO2 Identity Server maps those group IDs to the configured DPDP roles. The assigned roles determine which permissions (scopes) the user receives in the Consent Portal.

You can therefore manage access by adding users to or removing them from the relevant security groups in Entra ID.

You can use existing security groups or create dedicated groups for the DPDP roles.

| Entra ID security group (example) | DPDP role | For |
| :--- | :--- | :--- |
| `Users` | `dpdp-consent-user` | Data Principals who manage their own consents and complaints |
| `Admins` | `dpdp-consent-admin` | Administrators who manage purposes, data elements and consent oversight |
| `Data Protection Officers` | `dpdp-consent-dpo` | Data Protection Officers who handle complaints |

1. Go to **Entra ID** → **Groups** → **All groups**.
2. For each role, copy the **Object ID** of the security group you want to map.

To create a group that doesn't exist yet:

1. Go to **Entra ID** → **Groups** → **New group**.

2. Set **Group type** to **Security**, enter a name, and add the appropriate members.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/entra-new-security-group.png').default} alt="New Group form with Group type Security, the name DPDP-Consent-Users and Assigned membership" width="684" />
   </figure>

3. Click **Create**, and take note of the group's **Object ID**.

## Register the Microsoft Entra ID IdP

Next, add Microsoft Entra ID as an identity provider (IdP) in WSO2 Identity Server.

1. In the WSO2 Identity Server Console (`https://<IS_HOST>/console`), open **Connections**.

2. Click **New Connection**, select **Standard-Based IdP**, and click **Create**.

3. Select **OpenID Connect** and enter the following values. Replace `<TENANT_ID>` in each URL with the Directory (tenant) ID copied from Entra ID.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/is-connection-configuration.png').default} alt="Configuration step of the Standard based Connections wizard with the client ID, client secret and the two endpoint URLs" />
   </figure>

   | Parameter | Description |
   | :--- | :--- |
   | Identity provider name | `EntraID` |
   | Client ID | The Application (client) ID obtained from Entra ID. |
   | Client secret | The client secret value obtained from Entra ID. |
   | Authorization endpoint URL | `https://login.microsoftonline.com/<TENANT_ID>/oauth2/v2.0/authorize` |
   | Token endpoint URL | `https://login.microsoftonline.com/<TENANT_ID>/oauth2/v2.0/token` |
   | Mode of certificate configuration | JWKS endpoint |
   | JWKS endpoint URL | `https://login.microsoftonline.com/<TENANT_ID>/discovery/v2.0/keys` |

4. Click **Finish** to create the identity provider connection.

5. Open the new connection, select the **Settings** tab, enter the following values, and click **Update**.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/is-connection-settings.png').default} alt="Connection Settings tab showing the Authorized redirect URL, the Logout Endpoint URL and the openid and profile scopes" />
   </figure>

   | Parameter | Description |
   | :--- | :--- |
   | Logout Endpoint URL | `https://login.microsoftonline.com/<TENANT_ID>/oauth2/v2.0/logout` |
   | Scopes | `openid profile`. Add `profile` next to the default `openid`. |

   :::tip
   The **Authorized redirect URL** shown here must exactly match the redirect URI registered in Entra ID. If the values differ, update the app registration to use the URL shown in WSO2 Identity Server.
   :::

6. Leave the **Just-in-Time Provisioning** setting disabled. It is disabled by default, so federated users are not provisioned as local user accounts in WSO2 Identity Server.

## Configure user attributes

Map the claims received from Entra ID to the corresponding WSO2 Identity Server attributes.

1. On the **EntraID** connection, go to the **Attributes** tab.

2. Click **Add Attribute Mapping** and configure the following mappings.

   | Entra ID claim | WSO2 Identity Server claim | Purpose |
   | :--- | :--- | :--- |
   | `preferred_username` | Display Name (`http://wso2.org/claims/displayName`) | Identifies the user |
   | `upn` | Username (`http://wso2.org/claims/username`) | The username the Consent Portal uses for the signed-in user |
   | `groups` | Groups (`http://wso2.org/claims/groups`) | Role mapping |

3. Set **Subject Attribute** to `preferred_username`, then click **Update**.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/is-connection-attributes.png').default} alt="Attributes tab with the groups, preferred_username and upn mappings and the Subject Attribute set to preferred_username" />
   </figure>

## Map Entra ID groups to DPDP roles

Map each Entra ID security group to the corresponding DPDP role.

1. Open the **EntraID** connection and select the **Groups** tab.

2. Click **New Group** and create an entry for each role group. Use the group's **Object ID** as the group name because Entra ID sends this identifier in the `groups` claim.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/is-connection-groups.png').default} alt="Connection Groups tab with two groups named by their Entra Object IDs" />
   </figure>

3. Go to **User Management** → **Roles** and select `dpdp-consent-user`.

4. On the **Groups** tab, find **External Groups**, expand **EntraID**, and select the matching group under **Assign groups**.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/is-role-external-groups.png').default} alt="Groups tab of the dpdp-consent-user role with an EntraID external group assigned" />
   </figure>

5. Click **Update**.

6. Repeat steps 3–5 for `dpdp-consent-admin` and `dpdp-consent-dpo`.

The accelerator has already attached the right permissions (scopes) to each role.

## Enable Microsoft Entra ID login

1. In the WSO2 Identity Server Console, open **Applications** and select **DPDP Consent Portal**.

2. Open the **Login Flow** tab, click **Add Sign In Option**, select **EntraID**, and click **Add**.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/is-portal-login-flow.png').default} alt="Login Flow tab of the DPDP Consent Portal with a Sign In With EntraID option below the username and password form" />
   </figure>

3. Click **Update** to save the login-flow configuration.

## Try it out

Test the integration with an Entra ID account that belongs to one of the configured role groups.

1. Open a private or incognito browser window and go to `https://<IS_HOST>/consent-portal`.

2. On the WSO2 Identity Server sign-in page, select **Sign In With EntraID**.

   <figure className="doc-figure">
     <img src={require('@site/assets/images/learn/federated-login/is-portal-sign-in.png').default} alt="WSO2 Identity Server sign-in page for the Consent Portal with a Sign In With EntraID button" />
   </figure>

3. Sign in with an Entra ID account that belongs to one of the configured role groups.

4. Confirm the following:
   - The Consent Portal shows the user's Entra username.
   - The pages for the user's role are available.
   - **My Consents** lists the consents recorded for the user's username.
   - For a consent requested on someone else's behalf, the named authorizers are listed and can approve or reject it.
   - No new user appears under **User Management** → **Users** in WSO2 Identity Server.

:::note
Group membership is evaluated at sign-in. After changing a user's group membership, have the user sign out and sign in again.
:::

## Next Steps

- [Federated Login](introduction.md) — how federated login works and what applies to every identity provider.
- [Add Asgardeo Login](asgardeo.md) — set up another identity provider.
- [Managing Access](../managing-access.md) — the DPDP roles and the permissions each one grants.
- [Consent](../consent.md) — how users give, review, and revoke consent in the Consent Portal.
