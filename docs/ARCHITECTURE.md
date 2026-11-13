# Architecture

```text
Client -- X-API-Key --> Spring Boot REST API
                           |          |
                           |          +--> Redis Lua token bucket (submission rate limit)
                           |
                           +--> PostgreSQL (durable jobs, idempotency, leases)
                                      ^
                                      |
                             Worker dispatcher
                                      |
                              Concurrent workers
                                      |
                            Registered JobHandlers
```

PostgreSQL is the source of truth for job state. Claiming uses a short transaction and `FOR UPDATE SKIP LOCKED`; handler work occurs outside the transaction. A worker receives a monotonically increasing lease version. Heartbeats and completion/failure updates require the same worker ID and lease version and are rejected after expiry. The recovery scheduler changes expired processing jobs to retryable `FAILED` or terminal `DEAD` states and increments the lease version to fence stale workers.

Redis is not the queue store in this implementation. It executes the atomic token-bucket Lua script for API submission rate limiting. This separation means Redis queue loss cannot lose durable jobs.


All persisted local date-time values are interpreted as UTC. The JDBC pool initializes every PostgreSQL session to UTC so lease timestamps written by application code and compared in database SQL use the same clock convention.
