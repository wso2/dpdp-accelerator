# Security and recovery

1. Stop WSO2 IS and every process that reads or writes `WSO2DPDP_DB`.
2. Create a database backup and verify that it can be restored.
3. Protect configuration files and environment variables containing database credentials.
4. Run without `--execute` and review the generated report.
5. Run with `--execute` only after confirming the tenant, source UUID, replacement UUID, aliases, and event paths.
6. Retain the report according to the organization's privacy and audit policy.

The tool provides database transaction atomicity only. A report file and the database cannot participate in one atomic transaction.
