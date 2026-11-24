# Final-project requirements audit — result

> Date: 2026-11-24. Scope: `tour/` (Spring Boot 4.1.1 API), `fe-tour/` (Next.js 16), `docs/`.
> Method: read-only inspection of code, migrations, config and tests. Docs were treated as hints only;
> every finding below was checked against the actual files. No source, config, migration or doc was
> changed; no git commands were run; no database was contacted. Secret values are never printed —
> only paths and key names.

## Summary table

| # | Requirement | Status | One-line gap |
|---|---|---|---|
| 1 | Login with Gmail or Facebook | **Partial** | Google/Gmail sign-in is real end to end; Facebook has zero implementation (0 code matches). |
| 2 | Authorization with ≥ 3 distinct user groups | **Partial** | Three role strings exist (`ROLE_ADMIN`, `ROLE_USER`, `ROLE_STAFF`), but only `ROLE_ADMIN` is enforced end to end; `ROLE_STAFF` is an inert label. |
| 3 | Online payment with VNPay or MoMo | **Met** | VNPay Sandbox is implemented end to end (HMAC-SHA512, return, IPN, QueryDr, amount/idempotency, FE wiring). MoMo is only an enum label — acceptable because the requirement is "or". Caveat: sandbox-only and needs public HTTPS + credentials. |
| 4 | Database access through Spring Data JPA | **Met** | `spring-boot-starter-data-jpa`, 19 `JpaRepository` interfaces, 19 entities, specs + custom search. |
| 5 | Sending email with Spring Email | **Met** | `spring-boot-starter-mail` + `JavaMailSender`, 4 flows, after-commit async dispatch. Delivery against a real SMTP server was never verified. |
| 6 | Frontend project + backend project | **Met** | Two independent projects: `fe-tour/` (Next.js) and `tour/` (Spring Boot). |
| 7 | Good-looking UI, at least ~10 screens | **Met** | 24 reachable screens (9 public, 7 customer, 8 admin) with a deliberate editorial design system and a separate admin shell. |
| 8 | Report | **N/A** | Explicitly out of scope for this audit. |
| 9 | Well-organized source code | **Partial** | Clean package-by-layer structure and strong tests, but no backend README, no `.env.example`, no CI, no compose, dead artifacts and stale docs. |
| B1 | Microservice usage (bonus) | **Missing** | Single monolith; no service extracted. |
| B2 | AI integration (bonus) | **Missing** | No AI dependency, class, endpoint or key. |
| B3 | Technologies from course seminars (bonus) | **Partial** | JPA, Spring Security, Redis, Swagger/OpenAPI, VNPay are used; MapStruct, RabbitMQ, WebSocket and Cloudinary are documented but not implemented. |
| B4 | Cloud/internet deployment (bonus) | **Partial** | Only a Redis Dockerfile exists; no backend/frontend Dockerfile or compose; several localhost defaults and cookie/CORS settings must change. |

Docs are stale in important ways: `docs/progress.md` and `docs/feature-audit.md` claim there is no admin UI and no VNPay; both now exist. `docs/feature-list.md` is the most current of the old docs. `docs/api-spec.md` is largely accurate and is the best API reference.

---

## A. Roles (requirement 2)

### A.1 Every role that exists

| Role | Where it appears | Enforced? |
|---|---|---|
| `ROLE_ADMIN` | Seed: `tour/src/main/resources/db/quanlybantour_booking_floor.sql`, `db/seed/dev_seed.sql` (`demo_admin`). Literals in `service/AuthService.java`, `service/PaymentService.java`, `service/BookingService.java`, `service/AccountService.java` (last-admin guard), `security/SecurityConfig.java`, FE `components/admin-gate.tsx`, `components/site-header.tsx`, `lib/admin-accounts.ts`. | **Yes** |
| `ROLE_USER` | Default in `service/AuthService.java` (`DEFAULT_ROLE`) and `service/GoogleAuthService.java`; seeded in the two SQL files. | Implicit baseline only — no `hasRole("USER")` anywhere; meaning is "authenticated non-admin" + owner scoping. |
| `ROLE_STAFF` | Seed only: reference SQL + `dev_seed.sql` (`demo_staff`). | **No** — zero occurrences in `tour/src/main/java` or production FE code. A `demo_staff` login is just a regular user. It will nevertheless appear in the admin role dropdown because the dropdown is DB-driven. |
| `CUSTOMER`, `ROLE_B2_<uuid>` | FE test fixtures and one backend integration-test fixture only. | Not real roles. |

There is no role enum. `model/AccountRole.java` is a JPA entity with free-text `roleName` (`VARCHAR(50)`). `POST/PUT /api/account-roles` lets an admin create arbitrary role labels; only the exact string `ROLE_ADMIN` unlocks privileges. `beforeMigrate.sql` creates an **empty** `account_roles` table and no Flyway migration inserts roles — a fresh DB needs manual seeding or registration fails with "Default role is missing".

### A.2 How many groups are really enforced end to end: **1**

Chain: DB `account_roles.role_name` → JWT `role` claim (`JwtService.issue`, from the account, not the request) → `AuthPrincipal.role()` → `new SimpleGrantedAuthority(principal.role())` (`JwtAuthenticationFilter`) → `hasRole("ADMIN")` (the only role-based matcher, `SecurityConfig.java`). Owner scoping (`AuthPrincipal.id()` with an admin bypass in `BookingService`/`PaymentService`) is service logic, not a third role.

Frontend gate: `app/admin/layout.tsx` wraps `/admin/**` in `components/admin-gate.tsx`. It validates via `/api/auth/me` (`ensureSession()`) and requires **both** the validated session and the localStorage account to be `ROLE_ADMIN`. There is no `middleware.ts` and no server-side per-page check; backend matchers remain authoritative.

### A.3 Current backend access matrix (relevant groups)

| Group | Access |
|---|---|
| Booking list/actions | `GET /api/bookings`, `/{id}`, `/code/{code}`, `PUT /{id}`, `POST /{id}/cancel\|confirm\|complete`, `DELETE /{id}` = any authenticated user, owner-scoped; admin bypasses ownership. Aggregated `GET /api/admin/bookings` = **admin only**. |
| Passengers per departure | **No such endpoint exists.** Travelers appear in `/api/travelers/**` (admin CRUD) and as a `travelers[]` array inside a booking detail (`BookingResponse`), keyed by booking, not departure. `AdminBookingFilter` has `status/from/to/query` only. |
| Payments | `GET /api/bookings/{id}/payments`, `POST .../payments/callback` and all `/api/payments/**` = **admin only**. Exceptions: VNPay start/reconcile/return/status and mock are authenticated + owner-scoped; VNPay IPN is public but token+HMAC gated. |
| Cash payment recording | **Admin only**: `POST /api/payments` with `paymentMethod: "CASH"`, `status: "SUCCESS"`. FE button in `components/admin-bookings.tsx`. |
| Creating bookings for customers | No dedicated endpoint. `POST /api/bookings` has no `accountId` in `CreateBookingRequest`; an admin caller produces an unattributed booking, then must reassign the owner via admin-only `PUT /api/bookings/{id}` with `accountId`. No UI. |
| Accounts, account roles | `/api/accounts/**`, `/api/account-roles/**`, `GET /api/admin/accounts` = **admin only**. |
| Tours, departures, catalog | `GET /api/tours/**` public; `/api/admin/tours/**`, `/api/tour-departures/**`, `/api/customers/**`, `/api/travelers/**`, `/api/tour-guides/**`, `/api/tour-images/**`, `/api/accommodations/**`, `/api/travel-vehicles/**`, `/api/tourist-destinations/**` = **admin only**. |
| Promotions | `/api/promotions/**` = admin CRUD; customers apply codes via `POST /api/bookings`. |
| Stats | `GET /api/admin/stats` = admin only. |
| Refunds, invoices | `/api/refunds/**`, `/api/payments/{id}/refunds`, `/api/invoices/**` = admin; `GET /api/invoices/me` = authenticated owner. |

### A.4 Minimal design for a third role `ROLE_STAFF` (operations staff) — proposal only, NOT implemented

**Principle:** keep the existing single-role-per-account model. `ROLE_STAFF` is already a label in dev seed; make it real by granting it a subset of admin capabilities. Because admins must keep access to shared endpoints, protected routes use `hasAnyRole("ADMIN","STAFF")`, while admin-only routes stay `hasRole("ADMIN")`. No JWT-format change is needed: the `role` claim already carries the DB string and the filter turns it into `ROLE_STAFF` authority verbatim.

**Staff should gain:** booking list + filters (`GET /api/admin/bookings`); booking actions (confirm/complete/cancel, owner-independent); passengers per departure (new read endpoint); payment list/status/reconcile; cash payment recording (`POST /api/payments`); creating a booking on behalf of a customer (new endpoint accepting `accountId`).

**Admin-only stays:** accounts and account roles, tours/departures/catalog CRUD, promotions, `GET /api/admin/stats`, refund approval, Swagger in production.

**Exactly what must change:**

1. **Migration + seeding** — add a new Flyway migration (`V10__seed_staff_role.sql`, never edit V1–V9) that inserts the `ROLE_STAFF` row idempotently (`WHERE NOT EXISTS`). Also add it to `db/seed/dev_seed.sql` for dev. Consider a defensive guard so registration still works if the row is absent.
2. **SecurityConfig matrix** — for the shared paths, change `.hasRole("ADMIN")` to `.hasAnyRole("ADMIN","STAFF")` on specifically: `/api/admin/bookings/**`, booking action paths, `/api/payments/**` (or a narrower staff subset), and any new staff endpoints. Keep `.hasRole("ADMIN")` on accounts, roles, admin tours, departures, catalog, promotions, stats, refunds. Order of matchers matters (existing explicit matchers precede the admin block).
3. **JWT claims/authorities** — no schema or filter change for a single role. If multi-role accounts are ever wanted, that is a larger change (claims become a list, `AuthPrincipal.role()` → `roles()`, filter emits one authority per role). Explicitly out of scope for the minimal design.
4. **New endpoints needed (gaps, not just permissions)** — `GET /api/admin/departures/{id}/passengers` (join bookings → travelers, with pagination) and `POST /api/admin/bookings/for-customer` (body: `accountId` + existing `CreateBookingRequest` fields). Keep these in a new `StaffBookingController`/`StaffBookingService` that delegates to existing public service methods.
5. **Frontend admin gate + sidebar** — generalize `components/admin-gate.tsx` to accept `ROLE_ADMIN` **or** `ROLE_STAFF` (validated via `/api/auth/me`, both checks), add a `roles` field to `lib/admin-navigation.ts` and filter nav items in `components/admin/admin-sidebar.tsx`, add staff-only pages for passengers-per-departure and create-booking-for-customer, and show the console link in `components/site-header.tsx` for both roles. Existing admin-only pages should still hard-redirect staff.
6. **Admin account-management UI** — the role dropdown is already DB-driven (`components/admin/admin-account-editor.tsx` maps `GET /api/account-roles`), so `ROLE_STAFF` appears automatically after seeding. Add a human label/description and keep the last-active-admin guard (`lib/admin-accounts.ts`, `AccountService`) scoped to `ROLE_ADMIN` only.
7. **Tests** — `SecurityConfigTest`: staff 200 on shared endpoints, 403 on admin-only ones; `AdminBookingQueryService` staff filter test; new endpoint tests (passengers, create-for-customer); FE gate/navigation tests with a `ROLE_STAFF` session; a Flyway/idempotency check for the role row.

**Effort estimate (one developer):** authorization-only slice (migration + SecurityConfig + gate/sidebar + tests) ≈ **2–3 working days**. Full slice including the two new endpoints and their FE pages ≈ **5–8 working days** (~1–1.5 weeks). The two new endpoints dominate.

**Merge-conflict risk with the parallel work — HIGH.** Another developer is editing `BookingService`, `RefundService`, `PaymentService`, `AdminStatsService`, the admin tours page and the dashboard. Overlaps: cash-payment and create-for-customer naturally touch `PaymentService`/`BookingService`; the sidebar/dashboard gating touches `admin-dashboard.tsx`; role tests touch `SecurityConfigTest`; refund paths should be left admin-only. Mitigation: (a) land the authorization-only slice first (mostly `SecurityConfig.java`, a new migration, `admin-gate.tsx`, `admin-navigation.ts`, `admin-sidebar.tsx`, tests — minimal overlap with the named files); (b) put new behaviour in new `Staff*` controllers/services that call existing public methods rather than editing the parallel-owned services; (c) coordinate the one expected conflict in `admin-sidebar.tsx`/`admin-dashboard.tsx` before touching the dashboard; (d) defer passengers/create-for-customer until the parallel branch merges.

---

## B. Screens (requirement 7)

`fe-tour/app/` contains **25 `page.tsx` files** → 24 reachable screens plus one catch-all that always calls `notFound()`. The `(public)` segment is a route group (not in the URL). Three layouts and five error/not-found pages are excluded from the screen count.

**Public — 9**
| File under `app/(public)/` | URL | Name |
|---|---|---|
| `page.tsx` | `/` | Home / editorial landing |
| `tours/page.tsx` | `/tours` | Journeys catalogue (search + pagination) |
| `tours/[id]/page.tsx` | `/tours/[id]` | Journey details / departure choice |
| `login/page.tsx` | `/login` | Sign in |
| `register/page.tsx` | `/register` | Create account |
| `forgot-password/page.tsx` | `/forgot-password` | Forgot password |
| `reset-password/page.tsx` | `/reset-password` | Reset password |
| `verify-email/page.tsx` | `/verify-email` | Verify email landing |
| `payment/vnpay-return/page.tsx` | `/payment/vnpay-return` | VNPay payment status |

**Customer (logged-in) — 7**
| File | URL | Name |
|---|---|---|
| `payment/page.tsx` | `/payment` | Checkout / reserve seats |
| `account/bookings/page.tsx` | `/account/bookings` | My orders |
| `account/bookings/[id]/page.tsx` | `/account/bookings/[id]` | Order details |
| `account/profile/page.tsx` | `/account/profile` | Profile |
| `my-trips/page.tsx` | `/my-trips` | My trips |
| `my-trips/[id]/page.tsx` | `/my-trips/[id]` | Trip details (alias of order details) |
| `invoices/[bookingId]/page.tsx` | `/invoices/[bookingId]` | Invoice (printable) |

**Admin — 8 real screens + 1 catch-all**
| File under `app/admin/` | URL | Name |
|---|---|---|
| `page.tsx` | `/admin` | Dashboard |
| `accounts/page.tsx` | `/admin/accounts` | Accounts |
| `bookings/page.tsx` | `/admin/bookings` | Bookings |
| `catalog/page.tsx` | `/admin/catalog` | Catalog |
| `invoices/page.tsx` | `/admin/invoices` | Invoices |
| `invoices/[bookingId]/page.tsx` | `/admin/invoices/[bookingId]` | Invoice detail |
| `promotions/page.tsx` | `/admin/promotions` | Promotions |
| `tours/page.tsx` | `/admin/tours` | Tours & departures |
| `[...missing]/page.tsx` | `/admin/*` | Catch-all → `notFound()` |

**Distinct reachable screens: 24** (well above the ~10 target). Error/not-found pages: `app/not-found.tsx`, `app/(public)/not-found.tsx`, `app/(public)/error.tsx`, `app/admin/not-found.tsx`, `app/admin/error.tsx`.

---

## C. Code organization (requirement 9)

### Structure and layering

Backend `tour/src/main/java/com/j2ee/tour/` (191 Java files), package-by-layer: `controller/` 26, `service/` 32, `repository/` 24, `dto/` 65 (`request/` + `response/`), `model/` 19, `common/` 9, `security/` 8, `integration/` 3, `audit/` 2, `mapper/` 2, `spec/` 0 (only `.gitkeep`), root `TourApplication`. Layering is consistent: controllers return `ResponseEntity<ApiResponse<T>>`, `@Transactional` lives in services, repositories are Spring Data, manual/static mappers. `tour/AGENTS.md` documents conventions and "never-do" rules well.

Frontend `fe-tour/`: `app/` (25 pages, 3 layouts, 5 error/not-found), `components/` 45 `.tsx` (35 shared + 10 `components/admin/`), `lib/` 28 source `.ts` + 24 `*.test.ts`, `public/` (stock SVGs + 15 local photos with `CREDITS.md`). Strict TypeScript, `@/*` alias, server components preferred, typed API client.

### Naming consistency / duplication

- Four payment controllers: `PaymentController`, `PaymentCrudController`, `MockPaymentController`, `VnpayPaymentController`; two refund controllers (`RefundController`, `RefundCrudController`). Mixed `Crud`/`Mock`/domain suffixes.
- `PaymentController` and `MockPaymentController` both map `/api/bookings/{bookingId}/payments`; `TourController` and `TourRatingController` both map `/api/tours`.
- Orphan DTOs: `dto/request/BookingCreateRequest.java` (live one is `CreateBookingRequest`) and `dto/request/RefundRequest.java` (live one is `CreateRefundRequest`) — declared but referenced nowhere; opposite noun/verb order is confusing.
- `Vnpay`/`VNPay` casing varies between Java and FE files.
- FE duplicates `/my-trips` and `/account/bookings` (two customer order views).

### Dead code / leftovers

10 `.gitkeep` files in packages that already contain classes. `fe-tour/build-check-2.log` (old build output) and `fe-tour/tsconfig.tsbuildinfo` (generated) are committed artifacts. Stock Next SVGs in `fe-tour/public/`. `MockPaymentController` is *not* dead — it is an intentionally gated demo endpoint (`app.payment.mock-enabled`, default false). `docs/` has 29 markdown files, many one-off task reports; root also has `DATABASE_FIX_PLAN.md`, `prices-on-request.md`.

**TODO/FIXME/XXX/HACK: none** in either project's code (case-insensitive scan). Only incidental matches in docs.

### README / setup docs

- `fe-tour/README.md` exists (59 lines): install/run, lint/build/start, API URL resolution order, default CORS, design and caching notes. **But it is stale**: line 37 still says `/payment` is a UI-only preview that "does not create a booking", and its "Pages" list covers only 4 of 25 screens.
- **No `tour/README.md`** and **no root README**. Backend setup lives only in `tour/AGENTS.md` and `tour/src/test/AGENTS.md`.
- Docker/compose is not documented; no compose file exists anywhere.

### `.env.example` / `.gitignore` / secret-like files

- **No `.env.example` or `.env.sample` in either project**, even though both `.gitignore`s whitelist `!.env.example` and `fe-tour/AGENTS.md` says "Copy `.env.example` to `.env.local`".
- `.gitignore` coverage is otherwise good (`.env*`, `target/`, `node_modules/`, `.next/`, logs, `*.tsbuildinfo`, coverage). Note root `.gitignore` ignores `AGENTS.md`, which is where the best setup docs live.
- Env files present (key names only, **no values**): `tour/.env` (DB URL/username/password, `SPRING_FLYWAY_ENABLED`, `JWT_SECRET`, `APP_CORS_ALLOWED_ORIGINS`, `APP_COOKIE_SECURE`, `APP_TRUST_PROXY`, 4 unused `CLOUDINARY_*` keys, 8 `SPRING_MAIL_*` keys, `MAIL_FROM`, `APP_FRONTEND_URL`, `GOOGLE_CLIENT_ID`, 5 `VNPAY_*` keys); `fe-tour/.env.local` (`API_URL`, `NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_GOOGLE_CLIENT_ID`). Both are ignored by the `.gitignore` patterns.
- Other secret-bearing config (paths + key names only): `tour/src/main/resources/application.properties` (`app.jwt.secret`, `spring.datasource.password`, `app.payment.callback-secret`, `app.vnpay.hash-secret`, `app.vnpay.ipn-token`, `spring.mail.password`, …), `tour/src/test/resources/application-test.properties`, `tour/Dockerfile.redis` (`REDIS_PASSWORD` in commented examples), `docs/task-fix-1-report.md` (masked `SPRING_MAIL_PASSWORD` placeholder). No secret values are reproduced here.

### Test coverage

- Backend: 41 test files, **384 `@Test` annotations**, 2 `@Disabled` (both in `TourApplicationTests` — "Requires PostgreSQL"). 9 `@SpringBootTest` integration classes need a real PostgreSQL (Flyway + `ddl-auto=validate`); no H2/Testcontainers. 6 `@WebMvcTest` classes; ~25 Mockito unit tests.
- Frontend: **24 `*.test.ts` files / 198 Vitest cases**. No separate vitest config.
- **No CI configuration** (no `.github/workflows`, no pipeline files).

### Top 5 cheap improvements for a grader

1. Add a root `README.md` + `tour/README.md`, add the two missing `.env.example` files, and fix the stale `fe-tour/README.md` (checkout claim + 25-screen list).
2. Delete dead artifacts: 10 `.gitkeep`s, `build-check-2.log`, `tsconfig.tsbuildinfo`, the 2 orphan DTOs, stock SVGs; consolidate payment/refund controller naming.
3. Add a minimal `.github/workflows/ci.yml` running FE lint/typecheck/test/build and BE `./mvnw test`.
4. Add a `docker-compose.yml` for PostgreSQL + Redis and document how to run the DB-backed integration tests.
5. Tidy repository hygiene: archive one-off `task-*-report.md` docs, standardize `Vnpay`/`VNPay`, and stop ignoring `AGENTS.md` in root `.gitignore`.

---

## D. JPA and email (requirements 4 and 5) — both confirmed

**Spring Data JPA:** `tour/pom.xml` declares `spring-boot-starter-data-jpa` (Boot 4.1.1, Java 21); `application.properties` sets `spring.jpa.hibernate.ddl-auto=validate`. 19 `@Entity` classes in `model/` and 19 `JpaRepository<T, Long>` interfaces in `repository/` — e.g. `TourRepository`, `BookingRepository` (also `JpaSpecificationExecutor<Booking>`, `findByIdForUpdate` with `@Lock(PESSIMISTIC_WRITE)`, aggregate `findTopToursByBookingCount`), `PaymentRepository`, `TourDepartureRepository`. Custom data access: `TourSearchRepository`/`TourSearchRepositoryImpl` (JPQL + `EntityManager`), `BookingSpecifications` (`Specification<Booking>` for filters/ownership), and `AdminBookingListRepository` (CriteriaBuilder scalar projection).

**Spring Email:** `tour/pom.xml` declares `spring-boot-starter-mail`. `service/EmailService.java` uses `ObjectProvider<JavaMailSender>` + `SimpleMailMessage`, `@Async` methods, and swallows send failures so mail never breaks business flows. `service/AfterCommitMailDispatcher.java` sends via `TransactionSynchronization.afterCommit()`. Four flows: verification (`AuthService` registration, `ProfileService` email change), password reset (`AuthService`), booking confirmation (`BookingService.create`), cancellation (`BookingService.update`/`cancel`, `TourDepartureService` cascade). Config key names: `spring.mail.*`, `app.mail.from`, `app.web.frontend-url`. SMTP delivery against a real server has not been verified; with no SMTP configured the app logs a warning and continues.

---

## E. Cloud deployment readiness (bonus)

### Containers / orchestration

| File | Status |
|---|---|
| `tour/Dockerfile.redis` | Only Dockerfile. `redis:7-alpine`, password from `REDIS_PASSWORD` env (default empty), `/data` volume, 6379, healthcheck. Local convention: host 6380. |
| `tour/Dockerfile` | **Missing** |
| `fe-tour/Dockerfile` | **Missing** |
| `docker-compose.yml` / `.dockerignore` | **Missing** (no compose references in docs) |
| `fe-tour/next.config.ts` | No rewrites, no proxy; no `middleware.ts`. `fe-tour/AGENTS.md:24`: "No Next.js proxy. The browser calls the backend directly and relies on backend CORS." |

### Environment variables (names only)

Backend (`tour/src/main/resources/application.properties`, `spring.config.import=optional:file:.env[.properties]`):
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_CACHE_TYPE`, `SPRING_CACHE_TTL`, `SPRING_DATA_REDIS_URL`, `SPRING_DATA_REDIS_TIMEOUT`, `SPRING_DATA_REDIS_CONNECT_TIMEOUT`, `SPRING_FLYWAY_ENABLED`, `JWT_SECRET`, `APP_COOKIE_SECURE`, `APP_CORS_ALLOWED_ORIGINS`, `GOOGLE_CLIENT_ID`, `SPRING_MAIL_HOST`/`MAIL_HOST`, `SPRING_MAIL_PORT`/`MAIL_PORT`, `SPRING_MAIL_USERNAME`/`MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`/`MAIL_PASSWORD`, `SPRING_MAIL_SMTP_AUTH`, `SPRING_MAIL_SMTP_STARTTLS`, `SPRING_MAIL_SMTP_CONNECTIONTIMEOUT`, `SPRING_MAIL_SMTP_TIMEOUT`, `SPRING_MAIL_SMTP_WRITETIMEOUT`, `MAIL_FROM`, `APP_FRONTEND_URL`, `APP_TRUST_PROXY`, `PAYMENT_CALLBACK_SECRET`, `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_PAY_URL`, `VNPAY_RETURN_URL`, `VNPAY_IPN_TOKEN`, `PAYMENT_MOCK_ENABLED`. Hardcoded `@Value` defaults with no env placeholder: `app.auth.refresh-token-ttl` (P7D), password-reset (PT30M), email-verification (P2D), `app.jwt.access-token-ttl` (PT15M), `app.admin.list.max-page-size` (50). No `@ConfigurationProperties` classes.

Frontend: `API_URL`, `NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_GOOGLE_CLIENT_ID` (`NODE_ENV` only other `process.env` read). `NEXT_PUBLIC_*` values are inlined at **build time** — set them before `npm run build`.

### Every URL/config that must change for a public deployment

| Item | Current default | Required change |
|---|---|---|
| `VNPAY_PAY_URL` | empty | Exact `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html` (whitelisted in `VnpayGateway.isAllowedPayUrl`). |
| `VNPAY_RETURN_URL` | empty | Browser return must point to **FE**: `https://<FE-HOST>/payment/vnpay-return` (path validated; HTTPS for non-local). |
| `VNPAY_IPN_TOKEN` | empty | Random 32–128 char URL-safe token; register `https://<BE-HOST>/api/payments/vnpay/ipn/<token>` with VNPay Sandbox. IPN path is hardcoded (`VnpayPaymentController`), VNPay cannot reach localhost. |
| `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` | empty | Sandbox merchant credentials, backend only. |
| QueryDr endpoint | hardcoded `https://sandbox.vnpayment.vn/merchant_webapi/api/transaction` in `VnpayQueryClient.java` | Sandbox-only; production merchant requires a code change. |
| `PAYMENT_CALLBACK_SECRET` | empty | Set or signed callbacks fail closed. Keep `PAYMENT_MOCK_ENABLED=false`. |
| `GOOGLE_CLIENT_ID` / `NEXT_PUBLIC_GOOGLE_CLIENT_ID` | empty | Same client ID on both sides; add the public FE origin to Google Cloud **Authorized JavaScript origins**. No redirect URI/client secret (GIS ID-token flow). Blank → button hidden, backend 503. |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Exact public FE origin (scheme+host+port, no wildcard with credentials). |
| `API_URL` / `NEXT_PUBLIC_API_URL` | `http://localhost:8080` (fallback in `lib/api.ts`) | Public BE HTTPS origin. `API_URL` for server reads, `NEXT_PUBLIC_API_URL` for browser calls. |
| Next.js rewrites | none | Nothing to change today; adding a same-origin rewrite is recommended (see below). |
| `APP_FRONTEND_URL` | `http://localhost:3000` | Public FE origin — builds email verification/reset links in `EmailService`. |
| `MAIL_FROM` | `no-reply@tour.local` | Verified sending domain. |
| `APP_TRUST_PROXY` | `false` | Set `true` behind an ingress so `vnp_IpAddr` and rate limiting see real client IPs. |
| Swagger/actuator | public `GET`s | Decide whether to restrict at ingress or disable via `springdoc.*`. |

### Refresh-token cookie and cross-domain impact

`security/AuthCookieService.java`: name `refresh_token`; **HttpOnly=true** (hardcoded); **SameSite=Lax** (hardcoded); **Secure=false** by default (`APP_COOKIE_SECURE`); **Path=/api/auth**; **Domain not set** (host-only); **MaxAge=7 days** (`app.auth.refresh-token-ttl`, P7D); no Partitioned/Priority.

- Same registrable domain, different subdomains (e.g. `app.example.com` + `api.example.com`): cookie is same-site, `Lax` is fine; set `APP_COOKIE_SECURE=true` and the FE origin in CORS. No `Domain` needed because the cookie is sent to the BE host anyway.
- Different registrable domains (typical free-tier mix, e.g. `*.vercel.app` + `*.onrender.com`): cross-site. Requires a **code change** to `SameSite=None` plus `Secure=true` and a CSRF strategy (CSRF is currently disabled and `Lax` was part of the defense). A `Domain` attribute cannot bridge unrelated eTLD+1s; third-party-cookie blocking makes this fragile.
- **Same-origin proxy via Next.js rewrites avoids the problem:** add `source: '/api/:path*' → <BE_PUBLIC_URL>/api/:path*`, make `lib/api.ts` `BASE_URL` relative/empty, forward headers/cookies/query unchanged, set `APP_TRUST_PROXY=true`. The cookie becomes first-party, `Lax` is correct, CORS/third-party blocking are irrelevant, and `Path=/api/auth` still matches. Keep the VNPay IPN pointed directly at the BE host. This is a deliberate architecture change (today's design says no proxy), not a config-only tweak.

### Simplest free-tier setup and risks

Current infra: Supabase PostgreSQL (Flyway V1–V9, `ddl-auto=validate`), Redis **read cache only** (10-min TTL, fail-open `CacheErrorHandler`; not used for sessions/rate limits), SMTP via Spring Mail (async, failure-tolerant). RabbitMQ/WebSocket/Cloudinary are documented but not implemented.

Suggested: **FE** Vercel Hobby (native Next.js; no Dockerfile). **BE** source-build PaaS (Render-style) on Java 21 — add a backend Dockerfile if a container host is preferred; keep a single instance (in-memory rate limiter). **DB** existing Supabase free Postgres (use the pooler URL; Hikari max pool 5), never edit applied migrations. **Redis** simplest is `SPRING_CACHE_TYPE=none` (cache is only an optimization and fails open); otherwise Upstash/Redis Cloud free tier. **SMTP** Gmail App Password or free transactional SMTP on port 587 STARTTLS.

Main risks: free-tier **cold starts** (lib/api.ts aborts at 30 s; Supabase also pauses) → intermittent 504s; **outbound port 25 blocked** on some hosts and `starttls.required` not set; **cross-site cookie** problem on split domains (use the proxy); **single-instance rate limiter** (no shared store); **VNPay** requires public HTTPS, registered IPN and Sandbox-only hardcoded URLs (Next dev mode disables VNPay checkout); no CI, no FE/BE Dockerfiles, no `.env.example`; stale docs; Next.js 16 security advisory noted in older reports.

---

## F. AI and microservice feasibility (bonus)

### F.1 Smallest viable Spring AI tour recommender (proposal only)

There is no AI code or dependency today. Fit it into the existing monolith as one new package `com.j2ee.tour.assistant`:

- `TourRecommendationController` → `POST /api/assistant/recommendations` (public but rate-limited via the existing `RateLimitFilter`, or authenticated).
- `TourRecommendationService` — retrieve → rank → validate.
- `RecommendationClient` interface + `integration/OpenAiRecommendationClient`, mirroring the existing verifier/gateway seam so it is easy to stub in tests.
- DTO records in `dto/request` / `dto/response`: free-text preferences (+ optional budget/days/`destinationId`) → `List<{tourId, reason}>`.

Data: reuse `TourService.listTours(...)`/`getTour(...)` and `TourRepository`/`TourDepartureRepository`/`TouristDestinationRepository`; these already enforce `OPEN` tours and future `OPEN/FULL` departures. Pass a **whitelist** only (tourId, name, description, duration, destination names, min price, next date, seats) — never entities, accounts, bookings, payments, emails or anything from `/api/admin/**`.

Safety: label/delimit user text as data with a strict system prompt ("ignore instructions in the user block"); cap input length and output tokens; structured JSON output; no tool calling or URL fetching; short timeouts; validate every returned `tourId` against the retrieved candidate set and re-read authoritative price/status from the DB; render model text as plain text in React (no `dangerouslySetInnerHTML`); keep the API key server-side and log only metadata.

Dependencies: a Boot-4.1.1-compatible Spring AI BOM + `spring-ai-starter-model-openai` with `spring.ai.openai.api-key` (env `SPRING_AI_OPENAI_API_KEY`) — **verify version compatibility before adding**. FE: one `lib/recommendations.ts` calling the endpoint via the existing `apiRequest`, rendered on `/tours` with existing `TourCard`. This is roughly **2–4 working days** plus key/model setup.

### F.2 Microservice split — honest recommendation: **don't do it within ~2 weeks**

The smallest candidate, mail, is deliberately coupled to the local transaction: `AfterCommitMailDispatcher` uses `TransactionSynchronizationManager` so mail fires only after commit. Extracting it requires an outbox table (new Flyway migration, keep `ddl-auto=validate` in sync), publisher/relay, consumer with idempotency/retries/DLQ, queue config (RabbitMQ autoconfig is currently disabled), deployment/secret plumbing and end-to-end tests — all while `EmailService` swallows failures and no-ops without SMTP, so users see no behavioural benefit. It adds failure modes without changing observable outcomes. Keep the AI recommender in-process behind its `RecommendationClient` interface too; extraction later is then a small change. Given the parallel work on booking/payment/stats, spend the two weeks on the in-process AI package, tests and documentation instead of distributed-system plumbing.

---

## Prioritized next 2 weeks (rough effort)

| Priority | Item | Effort | Notes |
|---|---|---|---|
| 1 | Root + backend README, the two `.env.example` files, fix stale `fe-tour/README.md` and `docs/feature-list.md` "chưa triển khai" section | 0.5–1 day | Highest grader-visible payoff; pure docs. |
| 2 | Add CI workflow (FE lint/tsc/test/build, BE `./mvnw test`) | 0.5 day | No CI today. |
| 3 | Delete dead artifacts / consolidate payment+refund controller naming / standardize `Vnpay` casing | 0.5–1 day | Pure hygiene; avoid touching parallel-owned services. |
| 4 | `ROLE_STAFF` authorization slice: `V10` seed migration, SecurityConfig `hasAnyRole`, generalized FE gate + sidebar nav, SecurityConfigTest cases | 2–3 days | Do **after** the parallel branch lands; see Section A conflict notes. |
| 5 | Staff endpoints + pages: passengers per departure, create booking for customer | 3–5 days | New `Staff*` classes to avoid editing `BookingService`/`PaymentService` concurrently. |
| 6 | Docker/compose: backend + frontend Dockerfiles and `docker-compose.yml` for Postgres+Redis; document how to run DB-backed tests | 1–2 days | Makes the 8 integration tests demonstrably runnable. |
| 7 | In-process Spring AI recommender behind `RecommendationClient` (smallest version) | 2–4 days | Only after 1–4; verify Spring AI/Boot 4.1.1 compatibility first. |
| 8 | Deployment dry run on free tier (Vercel FE + source-build BE + Supabase + cache off), VNPay IPN registration, Google authorized origin, cookie/CORS/proxy decision | 1–2 days + provider waits | External credentials and public HTTPS required. |

**Explicitly not recommended in this window:** extracting a mail/notification microservice (Section F.2), or any refactor of `BookingService`, `RefundService`, `PaymentService`, `AdminStatsService` while the other developer is editing them.
