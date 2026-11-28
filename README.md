# Hành Trình — tour booking and management

A tour booking and management site for a single travel company. Customers browse tours and
departures, book seats, pay online and manage their trips. Operations staff use a restricted
console; administrators manage the whole system.

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
- Pay online through VNPay Sandbox when configured, or reserve now and pay later. The VNPay option
  is enabled only in a production frontend build (`next dev` hides it). A demo card payment
  endpoint exists but is disabled by default.
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
  cascade), plus printable/CSV passenger manifests per departure.
- Invoice list and printable detail.
- Catalogue management for accommodations, guides, vehicles, destinations and tour images.

### Role model

- `ROLE_USER` — the default role assigned on email registration and Google sign-in.
- `ROLE_STAFF` — operations staff, with the five console sections below.
- `ROLE_ADMIN` — full administrative access, subject to existing business-state validation.

| Capability | Customer (`ROLE_USER`) | Staff (`ROLE_STAFF`) | Admin (`ROLE_ADMIN`) |
|---|---|---|---|
| Browse tours, own bookings/profile/payments/invoices | Existing customer flow | No additional customer privileges | Existing admin behavior |
| View/filter operational bookings | Own only | Yes | Yes |
| Confirm / complete bookings | Own only | Yes | Yes |
| Cancel unpaid PENDING bookings | Own only | Yes | Yes |
| Record CASH payment, view payments, reconcile pending VNPay | Own VNPay flow only | Yes | Yes |
| Read customers and booking history | Own profile only | Yes | Yes |
| View/print invoices | Own only | Yes | Yes |
| Read departures and passenger manifests | Public departure reads only | Yes | Yes |
| Dashboard, statistics and reports | No | No | Yes |
| Tour/departure/catalog CRUD and departure cancellation | No | No | Yes |
| Promotions, accounts and roles; customer edit/delete | No | No | Yes |
| Refunds and PAID-booking cancellation | No | No | Admin-only where implemented |
| Any unlisted `/api/admin/**` operation, including future endpoints | No | No | Yes |

Backend security uses an explicit method/path staff allow-list before the admin-only fallback.
The frontend hides forbidden navigation and blocks direct URLs. Staff signs into
`/admin/bookings`; admins go to `/admin`; customers keep their current redirect behavior.
Staff cannot use generic booking PUT/DELETE or payment/invoice CRUD. Admins can assign Staff
through the DB-driven Accounts role selector. Self-demotion/disable/delete, removing the last
active admin, and renaming/deleting the admin role are protected.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 4.1.1, Java 21, Spring Web MVC, Spring Data JPA/Hibernate, Spring Security + JJWT |
| Database | PostgreSQL, Flyway migrations (`V1`–`V10`), Hibernate `ddl-auto=validate` |
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
createdb -U postgres tour      # once, if the database does not exist yet (or use pgAdmin)
cd tour
cp .env.example .env           # Windows: copy .env.example .env
# edit .env: replace the JWT_SECRET and datasource "change-me" placeholders
# (JWT_SECRET must be >= 32 bytes); or remove a datasource line to use its default
./mvnw spring-boot:run         # Windows: .\mvnw.cmd spring-boot:run
```

Flyway runs automatically at startup: `db/callback/beforeMigrate.sql` creates the two bootstrap
tables, then `db/migration/V1`–`V10` create the schema and seed roles. The API starts on
<http://localhost:8080>.

Flyway V10 idempotently seeds all three roles. The optional dev seed is **development-only**:

```bash
cd tour
psql -U postgres -d tour -f src/main/resources/db/seed/dev_seed.sql
```

`dev_seed.sql` adds demo tours, departures, bookings and sign-in-able demo accounts (password
`demo1234`):

- Username `demo_admin`, email `admin@example.com` (`ROLE_ADMIN`)
- Usernames `demo_user1` … `demo_user3`, emails `demo1@example.com` … `demo3@example.com` (`ROLE_USER`)
- Username `demo_staff`, email `staff@example.com` (`ROLE_STAFF`)

Sign in with the **username**, not the email. Never seed these known credentials in production.

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
npm run build         # production build (also type-checks)
npx tsc --noEmit      # optional standalone type check; run after build/dev
                      # so generated .next/types and next-env.d.ts exist
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
