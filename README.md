# Nimbus — Distributed Job Execution Platform

A backend platform for submitting, scheduling, and executing background jobs across distributed workers — a simplified take on Temporal, Sidekiq, and AWS Batch.

Built to production standards: versioned schema migrations, layered architecture, database-level integrity constraints, and integration tests against real PostgreSQL.

[![CI](https://github.com/GauravAcharya511/nimbus-job-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/GauravAcharya511/nimbus-job-platform/actions/workflows/ci.yml)

---

## Architecture

```
                 ┌─────────────┐
   HTTP client ──▶│ Controller  │  validation, HTTP semantics
                 └──────┬──────┘
                        │
                 ┌──────▼──────┐
                 │   Service   │  business logic, transaction boundaries
                 └──────┬──────┘
                        │
                 ┌──────▼──────┐
                 │ Repository  │  Spring Data JPA
                 └──────┬──────┘
                        │
                 ┌──────▼──────┐
                 │ PostgreSQL  │  schema owned by Flyway migrations
                 └─────────────┘
```

Nimbus is a **modular monolith**. Packages are organized by domain (`job`, `config`) rather than by layer, so boundaries stay clear if pieces are later extracted into separate services.

## Tech Stack

| Concern | Choice |
|---|---|
| Language / Runtime | Java 21 |
| Framework | Spring Boot 4.1 |
| Database | PostgreSQL 16+ |
| Migrations | Flyway |
| Persistence | Spring Data JPA / Hibernate |
| Testing | JUnit 5, MockMvc, Testcontainers |
| Build | Maven |
| CI | GitHub Actions |
| Observability | Spring Boot Actuator |

## Quickstart

```bash
docker compose up -d          # start PostgreSQL
./mvnw spring-boot:run        # Flyway migrates, app serves on :8081 (override with NIMBUS_PORT)
```

## API

Submit a job:

```bash
curl -i -X POST http://localhost:8081/api/jobs \
  -H 'Content-Type: application/json' \
  -d '{"type":"send-email","payload":"{\"to\":\"user@example.com\"}"}'
```

```
HTTP/1.1 201 Created
Location: /api/jobs/178286d9-1413-43cd-961e-220fe61f1f7d
```

```json
{
  "id": "178286d9-1413-43cd-961e-220fe61f1f7d",
  "type": "send-email",
  "status": "PENDING",
  "attempts": 0,
  "maxAttempts": 3,
  "createdAt": "2026-07-19T11:32:27Z"
}
```

### Authentication

Register or log in to receive a JWT, then send it as a bearer token on every request.

```bash
curl -s -X POST http://localhost:8081/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"password123","firstName":"Your","lastName":"Name"}'
```

```json
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "tokenType": "Bearer", "expiresInMinutes": 60 }
```

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/jobs
```

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Create an account. Returns `201` + JWT. |
| `POST` | `/api/auth/login` | Exchange credentials for a JWT. |

Passwords are hashed with BCrypt. The signing secret is read from `NIMBUS_JWT_SECRET`
and never committed; the checked-in default is for local development only.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/jobs` | Submit a job. Returns `201` + `Location`. |
| `GET` | `/api/jobs?page=0&size=20` | List jobs (paginated). |
| `GET` | `/api/jobs/{id}` | Fetch one job. `404` if unknown. |
| `GET` | `/actuator/health` | Liveness probe. |

Errors are returned as [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807) `application/problem+json`.

## Engineering Decisions

**Flyway owns the schema; Hibernate validates it.** Running with `ddl-auto: validate` means the application refuses to start if the JPA entities and the migrated schema disagree. Schema drift fails loudly at boot rather than silently at runtime.

**Integrity constraints live in the database.** Job status values and the `attempts <= max_attempts` invariant are enforced by `CHECK` constraints, not only in application code. Invalid rows cannot exist even if a bug bypasses the service layer.

**Indexed for the read path that matters.** `idx_jobs_status_created (status, created_at)` is built for the worker claim query (`WHERE status = 'PENDING' ORDER BY created_at`) — equality column first, then the sort column.

**Tests run against real PostgreSQL.** Testcontainers starts an actual database and applies the real migrations. An in-memory database such as H2 would pass the constraint tests vacuously, since it does not enforce the same rules.

**Unknown resources return 404, not 403.** Job lookups are scoped by owner
(`findByIdAndUserId`), so requesting another user's job is indistinguishable from
requesting one that does not exist. A 403 would confirm the resource exists.

**Workers claim jobs with `SELECT ... FOR UPDATE SKIP LOCKED`.** Each worker locks
the rows it claims and skips rows another worker already holds, so instances partition
the queue with no coordination service and never execute the same job twice. Claiming
and executing run in separate transactions, so row locks are not held for the duration
of the work.

**Failures retry with exponential backoff, then dead-letter.** A failed attempt sets
`next_attempt_at` to now + 2^attempts seconds and returns the job to `PENDING`. Once
`attempts` reaches `max_attempts` the job is marked `FAILED` and no longer claimed.

## Testing

```bash
./mvnw verify     # requires a running Docker daemon
```

- **API integration tests** — created / validation / not-found / pagination paths through the full stack
- **Schema constraint tests** — assert the database itself rejects invalid data

## Roadmap

- [x] **v0.1** — Job submission API, migrations, integration tests, CI
- [x] **v0.2** — JWT authentication and per-user job ownership
- [x] **v0.3** — Worker execution with retries and exponential backoff
- [ ] **v0.4** — Scheduled and recurring jobs
- [ ] **v0.5** — Redis-backed queue and distributed locking
- [ ] **v0.6** — Kafka event stream for job lifecycle events
- [ ] **v0.7** — Prometheus metrics and Grafana dashboards
- [ ] **v0.8** — Horizontally scaled workers

## License

MIT
