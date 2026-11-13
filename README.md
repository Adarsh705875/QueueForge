# QueueForge

QueueForge is a Java 21 / Spring Boot background-job processing service. It stores jobs durably in PostgreSQL, executes them with a concurrent worker pool, uses leases and heartbeats for crash recovery, applies Redis-backed token-bucket rate limiting to job submission, and exposes operational metrics.

> **Production status:** This repository is a hardened deployment candidate, not a formally certified or independently security-audited product. Run the full verification and operational checklist in `docs/PRODUCTION_READINESS.md` before handling real customer workloads. The included `DemoJobHandler` simulates work; real email, payment, report, or backup integrations must be implemented with provider-side idempotency.

## Features

- REST API to create, inspect, cancel, list, and replay jobs.
- PostgreSQL as the durable source of truth; Flyway schema migrations.
- Priority-based claiming with `FOR UPDATE SKIP LOCKED`.
- Multiple concurrent workers.
- Exponential retry delay with jitter and a dead-letter state.
- Worker leases, heartbeats, fencing versions, and expired-lease recovery.
- Idempotency keys with request-payload conflict detection and a unique database index.
- Delayed execution using `scheduledAt`.
- Redis Lua token bucket rate limiter (fail-closed if Redis is unavailable).
- API-key authentication, request validation, bounded pagination.
- Actuator health and Prometheus metrics on a separate management port.
- Docker Compose, Grafana dashboard, CI workflow, backup/restore scripts.

## Requirements

- Java 21 for local execution.
- Docker Desktop / Docker Engine with Compose v2 for the complete stack.
- Internet access on first build to download Maven dependencies and container images.

## Quick start (Docker Compose)

1. Copy `.env.example` to `.env`.
2. Generate strong values. Example on macOS/Linux:

   ```bash
   openssl rand -hex 32
   ```

   Use separate generated values for `DB_PASSWORD`, `REDIS_PASSWORD`, `QUEUEFORGE_API_KEY`, and `GRAFANA_ADMIN_PASSWORD`. Do not commit `.env`.
3. Load the environment variables into your shell when you want to run the sample curl commands:

   ```bash
   set -a; source .env; set +a
   ./scripts/check-env.sh
   ```

4. Start the stack:

   ```bash
   docker compose up --build -d
   docker compose ps
   docker compose logs -f app
   ```
5. Health endpoint (management port is internal to the Compose network):

   ```bash
   docker compose exec app curl -fsS http://localhost:8081/actuator/health
   ```
6. API is bound to `127.0.0.1:8080` by default. Send `X-API-Key` with every API request:

   ```bash
   curl -i -X POST http://127.0.0.1:8080/api/v1/jobs \
     -H "X-API-Key: $QUEUEFORGE_API_KEY" \
     -H "Idempotency-Key: welcome-email-user-123" \
     -H 'Content-Type: application/json' \
     -d '{"jobType":"SEND_EMAIL","payload":"welcome-user-123","priority":8,"maxAttempts":5}'
   ```

   Repeating the exact request with the same key returns the original job. Reusing the key with a different request returns HTTP 409.

7. Monitoring is available on localhost only:
   - Prometheus: http://127.0.0.1:9090
   - Grafana: http://127.0.0.1:3000 (credentials from `.env`)

## Local development

For a local Maven process using the Compose-managed dependencies, start them with `docker compose up -d postgres redis`, then source `.env` and run the app with these local connection settings:

```bash
set -a; source .env; set +a
export DB_URL=jdbc:postgresql://localhost:${POSTGRES_HOST_PORT:-5433}/queueforge
export REDIS_HOST=localhost
export REDIS_PORT=${REDIS_HOST_PORT:-6380}
./mvnw spring-boot:run
```

Alternatively, use your own local PostgreSQL and Redis instances with the connection settings in `application.properties`.

The existing PostgreSQL connection defaults match the original local QueueForge database. Back it up before the first Flyway migration. If you switch to the new Compose-managed database, follow `docs/MIGRATION.md` to preserve existing jobs.

Local defaults are intentionally development-only. The default local API key is `local-development-only-key-change-before-deployment-123456789`; override it with `QUEUEFORGE_API_KEY`. Do not use defaults in production.

## API

All `/api/v1/**` endpoints require `X-API-Key`.

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/jobs` | Create or idempotently retrieve a job |
| GET | `/api/v1/jobs/{id}` | Get job status |
| GET | `/api/v1/jobs?page=0&size=20` | Paginated jobs |
| PATCH | `/api/v1/jobs/{id}/cancel` | Cancel a queued job |
| GET | `/api/v1/jobs/dead` | List dead-letter jobs |
| POST | `/api/v1/jobs/{id}/replay` | Replay a dead-letter job |
| GET | `/api/v1/system/status` | Application status |

Job creation fields:

- `jobType`: required, up to 100 characters.
- `payload`: required, up to 64 KB. Do not put passwords, tokens, or payment secrets in payloads.
- `priority`: 1–10; defaults to 5.
- `maxAttempts`: 1–20; defaults to 5.
- `scheduledAt`: optional ISO date-time interpreted as UTC, e.g. `2026-12-01T10:30:00` (the API currently accepts a timezone-free ISO value; clients must send UTC).

Rate limiting is applied to job submission per API key. Defaults: bucket capacity 100, refill 10 tokens/second. Configure `RATE_LIMIT_CAPACITY` and `RATE_LIMIT_REFILL_PER_SECOND`.

## Tests and build

```bash
./mvnw clean verify
```

The test profile uses H2 and disables background workers. The production PostgreSQL migration, Redis Lua script, container image, and deployment behavior must additionally be verified in a Docker-enabled environment. See `docs/PRODUCTION_READINESS.md`.

## Important delivery semantics

QueueForge provides **at-least-once execution**, not exactly-once side effects. A worker can perform an external side effect and crash before marking the job complete. Job handlers must therefore use a stable business idempotency key with the downstream provider or their own transactional deduplication table. The database lease fencing token prevents stale workers from changing QueueForge's job state; it cannot undo or fence an external API call unless that downstream system supports it.

## Security and operations

- Use a unique API key and strong database/Redis/Grafana passwords.
- Terminate TLS at a trusted reverse proxy or load balancer.
- Keep PostgreSQL, Redis, and management port 8081 private.
- Restrict access to Prometheus and Grafana; they are bound to localhost in the provided Compose file.
- Use encrypted off-host backups and regularly test restores.
- Configure alerting, log retention, secret rotation, patching, and an incident response process.
- Read `docs/SECURITY.md` and `docs/PRODUCTION_READINESS.md` before deployment.
