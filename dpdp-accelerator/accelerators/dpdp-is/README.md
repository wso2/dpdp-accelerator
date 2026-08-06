# WSO2 DPDP Identity Server Accelerator

Adds a DPDP consent self-care portal on top of WSO2 Identity Server 7.3, backed by
the Identity Server's own consent management APIs. No separate consent server is
involved.

## What it deploys

| Artifact | Location |
|---|---|
| Consent portal (React SPA + Java BFF) | `<IS_HOME>/repository/deployment/server/webapps/consent-portal/` |
| Complaint management API (JAX-RS) | `<IS_HOME>/repository/deployment/server/webapps/api#dpdp#complaints/` |
| Portal configuration | `<IS_HOME>/repository/conf/dpdp-portal.properties` |
| Server settings | `<IS_HOME>/repository/conf/deployment.toml`, replaced from the shipped template |

## Prerequisites

- WSO2 Identity Server 7.3.0
- JDK 11 or later, `python3` and `curl` on the PATH

## Installation

1. Unzip this accelerator inside `<IS_HOME>` (or anywhere, passing `<IS_HOME>` to each script).

2. Copy the artifacts in, with the server stopped:
   ```
   sh bin/merge.sh <IS_HOME>
   ```

3. Apply the configuration, still stopped:
   ```
   sh bin/configure.sh <IS_HOME>
   ```
   Edit `repository/conf/configure.properties` first if your hostname, port,
   administrator credentials or database differ from the defaults. This step
   installs `deployment.toml`, writes `dpdp-portal.properties`, and applies the
   consent schema migration.

   > **`deployment.toml` is replaced, not merged.** The accelerator ships a
   > complete file — `repository/resources/wso2is-7.3.0-deployment.toml`, the
   > stock Identity Server 7.3.0 configuration plus the accelerator's own
   > settings. Your existing file is copied to `deployment.toml.bak-<timestamp>`
   > first; re-apply any local customisation from that backup before starting
   > the server. To target a different Identity Server version, add a template
   > beside the shipped one and point `PRODUCT_CONF_PATH` at it.

4. Start the Identity Server.

5. Register the portal application:
   ```
   sh bin/register-portal-app.sh <IS_HOME>
   ```

6. Restart the Identity Server so the portal picks up its client credentials.

7. Open `https://<host>:9443/consent-portal/`.

## Granting administration access

Every authenticated user can see and manage their own consents. The
administration and catalog areas additionally require the consent management
scopes, which are granted through the `dpdp-consent-admin` role created in
step 5 — assign users to that role in the Console.

## Complaint management API

The accelerator also deploys a grievance-redressal JAX-RS API backing DPDP complaint
handling. It is independent of the consent portal - no browser UI, no shared session.

Base path: `https://<host>:9443/api/dpdp/complaints/v1`

**It has no authentication of its own.** Every request is trusted on a caller-supplied
`org-id` header (and a `userId` field/param) rather than a validated token; callers are
expected to be an internal gateway or BFF that has already authenticated the request.
Because of this, the shipped `deployment.toml` carries a
`[[resource.access_control]]` rule with `secure = "false"` for
`(.*)/api/dpdp/complaints(.*)`, matching the consent portal's own carve-out - without it
the Identity Server's authentication valve rejects every request with `401` before it
reaches the servlet. Do not expose this context directly to untrusted callers.

### Database

Backed by a container-managed `javax.sql.DataSource`, declared in the webapp's own
`META-INF/context.xml` and looked up via JNDI at `java:comp/env/jdbc/ComplaintDB` (see
`DBUtil#getConnection()` in the complaint DAO module) — the same idiom the
[financial-services-accelerator](https://github.com/wso2/financial-services-accelerator)
uses for its own datasources, adapted for a plain Tomcat webapp rather than an
OSGi-managed one. `configure.sh` step `[4/4]` fills in the real connection details from
`configure.properties`:

```
COMPLAINT_DB_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver
COMPLAINT_DB_URL="jdbc:mysql://<host>:3306/<db>?useSSL=false&allowPublicKeyRetrieval=true"
COMPLAINT_DB_USERNAME=<user>
COMPLAINT_DB_PASSWORD=<password>
```

MySQL only for now; point `COMPLAINT_DB_URL` at an already-created, empty database - the
schema (`COMPLAINT`, `COMPLAINT_EVENT`, `COMPLAINT_ATTACHMENT`) is created automatically
on first startup. Edit `configure.properties`, not `context.xml` directly - re-running
`configure.sh` overwrites the substituted values.

If no `jdbc/ComplaintDB` resource is bound at all (for example, running the WAR outside
this accelerator), `DBUtil` falls back to `DriverManager` using the `CO_DB_TYPE` /
`CO_DB_URL` / `CO_DB_USER` / `CO_DB_PASS` system properties or environment variables,
defaulting to an in-memory H2 database that is wiped on every restart. This fallback
exists for standalone testing - a deployment through this accelerator should always go
through the JNDI datasource above.

## Notes on the underlying server

- **The consent management v2 API must be enabled.** `configure.sh` sets
  `[consent_mgt] enable_v2_api = true`. That one switch also renders the v2 rules
  into `repository/conf/identity/resource-access-control-v2.xml` and registers
  the v2 API resources with their `internal_consent_mgt_*` scopes. Do not edit
  those files by hand — they are generated.

- **The consent schema migration is required.** A stock 7.3.0 pack ships an
  identity database that predates the consent tables the v2 and self-service
  APIs need, and `/api/users/v1/me/consents` fails with a server error until
  `dbscripts/migrations/consent/<db>-migration.txt` is applied. `configure.sh`
  does this automatically for the embedded H2 database only; for any other
  database, apply the matching script yourself.

- **The portal's webapp context is deliberately unsecured at the server level**
  (`secure = "false"` for `(.*)/consent-portal/(.*)`). The portal's BFF
  authenticates every request itself: it rejoins the split access-token cookies
  and forwards the user's own token to the Identity Server, which then enforces
  that user's scopes. Without this rule the server would reject the SPA's static
  assets and the login redirects before any servlet ran.

- **Revoked consents cannot be re-approved through the portal.** The Identity
  Server's `authorize` operation accepts a consent in any state and will move a
  revoked or rejected one back to `ACTIVE`. Because a withdrawal has to be final,
  the BFF rejects an approve or reject unless the consent is `PENDING`, returning
  HTTP 409 `INVALID_CONSENT_STATE`.

- **Chrome and Safari on macOS reject the shipped certificate outright.** The
  Identity Server's primary keystore holds a self-signed *CA* certificate
  (`BasicConstraints: CA:TRUE`, with `Certificate Sign` key usage) and serves it
  directly as the TLS server certificate. A CA certificate is not a valid
  end-entity certificate, so Apple's verifier rejects it as malformed rather
  than merely untrusted: `NET::ERR_CERT_INVALID`, with no "Proceed anyway" link,
  and the `thisisunsafe` bypass does not work either — that bypass only covers
  trust failures. Its 825-day validity separately exceeds the 398-day maximum
  Apple and Chrome have enforced since September 2020. Firefox is unaffected
  because it uses its own verifier.

  Fix it with a dedicated TLS keystore holding a proper end-entity certificate,
  which leaves the primary keystore (and therefore token signing) untouched:

  ```
  keytool -genkeypair -alias localhost -keyalg RSA -keysize 2048 \
    -sigalg SHA256withRSA -validity 397 \
    -dname "CN=localhost, OU=WSO2, O=WSO2, L=Santa Clara, ST=CA, C=US" \
    -ext "SAN=DNS:localhost,IP:127.0.0.1" -ext "BC=ca:false" \
    -ext "KU=digitalSignature,keyEncipherment" -ext "EKU=serverAuth,clientAuth" \
    -keystore <IS_HOME>/repository/resources/security/tls.p12 \
    -storetype PKCS12 -storepass wso2carbon -keypass wso2carbon
  ```

  Import that certificate into `client-truststore.p12` as well — the portal's
  BFF calls the Identity Server over HTTPS and validates against that
  truststore — then point the server at it in `deployment.toml`:

  ```toml
  [keystore.tls]
  file_name = "tls.p12"
  type = "PKCS12"
  password = "wso2carbon"
  alias = "localhost"
  key_password = "wso2carbon"
  ```

  Browsers then show the ordinary self-signed warning
  (`ERR_CERT_AUTHORITY_INVALID`), which can be clicked through. To remove the
  warning entirely, trust the certificate on the machine — on macOS:

  ```
  security add-trusted-cert -r trustRoot \
    -k ~/Library/Keychains/login.keychain-db /path/to/tls.crt
  ```

  Use a certificate from a real certificate authority for anything beyond a
  local development machine.

## Features not available on Identity Server 7.3

The portal follows the Identity Server's consent model, so the following are
absent by design: element versioning, consent status-history timelines, consent
grouping (`groupId`), and per-element approval — a consent is approved or
rejected as a whole. Purpose versions are shown read-only.
