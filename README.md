# dpdp-accelerator
DPDP Accelerator is a collection of artifacts, reference implementations, and documentation that help organizations accelerate the adoption of DPDP Act.

## Build

Requires JDK 11+ (JDK 21+ to run the server), Maven 3.6.3+, and Node.js 20.19+ (or 22.12+) with
npm, all on the `PATH`.

Run from this directory (the repository root) — **not** from `dpdp-accelerator/`,
which only builds the consent portal on its own and skips the accelerator zip:

```sh
mvn clean install
```

This builds the consent portal (frontend + backend WAR) and packages
`wso2-dpdpiam-accelerator-<version>.zip` under
`dpdp-accelerator/accelerators/dpdp-is/target/` — ready to unzip inside
`<IS_HOME>`. See [`docs/content/setup-guide.md`](docs/content/setup-guide.md) for
installation.

## Documentation

The `docs/` directory is a [Docusaurus](https://docusaurus.io/) site whose
content lives in `docs/content/`. Run `npm install` then `npm run start` inside
`docs/` to preview it locally.

- [`docs/content/setup-guide.md`](docs/content/setup-guide.md) — preparing Identity Server and
  accelerator databases, JDBC drivers, datasources, and schemas.
- [`docs/content/configuration-guide.md`](docs/content/configuration-guide.md) — configuring
  the provisioned consent portal, roles, complaints, expiry, deletion, and Event Notifications.
- [`docs/content/event-notification-guide.md`](docs/content/event-notification-guide.md) —
  creating topics and webhook subscriptions, publishing events, and inspecting
  delivery history.
- [`docs/content/localization-guide.md`](docs/content/localization-guide.md) — correcting UI
  wording and localizing Purposes/Elements on a running deployment.
- [`docs/content/release-guide.md`](docs/content/release-guide.md) — cutting a release with the
  Release builder workflow.

## Roles

Assign the appropriate portal role per tenant when additional portal access is
needed (Console → **User Management → Users → *user* → Roles**):

| Role | Assign to |
|---|---|
| `dpdp-consent-user` | Regular users, to manage their own consents. |
| `dpdp-consent-admin` | Administrators, to manage other people's consents and the purpose/element catalog. |
| `dpdp-consent-dpo` | Data Protection Officers, to manage organization-wide complaints without full administration access. |

Basic self-service consent management does not require a portal role. See
the [Role Management Guide](docs/content/role-guide.md) and
[`docs/content/configuration-guide.md`](docs/content/configuration-guide.md#4-assign-portal-roles)
for details.
