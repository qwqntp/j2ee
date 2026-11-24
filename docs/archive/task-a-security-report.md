# Task A — Backend Security Hardening Report

**Date:** 2026-11-05  
**Scope:** `tour/` backend only (`fe-tour/` untouched)  
**Status:** Task A implementation complete. Unit/`@WebMvcTest` verification passes; database-backed integration tests are blocked by the missing local PostgreSQL instance.

---

## 1. Summary

Task A closed four security gaps in the tour backend:

| ID | Rule | Result |
|---|---|---|
| A1 | Admin-only CRUD endpoints must reject normal users | All 14 prefixes, payment CRUD, and payment callbacks now require `ROLE_ADMIN`; public tour reads remain open |
| A2 | Normal users must not change booking ownership or status; booking access must be owner-scoped | Non-admin `accountId`/`status` updates return `403`; list/get/get-by-code/update/delete are owner-scoped |
| A3 | Bookings must not be created for departures that already started | Creation rejects departures whose start date is not in the future |
| A4 | Payment amount/status/paid-at must not be trusted from clients; callbacks must be signed and owner-checked | Amount and paid-at are server-derived, callbacks require HMAC-SHA256, and payment endpoints are admin-only |

No database schema or Flyway changes were required.

---

## 2. Changes by Task

### A1 — Admin-only CRUD

`SecurityConfig` now has one explicit route matrix for the administrative API:

```
/api/admin/**
/api/accounts/**
/api/account-roles/**
/api/tour-departures/**
/api/promotions/**
/api/payments/**
/api/refunds/**
/api/invoices/**
/api/customers/**
/api/travelers/**
/api/tour-guides/**
/api/tour-images/**
/api/accommodations/**
/api/travel-vehicles/**
/api/tourist-destinations/**
/api/bookings/*/payments/**
```

These paths now require `ROLE_ADMIN` for every HTTP method. This also covers:

- `POST /api/payments` — previously reachable by any signed-in user;
- `POST /api/bookings/{bookingId}/payments/callback` — previously reachable by any signed-in user.

Public reads are unchanged: `GET /api/tours/**` remains unauthenticated, as do auth `POST`s, CORS preflight, Swagger, and `/actuator/health`.

### A2 — Booking ownership and protected fields

`BookingService` now:

- Scopes `GET /api/bookings` to the authenticated account for normal users; admins still list all bookings.
- Checks ownership for `GET /api/bookings/{id}`, `GET /api/bookings/code/{bookingCode}`, `PUT /api/bookings/{id}`, and `DELETE /api/bookings/{id}`.
- Returns `404` rather than revealing another user's booking.
- Rejects a non-admin `PUT /api/bookings/{id}` that supplies either `accountId` or `status` with **`403 Forbidden`**.
- Keeps `ROLE_ADMIN` able to change owner and status.
- Links newly created bookings to the authenticated account, so ownership checks have a real owner to compare against.

`CreateBookingRequest` was not changed, preserving the frontend contract `{ departureId, guestCount, note }`.

### A3 — Past-departure rejection

`BookingService.create` now rejects a departure when its start date is not strictly after the current server time:

```java
if (!departure.getDepartureStartDate().isAfter(LocalDateTime.now(clock))) {
    throw new IllegalStateException("Departure has already started");
}
```

The existing `OPEN` status check and pessimistic seat reservation remain in place. `BookingService` now takes a `Clock` bean (already provided by `ClockConfig`, zone `Asia/Ho_Chi_Minh`) so the rule is deterministic in tests.

### A4 — Payment hardening

#### Callback signature

New `PaymentSignatureService` implements HMAC-SHA256 over:

```
bookingId|providerTransactionId|method|successful|amount
```

- `amount` is canonicalized with `setScale(2, HALF_UP).toPlainString()`.
- Comparison uses `MessageDigest.isEqual` (constant-time).
- Invalid signature → `403`.
- Missing/blank `PAYMENT_CALLBACK_SECRET` → `503`; callbacks fail closed.
- Secret is supplied through configuration, never hard-coded.

#### Server-derived payment values

- `PaymentCallbackRequest` now includes `signature`.
- Callback verifies that the signed amount equals the booking total; mismatch → `400`.
- Stored amount is always `booking.bookingTotalAmount`, not the request amount.
- Stored status comes from the signed `successful` flag (`SUCCESS` / `FAILED`).
- `paidAt` is set from the server clock only when successful; otherwise `null`.
- `PaymentCreateRequest` no longer accepts `amount`, `status`, or `paidAt`. It is now `{ bookingId, paymentMethod, providerTxnId }`; amount is derived from the booking and the new payment always starts `PENDING` with no paid-at.
- `PaymentUpdateRequest` no longer accepts `amount` or `paidAt`. It remains `{ paymentMethod, providerTxnId, status }` for the ADMIN-only CRUD path, and `paidAt` is derived server-side from the resulting status.
- Provider transaction IDs remain idempotent. Reusing an ID for another booking → `409 Conflict`.

#### Ownership and access

- For a new callback, the booking must belong to the caller (`404` for non-owners); admins may process any booking.
- Idempotent replay returns the existing payment after checking that the provider transaction belongs to the same booking; a cross-booking transaction ID returns `409`.
- All payment CRUD and callback endpoints are ADMIN-only under A1.
- Refund and invoice rules were not changed.

---

## 3. Changed Files

| File | Change |
|---|---|
| `tour/src/main/java/com/j2ee/tour/security/SecurityConfig.java` | ADMIN route matrix for 14 CRUD prefixes, payment CRUD, and booking payment callbacks |
| `tour/src/main/java/com/j2ee/tour/service/BookingService.java` | Ownership checks, admin-only owner/status fields, past-departure rejection, injected `Clock` |
| `tour/src/main/java/com/j2ee/tour/service/PaymentService.java` | Server-derived amounts/paid-at, callback ownership, idempotency conflict handling |
| `tour/src/main/java/com/j2ee/tour/service/PaymentSignatureService.java` | **New** HMAC-SHA256 signing/verification service |
| `tour/src/main/java/com/j2ee/tour/controller/PaymentController.java` | Callback now receives the authenticated principal; no logic change elsewhere |
| `tour/src/main/java/com/j2ee/tour/dto/request/PaymentCallbackRequest.java` | Added mandatory `signature` |
| `tour/src/main/java/com/j2ee/tour/dto/request/PaymentCreateRequest.java` | Removed client-controlled `amount`/`status`/`paidAt` |
| `tour/src/main/java/com/j2ee/tour/dto/request/PaymentUpdateRequest.java` | Removed client-controlled `amount`/`paidAt` |
| `tour/src/main/resources/application.properties` | Added `app.payment.callback-secret=${PAYMENT_CALLBACK_SECRET:}` |
| `tour/src/test/resources/application-test.properties` | Added `app.payment.callback-secret=test-callback-secret` |
| `tour/src/test/java/com/j2ee/tour/security/SecurityConfigTest.java` | Added parameterized ROLE_USER `403` coverage for all admin prefixes and payment callback |
| `tour/src/test/java/com/j2ee/tour/service/BookingServiceTest.java` | **New** five ownership/field/past-departure tests |
| `tour/src/test/java/com/j2ee/tour/service/PaymentServiceTest.java` | **New** eight signature/ownership/server-value tests |
| `tour/src/test/java/com/j2ee/tour/BookingFlowIntegrationTests.java` | Updated callback signing/admin cancellation; added A2/A3 coverage |
| `tour/src/test/java/com/j2ee/tour/CrudIntegrationTests.java` | Updated payment DTO and signed callback usage |

No `fe-tour/` files were modified. No Flyway migration was added or edited.

---

## 4. API Contract Changes

### Access control

| Endpoint group | Before | After |
|---|---|---|
| `GET/POST/PUT/DELETE /api/accounts/**`, `/api/account-roles/**`, `/api/tour-departures/**`, `/api/promotions/**`, `/api/payments/**`, `/api/refunds/**`, `/api/invoices/**`, `/api/customers/**`, `/api/travelers/**`, `/api/tour-guides/**`, `/api/tour-images/**`, `/api/accommodations/**`, `/api/travel-vehicles/**`, `/api/tourist-destinations/**` | Any authenticated user | `ROLE_ADMIN` only |
| `POST /api/bookings/{id}/payments/callback` | Any authenticated user | `ROLE_ADMIN` only (temporary; see limitations) |
| `GET /api/tours/**` | Public | Public (unchanged) |
| Booking endpoints | Authenticated; some unscoped | Authenticated and owner-scoped |
| `PUT /api/bookings/{id}` with `accountId` or `status` | Any authenticated user | `ROLE_ADMIN` only; normal users get `403` |

### Request shapes

```jsonc
// POST /api/bookings  (unchanged, frontend-compatible)
{ "departureId": 1, "guestCount": 2, "note": "..." }

// PUT /api/bookings/{id}  (admin may still send accountId/status)
{ "accountId": 1, "guestCount": 2, "note": "...", "status": "CANCELLED" }

// POST /api/payments
{ "bookingId": 1, "paymentMethod": "CASH", "providerTxnId": "txn-1" }

// PUT /api/payments/{id}
{ "paymentMethod": "CARD", "providerTxnId": "txn-2", "status": "SUCCESS" }

// POST /api/bookings/{id}/payments/callback
{
  "providerTransactionId": "txn-1",
  "amount": 1000.00,
  "method": "CARD",
  "successful": true,
  "signature": "<hex hmac-sha256>"
}
```

`amount`, `status`, and `paidAt` are no longer accepted on payment creation. `amount` and `paidAt` are no longer accepted on payment updates.

---

## 5. Configuration

| Property | Environment variable | Default | Purpose |
|---|---|---|---|
| `app.payment.callback-secret` | `PAYMENT_CALLBACK_SECRET` | empty | HMAC-SHA256 key for payment callbacks |

**Production requirement:** set `PAYMENT_CALLBACK_SECRET` to a strong random value. With an empty secret, callbacks return `503` instead of being processed.

Test profile uses `app.payment.callback-secret=test-callback-secret`.

---

## 6. Verification

### Commands run

```powershell
cd tour

# Non-database suite (security + service + controller tests)
.\mvnw.cmd "-Dtest=!*IntegrationTests,!TourApplicationTests" test

# Full suite (requires PostgreSQL)
.\mvnw.cmd test
```

### Results

| Check | Result |
|---|---|
| Non-DB suite | **87 passed, 0 failed, 0 errors, 0 skipped** |
| `SecurityConfigTest` | 22 passed |
| `BookingServiceTest` | 5 passed |
| `PaymentServiceTest` | 8 passed |
| Full suite | 112 tests: 87 passed, **24 errors**, 1 skipped |

The 24 full-suite errors are **not assertion failures**. All of them come from the two `@SpringBootTest` integration classes failing to start because PostgreSQL is not running:

```
BookingFlowIntegrationTests: 11 errors
CrudIntegrationTests:        13 errors
Caused by: org.postgresql.util.PSQLException:
  Connection to localhost:5432 refused.
```

`TourApplicationTests.contextLoads` remains skipped for the same reason.

### Tests added for Task A

| Test | Rule verified |
|---|---|
| `userCannotReadAdminOnlyCrudEndpoints` (parameterized, 14 paths) | A1 |
| `userCannotCreatePaymentOrInvokeFakeCallback` | A1/A4 |
| `publicTourReadDoesNotRequireAuthentication` | A1 regression |
| `normalUserCannotChangeBookingStatus` | A2 |
| `normalUserCannotChangeBookingOwner` | A2 |
| `otherUserCannotUpdateBooking` | A2 |
| `createRejectsDepartureThatAlreadyStarted` | A3 |
| `adminCanStillChangeBookingStatus` | A2 regression |
| `callbackRejectsInvalidSignature` | A4 |
| `callbackRejectsTamperedAmount` | A4 |
| `callbackStoresBookingTotalAndServerPaidAt` | A4 |
| `otherUserCannotConfirmPaymentForBooking` | A4 |
| `callbackIsIdempotentForTheSameBooking` | A4 |
| `callbackRejectsProviderTransactionForAnotherBooking` | A4 |
| `createDerivesPendingPaymentFromBooking` | A4 |
| `updateDerivesPaidAtFromServerForSuccessfulPayment` | A4 |

Integration-test coverage was also extended for past departures, admin-only owner/status changes, and ownership-scoped reads/deletes, but those tests could not execute without PostgreSQL.

---

## 7. Limitations and Follow-ups

1. **Database-backed verification is incomplete.** The integration tests must be re-run against a disposable PostgreSQL instance:

   ```powershell
   cd tour
   .\mvnw.cmd test
   ```

   Required environment: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (or the test defaults at `localhost:5432/tour_test`).

2. **The payment callback is ADMIN-only for Task A.** Public provider return/IPN handling is intentionally deferred to Task B. Until then, only an authenticated admin can invoke the callback.

3. **Payment status remains an admin-editable field on the CRUD update path.** Amount and paid-at are server-derived, and status is constrained by the existing `PENDING|SUCCESS|FAILED` pattern. Public/client payment status changes remain impossible because the endpoint is admin-only.

4. **No schema change.** This task only changes application behavior; there is no Flyway migration to apply.

5. **No commit was created.** All changes are working-tree changes; no git commands were run.

---

## 8. Out of Scope

Task B (frontend/end-user completion, public payment flow, progress/api-spec updates) was **not started**. `docs/progress.md` and `docs/api-spec.md` were intentionally left unchanged.
