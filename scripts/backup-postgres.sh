#!/usr/bin/env bash
set -euo pipefail
mkdir -p backups
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "backups/queueforge-${stamp}.dump"
echo "Backup written to backups/queueforge-${stamp}.dump"
