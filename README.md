# Movie-Reservation-System-2

Backend system for a movie reservation service — a Java/Spring Boot port
of the Python/FastAPI reference implementation at
[oscaroguledo/Movie-Reservation-System](https://github.com/oscaroguledo/Movie-Reservation-System),
aiming for architectural parity rather than a simplified rewrite.

## Status

- **auth-api** — complete: accounts, JWT auth with server-side revocation,
  role-based access, rate limiting, the full event-driven write pipeline.
- **movie-api** — placeholder module only (catalog, showtimes,
  reservations, payments, admin reporting). A larger, separate piece of
  work once auth-api has proven itself out.

## Architecture

Two independently deployable services (`auth-api` today, `movie-api`
later) sharing a cache-aside + event-sourcing write pattern:

1. A write (register, update, delete, token revoke) lands in **Redis**
   immediately, so an immediate read reflects it (read-your-writes).
2. The same write is published as a domain event to **Kafka**.
3. An async worker (`AuthEventWorker`, a `@KafkaListener` running
   in-process for now — see below) consumes the event and persists it to
   **Postgres**, the source of truth. Redis is a cache that can always be
   rebuilt from Postgres.

This is deliberately **eventually consistent**, matching the Python
reference exactly — including its accepted races (e.g. a delete
processed before the register event that created the row).

Unlike the Python reference's separate `worker.py` process, auth-api's
Kafka consumer is an in-process component (`AuthEventWorker`) rather than
a second deployable. Splitting it out later would mean adding a
"worker-only" Spring profile that disables the web server, without
touching any event-handling code.

## Tech stack

Java 21, Spring Boot 3.2, Gradle (multi-module). Postgres 16 + Flyway,
Redis 7, Kafka 3.8 (KRaft), Spring Security + JWT (`io.jsonwebtoken`),
Argon2id (Spring Security's `Argon2PasswordEncoder`), Testcontainers +
JUnit 5 + AssertJ + Mockito + Awaitility for tests.

## Project structure

```
settings.gradle, build.gradle    # multi-module root — shared plugin/Java config
auth-api/
  Dockerfile
  build.gradle
  src/main/java/auth/
    Application.java
    config/       # Security, Redis, JPA/Flyway wiring
    controller/   # AuthController, UserController, HealthController
    dto/          # request/response records
    event/        # AuthEvent + subtypes, publisher, Kafka worker
    cache/        # Redis cache-aside helpers
    model/        # User, RevokedToken, UserType
    ratelimit/    # Redis sliding-window rate limiter + filter
    repository/   # Spring Data JPA repositories
    security/     # JwtProvider, JwtAuthenticationFilter, AuthPrincipal
    service/      # UserService, TokenService
    web/          # exceptions + GlobalExceptionHandler
  src/main/resources/
    application.yml
    db/migration/ # Flyway SQL, mirrors the Python schema.dbml
  src/test/java/auth/  # unit + Testcontainers integration tests
movie-api/                       # placeholder module
docker-compose.yml               # postgres, redis, kafka, auth-api
.env.example
```

## Running locally

### Docker Compose (recommended)

```bash
cp .env.example .env
# edit .env — at minimum set a real JWT_SECRET_KEY (32+ bytes)
docker compose up -d --build
curl http://localhost:8010/health
```

auth-api is on **`http://localhost:8010`** and Redis on `6380` — not the
Python reference's `8000`/`6379` — so both stacks can run at once on the
same machine without a port clash.

### Local dev (no Docker for the app itself)

```bash
cd auth-api  # or run from the repo root: ./gradlew :auth-api:bootRun
./gradlew bootRun
```

Needs Postgres/Redis/Kafka reachable at the hosts/ports in
`application.yml`'s defaults (`localhost`), or override via env vars
(`DB_HOST`, `REDIS_HOST`, `KAFKA_BOOTSTRAP_SERVERS`, etc. — see
`application.yml` and `.env.example`).

## API

| Method | Path            | Auth              | Notes                                  |
|--------|-----------------|--------------------|-----------------------------------------|
| GET    | `/health`       | none               |                                          |
| POST   | `/auth/register`| none (rate-limited)| always creates a REGULAR user           |
| POST   | `/auth/login`   | none (rate-limited)| returns a JWT access token              |
| POST   | `/auth/logout`  | bearer token       | revokes the caller's own token          |
| GET    | `/users/{id}`   | bearer token       | self or ADMIN                           |
| PATCH  | `/users/{id}`   | bearer token       | self or ADMIN                           |
| DELETE | `/users/{id}`   | bearer token       | self or ADMIN                           |

## Testing

```bash
./gradlew clean build
```

Runs the full unit + Testcontainers integration suite (Postgres, Redis,
and Kafka containers are started automatically — requires Docker).
