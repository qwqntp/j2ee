# F1 frontend audit — `fe-tour/` against the Batch 1–3 backend

> Date: 2026-11-14
> Scope: end-user surfaces in `fe-tour/`. Backend reference: `tour/src/main/java/com/j2ee/tour/controller/`
> and `docs/archive/task-b1-report.md`, `docs/archive/task-b2-report.md`, `docs/feature-audit.md`.
>
> Status legend: **exists** = complete for the F1 acceptance criteria; **partial** = useful code
> exists but a required part is missing or behaves differently; **missing** = no UI.

## Summary

| Feature | Status | Main files |
|---|---|---|
| Tour filters + sort | **missing** | `app/tours/page.tsx`, `lib/api.ts`, `lib/tours.ts` |
| Real booking form (travelers + promotion code) | **partial** | `components/payment-form.tsx`, `app/payment/page.tsx`, `lib/api.ts` |
| My orders (list / detail / cancel) | **partial** (exists as `/my-trips`) | `components/my-trips.tsx`, `components/trip-detail.tsx`, `components/trip-card.tsx`, `app/my-trips/**` |
| Profile + change password | **missing** | — |
| Email verification landing | **missing** | — |
| Invoices view | **exists** (not linked from the list) | `app/invoices/[bookingId]/page.tsx`, `components/invoice-view.tsx` |

Nothing found contradicts the earlier `docs/feature-audit.md`; the two differences worth calling out
are that `/payment` already creates a real booking (plus an optional demo payment) and that the
backend also emails a `/reset-password` link with no landing page. Both are handled as gaps below.

---

## 1. Tour filters + sort

**Status: missing.**

- `app/tours/page.tsx` reads only `keyword` and `page`; `pageHref` and the form drop every other
  query key, and there is no destination/price/date/duration/sort UI.
- `lib/api.ts` `getTours(page, keyword)` sends only `page`, `size=9` and `keyword`.
- `lib/tours.ts` `loadTours(page, keyword)` wraps that call in `"use cache"` + `cacheTag("tours")`.
- Backend support already exists in `TourController.list`: `destinationId`, `minPrice`, `maxPrice`,
  `startFrom`, `startTo`, `minDays`, `maxDays`, `sort` (`newest|priceAsc|priceDesc|dateAsc`), with
  400s for `min > max` and invalid sort.
- Because the frontend never sends the new parameters, none of the backend behaviour is reachable.
- Phase 0 added the public `GET /api/tours/destinations` option list needed for the dropdown.

## 2. Real booking form (travelers + promotion code)

**Status: partial.**

Existing:

- `app/payment/page.tsx` loads a tour and resolves a departure; it handles missing/sold-out dates.
- `components/payment-form.tsx` gates on `ensureSession()`, redirects to `/login?next=…`, and calls
  `POST /api/bookings` with `departureId`, `guestCount`, `note` and `travelers` (one full name per
  seat). It already shows inline API errors and handles 401.
- `lib/api.ts` `TravelerInput` already declares `fullName`, `phone`, `email`, `dateOfBirth`, `gender`,
  `idDocument`, but the form only fills `fullName`.

Gaps against F1:

- No `promotionCode` input and no `promotionCode` field in `createBooking`'s input type.
- Contact `name`/`email`/`phone` are concatenated into `booking_note` instead of being attached to
  each traveler / profile.
- Per-traveler `phone`, `email`, `dateOfBirth`, `gender` and `idDocument` are not collected.
- The "Pay online now (demo card)" option calls `POST /api/bookings/{id}/payments/mock` after create.
  F1 requires bookings to stop at `PENDING`; payment/VNPay/mock work is out of scope.
- After success the form stays on the checkout page; F1 requires navigating to the booking detail.
- 409 responses are shown as the same inline text rather than a distinct banner.
- The final price is computed in the UI from the departure price (`departure.price * guests`). This is
  fine as an estimate, but the authoritative subtotal/discount/total must come from the response.
- The UI caps guest count at 20 even when the departure has more seats. The backend has no such cap.

## 3. My orders (list / detail / cancel)

**Status: partial — the capability exists at `/my-trips`, not at `/account/bookings`.**

Existing:

- `app/my-trips/page.tsx` + `components/my-trips.tsx`: paginated "load more" list (20 per page),
  tab grouping (upcoming/past/cancelled), empty and error states, 401 → `/login?next=/my-trips`.
- `app/my-trips/[id]/page.tsx` + `components/trip-detail.tsx`: tour/date/meeting point, travelers,
  promotion and totals, status timeline, invoice link, cancellation.
- `lib/bookings.ts` exposes `canCancelBooking` (PENDING only), `canViewInvoice`, status labels/tones.
- `lib/api.ts` has `listBookings`, `getBooking`, `cancelBooking`.
- The backend `GET /api/bookings` is owner-scoped and newest first; `POST /api/bookings/{id}/cancel`
  returns 409 unless the booking is PENDING with no successful payment.

Gaps against F1:

- Required routes `/account/bookings` and `/account/bookings/[id]` do not exist. The existing
  `/my-trips` routes can stay as aliases or redirects.
- Cancellation is in the detail page only and shows 409 as generic inline text; F1 asks for a
  graceful 409 message/banner.
- Invoice is linked only from the detail page for PAID/CONFIRMED/COMPLETED, never from the list.
- The list uses grouped tabs and "load more" rather than a straightforward newest-first paginated
  list; the grouping hides the true ordering across tabs.

## 4. Profile + change password

**Status: missing.**

- There is no `/account/profile` route, no profile API call, and no change-password form.
- `components/site-header.tsx` shows the account username/email and links only to `/my-trips` and
  sign-out.
- Backend is ready: `GET/PUT /api/profile` (`ProfileResponse` with `customerId`, `username`, `email`,
  `emailVerified`, `name`, `phone`, `address`, `dateOfBirth`) and
  `POST /api/auth/change-password` (400 for a wrong current password, new `AuthResponse` and cookie on
  success).
- `lib/api.ts` has no `getProfile`, `updateProfile`, or `changePassword`.
- `lib/session.ts` currently has no helper to replace just the access token; `saveSession` can do it
  when the new `AuthResponse` is passed in.

## 5. Email verification landing

**Status: missing.**

- `EmailService` builds `${app.web.frontend-url}/verify-email?token=…` for verification and
  `${frontendUrl}/reset-password?token=…` for password reset.
- `fe-tour` has no `/verify-email` route and no `/reset-password` route. Both links currently 404.
- Backend endpoint: `POST /api/auth/verify-email` with `{ token }`; expired/reused tokens return 400.
- F1 only requires the verification landing. The reset-password landing is recorded here as a known
  gap outside F1 scope.

## 6. Invoices view

**Status: exists.**

- `app/invoices/[bookingId]/page.tsx` validates the id and renders `components/invoice-view.tsx`.
- `InvoiceView` calls `GET /api/bookings/{id}` and `GET /api/invoices/me`, finds the invoice whose
  `bookingId` matches, and renders the printable invoice with the journey, line items, discount,
  fees/tax, payment method/status and a print button.
- It handles 401 (login redirect), 404 (own booking missing) and no-invoice-yet.
- `GET /api/invoices/me` is paginated; the view requests 100 and filters client-side, so invoices
  beyond the first 100 would not be found. Acceptable for F1 but worth noting.
- The invoice is only reachable from booking detail after payment; the orders list does not link to it.
- `components/invoice-view.tsx` says "demo invoice" and defaults the payment method to CARD; this
  copy is cosmetic and does not affect the API behaviour.

---

## Backend surfaces used by F1

| Feature | Endpoint | Notes |
|---|---|---|
| Filters | `GET /api/tours` | Existing B2 params and validation |
| Destination dropdown | `GET /api/tours/destinations` | Added in Phase 0; public, `{id, name}` sorted by name |
| Booking create | `POST /api/bookings` | Bearer; `travelers` size must equal `guestCount`; server prices |
| Orders list | `GET /api/bookings` | Bearer; owner-scoped, newest first, paginated |
| Order detail | `GET /api/bookings/{id}` | Bearer; 404 for another owner's booking |
| Cancel | `POST /api/bookings/{id}/cancel` | Bearer; PENDING only, 409 otherwise |
| Profile | `GET/PUT /api/profile` | Bearer; `name` required; email optional |
| Change password | `POST /api/auth/change-password` | Bearer; wrong current password is 400 |
| Verify email | `POST /api/auth/verify-email` | Public; one-time token |
| Invoices | `GET /api/invoices/me` | Bearer; owner-scoped, paginated |
