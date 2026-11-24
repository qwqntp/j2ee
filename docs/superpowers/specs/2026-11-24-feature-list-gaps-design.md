# Design: first four unimplemented features

Date: 2026-11-24
Source: `docs/feature-list.md` §9, first four bullets.
Scope: `tour/` (Spring Boot API) and `fe-tour/` (Next.js app).

## Intent

Close the four §9 gaps that have working backend primitives but no usable
product surface: refunds, ratings, image upload, revenue reporting.

## Goals

1. Admin can review and progress refund requests; customer can cancel a paid
   booking and get a refund reservation without emailing support.
2. Customer can rate a paid/completed tour; public tour page shows approved
   ratings; admin can moderate them.
3. Admin can upload a tour image instead of pasting a URL.
4. Admin can scope dashboard numbers to a date range and export revenue as CSV.

## Non-goals

- No real payment provider call for refunds. Refunds are records with status;
  money movement stays out of scope.
- No real `.xlsx` file. CSV with UTF-8 BOM opens in Excel.
- No audit log, PDF invoice, i18n, wishlist, or other §9 items.
- No new npm or Maven dependencies.

## Constraints

- Flyway `V1`–`V9` applied. Next migration is `V10`. Never edit applied migrations.
- `ddl-auto=validate`: entity and schema change together.
- Status strings stay aligned with SQL `CHECK` constraints.
- Seat release reuses `BookingService.releaseSeats`; no second decrement path.
- `RefundService.requestRemaining` locks the payment row; keep that guarantee.
- `GET /api/tours/**` is public. Everything under `/api/admin/**` needs
  `ROLE_ADMIN`. Owner scope stays in service layer.
- `SecurityConfig` route matrix and CORS must not be loosened.
- Frontend is Next 16 App Router, TS strict, npm, no state/data/form library.

## Approaches

Considered: a single generic "gap" feature module versus four vertical slices.
Rejected: one module couples unrelated domains and forces new abstractions.
Chosen: four independent vertical slices, implemented in §9 order, each with its
own migration/endpoint/UI where needed and its own focused check.

## Decisions

### 1. Refunds

- Owner-scoped `POST /api/bookings/{id}/refund-request` in `BookingService`:
  lock booking, allow `PAID`/`CONFIRMED` only, require successful payments,
  call `RefundService.requestRemaining` per payment, cancel booking, release
  seats and promotion usage through existing helpers, send cancellation mail.
- Idempotency: repeated call on an already-cancelled booking returns 409.
- Admin UI at `/admin/refunds` uses existing `GET/PUT /api/refunds`.
- Customer UI: two-step confirm in `trip-detail.tsx` for `PAID`/`CONFIRMED`.

### 2. Ratings

- `V10__rating_moderation.sql`: add `rating_status VARCHAR` with CHECK
  (`PENDING`, `APPROVED`, `REJECTED`), default `PENDING`, backfill existing rows
  to `APPROVED`.
- Public `GET /api/tours/{id}/ratings` returns `APPROVED` only; average computed
  over approved rows only.
- Admin endpoints `GET /api/admin/ratings`, `PUT /api/admin/ratings/{id}`,
  `DELETE /api/admin/ratings/{id}`.
- Customer rating form in `trip-detail.tsx` for `PAID`/`COMPLETED`, one rating
  per booking (existing unique constraint).
- Public tour detail page renders average + approved comments.

### 3. Upload

- `POST /api/admin/uploads`, multipart `file`, admin-only.
- Config `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`;
  blank config returns 503, matching Google/VNPay fail-closed pattern.
- Server-side SHA-1 signature over sorted params; Java 21 `java.net.http.HttpClient`
  posts to Cloudinary REST. Validate content type and max size at the boundary.
- Returns `{ url, publicId }`. Frontend file input in admin tours and catalog
  images fills the URL field; `api.ts` gains a FormData path.
- Tradeoff: server proxy over direct signed browser upload — secret stays
  server-side, no frontend SDK, smaller diff. Add direct upload only if
  bandwidth or upload size becomes a problem.

### 4. Reports

- `GET /api/admin/stats?from=&to=` — optional ISO dates, default 90-day window.
- `GET /api/admin/reports/revenue.csv?from=&to=` — daily rows of successful
  payments for the window, UTF-8 BOM for Excel.
- No refund netting in v1; report is gross successful payments.
- Dashboard gains date inputs + export button; CSV fetch keeps its own
  raw-text path in `api.ts`.

## Verification

- Backend: one focused assertion test per rule (refund reservation, rating
  visibility, upload validation, date-windowed totals). `./mvnw test`.
- Frontend: `npm run lint`, `npx tsc --noEmit`, `npm test`, `npm run build`.
- No commit, no push.
