# Migrating the existing local QueueForge database

The Compose stack creates its own PostgreSQL volume (`queueforge_postgres_data`). It does not automatically attach the old Docker volume (`queueforge_pgdata`) or copy existing jobs. The old container and volume are left untouched.

## Option A — Keep using the existing local PostgreSQL

1. Stop the old Spring Boot process.
2. Back up the current database before starting the new code:

   ```bash
   mkdir -p backups
   docker exec queueforge-postgres pg_dump -U queueforge -d queueforge -Fc > backups/queueforge-before-flyway.dump
   ```

3. Start Redis with the development password if it is not already running:

   ```bash
   docker run --name queueforge-redis -p 6379:6379 -d redis:7-alpine redis-server --requirepass local-dev-redis-password
   ```

4. The migration defaults `LEGACY_TIME_ZONE` to `Asia/Kolkata`, matching the original Mac development environment. If the old application stored `LocalDateTime` values using another timezone, set `LEGACY_TIME_ZONE` to that exact IANA timezone before the first startup (for example `America/New_York`). Flyway converts legacy timestamps to UTC, baselines the non-empty existing schema at version 0, and applies V1.
5. Verify job records and run the API smoke test. Keep the backup until the migration is confirmed.

## Option B — Move the old database into the Compose-managed PostgreSQL

1. Back up the old database as above.
2. Copy `.env.example` to `.env`, set unique secrets, and source it in your shell (`set -a; source .env; set +a`).
3. Start only the new database and Redis services:

   ```bash
   docker compose up -d postgres redis
   ```

4. Restore the old database into the new, empty Compose database:

   ```bash
   docker compose exec -T postgres pg_restore --clean --if-exists --no-owner --no-privileges -U "$DB_USERNAME" -d queueforge < backups/queueforge-before-flyway.dump
   ```

5. Start the application and monitoring stack:

   ```bash
   docker compose up --build -d
   docker compose logs -f app
   ```

6. Flyway should baseline the restored pre-Flyway schema at version 0 and apply V1. Confirm that the app reports healthy and old job IDs remain accessible.

Do not delete the old `queueforge_pgdata` volume or container until the backup has been restored and verified. Practice this process on a copy before using it on important data.
