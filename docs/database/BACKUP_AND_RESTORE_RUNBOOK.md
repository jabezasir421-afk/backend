# Database Backup, Disaster Recovery & Health Check Runbook

## 1. Overview

This runbook defines the operational procedures for backing up, restoring, verifying, and monitoring the BlueCollar PostgreSQL database in production and staging environments.

---

## 2. Backup Protocol & Retention Policy

| Backup Type | Frequency | Format | Storage Target | Retention Period |
| :--- | :--- | :--- | :--- | :--- |
| **Full Database Dump** | Daily at 01:00 UTC | PostgreSQL Custom (`-F c`, compressed) | Encrypted S3 bucket / off-site volume | 30 days |
| **Weekly Archive** | Every Sunday | PostgreSQL Custom (`-F c`) | Cold Glacier archive | 365 days |
| **Pre-Deployment Backup** | Before every schema migration | PostgreSQL Custom (`-F c`) | Local volume & S3 | 7 days |

---

## 3. Automated Backup Execution

Use the included utility script [`scripts/db-backup-restore.sh`](file:///Users/jabezasir/Desktop/Hackathon/backend/scripts/db-backup-restore.sh):

```bash
# Set database connection parameters
export DB_HOST="localhost"
export DB_PORT="5432"
export DB_NAME="bluecollar"
export DB_USER="postgres"
export BACKUP_DIR="./backups"

# Execute backup
./scripts/db-backup-restore.sh backup
```

The script will:
1. Generate a timestamped `.dump` archive using `pg_dump -F c -b -v`.
2. Generate a matching `.dump.sha256` checksum file.
3. Validate that the archive was created without I/O errors.

---

## 4. Disaster Recovery & Database Restoration

To restore a database instance from a backup file:

```bash
# Verify checksum prior to restoration
shasum -a 256 -c ./backups/bluecollar_backup_YYYYMMDD_HHMMSS.dump.sha256

# Execute restore
./scripts/db-backup-restore.sh restore ./backups/bluecollar_backup_YYYYMMDD_HHMMSS.dump
```

The restore script applies `--clean --if-exists --no-owner --no-privileges` to rebuild tables, constraints, sequences, and indexes safely.

---

## 5. Post-Restore Verification & Integrity Auditing

After restoring, verify that database objects and row counts are consistent:

```bash
./scripts/db-backup-restore.sh verify
```

Expected output lists active database tables and row estimates:
```sql
SELECT schemaname, relname, n_live_tup FROM pg_stat_user_tables ORDER BY n_live_tup DESC;
```

---

## 6. Deployment Health Checks

### PostgreSQL Readiness Check
```bash
pg_isready -h localhost -p 5432 -U postgres -d bluecollar
```

### Application Liveness and Readiness
```bash
curl -fsS http://localhost:8080/actuator/health
```

Expected JSON response:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    }
  }
}
```
