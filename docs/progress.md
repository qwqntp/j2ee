# Progress — Tour Booking System

Updated: 2026-10-26 (Asia/Ho_Chi_Minh)

## Status

- **Phase 1 — Tour read APIs: done and verified.**
- **Phase 2 — Authentication, authorization, refresh tokens: done and verified.**
- **Phase 3 — Next.js landing, tour, and payment pages: done and verified.**
- **Phase 4 — API spec, Swagger config, final documentation: done and verified.**
- **Frontend redesign — Editorial Explorer visual refresh: done and verified.**

No commits were made. The existing uncommitted change in `docs/tech-stack.md` was not touched.

## Phase 4 — API spec and Swagger

### Implemented

- `docs/api-spec.md` documents:
  - Success/error envelopes, pagination, date and money conventions.
  - Access/refresh token behavior, cookie attributes, rotation and reuse detection.
  - CORS, rate limits, status codes and enums.
  - Every implemented tour, auth and admin endpoint with examples.
  - The **planned** booking/payment contract, clearly marked as not implemented.
  - Frontend integration and known limitations.
- `common/OpenApiConfig.java` adds the API title, version, description and `bearerAuth` HTTP bearer
  scheme.
- Controller annotations now describe public visibility, pagination limits, cookie/rate-limit
  behavior, and Bearer requirements for `/api/auth/me` and `/api/admin/accounts`.
- Swagger UI and OpenAPI JSON remain public:
  - `GET /swagger-ui.html` → redirects to `/swagger-ui/index.html`.
  - `GET /v3/api-docs`.

### Verification

- `tour\mvnw.cmd test` — **54 passed, 1 skipped** (same suite as phase 2).
- `GET /v3/api-docs` — **200**; the JSON contains the `bearerAuth` security scheme and the
  documented tags.
- `GET /swagger-ui/index.html` — **200**.
- `GET /api/tours/999` — **404** with `"Tour not found"`.
- `GET /api/tours/abc` — **400** with `"Malformed request"`.
- `GET /api/tours?size=0` — **400** with a `list.size` validation message.
- `GET /api/auth/me` and `GET /api/admin/accounts` without a token — **401** with the JSON error
  envelope.
- Only read-only requests were made. No account, booking or payment rows were written.

### Not verified yet

- Booking and payment are not implemented; the contract is a design target only.
- Swagger annotations are validated by the generated OpenAPI JSON, not by a full contract test.
- VNPay sandbox behavior, signatures, IPN idempotency and seat holds remain future work.

## Phase 3 — Next.js frontend

### Pages

- `/` — editorial landing page with hero search, destinations, curated experiences, travel notes,
  trust reasons, quote and final CTA.
- `/tours` — searchable, paginated catalogue with empty, out-of-range and demo-fallback states.
- `/tours/[id]` — detail page with gallery, itinerary, accommodation, guide, departures, prices,
  seat counts and sold-out states.
- `/payment` — UI-only checkout preview. It reads `tourId` and `departureId`, shows the selected
  departure, lets the user change the guest count, and shows a demo confirmation. It does not call
  a booking or payment endpoint.
- `not-found.tsx` and `error.tsx` — friendly fallback pages.

### Caching and data

- `cacheComponents` and `partialPrefetching` remain enabled in `next.config.ts`.
- `lib/tours.ts` uses `use cache`, `cacheLife("minutes")`, and tags `tours` / `tour-{id}`.
- Data components call `connection()` and are wrapped in `Suspense`, so the static shell can
  prerender while the cached data streams at request time.
- Demo/sample tours are selected **outside** the cached functions. A failed API read never stores
  sample data as live inventory; the UI shows a "Showing sample journeys" notice instead.
- `lib/api.ts` uses a 5-second timeout, validates the response envelope, and throws `ApiError` with
  the HTTP status. `lib/types.ts` mirrors the backend DTOs.
- API base URL: `API_URL`, then `NEXT_PUBLIC_API_URL`, then `http://localhost:8080`.

### UI and accessibility

- Tailwind v4 theme tokens use the Editorial Explorer palette: forest `#214E45`, warm ivory
  `#F7F4EC`, terracotta `#D9784F`, sage `#A8B7A0` and charcoal `#26332D`. Headlines use Playfair
  Display; body, UI and prices use Inter. Both are loaded through `next/font/google` with Vietnamese
  subsets, so no font request goes to Google at runtime.
- Layouts are photography-led and editorial: 4px radii, 1px borders, restrained shadows, rectangular
  buttons and guest counters. No gradients, glassmorphism or decorative blobs.
- Sticky header with a details/summary mobile menu; skip link; visible focus styles; reduced-motion
  handling; labelled form fields; semantic headings and breadcrumbs.
- Tour images prefer the live Cloudinary URL and fall back to a matching local destination photo in
  `lib/tour-images.ts` (diacritic-insensitive name matching). Editorial photos use the local `Photo`
  component, so the homepage has no remote image dependency.
- Dates are formatted from the API's wall-clock parts, avoiding browser timezone shifts. Money is
  formatted as VND.

### Editorial redesign

- Rebuilt `/`, `/tours`, `/tours/[id]`, `/payment`, the 404/error pages, header, footer, tour cards,
  forms, empty states, pagination and skeletons against the approved Editorial Explorer direction.
- Homepage sections: hero with keyword search, four destinations, curated experiences, travel notes,
  four trust reasons, a team quote and a final CTA. Section IDs are `#destinations`, `#experiences`,
  `#stories` and `#why`.
- Editorial copy and destination/story content live in `lib/editorial.ts`. Destination and story
  cards link to `/tours`; there are no fabricated reviews or article pages.
- Fifteen curated photos from Wikimedia Commons were resized and re-encoded into
  `fe-tour/public/images` (~5.4 MB total). Attribution is in `public/images/CREDITS.md` and linked
  from the footer.
- Routing, API contracts, `use cache`/`cacheLife`/cache tags, `Suspense` + `connection()`, demo
  fallback, auth behavior and the UI-only payment flow were preserved. No dependencies were added
  and the backend was not changed.

### Verification

- `npm ci` — 412 packages, no vulnerabilities (initial phase 3 setup).
- `npm run lint` — clean before and after the redesign.
- `npm run build` — success after the redesign; `/`, `/payment`, `/tours` and `/tours/[id]` are
  partial prerenders with dynamic holes.
- Backend down, production server:
  - `/`, `/tours`, `/tours/9001`, `/payment?tourId=9001&departureId=900101` and `/payment` returned
    **200** with the expected demo/sample states.
  - Search (`/tours?keyword=bay`), no-results, out-of-range page, sold-out departure, missing
    departure, 404 and error states were checked.
- Backend up on `8080` with Flyway off:
  - `/` and `/tours` switched to the two live seeded tours with no demo notice.
  - The live seed has no upcoming departures, so cards show new-dates/price-on-request states and
    detail pages show "No dates open just yet."
  - All four seeded Cloudinary image URLs for tour 1 return **404**; the UI falls back to the local
    Hội An photo on cards and the detail gallery. The payment page with no departure shows
    "Choose a departure first."
- Visual checks used headless Chrome through the DevTools Protocol at 1440px and a true 390px
  viewport. Headless Chrome's `--window-size=390` clamps `innerWidth` to 504px, so CDP
  `Emulation.setDeviceMetricsOverride` was required. No horizontal overflow was reported on any
  checked page.
- Home, catalogue, detail, checkout, 404, empty search and all homepage sections were captured and
  inspected at desktop and mobile sizes after the redesign.
- `GET /tours/abc` renders the not-found UI. Because the route streams and `notFound()` runs inside
  `Suspense`, the HTTP status is `200`; this is a Next.js streaming limitation, not a data leak.

### Not verified yet

- Keyboard-only and screen-reader audit.
- The non-empty live departure/summary path (seed departures are in the past).
- The live-image success path: every seeded Cloudinary URL currently returns 404, so only the local
  fallback was exercised.
- Booking/payment submission against a real backend (intentionally UI-only).
- Automated frontend component or end-to-end tests.
- Image host allowlisting/optimization. The current `<img>` approach deliberately accepts the URLs
  stored in the tour record and falls back to local photos.

## Phase 2 — Authentication and security

### Endpoints

- `POST /api/auth/register` — `201`, access token in the body, refresh token in a cookie.
- `POST /api/auth/login` — `200`, access token in the body, refresh token in a cookie.
- `POST /api/auth/refresh` — rotates the opaque refresh token and issues a new access token.
- `POST /api/auth/logout` — revokes the presented refresh token and clears the cookie.
- `GET /api/auth/me` — current account; requires a bearer access token.
- `GET /api/admin/accounts` — paginated account list; `ROLE_ADMIN` only.

### Tokens

- Access token: HS256 JWT, 15 minutes, claims `sub` (account id), `username`, `role`.
- Refresh token: 32 random bytes, base64url, opaque. Only its SHA-256 hex hash is stored.
- Refresh cookie: `HttpOnly`, `SameSite=Lax`, `Path=/api/auth`, 7 days, `Secure` configurable.
- Rotation: every refresh revokes the old row and inserts a new one.
- Reuse detection: presenting a revoked token revokes every active token for that account and returns `401`.
- Logout revokes the presented token. The access token remains valid until its 15-minute expiry.
- Disabled accounts cannot refresh. Their existing access token still expires naturally within 15 minutes.
- `AuthResponse` contains only `accessToken` and `account`; no `refreshToken`, no `expiresInSeconds`.

### Refresh-token table

- `V1__create_refresh_tokens.sql` creates `public.refresh_tokens`:
  - `account_id` FK to `public.accounts` with `ON DELETE CASCADE`.
  - `token_hash VARCHAR(64)` unique, `expires_at`, `revoked`, `created_at`.
  - Indexes on `account_id` and `expires_at`.
- Flyway is enabled with `baseline-on-migrate=true` and `baseline-version=0`, because the shared schema predates Flyway.
- The existing Supabase `auth.refresh_tokens` table is unrelated and unused; this project uses `public.refresh_tokens`.
- Added the `spring-boot-flyway` dependency: Spring Boot 4 moved Flyway auto-configuration into its own module.
- Applied once against the configured database:
  - `flyway_schema_history`: baseline `0` + SQL migration `1`, both successful.
  - `public.refresh_tokens` exists and is empty.
  - `accounts` still has the original 2 rows; no account writes were made.

### Security

- Rate limits only auth `POST`s:
  - login/register share one bucket: 10 per minute.
  - refresh: 30 per minute.
  - `429` with `Retry-After`; public `GET`/`OPTIONS` are exempt.
  - In-memory fixed window per IP + bucket, with stale-key eviction.
- `app.security.trust-proxy=false` by default. When true, the **rightmost** `X-Forwarded-For` hop is used (single trusted proxy).
- CORS origins from `APP_CORS_ALLOWED_ORIGINS` (default `http://localhost:3000`); credentials allowed.
- JSON `401`/`403` responses; no stack traces.
- BCrypt passwords. Register and login both reject passwords over 72 UTF-8 bytes.
- Unknown usernames still run a dummy BCrypt compare to reduce timing differences.
- Email uniqueness is checked case-insensitively in the service for a friendly message; the unique index `uq_accounts_email_lower` enforces it in the database.
- Stateless sessions; CSRF disabled. The refresh cookie relies on `SameSite=Lax`.

### Verification

- `tour\mvnw.cmd test` — **54 passed, 1 skipped**.
  - `AuthControllerTest`: 5.
  - `TourControllerTest`: 7.
  - `SecurityConfigTest`: 5.
  - `JwtAuthenticationFilterTest`: 3.
  - `RateLimitFilterTest`: 4.
  - `JwtServiceTest`: 4.
  - `FixedWindowRateLimiterTest`: 3.
  - `AuthServiceTest`: 9.
  - `RefreshTokenServiceTest`: 7.
  - `TourServiceTest`: 7.
  - `TourApplicationTests.contextLoads`: skipped (disabled; requires PostgreSQL).
- Startup check with Flyway enabled:
  - Flyway created `flyway_schema_history`, baselined at `0`, and applied `V1`.
  - `/actuator/health` — **200 / UP**.
  - `GET /api/tours` — **200**.
  - `GET /api/auth/me` and `GET /api/admin/accounts` without a token — **401** with the JSON error envelope.
  - No Hibernate schema-validation errors.
  - Only health, tours, and unauthenticated `GET`s were called. No register/login. Accounts count unchanged. JDBC URL and username redacted.

### Not verified yet

- Real register/login/refresh/logout writes against the shared database. Tests mock the repositories, so no account or refresh-token rows were written.
- The pessimistic lock and bulk revoke SQL are not exercised against real PostgreSQL. The reuse-detection revocation commits through `noRollbackFor`; that transaction boundary is also untested against a real database.
- Concurrent refresh rotation under load. A pessimistic lock and reuse detection are implemented, but not stress-tested.
- The non-empty tour aggregate path. Seed departures are in the past, so the query returns no rows.
- Production cookie/CORS behavior. Cross-site production needs `SameSite=None; Secure` plus CSRF handling, or a same-site proxy.
- Email uniqueness under concurrent registration.
- Seed text encoding. The SQL file shows mojibake for Vietnamese text; a UTF-8 client check is still needed.

## Phase 1 — Tour read APIs

### Implemented

- `GET /api/tours` — paginated open tours, optional keyword search.
- `GET /api/tours/{id}` — open tour detail with itinerary, images, accommodation, guide, destinations, vehicles, and upcoming departures.
- `GET /api/tours/{id}/departures` — upcoming public departures.
- Only `OPEN` tours are listed or returned; anything else is `404`.
- Only future (`departureStartDate > now`) `OPEN` and `FULL` departures are exposed.
- `FULL` departures are shown as sold out instead of disappearing.
- Summary semantics:
  - `priceFrom` = minimum price across upcoming `OPEN`/`FULL` departures.
  - `nextDepartureDate` = earliest upcoming `OPEN`/`FULL` departure.
  - `seatsAvailable` = sum of seats across upcoming `OPEN` departures.
  - `departureCount` = number of upcoming `OPEN`/`FULL` departures.
- One aggregate query for card stats; `Clock` fixed to `Asia/Ho_Chi_Minh`.
- Page/size validation: `page >= 0`, `1 <= size <= 50`, keyword max 100 characters; wildcards stripped.
- `@BatchSize(25)` on all four `Tour` collections.
- `Account.emailVerified` / `Account.status` use `Short` to match PostgreSQL `SMALLINT`.

### Verification

- Read-only startup check: app started on 8081 with Flyway off, `/actuator/health` UP, `GET /api/tours` 200 with two seeded tours, no schema validation errors.
- Only health and tours were called; no writes.

## Decisions

- Stateless access JWT; opaque, DB-backed refresh tokens with rotation and reuse detection.
- Refresh tokens are cookie-only. The body never contains the refresh token.
- Flyway now owns the refresh-token table. The existing schema is baselined at version `0` so `V1` still runs.
- Static mappers instead of MapStruct because of computed fields and itinerary parsing. Deliberate deviation from `docs/tech-stack.md`.
- No `UserDetailsService` or `AuthenticationManager`: login checks BCrypt directly, and the JWT filter builds authorities from the token. Simpler, with the same behavior.
- In-memory rate limiting, no Redis. Adequate for one instance; a shared store is needed for multiple instances.
- Expired refresh-token rows are not purged yet. The `expires_at` index is in place for a future scheduled cleanup.
- `X-Forwarded-For` is ignored by default; when trusted, the rightmost hop is used.
- No Testcontainers: Docker is not installed. Repository/aggregate tests against a real PostgreSQL remain unverified.
- `TourResponse` no longer exposes legacy `tours` price/date/slot fields; departures are the source of truth.
- The frontend keeps the Next.js cache-components model: static shell, `Suspense` + `connection()` for dynamic holes, and `use cache`/`cacheLife` for public tour reads.
- Demo data is selected outside the cached read. The cache never stores sample data as live inventory.
- The payment page is deliberately UI-only. It does not invent booking or payment API calls that the backend cannot honor.
- Remote tour images use a plain `<img>` with a local artwork fallback. No image host allowlist or optimizer is configured yet.
- `notFound()` inside a streamed `Suspense` boundary returns HTTP 200 with the not-found UI. This is documented rather than worked around.
- Swagger/OpenAPI is public in development. Production should disable it or put it behind an admin/network boundary.

## How to run

```powershell
cd tour
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

`tour/.env` must contain:

```text
SPRING_DATASOURCE_URL=...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
SPRING_FLYWAY_ENABLED=true
JWT_SECRET=...                 # at least 32 bytes
APP_CORS_ALLOWED_ORIGINS=http://localhost:3000
APP_COOKIE_SECURE=false        # true in production over HTTPS
APP_TRUST_PROXY=false
```

`JWT_SECRET` was generated into the local `.env`; it is gitignored and not printed in this log.

Frontend:

```powershell
cd fe-tour
npm ci
npm run dev          # http://localhost:3000
npm run lint
npm run build
```

The frontend reads `API_URL` first, then `NEXT_PUBLIC_API_URL`, then defaults to
`http://localhost:8080`. The backend CORS default already allows `http://localhost:3000`.

Swagger:

- UI: `http://localhost:8080/swagger-ui.html`
- JSON: `http://localhost:8080/v3/api-docs`
- API spec: `docs/api-spec.md`

## Files added/changed in phase 3 and 4

Added:

- `fe-tour/lib/types.ts` — frontend mirror of the tour/auth response shapes.
- `fe-tour/lib/api.ts` — server-side fetch, timeout, envelope validation, `ApiError`.
- `fe-tour/lib/tours.ts` — `use cache` + `cacheLife` + `cacheTag` public reads.
- `fe-tour/lib/demo-tours.ts` — sample journeys used only outside the cache.
- `fe-tour/lib/format.ts` — wall-clock date, duration, VND and seat formatting.
- `fe-tour/lib/editorial.ts` — static destinations, travel notes and trust reasons.
- `fe-tour/lib/tour-images.ts` — Vietnamese-aware tour-name-to-local-photo fallback.
- `fe-tour/components/site-header.tsx`, `site-footer.tsx`.
- `fe-tour/components/tour-card.tsx`, `tour-image.tsx`, `photo.tsx`, `section-heading.tsx`.
- `fe-tour/components/icons.tsx`, `empty-state.tsx`, `demo-notice.tsx`, `pagination.tsx`,
  `skeleton.tsx`.
- `fe-tour/components/payment-form.tsx` — client-only demo checkout.
- `fe-tour/public/images/` — 15 local editorial photos plus `CREDITS.md`.
- `fe-tour/app/tours/page.tsx`, `app/tours/[id]/page.tsx`, `app/payment/page.tsx`,
  `app/not-found.tsx`, `app/error.tsx`.
- `docs/api-spec.md`.
- `tour/src/main/java/com/j2ee/tour/common/OpenApiConfig.java`.

Removed:

- `fe-tour/components/tour-artwork.tsx` — replaced by local editorial photos and `TourImage`
  fallbacks.

Changed:

- `fe-tour/app/layout.tsx` — metadata, Playfair Display + Inter, skip link, header/footer shell.
- `fe-tour/app/page.tsx` — editorial landing page and cached featured journeys.
- `fe-tour/app/globals.css` — Editorial Explorer tokens, fonts, base styles and component classes.
- `fe-tour/README.md` — frontend run/config notes, pages, design system and image credits.
- `tour/src/main/java/com/j2ee/tour/controller/{Tour,Auth,Admin}Controller.java` — OpenAPI
  descriptions and bearer requirements.
- `progress.md`.

## Files added/changed in phase 2

Added:

- `model/RefreshToken.java`
- `repository/AccountRepository.java`
- `repository/AccountRoleRepository.java`
- `repository/RefreshTokenRepository.java`
- `mapper/AccountMapper.java`
- `security/AuthPrincipal.java`
- `security/JwtService.java`
- `security/JwtAuthenticationFilter.java`
- `security/AuthCookieService.java`
- `security/FixedWindowRateLimiter.java`
- `security/RateLimitFilter.java`
- `service/AuthService.java`
- `service/RefreshTokenService.java`
- `service/AdminService.java`
- `controller/AuthController.java`
- `controller/AdminController.java`
- `resources/db/migration/V1__create_refresh_tokens.sql`
- Tests under `src/test/java/com/j2ee/tour/{controller,security,service}`.

Changed:

- `security/SecurityConfig.java` — JWT filter, rate-limit filter, CORS, JSON 401/403, BCrypt bean, auth rules.
- `dto/response/AuthResponse.java` — dropped `refreshToken`.
- `resources/application.properties` — auth, CORS, rate-limit, Flyway baseline properties.
- `test/resources/application-test.properties` — test auth properties.
- `pom.xml` — added `spring-boot-flyway`.

## Next

1. Implement the booking/payment backend described in `docs/api-spec.md`: seat holds, server-derived
   totals, VNPay signature verification, IPN idempotency and expiry cleanup.
2. Add an auth UI or a guest-checkout decision before wiring the payment page to a real endpoint.
3. Revalidate the `tours` / `tour-{id}` cache tags after admin tour or departure changes.
4. Finish keyboard and screen-reader checks, plus a non-empty departure fixture.
5. Decide on image hosting/optimization and an allowlist for remote tour images; repair or replace
   the dead seeded Cloudinary URLs.
6. Add automated frontend tests and a VNPay sandbox integration test.
7. Disable or protect Swagger/OpenAPI in production.
