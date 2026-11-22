# Backend admin-management coverage

> Created: 2026-11-22
> Scope: backend only — `tour/` (Spring Boot). Frontend admin UI is tracked separately in `docs/admin-gap-checklist.md`.
> Method: mapped every controller route, then checked `SecurityConfig.java` (`hasRole("ADMIN")` route matrix, lines 89–106) for which routes are admin-only.

## Definition used

A backend feature **has admin management** when it exposes admin-only endpoints that let an `ROLE_ADMIN` account create, read, update, delete, or operate on that feature. Admin-only means either:

- under `/api/admin/**`, or
- listed in the `hasRole("ADMIN")` matcher block in `SecurityConfig.java`.

A feature is **missing admin management** when the backend has no admin-only endpoint for it (only public/owner-scoped routes exist), or when the relevant capability does not exist at all.

---

## Features that HAVE admin management

| # | Feature | Admin capability | Main endpoints | Where secured |
|---|---------|------------------|----------------|---------------|
| 1 | Dashboard / statistics | Revenue, success-payment counts/average, bookings by status, tour/departure counts, top tours, capacity risks | `GET /api/admin/stats` | `/api/admin/**` → `AdminController`, `AdminStatsService` |
| 2 | Accounts | Full CRUD; update status (lock/unlock), roleId, password, emailVerified; paged list | `GET/POST/PUT/DELETE /api/accounts`, `GET /api/admin/accounts` | `/api/accounts/**` + `/api/admin/**` |
| 3 | Account roles | Full CRUD | `GET/POST/PUT/DELETE /api/account-roles` | `/api/account-roles/**` |
| 4 | Tours | Full CRUD, admin detail view (full description) | `GET/POST/PUT/DELETE /api/admin/tours` | `/api/admin/**` → `AdminTourController` |
| 5 | Tour departures | Full CRUD; setting `CANCELLED` cascades booking cancellation, seat release, refunds | `GET/POST/PUT/DELETE /api/tour-departures` | `/api/tour-departures/**` |
| 6 | Promotions | Full CRUD | `GET/POST/PUT/DELETE /api/promotions` | `/api/promotions/**` |
| 7 | Payments | Full CRUD + signed VNPay callback + admin reconciliation | `GET/POST/PUT/DELETE /api/payments`, `POST /api/bookings/{id}/payments/callback`, `POST /api/bookings/{id}/payments/vnpay/reconcile` | `/api/payments/**`, `/api/bookings/*/payments/**` |
| 8 | Refunds | Create refund request + admin review/update/delete, with over-refund guard | `POST /api/payments/{paymentId}/refunds`, `GET/PUT/DELETE /api/refunds` | `/api/payments/**`, `/api/refunds/**` |
| 9 | Invoices | Full CRUD + by-booking lookup (plus `/me` for customers) | `GET/POST/PUT/DELETE /api/invoices`, `GET /api/invoices/by-booking/{id}` | `/api/invoices/**` |
| 10 | Customers | Full CRUD | `GET/POST/PUT/DELETE /api/customers` | `/api/customers/**` |
| 11 | Travelers | Full CRUD | `GET/POST/PUT/DELETE /api/travelers` | `/api/travelers/**` |
| 12 | Tour guides | Full CRUD | `GET/POST/PUT/DELETE /api/tour-guides` | `/api/tour-guides/**` |
| 13 | Tour images | CRUD of image records (URL string only — see upload gap below) | `GET/POST/PUT/DELETE /api/tour-images` | `/api/tour-images/**` |
| 14 | Accommodations | Full CRUD | `GET/POST/PUT/DELETE /api/accommodations` | `/api/accommodations/**` |
| 15 | Travel vehicles | Full CRUD | `GET/POST/PUT/DELETE /api/travel-vehicles` | `/api/travel-vehicles/**` |
| 16 | Tourist destinations | Full CRUD | `GET/POST/PUT/DELETE /api/tourist-destinations` | `/api/tourist-destinations/**` |

### Partial admin management

| Feature | What exists | What is missing on the backend |
|---------|-------------|--------------------------------|
| Bookings | Admin bypasses owner-scoping on `GET/PUT/DELETE /api/bookings`, `GET /{id}`, `GET /code/{code}`; `AdminBookingFilter` supports `status`, `from`, `to`, `q`; admin can `confirm`, `complete`, `cancel`, `delete` | No dedicated admin-only booking controller — these are shared owner-scoped routes (not in the `hasRole("ADMIN")` block); no admin-specific booking state-machine enforcement (admin can skip states via `PUT`); no tour/departure/account-specific filter params |

---

## Features that DO NOT have admin management

| # | Feature / gap | Backend reality | Admin endpoint? |
|---|---------------|-----------------|-----------------|
| 1 | Ratings & reviews moderation | `TourRatingController` only has public `GET /api/tours/{id}/ratings` and owner-only `POST /api/tours/{id}/ratings` | **No** admin list across all tours, no delete/hide, no visibility flag, no moderation |
| 2 | Cloudinary / image upload | `TourImageRequest` stores a plain `imageUrl` string; no `MultipartFile`, no signed-upload endpoint, no Cloudinary integration | **No** upload endpoint (only URL CRUD in feature 13) |
| 3 | Email / notification operations | `EmailService` sends transactional mail on booking confirm/cancel/verify/reset only | **No** admin resend, delivery-status view, or template preview |
| 4 | Audit log | No audit entity, table, repository, or controller; admin mutations are not recorded | **No** audit log at all |
| 5 | Reports & exports | No CSV/Excel/streaming endpoint; `GET /api/admin/stats` gives figures but no export, no date-range param, no occupancy/tour-revenue report | **No** report/export endpoint |
| 6 | Customer self-service counterparts needing admin control | `ProfileController`, `AuthController` change-password/forgot/reset/verify are user routes | No admin-specific endpoints (admin edits users indirectly via `/api/accounts`); acceptable, but noted for completeness |

---

## Known weaknesses inside the "has admin management" features

These features have admin endpoints, but the backend implementation still has gaps worth noting:

- **Payments callback is admin-only** (`/api/bookings/*/payments/**` → `hasRole("ADMIN")`), so a real payment provider cannot call it; admin can also mark a payment `SUCCESS` via `PUT /api/payments/{id}` without signature verification. No audit of these actions.
- **Image management is text-only** — no storage/upload (see gap 2).
- **Stats are a fixed snapshot** — `AdminStatsService` uses a hard-coded 90-day window and top-5 limits, with no date-range filter.
- **No audit trail** on any of the admin mutations above (gap 4).
- Bookings admin control is partial (see table above).

---

## Bottom line

- **16 backend feature areas have admin management** (mostly full admin-only CRUD), plus **1 partial** (bookings).
- **6 feature areas lack admin management**, the most significant being **ratings moderation, image upload, audit log, and reports/exports**.
- Backend admin *API* coverage is broad; the remaining backend work is mostly the missing capabilities above, not adding basic CRUD. (Frontend admin UI is a separate, much larger gap — see `docs/admin-gap-checklist.md`.)

> Note: `docs/feature-audit.md` is outdated on this topic — it states the backend has no stats endpoint and only account listing under `/api/admin`. The current code has `GET /api/admin/stats` and the full `hasRole("ADMIN")` route matrix, as reflected above.
