# Production Readiness and Release Gate

This checklist defines the evidence required before QueueForge handles real customer workloads. Passing a local build alone is not production certification.

## 1. Build and supply chain

- [ ] Run `./mvnw clean verify` on Java 21.
- [ ] Run `docker compose config` after creating `.env`.
- [ ] Build the image from a clean checkout.
- [ ] Pin base images and dependencies to approved immutable versions/digests for release.
- [ ] Generate an SBOM and scan dependencies/container images for known vulnerabilities.
- [ ] Review all dependency updates and keep a patching cadence.

## 2. Database and durability

- [ ] Test Flyway V1 on an empty PostgreSQL 16 database.
- [ ] Test Flyway V1 against a copy of the existing pre-migration database.
- [ ] Verify constraints, indexes, and query plans with realistic job volume.
- [ ] Configure automated encrypted backups to off-host storage.
- [ ] Complete a restore drill and document RPO/RTO.
- [ ] Configure PostgreSQL TLS, least-privilege database roles, disk monitoring, and connection limits.

## 3. Queue correctness

- [ ] Submit the same idempotency key concurrently from multiple clients; exactly one job row must be created.
- [ ] Reuse an idempotency key with a different request; API must return HTTP 409.
- [ ] Run many workers against one queue; each job may have only one current valid lease owner.
- [ ] Kill a worker during execution; the expired lease must be recovered.
- [ ] Verify a stale worker cannot complete/fail a job after lease fencing changes.
- [ ] Simulate Redis outage; job submission must fail closed with HTTP 503 rather than bypass rate limits.
- [ ] Simulate PostgreSQL outage and recovery; confirm no silent job loss.
- [ ] Verify a job that exhausts attempts enters `DEAD`, and replay creates a fresh execution cycle.
- [ ] Verify delayed jobs do not execute before `scheduledAt`.
- [ ] Ensure handlers are idempotent at the external side-effect boundary.
- [ ] Define job and idempotency-key retention/archival rules; never purge deduplication records before the agreed retry/replay window.

## 4. Security

- [ ] Replace all development secrets with generated secrets from a secret manager.
- [ ] Terminate TLS and enforce HTTPS for all external API traffic.
- [ ] Restrict API key distribution, scope, rotation, and revocation.
- [ ] Restrict network access to PostgreSQL, Redis, and management port 8081.
- [ ] Run an independent SAST, dependency, container, and penetration assessment.
- [ ] Review payload data classification, retention, and log redaction.
- [ ] Add tenant identity and tenant-scoped idempotency/rate limits before multi-tenant use.

## 5. Performance and capacity

- [ ] Define target job sizes, handler duration, arrival rate, and concurrency.
- [ ] Run k6 load tests at expected peak and 2x peak.
- [ ] Measure API p50/p95/p99 latency, throughput, queue wait time, worker saturation, DB pool saturation, and Redis latency.
- [ ] Benchmark PostgreSQL claim queries with realistic data and `EXPLAIN (ANALYZE, BUFFERS)`.
- [ ] Set capacity limits and backpressure policy based on measured results.
- [ ] Configure autoscaling only after worker coordination and database capacity are understood.

## 6. Operations

- [ ] Define SLOs and alert thresholds for API availability, queue age, dead jobs, worker saturation, database pool, Redis errors, and recovery counts.
- [ ] Route alerts to an actively monitored channel.
- [ ] Centralize structured logs with correlation/job IDs and retention controls.
- [ ] Configure distributed tracing for API and downstream handlers.
- [ ] Test graceful shutdown, rolling deployment, rollback, and node loss.
- [ ] Document incident response, secret rotation, restore, and queue-drain procedures.

## Current implementation boundaries

- The demo handler only simulates email/report/backup work.
- Rate limiting is per API key; the project currently has one configured API key, not a tenant/key registry.
- Redis is used for rate limiting, while PostgreSQL is the durable queue of record.
- Delivery is at-least-once. Downstream integrations must deduplicate their own side effects.
- No formal SOC 2, ISO 27001, or penetration-test certification is included.
