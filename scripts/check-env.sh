#!/usr/bin/env bash
set -euo pipefail

required=(DB_PASSWORD REDIS_PASSWORD QUEUEFORGE_API_KEY GRAFANA_ADMIN_PASSWORD)
for name in "${required[@]}"; do
  value="${!name:-}"
  if [[ -z "$value" || "$value" == REPLACE_* || "$value" == CHANGE_ME* || "$value" == password ]]; then
    echo "ERROR: $name is missing or still contains a placeholder." >&2
    exit 1
  fi
  if (( ${#value} < 32 )); then
    echo "ERROR: $name must contain at least 32 characters." >&2
    exit 1
  fi
done

echo "Environment secret checks passed. Keep .env out of version control."
