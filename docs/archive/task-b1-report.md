# Task B1 - Bookings, travelers, promotions

## Summary

Batch 1 is complete. Bookings are owner-scoped, support cancellation with seat/promotion release, accept optional real travelers, and apply promotions server-side. No `fe-tour/` files were modified and no payment code was changed.

**Full test result:** `.\mvnw.cmd test` -> **133 tests, 0 failures, 0 errors, 1 skipped, BUILD SUCCESS** (the skipped test is the pre-existing disabled `TourApplicationTests`).

---

## Step 0 checks

### a) Guest-count updates

Already correct: `BookingService.update` calculates the seat delta from the old/new `guestCount`, reserves/releases seats on the locked departure, recalculates `subtotal` from the departure price, and recalculates `totalAmount` as `subtotal - bookingDiscountAmount`.

A regression test was added (`updateGuestCountRecalculatesSeatsAndTotal` in both the service and integration suites).

### b) "Departure has already started" HTTP status

`BookingService.create` throws `IllegalStateException`, and `GlobalExceptionHandler` maps `IllegalStateException` to **409 Conflict**. No mapping change was needed.

A controller test now verifies the HTTP response is `409` with the message `Departure has already started`.

### c) Test profile / payment secret

- Added `@ActiveProfiles("test")` to `BookingFlowIntegrationTests`, `CrudIntegrationTests`, and the disabled `TourApplicationTests`.
- Removed the datasource overrides and `spring.flyway.enabled=false` from `application-test.properties`.
- Integration tests therefore use the application datasource (Supabase via `.env` / `SPRING_DATASOURCE_*`) and Flyway, exactly like the running app, while also loading `app.payment.callback-secret=test-callback-secret`.
- The known payment-related integration-test errors no longer occur.

---

## What changed by item

### 1. Owner-scoped bookings, detail and cancel

- `GET /api/bookings` now defaults to **newest first** by `bookingCreatedAt DESC, bookingId DESC` when the caller does not provide an explicit sort. Normal users only receive their own bookings; admins keep the existing all-bookings view.
- `GET /api/bookings/{id}` keeps the ownership check and returns travelers plus applied promotion information. Another user receives `404`.
- Added `POST /api/bookings/{id}/cancel`.
  - Owner-scoped, with the existing admin bypass.
  - Allowed only when the booking status is `PENDING` and there are no successful payments.
  - Releases held seats back to the departure in the same transaction.
  - Decrements the applied promotion's used count.
  - Sets status to `CANCELLED`.
  - Any other status, a paid booking, or a booking with successful payments returns `409`.
- Admin status changes to `CANCELLED` through `PUT /api/bookings/{id}` also release promotion usage.

### 2. Real travelers

- `POST /api/bookings` accepts an optional `travelers` array.
- Each traveler supports: `fullName` (required), `phone`, `email`, `dateOfBirth`, `gender`, `idDocument`.
- If provided, the list size must equal `guestCount`; otherwise the request returns `400`.
- Travelers are saved to the `travelers` table and linked to the booking.
- If `travelers` is omitted, behavior is unchanged: no Traveler rows are created.
- `TravelerResponse` now includes `phone` and `email`.
- Admin traveler CRUD also accepts optional `phone` / `email`; old 5-argument service calls still compile through an overloaded constructor.

### 3. Promotions

- `POST /api/bookings` accepts an optional `promotionCode`.
- Server-side validation checks:
  - code exists (case-insensitive),
  - promotion is active,
  - today is within `startDate`..`endDate`,
  - usage limit has not been reached,
  - order subtotal meets the minimum-order amount (when set).
- Discount is calculated server-side as `subtotal * discountPercentage / 100`, rounded `HALF_UP` to 2 decimals and capped at the subtotal.
- `discountAmount` and `totalAmount` are stored from the server calculation; client values are never trusted.
- The promotion used count is incremented under a pessimistic row lock during booking creation.
- Invalid code/state returns `400` with a clear message.
- No `promotionCode` -> `discountAmount = 0`, as before.
- Cancelling a booking decrements the used count.
- Admin promotion CRUD now supports optional `code`, `active`, `usageLimit`, and `minOrderAmount`; `usedCount` is server-managed.

---

## New / changed endpoints

### `GET /api/bookings`

- Auth: authenticated user.
- Behavior: current user's bookings only, paginated, newest first by default.
- Response: existing `PageResponse<BookingResponse>` shape; `BookingResponse` now also has `promotion`.

### `GET /api/bookings/{id}`

- Auth: booking owner or admin.
- Response: `BookingResponse` with `travelers` (now including `phone`/`email`) and `promotion`.
- Other users: `404`.

### `POST /api/bookings`

- Auth: authenticated user.
- Request:
  ```json
  {
    "departureId": 1,
    "guestCount": 2,
    "note": "optional",
    "travelers": [
      {
        "fullName": "Alice",
        "phone": "0901234567",
        "email": "alice@example.com",
        "dateOfBirth": "1990-01-01",
        "gender": "FEMALE",
        "idDocument": "ID1"
      }
    ],
    "promotionCode": "SUMMER"
  }
  ```
  `travelers` and `promotionCode` are optional. Existing frontend payload `{ departureId, guestCount, note }` still works unchanged.
- Response: existing booking fields plus:
  - `travelers[]` with `phone` / `email`,
  - `promotion`: `{ id, code, name, discountPercentage }`.

### `POST /api/bookings/{id}/cancel`

- Auth: booking owner or admin.
- Request: no body.
- Success: `200` with `BookingResponse` and `status = "CANCELLED"`.
- Rejected: `409` when the booking is not `PENDING` or has successful payments.

### Admin promotion endpoints (`POST/PUT /api/promotions`)

- New optional request fields: `code`, `active`, `usageLimit`, `minOrderAmount`.
- Response now includes `code`, `active`, `usageLimit`, `usedCount`, `minOrderAmount`.
- Existing payloads without these fields still work.

### Admin traveler endpoints (`POST/PUT /api/travelers`)

- New optional request fields: `phone`, `email`.
- Response now includes `phone`, `email`.

---

## Database migration

New additive migration:

- `tour/src/main/resources/db/migration/V5__booking_promotions_and_traveler_contacts.sql`
  - `promotions`: `promotion_code`, `is_active`, `usage_limit`, `used_count`, `min_order_amount`
  - unique partial index on `promotion_code`
  - `bookings.promotion_id` + FK/index
  - `travelers.traveler_phone`, `travelers.traveler_email`
  - non-negative checks for promotion usage/min-order fields

V5 was applied to the shared Supabase database by the integration-test run (`flyway_schema_history` is now at v5). It is additive and does not rewrite existing rows or migrations.

---

## Files changed

### Main code

- `tour/src/main/resources/db/migration/V5__booking_promotions_and_traveler_contacts.sql` - additive schema for promotions, booking promotion link and traveler contacts.
- `tour/src/main/java/com/j2ee/tour/model/Promotion.java` - new code/active/usage/min-order fields.
- `tour/src/main/java/com/j2ee/tour/model/Booking.java` - nullable `promotion` relation.
- `tour/src/main/java/com/j2ee/tour/model/Traveler.java` - phone/email fields; excludes the back-reference from Lombok `toString`/`equals`/`hashCode` to avoid recursive traversal.
- `tour/src/main/java/com/j2ee/tour/dto/request/CreateBookingRequest.java` - optional `travelers` and `promotionCode`; old 3-argument constructor retained.
- `tour/src/main/java/com/j2ee/tour/dto/request/BookingTravelerRequest.java` - new traveler input DTO.
- `tour/src/main/java/com/j2ee/tour/dto/request/TravelerRequest.java` - optional phone/email; old 5-argument constructor retained.
- `tour/src/main/java/com/j2ee/tour/dto/request/PromotionRequest.java` - optional code/active/usage/min-order; old 5-argument constructor retained.
- `tour/src/main/java/com/j2ee/tour/dto/response/BookingResponse.java` - adds applied promotion summary.
- `tour/src/main/java/com/j2ee/tour/dto/response/BookingPromotionResponse.java` - new promotion summary DTO.
- `tour/src/main/java/com/j2ee/tour/dto/response/TravelerResponse.java` - adds phone/email.
- `tour/src/main/java/com/j2ee/tour/dto/response/PromotionResponse.java` - adds new promotion fields.
- `tour/src/main/java/com/j2ee/tour/repository/BookingRepository.java` - pessimistic booking lookup for cancel/update.
- `tour/src/main/java/com/j2ee/tour/repository/PromotionRepository.java` - case-insensitive code lookup and pessimistic locks.
- `tour/src/main/java/com/j2ee/tour/service/BookingService.java` - owner-scoped newest-first list, traveler persistence, promotion validation/discount/usage, cancel flow, promotion release.
- `tour/src/main/java/com/j2ee/tour/service/PromotionService.java` - manages new promotion fields and unique code normalization.
- `tour/src/main/java/com/j2ee/tour/service/TravelerService.java` - maps phone/email.
- `tour/src/main/java/com/j2ee/tour/controller/BookingController.java` - new cancel endpoint.

### Tests / test configuration

- `tour/src/test/resources/application-test.properties` - removed datasource and Flyway overrides so tests use the app database and load the test payment secret.
- `tour/src/test/java/com/j2ee/tour/BookingFlowIntegrationTests.java` - `@ActiveProfiles("test")`, new traveler/promotion/cancel/list/guest-count integration tests.
- `tour/src/test/java/com/j2ee/tour/CrudIntegrationTests.java` - `@ActiveProfiles("test")`.
- `tour/src/test/java/com/j2ee/tour/TourApplicationTests.java` - `@ActiveProfiles("test")`.
- `tour/src/test/java/com/j2ee/tour/service/BookingServiceTest.java` - expanded from 5 to 20 tests.
- `tour/src/test/java/com/j2ee/tour/security/SecurityConfigTest.java` - includes `BookingController` and verifies the `409` started-departure response.

No `fe-tour/` files were changed. No payment production files were changed.

---

## Config / environment variables

No new environment variables are required.

Existing values used by the batch:

- Datasource and Flyway: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_FLYWAY_ENABLED` (from `tour/.env` or the process environment).
- Tests now load `app.payment.callback-secret=test-callback-secret` from `application-test.properties`.
- Production payment callback secret remains `PAYMENT_CALLBACK_SECRET` as before.

---

## Tests added / updated

- `BookingServiceTest`: 20 tests, including:
  - guest-count seat/total recalculation,
  - traveler save/link and size mismatch,
  - valid promotion discount + usage increment,
  - inactive/expired/over-limit/min-order/unknown promotion rejection,
  - cancel releases seats and promotion usage,
  - other user cannot cancel,
  - paid/already-cancelled booking returns conflict,
  - owner-scoped newest-first list,
  - detail includes promotion info.
- `BookingFlowIntegrationTests`: 16 tests, including real DB coverage for travelers + promotion persistence, cancellation, cross-user cancel denial, owner-scoped list ordering, and guest-count updates.
- `SecurityConfigTest`: 23 tests, including the started-departure `409` response.
- Existing tests updated for the new `BookingService` constructor and `@ActiveProfiles("test")`.

**Command result:**

```text
Tests run: 133, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

---

## Assumptions

- Cancellation is allowed only for `PENDING` bookings with no successful payments. `PAID`, `CONFIRMED`, `COMPLETED`, and already `CANCELLED` bookings return `409`; refunds are out of scope.
- Promotion codes are case-insensitive for lookup and are stored uppercase when created/updated by admin.
- If `travelers` is provided, its size must exactly equal `guestCount`; an empty list with `guestCount > 0` is rejected with `400`.
- The promotion discount is calculated from the current subtotal, rounded to 2 decimals and capped at the subtotal. Changing `guestCount` later keeps the stored discount amount fixed and recalculates the total from the new subtotal.
- Changing a cancelled booking back to a non-cancelled state through the admin update path does not re-increment promotion usage.
- `GET /api/bookings` honors an explicit client sort if one is supplied; otherwise it uses newest-first.
- Admins continue to see all bookings on `GET /api/bookings`; normal users only see their own.

---

## Risks / unfinished

- V5 has already been applied to Supabase. Do not edit it; any further schema change must be a new migration (V6+).
- Promotion usage is tracked with a simple counter. Booking creation locks the promotion row; admin promotion updates do not use the same lock, so heavy concurrent admin edits could still race.
- Guest-count updates do not re-validate promotion minimum-order/usage; they only recalculate the total from the stored discount. If business rules require revalidation, that should be a follow-up.
- Admin reactivation of a cancelled booking does not restore promotion usage.
- No frontend changes were made; the frontend can adopt `travelers`, `promotionCode`, and the cancel endpoint later.

---

## Manual verification steps

1. Run `.\mvnw.cmd test` in `tour/`; expect `133 tests, 0 failures, 0 errors, 1 skipped`.
2. Start the backend and verify `flyway_schema_history` contains version `5`.
3. Create a booking with the existing frontend payload `{ departureId, guestCount, note }`; it should still return `PENDING`, `discountAmount = 0`, and an empty travelers list.
4. Create an admin promotion with `code`, `active`, `usageLimit`, and `minOrderAmount`.
5. Create a booking with `travelers` and `promotionCode`; verify travelers are returned with phone/email, the discount/total are server-calculated, and `usedCount` increments.
6. Call `GET /api/bookings` as two different users; each should see only their own bookings, newest first.
7. Call `GET /api/bookings/{id}` for another user's booking; expect `404`.
8. Call `POST /api/bookings/{id}/cancel` on a `PENDING` booking; expect `200`, `CANCELLED`, released seats and decremented promotion usage.
9. Call the cancel endpoint on a `PAID` booking; expect `409`.
10. Submit an invalid or expired promotion code; expect `400` with a clear message.
