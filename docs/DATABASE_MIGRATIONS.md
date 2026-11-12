# Database migration paths

StockWise uses separate Flyway locations for MySQL and TiDB while sharing the
migrations that are compatible with both engines:

- `db/migration/common` contains V1–V8 and V10.
- `db/migration/mysql` contains the published MySQL V9 and V11 unchanged.
- `db/migration/tidb` contains TiDB-compatible V9 and V11 migrations at the
  same versions.

The `mysql` Spring profile selects `common` plus `mysql`. The explicit `tidb`
profile expands to the `mysql` application profile for its backend services and
datasource settings, then a TiDB-only Flyway customizer selects `common` plus
`tidb`. It never includes the MySQL V9 location at the same time. Run the
backend with `SPRING_PROFILES_ACTIVE=mysql` for MySQL or
`SPRING_PROFILES_ACTIVE=tidb` for TiDB.

Moving a migration to a profile-specific location does not change its SQL or
Flyway checksum. The MySQL V9 content remains byte-for-byte unchanged. Do not
edit a migration that has already been applied; create a separately selected
engine path when engine-specific SQL is required.

TiDB V9 retains the event-type and authentication-method checks and all
single-column foreign keys. It additionally creates a unique
`auth_session(user_id, id)` key and a composite event-to-session foreign key,
so a non-null event user/session pair must identify the same session owner.
The existing nullable `user_id` and `session_id` columns remain nullable for
anonymous login failures. TiDB cannot safely express the original
conditional-session CHECK on a foreign-key column, so the domain factory
enforces that only login failures may omit a session and that any supplied
session belongs to the supplied user. The database still enforces session
existence and ownership for non-null user/session pairs.

TiDB V11 is separate because TiDB rejects the MySQL migration's combined
`DROP CHECK` and column changes. It adds the expanded status check first,
applies each column change separately, and then removes the narrower prior
check. The MySQL V11 migration remains unchanged.
