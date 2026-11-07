#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# BlueCollar Production Database Backup & Restore Utility
# ==============================================================================

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-bluecollar}"
DB_USER="${DB_USER:-postgres}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"

mkdir -p "${BACKUP_DIR}"

usage() {
    echo "Usage: $0 {backup|restore <backup_file>|verify|healthcheck}"
    exit 1
}

backup() {
    local timestamp
    timestamp=$(date +%Y%m%d_%H%M%S)
    local backup_file="${BACKUP_DIR}/${DB_NAME}_backup_${timestamp}.dump"

    echo "==> Creating compressed PostgreSQL backup for ${DB_NAME} at ${DB_HOST}:${DB_PORT}..."
    pg_dump -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -F c -b -v -f "${backup_file}" "${DB_NAME}"

    echo "==> Computing SHA-256 checksum..."
    shasum -a 256 "${backup_file}" > "${backup_file}.sha256"

    echo "==> Backup completed successfully: ${backup_file}"
    ls -lh "${backup_file}"
}

restore() {
    local backup_file="${1:-}"
    if [[ -z "${backup_file}" || ! -f "${backup_file}" ]]; then
        echo "Error: Backup file not specified or does not exist: '${backup_file}'"
        exit 1
    fi

    echo "==> Restoring database ${DB_NAME} from ${backup_file}..."
    pg_restore -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" \
        --clean --if-exists --no-owner --no-privileges \
        -d "${DB_NAME}" -v "${backup_file}"

    echo "==> Restore completed successfully."
}

verify() {
    echo "==> Verifying table row counts in ${DB_NAME}..."
    psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -c \
        "SELECT schemaname, relname, n_live_tup FROM pg_stat_user_tables ORDER BY n_live_tup DESC;"
}

healthcheck() {
    local app_url="${APP_URL:-http://localhost:8080}"
    echo "==> Testing PostgreSQL connectivity..."
    pg_isready -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}"

    echo "==> Testing Spring Boot Actuator health endpoint at ${app_url}/actuator/health..."
    if command -v curl >/dev/null 2>&1; then
        curl -fsS "${app_url}/actuator/health" || {
            echo "Healthcheck failed!"
            exit 1
        }
        echo ""
    fi
    echo "==> All deployment health checks passed."
}

command="${1:-}"
case "${command}" in
    backup)
        backup
        ;;
    restore)
        shift
        restore "$@"
        ;;
    verify)
        verify
        ;;
    healthcheck)
        healthcheck
        ;;
    *)
        usage
        ;;
esac
