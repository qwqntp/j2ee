# Task B3 - Email flows, invoices, tour ratings

## Summary

Batch 3 is complete. The backend now supports:

- password reset and email verification using hashed, one-time tokens with expiry;
- asynchronous, failure-tolerant email delivery for verification, password reset, booking confirmation and booking cancellation;
- automatic invoice creation whenever a booking becomes `PAID`, including the payment callback path and the admin status-update path;
- a customer-scoped `GET /api/invoices/me` endpoint;
- tour ratings restricted to the owner of a `PAID`/`COMPLETED` booking for that tour, with one rating per booking;
- a public paginated rating list plus average rating for a tour.

No `fe-tour/` files were modified. Payment behavior, payment CRUD/VNPay/refunds were not changed. The only payment-code change is the invoice-creation hook inside `PaymentService.recomputeBookingStatus`.

---

## Test status for this run

The last full-suite attempt was interrupted and, per the run rule for this session, database-backed tests were not retried.

**Latest executed command:**

```text
.\mvnw.cmd "-Dtest=!*IntegrationTests,!TourApplicationTests" test
```

**Latest result:** **130 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS.**

Executed test classes:

- `AuthControllerTest` - 8
- `TourControllerTest` - 9
- `TourRatingControllerTest` - 2
- `FixedWindowRateLimiterTest` - 3
- `JwtAuthenticationFilterTest` - 3
- `JwtServiceTest` - 4
- `RateLimitFilterTest` - 4
- `SecurityConfigTest` - 25
- `AuthServiceTest` - 15
- `AuthTokenServiceTest` - 6
- `BookingServiceTest` - 25
- `EmailServiceTest` - 3
- `PaymentServiceTest` - 8
- `RefreshTokenServiceTest` - 8
- `TourServiceTest` - 7

Database-backed tests that are written but were **not executed in the final verification pass**:

- `BookingFlowIntegrationTests` - now 24 tests, including the new invoice-listing and tour-rating integration cases.
- `CrudIntegrationTests` - 13 tests.
- `TourApplicationTests` - remains disabled/skipped; it is the pre-existing skipped test.

Context: an earlier full-suite attempt in this session ran 168 tests with 1 failure and 1 skip. The failure was `TourRatingControllerTest.createReturnsCreatedRating` (a Mockito matcher did not match the null principal in a filtered `@WebMvcTest`). That test was fixed and now passes in the 130-test non-database run. In that earlier attempt, `BookingFlowIntegrationTests` (24) and `CrudIntegrationTests` (13) passed, and Flyway reported the schema at version 6. The final correctness claim for this report is therefore limited to the executed non-database suite.

---

## 1. Email and auth token flows

### Mail setup

- Added `spring-boot-starter-mail`.
- Added `@EnableAsync` to `TourApplication`.
- Added `EmailService` with `@Async` methods:
  - `sendPasswordReset`
  - `sendEmailVerification`
  - `sendBookingConfirmation`
  - `sendBookingCancellation`
- Every send is wrapped in `try/catch`; a failed SMTP send logs a warning and never breaks registration, booking, cancellation or password reset.
- When `spring.mail.host` is blank, sending is skipped and one warning is logged:
  `Mail is not configured (set SPRING_MAIL_HOST/MAIL_HOST); email sending is skipped`.
- Mail is configurable through environment variables (see the config section below).

### One-time auth tokens

- New `auth_tokens` table via `V6__auth_tokens_and_tour_ratings.sql`.
- Tokens are random 32-byte URL-safe values.
- Only the SHA-256 hash is stored in `auth_tokens.token_hash`; the raw token is only placed in the email link.
- Token types: `PASSWORD_RESET`, `EMAIL_VERIFICATION`.
- `expires_at` and `used_at` are enforced server-side.
- A token row is selected with a pessimistic write lock and marked used on consumption, making reuse and concurrent consumption fail.
- Issuing a new token of the same type deletes the previous unused token for that account.
- Default TTLs:
  - password reset: 30 minutes (`app.auth.password-reset-token-ttl`);
  - email verification: 2 days (`app.auth.email-verification-token-ttl`).

### Registration / verification

- Registration still succeeds when mail is unavailable.
- Registration now issues an email-verification token and sends the verification link asynchronously.
- `POST /api/auth/verify-email` consumes the token and sets `accounts.email_verified = 1`.
- Invalid, expired or already-used tokens return `400` with `Invalid or expired token`.

### Forgot / reset password

- `POST /api/auth/forgot-password` always returns the same generic message:
  `If the email exists, a password reset link has been sent`.
- Unknown email addresses produce no token and no email, but no distinguishable error.
- Known email addresses receive a one-time reset link.
- `POST /api/auth/reset-password` consumes the reset token, updates the BCrypt password hash, and revokes all refresh tokens for that account.
- Reset tokens are single-use and expire after 30 minutes by default.

---

## 2. Invoices

### Automatic creation

- `InvoiceService.createForPaidBooking` is idempotent:
  - if an invoice already exists for the booking, it returns the existing response;
  - otherwise it creates a `PAID` invoice with the booking total, discount, customer snapshot and applied promotion.
- The hook is called when a booking becomes `PAID` from:
  - `PaymentService.recomputeBookingStatus` (payment callback / payment status update path);
  - `BookingService.update` when an admin changes the status to `PAID`.
- No payment logic, signature verification, VNPay behavior or refund behavior was changed.

### Customer invoice endpoint

- `GET /api/invoices/me` returns a `PageResponse<InvoiceResponse>` containing only invoices whose booking belongs to the authenticated account.
- Default ordering is newest first by `invoiceCreatedAt DESC, invoiceId DESC`.
- Admin invoice CRUD remains admin-only under `/api/invoices/**`; the `/api/invoices/me` matcher is checked first and is available to any authenticated user.

---

## 3. Tour ratings

- New `tour_ratings` table and `TourRating` entity.
- One rating per booking is enforced by both a service check and a unique constraint on `booking_id`.
- `POST /api/tours/{tourId}/ratings` rules:
  - the booking must exist and belong to the caller; otherwise `404`;
  - the booking must be `PAID` or `COMPLETED`; otherwise `403`;
  - the booking must belong to the supplied tour; otherwise `400`;
  - duplicate rating for the same booking returns `409`;
  - `rating` must be 1..5; `comment` is optional, max 2000 characters.
- `GET /api/tours/{tourId}/ratings` is public and returns:
  - paginated rating content;
  - `averageRating`, rounded to 2 decimals;
  - `ratingCount` and the standard page metadata.
- A missing tour returns `404`.
- Default rating order is newest first by `ratingCreatedAt DESC, ratingId DESC`.

---

## New / changed endpoints

| Method | Path | Auth | Success |
|---|---|---|---|
| `POST` | `/api/auth/forgot-password` | Public | `200` |
| `POST` | `/api/auth/reset-password` | Public | `200` |
| `POST` | `/api/auth/verify-email` | Public | `200` |
| `GET` | `/api/invoices/me` | Bearer | `200` |
| `GET` | `/api/tours/{tourId}/ratings` | Public | `200` |
| `POST` | `/api/tours/{tourId}/ratings` | Bearer | `201` |

### Example requests

Forgot password:

```json
{ "email": "traveler@example.com" }
```

Reset password:

```json
{
  "token": "<raw token from the email link>",
  "newPassword": "new-password"
}
```

Verify email:

```json
{ "token": "<raw token from the email link>" }
```

Create rating:

```json
{
  "bookingId": 30,
  "rating": 5,
  "comment": "Great trip"
}
```

Rating list response shape:

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "content": [
      {
        "id": 1,
        "tourId": 1,
        "accountId": 2,
        "accountUsername": "traveler",
        "bookingId": 30,
        "rating": 5,
        "comment": "Great trip",
        "createdAt": "2026-11-07T10:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "last": true,
    "hasNext": false,
    "hasPrevious": false,
    "averageRating": 5.00,
    "ratingCount": 1
  },
  "timestamp": "..."
}
```

---

## Database migration

New additive migration:

- `tour/src/main/resources/db/migration/V6__auth_tokens_and_tour_ratings.sql`
  - `auth_tokens` with account FK, unique `token_hash`, `token_type` check, `expires_at`, `used_at`, `created_at` and lookup indexes.
  - `tour_ratings` with tour/account/booking FKs, unique `booking_id`, 1..5 value check and indexes.

V6 was applied to the shared Supabase database during an earlier integration-test run (`flyway_schema_history` reported version 6). V5 and earlier migrations were not edited. Do not edit V6 after it has been applied; use V7+ for any further schema change.

---

## Config / environment variables

SMTP:

- `SPRING_MAIL_HOST` or `MAIL_HOST` - leave empty to disable sending safely.
- `SPRING_MAIL_PORT` or `MAIL_PORT` - default `587`.
- `SPRING_MAIL_USERNAME` or `MAIL_USERNAME`.
- `SPRING_MAIL_PASSWORD` or `MAIL_PASSWORD`.
- `SPRING_MAIL_SMTP_AUTH` or `MAIL_SMTP_AUTH` - default `true`.
- `SPRING_MAIL_SMTP_STARTTLS` or `MAIL_SMTP_STARTTLS` - default `true`.
- `MAIL_FROM` - default `no-reply@tour.local`.
- `APP_FRONTEND_URL` - default `http://localhost:3000`; used to build email links.

Auth token TTLs:

- `app.auth.password-reset-token-ttl` - default `PT30M`.
- `app.auth.email-verification-token-ttl` - default `P2D`.

No other new environment variables are required.

---

## Files changed

### Main code

- `tour/pom.xml` - added `spring-boot-starter-mail`.
- `tour/src/main/java/com/j2ee/tour/TourApplication.java` - `@EnableAsync`.
- `tour/src/main/resources/application.properties` - mail, frontend URL and auth-token TTL configuration.
- `tour/src/main/resources/db/migration/V6__auth_tokens_and_tour_ratings.sql` - new additive schema.
- `tour/src/main/java/com/j2ee/tour/model/AuthToken.java` - new entity.
- `tour/src/main/java/com/j2ee/tour/repository/AuthTokenRepository.java` - locked lookup and per-account deletion.
- `tour/src/main/java/com/j2ee/tour/service/AuthTokenService.java` - hashed token issue/consume logic.
- `tour/src/main/java/com/j2ee/tour/service/EmailService.java` - async, failure-tolerant mail sender.
- `tour/src/main/java/com/j2ee/tour/service/AuthService.java` - verification on registration, forgot/reset/verify methods, refresh-token revocation.
- `tour/src/main/java/com/j2ee/tour/controller/AuthController.java` - new auth endpoints.
- `tour/src/main/java/com/j2ee/tour/dto/request/ForgotPasswordRequest.java`
- `tour/src/main/java/com/j2ee/tour/dto/request/ResetPasswordRequest.java`
- `tour/src/main/java/com/j2ee/tour/dto/request/VerifyEmailRequest.java`
- `tour/src/main/java/com/j2ee/tour/service/RefreshTokenService.java` - revoke all refresh tokens for an account.
- `tour/src/main/java/com/j2ee/tour/model/TourRating.java` - new entity.
- `tour/src/main/java/com/j2ee/tour/repository/TourRatingRepository.java` - paged list, duplicate check, average.
- `tour/src/main/java/com/j2ee/tour/service/TourRatingService.java` - ownership/status/tour/duplicate rules and average.
- `tour/src/main/java/com/j2ee/tour/controller/TourRatingController.java` - public list and authenticated create.
- `tour/src/main/java/com/j2ee/tour/dto/request/TourRatingRequest.java`
- `tour/src/main/java/com/j2ee/tour/dto/response/TourRatingResponse.java`
- `tour/src/main/java/com/j2ee/tour/dto/response/TourRatingPageResponse.java`
- `tour/src/main/java/com/j2ee/tour/service/InvoiceService.java` - idempotent paid-booking creation and owner-scoped listing.
- `tour/src/main/java/com/j2ee/tour/repository/InvoiceRepository.java` - customer-scoped paged query.
- `tour/src/main/java/com/j2ee/tour/controller/InvoiceController.java` - `GET /api/invoices/me`.
- `tour/src/main/java/com/j2ee/tour/service/BookingService.java` - confirmation/cancellation email hooks and admin `PAID` invoice hook.
- `tour/src/main/java/com/j2ee/tour/service/PaymentService.java` - invoice hook when a payment path makes a booking `PAID`.
- `tour/src/main/java/com/j2ee/tour/security/SecurityConfig.java` - public new auth POSTs, authenticated `/api/invoices/me`, admin invoice rules preserved.

### Tests

- `tour/src/test/java/com/j2ee/tour/service/AuthServiceTest.java` - new auth-flow coverage.
- `tour/src/test/java/com/j2ee/tour/service/AuthTokenServiceTest.java` - new hashing/expiry/reuse coverage.
- `tour/src/test/java/com/j2ee/tour/service/EmailServiceTest.java` - missing-config and SMTP-failure tolerance.
- `tour/src/test/java/com/j2ee/tour/service/BookingServiceTest.java` - email/invoice hooks and fixed-clock date fixture.
- `tour/src/test/java/com/j2ee/tour/service/PaymentServiceTest.java` - invoice hook verification.
- `tour/src/test/java/com/j2ee/tour/controller/AuthControllerTest.java` - new auth endpoint responses.
- `tour/src/test/java/com/j2ee/tour/controller/TourRatingControllerTest.java` - rating list/create mappings.
- `tour/src/test/java/com/j2ee/tour/security/SecurityConfigTest.java` - public forgot-password and authenticated owner invoice route.
- `tour/src/test/java/com/j2ee/tour/BookingFlowIntegrationTests.java` - invoice-listing and rating integration coverage.

---

## Assumptions

- "Booking confirmation email" is sent when a booking is created, not when it is paid.
- "Booking cancellation email" is sent from the owner cancel endpoint and from an admin status change to `CANCELLED`; deleting a booking is not treated as a cancellation.
- Password reset revokes refresh tokens; already-issued stateless access tokens remain valid until their normal short expiry.
- Email verification does not block login; it only sets `emailVerified`.
- A rating comment is optional.
- `GET /api/tours/{tourId}/ratings` is public, including when the tour has no ratings; the average is `0.00` and `ratingCount` is `0`.
- Invoice status is `PAID`; tax and service fee default to `0` because the current booking model has no tax/fee fields.
- Invoice `paymentMethod` is left null for automatically created invoices because the booking model does not store the payment method.

---

## Risks / limitations

- Database-backed integration tests for this batch were not part of the final verification pass because of the supplied network rule. They were written and had passed in an earlier full-suite attempt, but a fresh final DB run is still recommended.
- An interrupted full-suite attempt is recorded as a network/tool interruption; no retry loop was attempted.
- `auth_tokens` rows are not automatically purged yet. They are one-time and expiry-checked, but expired rows will accumulate. A scheduled cleanup can be added later.
- Forgot-password has the generic response required by the task but is not separately rate-limited beyond the existing global auth limits.
- Email templates are plain text; there is no HTML template/theme support.
- SMTP delivery was tested with mocks only. No real SMTP endpoint was available in this run.
- Invoice creation is part of the payment/booking transaction. A database failure while creating the invoice will roll back that transaction rather than leave a `PAID` booking without an invoice.
- If a booking is later moved out of `PAID` (for example by deleting a payment), an already-created invoice is not deleted or reversed.
- Rating creation checks then inserts; the unique `booking_id` constraint is the final guard against a concurrent duplicate, which maps to `409` through the existing data-integrity handler.
- No `fe-tour/` changes were made; the frontend must be wired to the new endpoints separately.

---

## Manual verification steps

1. Run the non-database suite:
   ```text
   .\mvnw.cmd "-Dtest=!*IntegrationTests,!TourApplicationTests" test
   ```
   Expect 130 tests, 0 failures, 0 errors.
2. With SMTP unset, call `POST /api/auth/register`; registration should still succeed and the log should contain one warning that mail is not configured.
3. Call `POST /api/auth/forgot-password` for an existing and a missing email; both should return the same generic message.
4. With a real or captured reset token, call `POST /api/auth/reset-password`; verify the password changes, the token cannot be reused, and an old refresh token is rejected.
5. Verify a registration token through `POST /api/auth/verify-email`; confirm `accounts.email_verified` becomes `1` and reuse returns `400`.
6. Make a booking reach `PAID` through the payment callback; confirm an invoice exists and `GET /api/invoices/me` returns it only for the owner.
7. Change another booking to `PAID` through the admin booking update path; confirm an invoice is created idempotently.
8. Create a rating for a paid booking; confirm `POST /api/tours/{tourId}/ratings` returns `201`, a second attempt returns `409`, and `GET /api/tours/{tourId}/ratings` shows the average.
9. Try to rate another user's booking; expect `404`. Try to rate an unpaid booking; expect `403`.
10. When the database is available again, run the full suite:
    ```text
    .\mvnw.cmd test
    ```
    Verify Flyway is at version 6 and that the integration tests pass.
