# Task F1 — end-user frontend report

> Date: 2026-11-14
> Scope: `fe-tour/` end-user surfaces for the Batch 1–3 backend, plus the small Phase 0 public
> endpoint the catalogue filter needs.
> Status: implementation complete; all automated checks pass. No commit was made.

## Summary

| Phase | Result |
|---|---|
| 0 — destinations endpoint | Added public `GET /api/tours/destinations`, repository/service/controller, 2 controller tests, API spec |
| 1 — frontend audit | `docs/archive/fe-audit.md` written against the real controllers |
| 2 — tour filters + sort | New filter form, URL state, destination dropdown, backend 400 surface, cache boundary |
| 3 — real booking form | Per-traveler manifest, promotion code, server-priced totals, 400 inline / 409 banner, redirect to order |
| 4 — my orders | `/account/bookings` list and `/account/bookings/[id]` detail with cancel + 409 handling |
| 5 — profile, password, verification | `/account/profile`, change-password, `/verify-email`, header links |

Verification evidence:

- Frontend: `npm run lint` clean, `npx tsc --noEmit` clean, `npm test` **9 files / 76 tests passed**,
  `npm run build` succeeded with the new routes.
- Backend: targeted `TourControllerTest` **16 tests passed**; full suite **234 tests, 0 failures,
  0 errors, 1 skipped**, `BUILD SUCCESS`.

No new dependencies were added.

---

## Phase 0 — public destination options

Why: the catalogue destination filter needs `{ id, name }` without a Bearer token. The existing
`/api/tourist-destinations` CRUD is protected, and its response carries fields the filter does not
need.

Changes:

- `tour/.../dto/response/TourDestinationOptionResponse.java` — new `(Long id, String name)` record.
- `tour/.../repository/TouristDestinationRepository.java` — `findAllByOrderByDestinationNameAsc()`.
- `tour/.../service/TourService.java` — `listDestinations()`, deliberately **not** cached so admin
  edits show up immediately.
- `tour/.../controller/TourController.java` — `GET /destinations`.
- `TourControllerTest` — verifies the payload shape and that `/destinations` resolves before `/{id}`.
- `docs/api-spec.md` — endpoint row, request/response example, B2 filter parameters, profile and
  change-password contracts (the spec had drifted).

Security: no `SecurityConfig` change. `GET /api/tours/**` is already `permitAll`, and Spring matches
the literal `/destinations` mapping ahead of `/{id}`.

## Phase 1 — audit

`docs/archive/fe-audit.md` records, per feature, what existed, what was missing, and which backend endpoint
covers it. Headline findings:

- Filters were missing entirely (the page sent only `keyword`/`page`).
- Checkout already created a real booking but also ran the demo mock payment and collected only
  traveler names.
- The orders capability existed at `/my-trips`, not at the required `/account/bookings`.
- Profile, change password and the email-verification landing did not exist.
- Invoices existed and were functional but linked only from booking detail.
- The backend also emails a `/reset-password` link with no landing page (recorded as an out-of-scope
  gap).

## Phase 2 — tour filters and sort

New/changed:

- `lib/tour-filters.ts` (new) — shared `TourFilters` shape, parser, parity validators, URL builder,
  API query builder, page parser, `FormData` adapter.
- `lib/tour-filters.test.ts` (new) — 14 tests.
- `components/tour-filters.tsx` (new) — accessible filter form (labels, `role="alert"`, clear-all).
- `app/tours/page.tsx` — reads filters from the URL, renders the form, handles backend 400, keeps
  count/empty/out-of-range/pagination states.
- `lib/api.ts` — `getTours(filters, page, size)` and `getTourDestinations()`.
- `lib/tours.ts` — unfiltered catalogue pages keep `use cache` + `cacheLife` + `cacheTag("tours")`;
  filtered combinations call the API directly so the cache is never keyed by unbounded input.
- `lib/types.ts` — `TourDestinationOption`.

Behaviour:

- Supported parameters: `keyword`, `destinationId`, `minPrice`, `maxPrice`, `startFrom`, `startTo`,
  `minDays`, `maxDays`, `sort` (`newest|priceAsc|priceDesc|dateAsc`), plus `page`.
- Malformed values are dropped client-side; parity rules (`min <= max`, `startFrom <= startTo`,
  `minDays <= maxDays`) are validated before navigation, and a backend `400` message is still shown
  verbatim if it disagrees.
- Filter state lives in the query string, so links are shareable and pagination keeps every filter.
- The destination dropdown comes from the Phase 0 endpoint (cached as an option list).

## Phase 3 — real booking form

New/changed:

- `lib/booking-form.ts` (new) — traveler drafts, resize logic, payload builder, validator.
- `lib/booking-form.test.ts` (new) — 10 tests.
- `components/payment-form.tsx` — rewritten.
- `app/payment/page.tsx` — copy/metadata updated (no demo payment).
- `lib/api.ts` — `createBooking` accepts `promotionCode`.

Behaviour:

- Login is required: `ensureSession()` gates the form and redirects to
  `/login?next=/payment?tourId=…&departureId=…`.
- Sends `{ departureId, guestCount, note, travelers[], promotionCode }`; `travelers.length` always
  equals `guestCount` because the draft array is resized with the seat counter.
- Each traveler collects full name (required) plus optional phone, email, date of birth, gender and
  ID document, matching `BookingTravelerRequest`.
- The guest counter now follows the departure's actual seats (no artificial 20 cap).
- The sidebar labels the figure an **estimated subtotal** and states the server confirms subtotal,
  discount and total; the authoritative amounts are shown on the booking page.
- `400` is inline; `409` is a distinct banner with a "Choose another date" link.
- On success: flash message + `router.push("/account/bookings/{id}")`. The booking stays `PENDING`.
- Mock/demo payment was removed from the checkout and from booking detail. The backend endpoint still
  exists but the end-user UI no longer calls it.

## Phase 4 — my orders

New/changed:

- `components/account-bookings.tsx` (new) — newest-first list, `GET /api/bookings?page=N&size=10`,
  pagination, empty/out-of-range/error states, 401 → login.
- `app/account/bookings/page.tsx` (new).
- `app/account/bookings/[id]/page.tsx` (new) — id validation, `TripDetail` with
  `basePath="/account/bookings"`.
- `components/trip-detail.tsx` — `basePath` prop, cancel only for `PENDING`, dedicated 409 banner,
  richer traveler detail, demo pay action removed.
- `components/trip-card.tsx` — `basePath` prop for detail links.

Behaviour:

- `ensureSession()` is the gate; `401` clears the session and redirects to login.
- The list is paginated newest first (10 per page) with previous/next controls.
- Detail shows journey, dates, meeting point, travelers (name/contact/DOB/gender/ID), promotion,
  status and totals.
- Cancel is shown only for `PENDING`; a `409` is presented as "no longer cancellable" and the page
  reloads the latest status.
- `/my-trips` and `/my-trips/[id]` remain as aliases using the default `basePath`, so old links keep
  working.

## Phase 5 — profile, change password, email verification

New/changed:

- `lib/profile.ts` (new) — profile payload/validator and password-change validator (length,
  confirmation, difference, BCrypt 72-byte limit).
- `lib/profile.test.ts` (new) — 10 tests.
- `lib/api.ts` — `getProfile`, `updateProfile`, `changePassword`, `verifyEmail`; `Profile` type.
- `lib/auth.ts` — re-exports `saveSession`.
- `components/account-profile.tsx` (new) — profile form + change-password form.
- `app/account/profile/page.tsx` (new).
- `components/verify-email.tsx` + `app/verify-email/page.tsx` (new).
- `components/skeleton.tsx` — `ProfileSkeleton`.
- `components/site-header.tsx` — logged-in menu now links **My orders** and **Profile** (desktop and
  mobile).

Behaviour:

- Profile `GET`/`PUT /api/profile`; `name` is required, other fields optional. Blank optional fields
  are omitted. The UI shows `emailVerified` and a re-verification note; changing the email reports
  that a fresh verification link was sent.
- Change password posts `{ currentPassword, newPassword }`. A wrong current password is a `400` and
  is shown inline **without** logging out. On success the new `AuthResponse` replaces the stored
  access token via `saveSession`, so the current session stays valid.
- `/verify-email?token=…` posts `POST /api/auth/verify-email` once, then shows success or an
  expired/reused-link error with a route back to the profile.

---

## Routes

| Route | Kind | Notes |
|---|---|---|
| `/tours` | server | filters, sort, pagination, backend 400 |
| `/payment` | server shell + client form | requires login, creates `PENDING` booking |
| `/account/bookings` | client | newest-first paginated orders |
| `/account/bookings/[id]` | client | detail, cancel, 409 handling |
| `/account/profile` | client | profile + change password |
| `/verify-email` | client | verification landing |
| `/my-trips`, `/my-trips/[id]` | unchanged aliases | reuse the same components |

## API calls used by the new surfaces

| Call | Used by |
|---|---|
| `GET /api/tours` (B2 params) | `/tours` |
| `GET /api/tours/destinations` | `/tours` dropdown |
| `POST /api/bookings` | `/payment` |
| `GET /api/bookings` | `/account/bookings` |
| `GET /api/bookings/{id}` | order detail |
| `POST /api/bookings/{id}/cancel` | order detail |
| `GET /api/profile` | `/account/profile` |
| `PUT /api/profile` | `/account/profile` |
| `POST /api/auth/change-password` | `/account/profile` |
| `POST /api/auth/verify-email` | `/verify-email` |

## Verification: what was run vs. what was only built

Run and passing:

- Backend `TourControllerTest` (16 tests) and the full backend suite (234 / 0 / 0 / 1 skipped).
- Frontend ESLint, TypeScript, Vitest (76 tests), production build.

Build-only / not manually exercised:

- No browser session against a running backend was performed, so the following are verified by types,
  unit tests and build only: live filter round-trips, actual booking creation, cancellation, profile
  save, password change and email verification.
- No end-to-end test suite exists in `fe-tour/`.

## Known gaps

1. `/reset-password?token=…` still has no landing page; the backend sends that link. Out of F1 scope.
2. The orders list does not link to invoices. Invoices remain reachable from booking detail for
   `PAID`/`CONFIRMED`/`COMPLETED` bookings, and `InvoiceView` still filters the first 100 invoices
   client-side.
3. `/my-trips` remains as an alias; there is no redirect, so both routes are maintained.
4. The backend mock-payment endpoint still exists and is callable; the end-user UI simply no longer
   uses it.
5. Invoice copy still says "demo invoice" and defaults the payment method label to CARD; cosmetic.
6. No live manual smoke test (see above).
