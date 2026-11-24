# Task B1 fix - guest-count promotion recalculation

## Summary

Fixed `BookingService.update` so that when `guestCount` changes on a booking with an applied promotion:

- `discountAmount` is recalculated from the promotion percentage on the new subtotal using `HALF_UP`, capped at the subtotal;
- the promotion minimum-order amount is re-validated against the new subtotal;
- a no-longer-qualifying subtotal returns `400` with `Order does not meet promotion minimum amount`;
- `totalAmount` is clamped at zero and can never be negative.

The validation and calculation happen before seats are reserved/released, so a rejected update does not leave partial seat changes behind.

No other production behavior was changed.

**Full test result:** `.\mvnw.cmd test` -> **137 tests, 0 failures, 0 errors, 1 skipped, BUILD SUCCESS**.

---

## What changed

`BookingService.update` now computes the prospective subtotal and discount before touching departure seats:

```text
newSubtotal = departure price * new guestCount
if booking has a promotion:
    if newSubtotal < promotion.minOrderAmount -> 400
    newDiscount = promotion percentage * newSubtotal (HALF_UP, capped)
else:
    newDiscount = existing booking discount
total = max(0, newSubtotal - newDiscount)
```

The normal-user update path and the admin update path both use the same calculation, keeping guest-count edits consistent. No other methods or production files were changed.

---

## Files changed

- `tour/src/main/java/com/j2ee/tour/service/BookingService.java` - re-computes promotion discount and validates the minimum order on guest-count updates; clamps total at zero.
- `tour/src/test/java/com/j2ee/tour/service/BookingServiceTest.java` - added unit tests for discount recalculation and minimum-order rejection.
- `tour/src/test/java/com/j2ee/tour/BookingFlowIntegrationTests.java` - added the same two scenarios as database-backed integration tests.

No migration, configuration, DTO, endpoint, payment, or frontend changes were made.

---

## Tests added

### Unit tests (`BookingServiceTest`)

1. `reducingGuestCountRecomputesPromotionDiscount`
   - Starts with 2 guests at 500 each, a 10% promotion, discount 100, total 900.
   - Updates to 1 guest.
   - Verifies subtotal 500, discount 50, total 450, and one released seat.

2. `reducingGuestCountBelowPromotionMinimumReturnsBadRequest`
   - Promotion minimum order is 600.
   - Reduces the booking from 2 guests (subtotal 1000) to 1 guest (subtotal 500).
   - Verifies `400`, the message `Order does not meet promotion minimum amount`, and that the booking/seat state was not changed.

### Integration tests (`BookingFlowIntegrationTests`)

1. `reducingGuestCountRecomputesPromotionDiscount`
   - Persists a real promotion and booking, updates the guest count, and verifies the recalculated amounts and released departure seats.

2. `reducingGuestCountBelowPromotionMinimumReturnsBadRequest`
   - Persists a promotion with a 600 minimum order, attempts to reduce the booking below that threshold, and verifies `400` plus unchanged booking and seat state.

---

## Test result

```text
Tests run: 137, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

The skipped test is the pre-existing disabled `TourApplicationTests`.

---

## Assumptions

- The recalculated discount uses the promotion's current percentage, not a historical snapshot.
- The minimum-order check uses the new subtotal and is inclusive: `newSubtotal >= minOrderAmount` qualifies.
- If the booking has no promotion, the existing discount is preserved, matching prior behavior.
- A guest-count update with a promotion now applies to any caller of `update`, including admins, because both use the same service path.

---

## Manual verification

1. Create a promotion with 10% discount and minimum order 500.
2. Create a 2-guest booking using that promotion; expect subtotal 1000, discount 100, total 900.
3. Update the booking to 1 guest; expect subtotal 500, discount 50, total 450, and one released seat.
4. Create a promotion with minimum order 600.
5. Create a 2-guest booking and try to reduce it to 1 guest; expect `400` with `Order does not meet promotion minimum amount`, and confirm the booking and departure seats are unchanged.
