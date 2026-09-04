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
`<IS_HOME>`. See [`docs/setup-guide.md`](docs/setup-guide.md) for
installation.

## Documentation

- [`docs/introduction.md`](docs/introduction.md) — understanding the DPDP Act,
  its participants, and how the accelerator supports operational workflows.
- [`docs/quickstart.md`](docs/quickstart.md) — installing a local H2 deployment
  and confirming automatic lifecycle event publication.
- [`docs/tryout-flows.md`](docs/tryout-flows.md) — exercising the catalog,
  consent lifecycle, complaints, Event Notifications, and account deletion.
- [`docs/grievances-guide.md`](docs/grievances-guide.md) — understanding and
  operating the tenant-scoped grievance/complaint service.
- [`docs/setup-guide.md`](docs/setup-guide.md) — installing the accelerator
  and starting the Identity Server.
- [`docs/configuration-guide.md`](docs/configuration-guide.md) — configuring
  the automatically provisioned applications and runtime features.
- [`docs/role-guide.md`](docs/role-guide.md) — assigning portal and integration
  roles and understanding their scopes.
- [`docs/event-notification-guide.md`](docs/event-notification-guide.md) —
  creating topics and webhook subscriptions, publishing events, and inspecting
  delivery history.
- [`docs/localization-guide.md`](docs/localization-guide.md) — correcting UI
  wording and localizing Purposes/Elements on a running deployment.
- [`docs/release-guide.md`](docs/release-guide.md) — cutting a release with the
  Release builder workflow.

## Roles

Basic self-service consent management requires no portal role. Assign these
roles per tenant only when the user needs the additional capability (Console →
**User Management → Users → *user* → Roles**):

| Role | Assign to |
|---|---|
| `dpdp-consent-user` | Users who need personal consent history, complaint self-service, or self-service account deletion. |
| `dpdp-consent-admin` | Administrators who manage other users' consents, the purpose/element catalog, Event Notifications, and all complaints. |
| `dpdp-consent-dpo` | Data Protection Officers who manage all complaints without full portal administration. |

See the [`Role Management Guide`](docs/role-guide.md) for details.
