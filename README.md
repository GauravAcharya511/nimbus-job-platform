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
./mvnw spring-boot:run        # Flyway migrates, app serves on :8080
```

## API

Submit a job:

```bash
curl -i -X POST http://localhost:8080/api/jobs \
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

## Testing

```bash
./mvnw verify     # requires a running Docker daemon
```

- **API integration tests** — created / validation / not-found / pagination paths through the full stack
- **Schema constraint tests** — assert the database itself rejects invalid data

## Roadmap

- [x] **v0.1** — Job submission API, migrations, integration tests, CI
- [ ] **v0.2** — JWT authentication and per-user job ownership
- [ ] **v0.3** — Worker execution with retries and exponential backoff
- [ ] **v0.4** — Scheduled and recurring jobs
- [ ] **v0.5** — Redis-backed queue and distributed locking
- [ ] **v0.6** — Kafka event stream for job lifecycle events
- [ ] **v0.7** — Prometheus metrics and Grafana dashboards
- [ ] **v0.8** — Horizontally scaled workers

## License

MIT
