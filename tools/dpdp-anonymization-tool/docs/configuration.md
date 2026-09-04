# Configuration

`conf/config.json` defines the JDBC connection, event-rule file, JDBC fetch size, and report directory.
Database credentials should be environment placeholders such as `${DPDP_DB_USERNAME}` and `${DPDP_DB_PASSWORD}`.
Do not commit operational credentials.

Supported databases in the initial release are H2 2.x and MySQL 8.x. The complete operation has not been validated for PostgreSQL or SQLite.
