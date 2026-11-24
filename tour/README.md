# Backend — `tour/`

Spring Boot 4.1.1 REST API for the tour booking project. Java 21, PostgreSQL, Flyway,
Spring Data JPA/Hibernate, Spring Security + JWT, optional Redis read cache, Spring Mail,
VNPay Sandbox payments and springdoc OpenAPI.

The front end lives in [`../fe-tour`](../fe-tour); the API contract is
[`../docs/api-spec.md`](../docs/api-spec.md).

## Prerequisites

- Java 21 (`pom.xml` sets `java.version=21`).
- PostgreSQL. The default datasource is `jdbc:postgresql://localhost:5432/tour` with user/password
  `postgres`/`postgres`.
- Optional: Redis (the read cache is enabled by default), an SMTP server, VNPay Sandbox
  credentials, a Google OAuth client ID.

The Maven wrapper is committed (`mvnw`, `mvnw.cmd`, `.mvn/wrapper`), so a global Maven install is
not required. It downloads Maven 3.10.0 on first use.

## Configuration

`src/main/resources/application.properties` imports an optional `.env` file from this directory:

```properties
spring.config.import=optional:file:.env[.properties]
```

Copy the template and edit it:

```bash
cp .env.example .env        # Windows: copy .env.example .env
```

Notes:

- `.env` uses **Java properties syntax** (`KEY=value`), not shell `export` syntax.
- Replace the `change-me` placeholders in the copied file. If you leave a datasource line out,
  the default from `application.properties` applies (`postgres`/`postgres` locally).
- OS environment variables override values from `.env`; command-line arguments override both.
- Never commit `.env` (ignored by [`../.gitignore`](../.gitignore)).
- There are no production Spring profiles. Tests activate the `test` profile with
  `@ActiveProfiles("test")`, which loads `src/test/resources/application-test.properties`.
- `spring.jpa.hibernate.ddl-auto=validate`: schema changes must be new Flyway migrations, never
  entity-only changes.

## Environment variables

`JWT_SECRET` is the only value that must be set for a normal local start; everything else has a
safe default or disables the related integration when blank.

### Database, Flyway and cache

| Variable | Purpose | Required? | Example placeholder |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL | No (defaults to local `tour`) | `jdbc:postgresql://localhost:5432/tour` |
| `SPRING_DATASOURCE_USERNAME` | Database user | No (default `postgres`) | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | No (default `postgres`) | `change-me` |
| `SPRING_FLYWAY_ENABLED` | Run migrations at startup | No (default `true`) | `true` |
| `SPRING_CACHE_TYPE` | Spring cache provider | No (default `redis`; set `none` without Redis) | `redis` |
| `SPRING_CACHE_TTL` | Tour read-cache TTL | No (default `PT10M`) | `PT10M` |
| `SPRING_DATA_REDIS_URL` | Redis connection | No (default `redis://localhost:6379`) | `redis://localhost:6379` |
| `SPRING_DATA_REDIS_TIMEOUT` | Redis command timeout | No (default `1s`) | `1s` |
| `SPRING_DATA_REDIS_CONNECT_TIMEOUT` | Redis connect timeout | No (default `1s`) | `1s` |

### Auth and CORS

| Variable | Purpose | Required? | Example placeholder |
|---|---|---|---|
| `JWT_SECRET` | HMAC key for access tokens; **must be at least 32 bytes** or startup fails | **Yes** | `change-me-to-a-random-32-byte-secret` |
| `APP_COOKIE_SECURE` | Mark the refresh cookie `Secure` (HTTPS) | No (default `false`) | `false` |
| `APP_CORS_ALLOWED_ORIGINS` | Comma-separated exact frontend origins | No (default `http://localhost:3000`) | `http://localhost:3000` |
| `GOOGLE_CLIENT_ID` | Google ID-token audience; blank disables Google sign-in | No (blank) | `change-me.apps.googleusercontent.com` |
| `APP_FRONTEND_URL` | Frontend origin used in email links | No (default `http://localhost:3000`) | `http://localhost:3000` |
| `APP_TRUST_PROXY` | Trust `X-Forwarded-*` for client IP / rate limits | No (default `false`) | `false` |

### Mail / SMTP

The `SPRING_MAIL_*` names take precedence; the shorter `MAIL_*` names are supported aliases
(`${SPRING_MAIL_HOST:${MAIL_HOST:}}`). If `SPRING_MAIL_HOST` and `MAIL_HOST` are both blank,
email sending is skipped safely instead of hanging startup.

| Variable | Purpose | Required? | Example placeholder |
|---|---|---|---|
| `SPRING_MAIL_HOST` / `MAIL_HOST` | SMTP host | No (blank disables sending) | `smtp.example.com` |
| `SPRING_MAIL_PORT` / `MAIL_PORT` | SMTP port | No (default `587`) | `587` |
| `SPRING_MAIL_USERNAME` / `MAIL_USERNAME` | SMTP user | No (blank) | `change-me@example.com` |
| `SPRING_MAIL_PASSWORD` / `MAIL_PASSWORD` | SMTP password / app password | No (blank) | `change-me` |
| `SPRING_MAIL_SMTP_AUTH` / `MAIL_SMTP_AUTH` | SMTP AUTH | No (default `true`) | `true` |
| `SPRING_MAIL_SMTP_STARTTLS` / `MAIL_SMTP_STARTTLS` | STARTTLS | No (default `true`) | `true` |
| `SPRING_MAIL_SMTP_CONNECTIONTIMEOUT` | Connect timeout (ms) | No (default `5000`) | `5000` |
| `SPRING_MAIL_SMTP_TIMEOUT` | Read timeout (ms) | No (default `5000`) | `5000` |
| `SPRING_MAIL_SMTP_WRITETIMEOUT` | Write timeout (ms) | No (default `5000`) | `5000` |
| `MAIL_FROM` | From address | No (default `no-reply@tour.local`) | `no-reply@example.com` |

### Payments

| Variable | Purpose | Required? | Example placeholder |
|---|---|---|---|
| `PAYMENT_CALLBACK_SECRET` | HMAC-SHA256 secret for signed payment callbacks | No (blank fails callbacks closed) | `change-me` |
| `PAYMENT_MOCK_ENABLED` | Enable the owner-only demo card endpoint | No (default `false`) | `false` |
| `VNPAY_TMN_CODE` | VNPay merchant code | No (blank fails VNPay closed) | `change-me` |
| `VNPAY_HASH_SECRET` | VNPay HMAC secret | No (blank) | `change-me` |
| `VNPAY_PAY_URL` | VNPay payment page URL | No (blank) | `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html` |
| `VNPAY_RETURN_URL` | Browser return URL (frontend) | No (blank) | `http://localhost:3000/payment/vnpay-return` |
| `VNPAY_IPN_TOKEN` | URL-safe token for the IPN route | No (blank) | `change-me-to-a-random-url-safe-token` |

Some settings have no environment placeholder (defaults are in `application.properties` and can be
overridden with Spring properties): `app.jwt.access-token-ttl` (`PT15M`),
`app.auth.refresh-token-ttl` (`P7D`), `app.auth.password-reset-token-ttl` (`PT30M`),
`app.auth.email-verification-token-ttl` (`P2D`), `app.admin.list.max-page-size` (`50`).

## Run

```bash
cd tour
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

The API listens on port 8080. Flyway runs at startup:

- `db/callback/beforeMigrate.sql` creates the two bootstrap tables (`account_roles`, `accounts`).
- `db/migration/V1`–`V9` create the rest of the schema.
- `baseline-on-migrate=true` with `baseline-version=0` keeps pre-Flyway databases working.

Never edit an applied migration; add `V10__*.sql` or later. `db/quanlybantour_booking_floor.sql` is a
reference schema + seed, **not** a migration — do not apply it on top of a Flyway database.

### Seed roles and dev data

Flyway creates `account_roles` but does not insert rows. `db/seed/dev_seed.sql` expects
`ROLE_ADMIN`, `ROLE_USER` and `ROLE_STAFF` to exist (it seeds a `demo_staff` account) and is run
manually with `psql`, not by Flyway:

```bash
psql -U postgres -d tour -c "INSERT INTO account_roles (role_name) SELECT v.role_name FROM (VALUES ('ROLE_ADMIN'), ('ROLE_USER'), ('ROLE_STAFF')) AS v(role_name) WHERE NOT EXISTS (SELECT 1 FROM account_roles r WHERE r.role_name = v.role_name);"

psql -U postgres -d tour -f src/main/resources/db/seed/dev_seed.sql
```

Demo accounts created by the seed use password `demo1234`:
`admin@example.com` (`ROLE_ADMIN`), `demo1@example.com` … `demo3@example.com` (`ROLE_USER`) and
`staff@example.com` (`ROLE_STAFF`, an inert label today). Re-running the seed is guarded by
`WHERE NOT EXISTS` / `ON CONFLICT` checks.

### Redis (optional)

The read cache is on by default. Either run Redis on `localhost:6379`, point
`SPRING_DATA_REDIS_URL` at an existing instance, or disable the cache with
`SPRING_CACHE_TYPE=none`. `Dockerfile.redis` contains a small standalone Redis image example
(port 6380 in its comments); credentials are supplied at runtime, never baked into the image.

## Tests

```bash
cd tour
./mvnw test                                                      # full suite
./mvnw "-Dtest=!*IntegrationTests,!TourApplicationTests" test    # no database needed
./mvnw -Dtest=TourServiceTest test                               # one class
./mvnw -Dtest=TourServiceTest#someMethod test                    # one method

# Windows
.\mvnw.cmd test
.\mvnw.cmd "-Dtest=!*IntegrationTests,!TourApplicationTests" test
```

- `@WebMvcTest` / Mockito tests cover controllers, security, services and config.
- `*IntegrationTests` and `TourApplicationTests` are `@SpringBootTest` classes and need a running
  PostgreSQL. They use the configured datasource and let Flyway + Hibernate validation run.
- There is no H2 or Testcontainers setup. Do not point the tests at a shared or production
  database; use a disposable local database.

## Build

```bash
./mvnw package                 # runs the full test suite (needs PostgreSQL)
./mvnw -DskipTests package     # build the jar without tests
java -jar target/tour-0.0.1-SNAPSHOT.jar
```

## API docs

- Swagger UI: <http://localhost:8080/swagger-ui/index.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Machine-readable contract: [`../docs/api-spec.md`](../docs/api-spec.md)
- Health: <http://localhost:8080/actuator/health>

Public GETs are limited to tour reads, the VNPay IPN route plus Swagger/health. Auth `POST`s are
public. Everything else requires a Bearer access token; the admin route matrix requires
`ROLE_ADMIN`.

## Optional integrations

### VNPay Sandbox

The backend implements VNPay PAY 2.1.0 with HMAC-SHA512, a 15-minute payment window, browser
return verification, IPN handling and QueryDr reconciliation. Set:

- `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` — Sandbox merchant credentials (backend only).
- `VNPAY_PAY_URL` — exactly `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`.
- `VNPAY_RETURN_URL` — the frontend return page, e.g.
  `http://localhost:3000/payment/vnpay-return`.
- `VNPAY_IPN_TOKEN` — a random URL-safe token; register
  `https://<backend-host>/api/payments/vnpay/ipn/<token>` with VNPay. The IPN needs a public HTTPS
  URL, so it cannot reach localhost.

Blank VNPay settings fail closed. The frontend disables the VNPay checkout option under
`next dev` (to keep signed return parameters out of the development logger), so test it with a
production frontend build. The gated demo card endpoint (`PAYMENT_MOCK_ENABLED`) is separate and
off by default.

### Google sign-in

Set the same Google OAuth **client ID** on both sides:

- backend: `GOOGLE_CLIENT_ID`;
- frontend: `NEXT_PUBLIC_GOOGLE_CLIENT_ID` (see `fe-tour/.env.example`).

Add the frontend origin to the Google Cloud console's **Authorized JavaScript origins**. This uses
the Google Identity Services ID-token flow: no client secret, redirect URI or Spring OAuth2 client
is involved. Blank values hide the button and make `POST /api/auth/google` return `503`.

### SMTP / email

Set the `SPRING_MAIL_*` values (or the `MAIL_*` aliases) and `MAIL_FROM`. Email is sent
asynchronously after the database transaction commits, with UTF-8 encoding and 5-second timeouts.
Blank host/user values leave mail disabled; verification, reset and booking emails are then skipped
rather than blocking the request.
