# Configuring the Consent Portal application

Complete this after installing the accelerator and starting the Identity
Server — see [`setup-guide.md`](setup-guide.md) if you haven't done that yet.
This is the last step before the portal is ready to use.

## 1. Register the OAuth application

In the Console (`https://<host>:9443/console`):

1. **Applications → New Application → Standard-Based Application**.
2. Name: `DPDP Consent Portal`. Protocol: **OpenID Connect**.
3. Authorized redirect URL: `https://<host>:9443/consent-portal/auth/callback`
4. Open the new application's **Protocol** tab and enable the **Code** and
   **Refresh Token** grant types.
5. On the same tab, under **Access Token**, set **Type** to **JWT**.
6. Note the **Client ID** and **Client Secret** on the same tab — you'll need
   them in step 5.
7. Set the Application role type as Organization.

## 2. Skip the consent screen and request the username claim

1. **Advanced** tab → enable **Skip login consent** and **Skip logout
   consent**.
2. **User Attributes** tab → add `http://wso2.org/claims/username` as a
   mandatory requested claim, so the portal can display the signed-in
   user's name.

## 3. Authorize the consent management APIs

On the **API Authorization** tab, authorize all three resources below,
selecting **all scopes** for each and policy **RBAC**:

- Consent Management — Consents
- Consent Management — Purposes
- Consent Management — Elements

## 4. Create the portal roles

In **User Management → Roles → New Role**, create two organization-wide
roles (Audience: **Organization**):

1. `dpdp-consent-admin` — Permissions: every scope authorized in step 3.
2. `dpdp-consent-user` — No permissions assigned yet.

Assign users to `dpdp-consent-admin` from **User Management → Users →
*user* → Roles** to grant them portal administration access.

## 5. Add the client credentials to the portal configuration

Edit `<IS_HOME>/repository/conf/dpdp-portal.properties`:

```properties
oauth.client.id=<Client ID>
oauth.client.secret=<Client Secret>
```

## 6. Restart

Restart the Identity Server, then open `https://<host>:9443/consent-portal/`.
