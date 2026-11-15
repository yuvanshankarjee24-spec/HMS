# Backup, Migration, Verification & Rollback Runbook

All configuration credentials are loaded from `.env` (see `.env.example`). Never commit `.env` or plaintext credentials.

## 1. Pre-Migration Backup (Mandatory Before Upgrading Existing MySQL Data)

```bash
# Create timestamped full logical backup with single-transaction consistency
mkdir -p backups
mysqldump \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USER}" \
  --password="${DB_PASSWORD}" \
  --single-transaction \
  --routines \
  --triggers \
  --events \
  --default-character-set=utf8mb4 \
  "${DB_NAME}" > "backups/hms_pre_consolidation_$(date -u +%Y%m%dT%H%M%SZ).sql"
```

## 2. Pre-Migration Verification Snapshot

Run the pre-check queries before applying `V10` and `V11` to capture baseline row counts:

```bash
mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" -p "${DB_NAME}" < scripts/verify_migration.sql > backups/pre_counts.txt
```

## 3. Running Flyway Consolidation Migrations (`V10` & `V11`)

Start the Spring Boot application with production/MySQL environment variables exported from `.env`:
- Flyway automatically executes `V10__core_shared_schema.sql` and `V11__link_modules_integrity.sql` inside schema validation.
- The startup `DbHealthConfig` runner checks `SELECT 1` and fails fast with a clear error message if the database is unreachable.
- Verify health after startup:

```bash
curl -f http://localhost:8080/health
```

## 4. Post-Migration Verification & Optional Production FK Hardening

```bash
# Confirm zero data loss, zero orphan rows, and standardized lowercase statuses
mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" -p "${DB_NAME}" < scripts/verify_migration.sql

# (MySQL only) Enforce ON DELETE RESTRICT on legacy V1-V9 cascade FKs
mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" -p "${DB_NAME}" < scripts/mysql_enforce_restrict_fks.sql
```

## 5. Idempotent Seed Data (Dev / QA Environments)

```bash
mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" -p "${DB_NAME}" < scripts/seed_consolidated.sql
```

## 6. Rollback Procedure

### Option A: Reverse DDL Rollback (without dropping pre-V10 user data)
Run the inverse scripts in reverse version order (`V11__down.sql`, then `V10__down.sql`):

```bash
mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" -p "${DB_NAME}" < src/main/resources/db/rollback/V11__down.sql
mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" -p "${DB_NAME}" < src/main/resources/db/rollback/V10__down.sql
```

### Option B: Full Point-in-Time Restore from Pre-Migration Dump

```bash
mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" -p "${DB_NAME}" < backups/hms_pre_consolidation_<timestamp>.sql
```

