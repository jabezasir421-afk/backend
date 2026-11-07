#!/usr/bin/env bash
set -euo pipefail

# This runner is intentionally restricted to loopback addresses.
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
OPENAPI_URL="${OPENAPI_URL:-${BASE_URL}/v3/api-docs}"

case "$BASE_URL" in
  http://127.0.0.1:*|http://localhost:*|http://[::1]:*)
    ;;
  *)
    echo "Refusing to test non-local target: $BASE_URL" >&2
    echo "This script is for localhost only." >&2
    exit 2
    ;;
esac

if [[ "${BLUECOLLAR_TEST_DB_CONFIRMED:-}" != "YES" ]]; then
  echo "Safety check: Schemathesis can create, update, and delete API resources." >&2
  echo "Point BlueCollar at a dedicated disposable local test database, then run:" >&2
  echo "  BLUECOLLAR_TEST_DB_CONFIRMED=YES ./tools/api-testing/run-schemathesis.sh" >&2
  exit 2
fi

if ! command -v uvx >/dev/null 2>&1; then
  echo "uvx was not found. Install uv first: brew install uv" >&2
  exit 127
fi

if [[ -n "${API_TOKEN:-}" ]]; then
  exec uvx schemathesis run "$OPENAPI_URL" \
    --url "$BASE_URL" \
    --header "Authorization: Bearer ${API_TOKEN}" \
    --phases examples,fuzzing \
    --max-examples 10 \
    --max-time 90 \
    --workers 1 \
    --request-timeout 10 \
    --max-failures 10
fi

exec uvx schemathesis run "$OPENAPI_URL" \
  --url "$BASE_URL" \
  --phases examples,fuzzing \
  --max-examples 10 \
  --max-time 90 \
  --workers 1 \
  --request-timeout 10 \
  --max-failures 10
