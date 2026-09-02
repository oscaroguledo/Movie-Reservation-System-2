# Movie-Reservation-System-2

Backend system for a movie reservation service — a Java/Spring Boot port
of the Python/FastAPI reference implementation at
[oscaroguledo/Movie-Reservation-System](https://github.com/oscaroguledo/Movie-Reservation-System),
aiming for architectural parity rather than a simplified rewrite.

## Status

- **auth-api** — complete: accounts, JWT auth with server-side revocation,
  role-based access, rate limiting, the full event-driven write pipeline.
- **movie-api** — complete: catalog (genres/movies/showrooms/seats),
  screening scheduling, seat-hold reservations (guests included), simulated
  payments, admin reporting — all on the same eventually-consistent write
  pipeline as auth-api, plus its own Redis-backed concurrency guards (see
  below).

## Architecture

Two independently deployable services sharing a cache-aside +
event-sourcing write pattern:

1. A write lands in **Redis** immediately, so an immediate read reflects
   it (read-your-writes).
2. The same write is published as a domain event to **Kafka**.
3. An async worker (`AuthEventWorker` / `MovieEventWorker`, a
   `@KafkaListener` running in-process for now — see below) consumes the
   event and persists it to **Postgres**, the source of truth. Redis is a
   cache that can always be rebuilt from Postgres.

This is deliberately **eventually consistent**, matching the Python
reference exactly — including its accepted races (e.g. a delete
processed before the register event that created the row). `movie-api`'s
worker additionally replicates the reference's idempotent-upsert +
manual-ack semantics: a duplicate CREATE (redelivery) is treated as
harmless, an UPDATE arriving before its CREATE falls back to creating the
row, and only a DB-unavailable failure leaves a message unacknowledged.

Unlike the Python reference's separate `worker.py` process, both
services' Kafka consumers are in-process components rather than a second
deployable. Splitting either out later would mean adding a "worker-only"
Spring profile that disables the web server, without touching any
event-handling code.

**`movie-api`'s reservation flow is the one place Redis is the actual
correctness guard, not just a cache**: seat holds use `SET key value NX
PX <ttl>` (Spring: `setIfAbsent`) keyed by `showtime:seat` — that atomic
operation is what prevents two people booking the same seat; the Postgres
partial-unique-index is a backstop, not the live guard. A PENDING
reservation is lazily settled to EXPIRED on read once its hold TTL has
passed (no background sweep). Guests are first-class — no token yields a
guest principal, not a 401, and a guest's reservation id is its only
credential. Payments are simulated: a `confirm` succeeds only if the
submitted amount exactly matches the screening's price, otherwise it's
recorded FAILED and returns 402; cancelling a CONFIRMED reservation
auto-refunds. Screening-overlap prevention is a similar Redis lock
(`SETNX`) around a check-then-append against the showroom's schedule.
`movie-api` validates the JWTs `auth-api` issues (same shared secret) but
has no access to `auth-api`'s revocation table, so it does **not** check
revocation — a real, acknowledged limitation carried over from the Python
reference as-is.

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
movie-api/
  Dockerfile
  build.gradle
  src/main/java/movie/
    Application.java
    config/       # Redis, JPA/Flyway, Security wiring
    controller/   # Genre/Movie/Showroom/Screening/Reservation/Payment/
                  # AdminReport/HealthController
    dto/          # request/response records
    event/        # MovieEvent + subtypes, publisher, Kafka worker
    cache/        # EntityCacheService, SeatLockService, ScreeningCacheService,
                  # ReservationCacheService
    model/        # Genre, Movie, Showroom, ShowroomSeat, Showtime,
                  # MovieShowtime, Reservation, Payment, + status enums
    ratelimit/    # Redis sliding-window rate limiter (POST /reservations only)
    repository/   # Spring Data JPA repositories
    security/     # MoviePrincipal, JwtPrincipalFilter (guest-by-default,
                  # no revocation check)
    service/      # Genre/Movie/Showroom/Screening/Reservation/Payment/
                  # ReportingService
    web/          # exceptions + GlobalExceptionHandler
  src/main/resources/
    application.yml
    db/migration/ # Flyway SQL, mirrors the Python schema.dbml
  src/test/java/movie/  # unit + Testcontainers integration tests
docker-compose.yml               # postgres, redis, kafka, auth-api, movie-api
.env.example
```

## Running locally

### Docker Compose (recommended)

```bash
cp .env.example .env
# edit .env — at minimum set a real JWT_SECRET_KEY (32+ bytes)
docker compose up -d --build
curl http://localhost:8010/health   # auth-api
curl http://localhost:8011/health   # movie-api
```

auth-api is on **`http://localhost:8010`**, movie-api on
**`http://localhost:8011`**, Redis on `6380` — not the Python reference's
`8000`/`8001`/`6379` — so both stacks can run at once on the same machine
without a port clash. Both services share one Postgres database
(`auth_api`/`movie_api` as separate schemas within it, matching the
Python reference's own shape), plus the same Redis and Kafka.

### Local dev (no Docker for the app itself)

```bash
./gradlew :auth-api:bootRun   # or :movie-api:bootRun
```

Needs Postgres/Redis/Kafka reachable at the hosts/ports in each module's
`application.yml` defaults (`localhost`), or override via env vars
(`DB_HOST`, `REDIS_HOST`, `KAFKA_BOOTSTRAP_SERVERS`, etc. — see each
module's `application.yml` and `.env.example`).

## API

### auth-api (`:8010`)

| Method | Path            | Auth              | Notes                                  |
|--------|-----------------|--------------------|-----------------------------------------|
| GET    | `/health`       | none               |                                          |
| POST   | `/auth/register`| none (rate-limited)| always creates a REGULAR user           |
| POST   | `/auth/register/admin` | ADMIN       | the only way to grant admin             |
| POST   | `/auth/login`   | none (rate-limited)| returns a JWT access token              |
| POST   | `/auth/logout`  | bearer token       | revokes the caller's own token          |
| GET    | `/users/me`     | bearer token       |                                          |
| GET    | `/users`        | ADMIN               | filter by `type`, paginate             |
| GET    | `/users/{id}`   | bearer token       | self or ADMIN                           |
| PATCH  | `/users/{id}`   | bearer token       | self or ADMIN                           |
| DELETE | `/users/{id}`   | bearer token       | self or ADMIN                           |

### movie-api (`:8011`)

| Method | Path                                                    | Auth                | Notes                              |
|--------|----------------------------------------------------------|---------------------|-------------------------------------|
| GET    | `/health`                                                 | none                 |                                     |
| POST/GET/PATCH/DELETE | `/genres`, `/genres/{id}`                 | writes: ADMIN; reads: none | full CRUD                    |
| POST/GET/PATCH/DELETE | `/movies`, `/movies/{id}`                 | writes: ADMIN; reads: none | includes genre association   |
| POST/GET/PATCH/DELETE | `/showrooms`, `/showrooms/{id}`           | writes: ADMIN; reads: none |                               |
| POST   | `/showrooms/{id}/seats`                                   | ADMIN                | bulk-create rows × seats-per-row   |
| GET    | `/showrooms/{id}/seats`                                   | none                 |                                     |
| POST   | `/screenings`                                             | ADMIN                | Redis-locked overlap check → 409   |
| GET    | `/screenings`                                             | none                 | by date or upcoming                |
| DELETE | `/screenings/{movieId}/{showroomId}/{showtimeId}`         | ADMIN                | refused if reservations exist      |
| GET    | `/screenings/{movieId}/{showroomId}/{showtimeId}/seats`   | none                 | seat map: available/held/booked    |
| POST   | `/reservations`                                           | none (guest OK, rate-limited) | all-or-nothing multi-seat hold |
| GET    | `/reservations`                                           | bearer token (not guest) | caller's own reservations      |
| GET    | `/reservations/{id}`                                      | none                 | lazily settles to EXPIRED if past TTL |
| POST   | `/reservations/{id}/confirm`                               | none                 | simulated payment; 402 on amount mismatch |
| PATCH  | `/reservations/{id}/cancel`                                | none                 | auto-refunds if CONFIRMED          |
| GET    | `/reservations/{id}/payments`                              | none                 |                                     |
| GET    | `/admin/reservations`                                      | ADMIN                | filter by status, paginate         |
| GET    | `/admin/screenings/{movieId}/{showroomId}/{showtimeId}/capacity` | ADMIN          |                                     |
| GET    | `/admin/revenue`                                            | ADMIN                | gross / refunded / net             |

## Testing

```bash
./gradlew clean build
```

Runs the full unit + Testcontainers integration suite for both modules
(Postgres, Redis, and Kafka containers are started automatically —
requires Docker). Both modules have also been exercised manually against
the real `docker compose up` stack — end-to-end flows (register → login,
catalog CRUD, schedule a screening → hold seats → double-booking rejected
with 409 → confirm with payment → cancel-and-refund → admin report) all
verified against the running containers, not just the automated suite.
