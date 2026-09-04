# WSO2 DPDP Anonymization Tool

This standalone administrative tool pseudonymizes configured user identifiers in `WSO2DPDP_DB`.
It is intentionally separate from the DPDP Accelerator runtime and the legacy Identity Anonymization Tool.

## Safety model

- The tenant domain is mandatory and is applied as `ORG_ID` to every query.
- Source and replacement values must be different canonical UUIDs.
- A target value found without the source is reported as ambiguous; it is not claimed as proof of a prior run.
- The default mode is a dry-run; `--execute` is required to commit.
- Covered database changes use one JDBC transaction and roll back together.
- Event payloads are inspected only at explicitly configured topic/path combinations.
- Full identifiers and discovered usernames are not written to reports.

Stop WSO2 IS, DPDP workers, and other writers and take a tested database backup before execution.

## Build

The project targets Java 8.

```bash
mvn clean verify
```

Distribution:

```text
components/dpdp-anonymization-distribution/target/dpdp-anonymization-tool-1.0.0-SNAPSHOT.zip
```

## Run

Dry-run:

```bash
./bin/dpdp-anonymize \
  --tenant-domain example.com \
  --user-id 18b7c17d-ef74-48c5-a0c8-a1df9b21ff87 \
  --pseudonym 216d6aac-7e84-4484-a71e-c52f89b3cb1d
```

Commit only after reviewing the dry-run report:

```bash
./bin/dpdp-anonymize \
  --tenant-domain example.com \
  --user-id 18b7c17d-ef74-48c5-a0c8-a1df9b21ff87 \
  --pseudonym 216d6aac-7e84-4484-a71e-c52f89b3cb1d \
  --execute
```

Use repeatable `--username` options only for aliases independently verified by the operator.

## Scope

The tool covers:

- `COMPLAINT.USER_ID` and its correlated `USER_NAME`.
- `COMPLAINT_EVENT.ACTOR_USER_ID` and its correlated `ACTOR_USER_NAME`.
- `DPDP_CONSENT_STATUS_AUDIT.ACTION_BY` for exact trusted identities.
- `DPDP_CONSENT_HISTORY.ACTION_BY` and configured snapshot identity fields.
- Allowlisted paths within `EVENT.PAYLOAD` after a tenant-safe `TOPIC` join.

It does not provide complete erasure. Attachments, comments, descriptions, arbitrary unconfigured JSON, operational identifiers, and other databases are outside scope.
