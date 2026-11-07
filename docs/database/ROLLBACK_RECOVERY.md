# Database Migration Rollback and Disaster Recovery Guide

This document outlines the operational procedures, rollback strategies, and disaster recovery instructions for Flyway database migrations in BlueCollar.

---

## 1. Pre-Migration Baseline & Backup Protocol

Before applying any migration to a staging or production database, an explicit database backup must be captured and verified.

### Automated Backup Command (PostgreSQL)

```bash
# Capture full database snapshot with schema and data
pg_dump -h <DB_HOST> -p <DB_PORT> -U <DB_USER> -d <DB_NAME> \
  -F c -b -v -f "bluecollar_backup_$(date +%Y%m%d_%H%M%S).dump"
```

### Verifying Backup File

```bash
pg_restore -l bluecollar_backup_*.dump | head -n 30
```

---

## 2. Destructive Schema Changes & Recovery Procedures

### Case Study: V22 Customer Profile Photo Migration (`avatar_url` -> `profile_photo_file_id`)

#### Forward Migration Workflow
1. `customer.profile_photo_file_id UUID` column added.
2. Legacy `avatar_url` strings migrated into `stored_file` records (`file_category = 'PROFILE_PHOTO'`, `entity_type = 'CUSTOMER'`).
3. `customer.profile_photo_file_id` updated to link to the created `stored_file.id`.
4. Foreign key constraint `fk_customer_profile_photo` added referencing `stored_file(id)`.
5. Obsolete `avatar_url` column safely dropped.

#### Rollback Procedure (Emergency Reversal)

If an application release must be rolled back to a version preceding V22:

```sql
-- Step 1: Re-add the avatar_url column to customer
ALTER TABLE customer
    ADD COLUMN avatar_url VARCHAR(500);

-- Step 2: Restore avatar_url from stored_file records
UPDATE customer c
SET avatar_url = sf.storage_key
FROM stored_file sf
WHERE sf.id = c.profile_photo_file_id;

-- Step 3: Drop the foreign key constraint
ALTER TABLE customer
    DROP CONSTRAINT IF EXISTS fk_customer_profile_photo;

-- Step 4: Drop the profile_photo_file_id column
ALTER TABLE customer
    DROP COLUMN IF EXISTS profile_photo_file_id;

-- Step 5: Clean up flyway schema history
DELETE FROM flyway_schema_history WHERE version = '22';
```

---

## 3. Flyway Migration Failure & Repair Playbook

When a migration script encounters an error or partial execution in production:

1. **Investigate the Failure**:
   ```sql
   SELECT installed_rank, version, description, type, script, success
   FROM flyway_schema_history
   ORDER BY installed_rank DESC
   LIMIT 5;
   ```
2. **Correct the Underlying Cause**:
   - Check if an existing invalid record violated a newly introduced constraint.
   - Fix the SQL script or resolve data discrepancies.
3. **Run Flyway Repair**:
   ```bash
   ./mvnw flyway:repair -Dflyway.configFiles=flyway.conf
   ```
   *Note: `flyway:repair` removes failed migration entries and aligns checksums of repaired scripts.*
4. **Re-run the Migration**:
   ```bash
   ./mvnw flyway:migrate
   ```

---

## 4. Migration Invariant and Safety Rules

1. **Idempotency**: All DDL must use `IF NOT EXISTS` where supported (`CREATE INDEX IF NOT EXISTS`).
2. **Never Edit Deployed Migrations**: Once a migration has been executed in any persistent environment (staging or production), its script content and checksum must remain immutable. Any new changes must be added as a subsequent incremental migration (e.g., `V26`).
3. **Zero Data Loss**: Columns must never be dropped until data is safely mapped and populated in the target schema.
4. **Referential Integrity**: Foreign keys must be validated with existing data before applying constraints.
