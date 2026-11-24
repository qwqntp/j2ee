# Hành Trình — tour booking and management

A tour booking and management site for a single travel company. Customers browse tours and
departures, book seats, pay online and manage their trips; staff use a separate admin console to
manage tours, departures, bookings, payments, accounts, promotions and invoices.

The repository contains two independent applications:

- `tour/` — Spring Boot REST API, PostgreSQL schema (Flyway) and business rules.
- `fe-tour/` — Next.js App Router front end for customers and administrators.

## Feature overview

### Customer

- Browse and search tours with destination, price, date and duration filters plus sorting.
- Tour detail pages with departures, images, ratings and availability.
- Register / sign in with email and password, or with Google Identity Services when configured.
  Access tokens are short-lived JWTs; the refresh token is an HttpOnly cookie.
- Forgot-password, reset-password and email-verification flows.
- Checkout that creates a real `PENDING` booking with one traveler per seat, an optional promotion
  code and server-computed totals.
- Pay online through VNPay Sandbox when configured, or reserve now and pay later. A demo card
  payment endpoint exists but is disabled by default.
- Booking list and detail (`/account/bookings`, aliases under `/my-trips`), cancel, confirm,
  complete, and a printable invoice.
- Profile editing and password change.

### Administrator

- Dashboard with revenue, booking-status, tour/departure and capacity statistics.
- Booking operations: filters, detail, recording cash payments, confirm/complete, pending
  cancellation.
- Account and role management.
- Promotion management.
- Tour and departure management (including the guide-overlap guard and departure-cancellation
  cascade).
- Invoice list and printable detail.
- Catalogue management for accommodations, guides, vehicles, destinations and tour images.

### Role model

- `ROLE_USER` — the default role assigned on email registration and Google sign-in.
- `ROLE_ADMIN` — required for the admin console and every route under `/api/admin/**` and the
  admin-only prefixes in `SecurityConfig`. The front end also gates the console client-side.
- `ROLE_STAFF` — present in the reference schema and dev seed as a label. **No code enforces it**;
  it currently has the same permissions as `ROLE_USER`.

Roles live in the `account_roles` table and can be managed through the admin API/UI. Only the two
roles above affect authorization today.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 4.1.1, Java 21, Spring Web MVC, Spring Data JPA/Hibernate, Spring Security + JJWT |
| Database | PostgreSQL, Flyway migrations (`V1`–`V9`), Hibernate `ddl-auto=validate` |
| Cache | Spring Cache + Redis (optional; can be disabled) |
| Auth | Email/password + Google ID-token sign-in; JWT access token + rotating refresh cookie |
| Payments | VNPay Sandbox PAY 2.1.0 (HMAC-SHA512, return/IPN/QueryDr); gated demo card endpoint |
| Email | Spring Mail / SMTP, asynchronous after-commit delivery |
| API docs | springdoc-openapi (Swagger UI) |
| Frontend | Next.js 16 App Router, React 19, TypeScript 5, Tailwind CSS 4, Vitest |
| CI | GitHub Actions (backend unit/web tests, frontend lint/test/build) |

## Repository layout

```
.
├── tour/        Spring Boot API, Flyway migrations, backend tests
├── fe-tour/     Next.js customer + admin UI
├── docs/        API spec, feature inventory and historical notes
│   ├── admin/   protected audit/task records
│   └── archive/ historical one-off reports (may be outdated)
└── .github/     CI workflow
```

## Quick start

### Prerequisites

- Java 21 (the Maven wrapper downloads Maven 3.10.0; no global Maven needed).
- Node.js 20.9 or newer (CI uses Node 22) with npm.
- PostgreSQL with a database named `tour` (default connection:
  `jdbc:postgresql://localhost:5432/tour`, user/password `postgres`/`postgres`).
- Optional: Redis for the read cache. Without Redis, set `SPRING_CACHE_TYPE=none`.

### 1. Backend

```bash
createdb -U postgres tour      # once, if the database does not exist yet
cd tour
cp .env.example .env           # Windows: copy .env.example .env
# edit .env: JWT_SECRET is required (>= 32 bytes) and must not stay "change-me"
./mvnw spring-boot:run         # Windows: .\mvnw.cmd spring-boot:run
```

Flyway runs automatically at startup: `db/callback/beforeMigrate.sql` creates the two bootstrap
tables, then `db/migration/V1`–`V9` create the schema. The API starts on
<http://localhost:8080>.

Flyway does **not** seed roles. While the backend is running, open a second terminal and run the
one-time role insert plus the optional dev seed:

```bash
psql -U postgres -d tour -c "INSERT INTO account_roles (role_name) SELECT v.role_name FROM (VALUES ('ROLE_ADMIN'), ('ROLE_USER'), ('ROLE_STAFF')) AS v(role_name) WHERE NOT EXISTS (SELECT 1 FROM account_roles r WHERE r.role_name = v.role_name);"

psql -U postgres -d tour -f src/main/resources/db/seed/dev_seed.sql
```

`dev_seed.sql` adds demo tours, departures, bookings and sign-in-able demo accounts (password
`demo1234`):

- `admin@example.com` (`ROLE_ADMIN`)
- `demo1@example.com`, `demo2@example.com`, `demo3@example.com` (`ROLE_USER`)
- `staff@example.com` (`ROLE_STAFF`, currently the same permissions as `ROLE_USER`)

### 2. Frontend

```bash
cd fe-tour
cp .env.example .env.local     # Windows: copy .env.example .env.local
npm ci
npm run dev
```

Open <http://localhost:3000>. The browser calls the backend directly (default
`http://localhost:8080`); the backend allows the frontend origin through
`APP_CORS_ALLOWED_ORIGINS`.

| Service | Default URL |
|---|---|
| Frontend | <http://localhost:3000> |
| Backend API | <http://localhost:8080> |
| Swagger UI | <http://localhost:8080/swagger-ui/index.html> |
| OpenAPI JSON | <http://localhost:8080/v3/api-docs> |
| Health | <http://localhost:8080/actuator/health> |

## Tests

### Backend

The full suite needs a real PostgreSQL database (Flyway and `ddl-auto=validate` run at startup; no
H2, no Testcontainers). Do not point tests at a shared or production database.

```bash
cd tour
./mvnw test                                                      # all tests
./mvnw "-Dtest=!*IntegrationTests,!TourApplicationTests" test    # unit + web-layer tests only
./mvnw -Dtest=TourServiceTest test                               # one class

# Windows
.\mvnw.cmd test
.\mvnw.cmd "-Dtest=!*IntegrationTests,!TourApplicationTests" test
```

`*IntegrationTests` and `TourApplicationTests` are the database-backed classes. Everything else
(service unit tests, `@WebMvcTest` controller/security tests, config tests) runs without a
database.

### Frontend

```bash
cd fe-tour
npm test              # Vitest
npm run lint          # ESLint
npx tsc --noEmit      # type check (no npm script exists for this)
npm run build         # production build
```

## Documentation

- [API contract](docs/api-spec.md) — endpoints, envelopes, auth and payment callbacks.
- [Feature inventory](docs/feature-list.md) — what exists on the API and in the UI.
- [Admin gap checklist](docs/admin-gap-checklist.md) — remaining admin work.
- [Archive](docs/archive/README.md) — historical reports, kept for traceability.
- Swagger UI (running backend): <http://localhost:8080/swagger-ui/index.html>.

Backend-specific configuration (environment variables, profiles, VNPay/Google/SMTP) is documented
in [`tour/README.md`](tour/README.md). Frontend routes and design notes are in
[`fe-tour/README.md`](fe-tour/README.md).
