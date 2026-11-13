#!/usr/bin/env bash
set -euo pipefail
: "${QUEUEFORGE_API_KEY:?Set QUEUEFORGE_API_KEY}"
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
MANAGEMENT_URL="${MANAGEMENT_URL:-http://127.0.0.1:8081}"
curl -fsS "$MANAGEMENT_URL/actuator/health"
printf '\nCreating test job...\n'
curl -fsS -X POST "$BASE_URL/api/v1/jobs" \
  -H "X-API-Key: $QUEUEFORGE_API_KEY" \
  -H 'Idempotency-Key: smoke-test-queueforge-001' \
  -H 'Content-Type: application/json' \
  -d '{"jobType":"TEST_JOB","payload":"production-smoke-test","priority":5}'
printf '\n'
