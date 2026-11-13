# Security Notes

## Authentication

All `/api/v1/**` endpoints require `X-API-Key`. The filter compares the supplied key using a constant-time byte comparison. In production, `QUEUEFORGE_API_KEY` is mandatory through the `prod` profile. Use a secret manager, rotate keys, and never commit `.env`.

This is a single-key service authentication model, not a full user/tenant identity system. Do not expose it as a public multi-customer SaaS without adding per-client key storage, scopes, rotation/revocation, tenant authorization, and audit records.

## Network boundaries

The Compose file binds the application API, Prometheus, and Grafana to localhost. PostgreSQL, Redis, and management port 8081 are not published. In a production deployment, keep the management network private and place a TLS reverse proxy/load balancer in front of the API.

## Secrets and payloads

Never put credentials, access tokens, payment data, or other secrets in job payloads. Payloads are persisted in PostgreSQL and returned by the job inspection API. Restrict database access and define data retention/deletion policies.

## Redis failure policy

Job submission is fail-closed if Redis rate limiting is unavailable. This prevents a Redis outage from silently disabling the configured request limit. Redis must be private, password-protected, backed up if required by your operational policy, and monitored.

## Execution semantics

The service offers at-least-once job execution. Fencing versions protect QueueForge's database state from stale worker updates, but they do not make external effects exactly once. A handler must use provider idempotency keys or an application-side transactional deduplication mechanism.
