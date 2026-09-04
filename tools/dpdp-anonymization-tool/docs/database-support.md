# Database support

The initial implementation supports H2 2.x and MySQL 8.x. Every supported table must exist before a run starts.

The processor uses repeatable-read transactions and row locks. Services must remain stopped for the complete run, including verification and commit.
