# Test Result — Full Backend Suite

**Date:** 2026-11-05  
**Working directory:** `tour/`  
**Command:** `.\mvnw.cmd test`  
**Finished at:** 2026-11-05 10:42:22 +07:00  
**Exit code:** `1` — **BUILD FAILURE**

---

## 1. Overall Result

| Metric | Count |
|---|---|
| Tests run | 112 |
| Passed | 107 |
| Failures (assertion) | 0 |
| Errors (exceptions) | 4 |
| Skipped | 1 |

> Surefire reports 4 **errors**, not assertion failures. All 4 errors have the same cause: an empty payment-callback signing key.

---

## 2. Per-Class Results

| Test class | Run | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| `BookingFlowIntegrationTests` | 11 | 0 | 3 | 0 | **ERROR** |
| `CrudIntegrationTests` | 13 | 0 | 1 | 0 | **ERROR** |
| `AuthControllerTest` | 5 | 0 | 0 | 0 | Passed |
| `TourControllerTest` | 9 | 0 | 0 | 0 | Passed |
| `FixedWindowRateLimiterTest` | 3 | 0 | 0 | 0 | Passed |
| `JwtAuthenticationFilterTest` | 3 | 0 | 0 | 0 | Passed |
| `JwtServiceTest` | 4 | 0 | 0 | 0 | Passed |
| `RateLimitFilterTest` | 4 | 0 | 0 | 0 | Passed |
| `SecurityConfigTest` | 22 | 0 | 0 | 0 | Passed |
| `AuthServiceTest` | 9 | 0 | 0 | 0 | Passed |
| `BookingServiceTest` | 5 | 0 | 0 | 0 | Passed |
| `PaymentServiceTest` | 8 | 0 | 0 | 0 | Passed |
| `RefreshTokenServiceTest` | 8 | 0 | 0 | 0 | Passed |
| `TourServiceTest` | 7 | 0 | 0 | 0 | Passed |
| `TourApplicationTests` | 1 | 0 | 0 | 1 | Skipped (`@Disabled`) |

The database-backed integration tests now connect to PostgreSQL successfully. 20 of the 24 integration tests passed.

---

## 3. Failed Tests

| # | Test | Error |
|---|---|---|
| 1 | `BookingFlowIntegrationTests.bookingDeleteRejectedWhenPaymentsExist` | `IllegalStateException: Unable to create payment signature` |
| 2 | `BookingFlowIntegrationTests.paymentCompletesBookingWhenSumReachesTotal` | `IllegalStateException: Unable to create payment signature` |
| 3 | `BookingFlowIntegrationTests.refundRejectsAmountExceedingPayment` | `IllegalStateException: Unable to create payment signature` |
| 4 | `CrudIntegrationTests.refundUpdateAndDelete` | `IllegalStateException: Unable to create payment signature` |

### Stack trace

All four failures share this cause:

```
java.lang.IllegalStateException: Unable to create payment signature
    at com.j2ee.tour.service.PaymentSignatureService.sign(PaymentSignatureService.java:55)
    at com.j2ee.tour.BookingFlowIntegrationTests.callback(BookingFlowIntegrationTests.java:112)
    ...
Caused by: java.lang.IllegalArgumentException: Empty key
    at java.base/javax.crypto.spec.SecretKeySpec.<init>(SecretKeySpec.java:108)
    at com.j2ee.tour.service.PaymentSignatureService.sign(PaymentSignatureService.java:51)
```

---

## 4. Root Cause

1. `BookingFlowIntegrationTests` and `CrudIntegrationTests` use `@SpringBootTest` but do **not** declare `@ActiveProfiles("test")`. The test log confirms:

   ```
   No active profile set, falling back to 1 default profile: "default"
   ```

2. Because the `test` profile is not active, `src/test/resources/application-test.properties` is not loaded. That file contains:

   ```properties
   app.payment.callback-secret=test-callback-secret
   ```

3. The default `application.properties` has:

   ```properties
   app.payment.callback-secret=${PAYMENT_CALLBACK_SECRET:}
   ```

   `PAYMENT_CALLBACK_SECRET` was not set in the environment for this run, so the secret resolved to an empty string.

4. The integration-test helper calls `PaymentSignatureService.sign(...)` directly. HMAC initialization rejects the empty key with `IllegalArgumentException: Empty key`, which is wrapped as `IllegalStateException: Unable to create payment signature`.

This is a **test-configuration gap**, not a production callback-path regression: production callback verification fails closed with `503` when the secret is missing, but the test helper signs callbacks before invoking the service.

---

## 5. Suggested Fix (Not Applied)

Any one of these would resolve the four errors:

1. **Set the secret for the test run:**

   ```powershell
   $env:PAYMENT_CALLBACK_SECRET = 'test-callback-secret'
   .\mvnw.cmd test
   ```

2. **Add a test property source to both integration classes:**

   ```java
   @TestPropertySource(properties = "app.payment.callback-secret=test-callback-secret")
   ```

3. **Activate the test profile**, only if `jdbc:postgresql://localhost:5432/tour_test` with `test`/`test` credentials is available. This would also switch the datasource from the default `tour` database.

No source, test, configuration, or migration files were changed to produce this report.

---

## 6. Notes

- The previous run was blocked entirely by `Connection to localhost:5432 refused` (24 integration errors). In this run, PostgreSQL was reachable and the integration suite executed; the remaining 4 errors are the signing-key issue described above.
- `TourApplicationTests.contextLoads` is skipped by `@Disabled("Requires PostgreSQL; covered by the manual read-only startup check")`.
- Surefire reports are available under `tour/target/surefire-reports/`.
