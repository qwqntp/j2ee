# Task G2 — VNPay Sandbox payment

## Outcome

Customers can reserve only or pay an owned booking on VNPay Sandbox. The app does not
collect bank or card details. It displays payment success only after the backend has
committed a successful VNPay IPN. A signed browser return can report what VNPay told the
browser, but cannot settle a payment.

No G2 Flyway migration or dependency was added. The existing `payments` table stores
attempts and its unique `payment_provider_txn_id` index protects merchant references.
Booking totals, the booking lifecycle, seat handling, and invoice creation use the
existing models. Applied migrations were not edited.

## Endpoints and flow

| Endpoint | Access | Behavior |
|---|---|---|
| `POST /api/bookings/{bookingId}/payments/vnpay` | Bearer; booking owner | No body. Locks the booking, derives the outstanding amount from recorded payments, and returns `{ reference, paymentUrl, expiresAt }` for one open attempt. |
| `POST /api/payments/vnpay/return` | Bearer; booking owner | Receives `{ "query": "…" }`, validates the signed return, and reads the attempt. Does not write. |
| `GET /api/payments/vnpay/status/{reference}` | Bearer; booking owner | Reads the same recorded status without the signed query. |
| `GET /api/payments/vnpay/ipn/{routeToken}` | Registered HTTPS route credential plus signed VNPay query | Applies a definitive outcome once, or returns a provider acknowledgement without changing records. |

1. Checkout reserves seats with the existing booking API. For VNPay, it puts the existing
   booking ID into the checkout URL before sending the separate payment request. Reloading
   or recovering from a failed redirect opens that booking rather than creating another.
2. The backend verifies that the booking is owned, pending, and payable. It sends the
   outstanding balance in VND × 100, without rounding, and generates a signed PAY 2.1.0 URL
   with a 15-minute GMT+7 time window. The frontend only redirects to the permitted HTTPS
   Sandbox pay endpoint.
3. VNPay redirects the browser to `http://localhost:3000/payment/vnpay-return` for local
   testing. The frontend tries session refresh first. If sign-in is needed, it preserves the
   signed context in the tab with a 15-minute validity window and sends only the opaque
   reference through login. If tab storage is unavailable, the owner can still recheck by
   reference. The page shows pending, failure, cancellation, expiry, invalid return, or
   recorded success and links to the booking or its history.
4. In a separate HTTPS call, VNPay sends IPN. A verified `00` response code **and** `00`
   transaction status for the stored amount can set the attempt `SUCCESS` and the booking
   `PAID`, and create or settle one invoice in the same transaction. Signed definitive
   nonpayment sets the attempt `FAILED` without paying the booking. Ambiguous, contradictory,
   or late ineligible outcomes require reconciliation.

## Verification and security decisions

- [VNPay PAY 2.1.0](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html) documents
  server-to-server HTTPS GET at a merchant-registered URL. The IPN credential is a path
  segment in that URL, not a custom request header VNPay would have to provide. The bare
  route does not authorize settlement. The design uses a separate credential because the
  browser return also carries a valid VNPay signature.
- Authentication of the IPN route precedes payload processing. The backend verifies
  HMAC-SHA512 in constant time, rejects malformed and repeated fields, and checks the merchant
  code, managed reference, exact amount, and both status fields. A successful callback also
  needs a valid VNPay transaction number. Ownership is checked on each customer route.
- IPN uses HTTP `200` with exact JSON keys `RspCode` and `Message`: `00` committed result,
  `02` duplicate terminal result, `01` unknown reference, `04` invalid amount,
  `97` invalid credential/signature/merchant, and `99` malformed, unresolved, contradictory,
  or processing failure. The first `00` acknowledgement is sent after commit.
- The booking write lock serializes starts, competing payments, the cancellation cascade, and
  IPN processing. Settlement does not acquire a departure lock. An invoice write lock prevents
  settlement from rewriting an invoice just marked paid in another transaction. The legacy
  admin and mock paths cannot modify a managed VNPay attempt.
- An expired URL is not proof of nonpayment. An unresolved attempt blocks new references,
  competing payments and booking changes that would invalidate it. There is no automatic
  reconciliation or provider refund; operations must investigate when IPN is missing, ambiguous,
  or cannot settle a changed booking. Do not tell the customer to pay again in this state.
- `next dev` can record the incoming return query before React can clean the URL. The app
  disables VNPay launch in development mode. Use the production frontend for real Sandbox
  testing and redact sensitive paths and queries in deployed request logs and tracing.

## Changed files

- **Backend protocol and API:** `tour/src/main/java/com/j2ee/tour/integration/VnpayGateway.java`,
  `controller/VnpayPaymentController.java`, `dto/response/VnpayStartResponse.java`,
  `dto/response/VnpayStatusResponse.java`, `security/SecurityConfig.java`, and
  `tour/src/main/resources/application.properties`.
- **Backend lifecycle:** `service/VnpayPaymentService.java`, `service/PaymentService.java`,
  `service/BookingService.java`, `service/InvoiceService.java`, `service/TourDepartureService.java`,
  and the `PaymentRepository`, `BookingRepository`, and `InvoiceRepository` under the same
  Java package root.
- **Frontend:** `fe-tour/app/payment/page.tsx`, `fe-tour/app/payment/vnpay-return/page.tsx`,
  `fe-tour/components/payment-form.tsx`, `trip-detail.tsx`, and `vnpay-return.tsx`;
  `fe-tour/lib/api.ts`, `auth.ts`, `types.ts`, and `vnpay.ts`.
- **Tests:** `VnpayGatewayTest`, `VnpayTestPayload`, `VnpayPaymentControllerTest`,
  `VnpayPaymentIntegrationTests`, existing payment and departure-cancellation test fixtures,
  and `fe-tour/lib/{api,auth,vnpay}.test.ts`.
- **Docs:** `docs/api-spec.md` and the G2 design and plan in `docs/superpowers/`.

## Actual verification

Commands ran from temporary source copies that excluded dotenv files. Backend database tests
used an isolated, loopback-only PostgreSQL 17.12 instance with synthetic fixtures, not the
shared datasource. No merchant credentials or live payment data were used.

| Check | Result |
|---|---|
| Backend `.\mvnw.cmd test` | BUILD SUCCESS: **363 run, 0 failures, 0 errors, 1 skipped**. The skip was the pre-existing `TourApplicationTests` case. |
| Backend `.\mvnw.cmd -DskipTests package` | BUILD SUCCESS; Spring Boot JAR produced after the full test run. |
| Frontend `npm.cmd test` | **151 passed** in 19 files. |
| Frontend `npm.cmd run lint` | Exit 0. |
| Frontend `npx.cmd tsc --noEmit` | Exit 0. |
| Frontend `npm.cmd run build` | Passed on retry, generated 27 pages including `/payment/vnpay-return`. Next.js 16.4.3 reported a known security advisory; upgrade separately before deployment. |
| Local production HTTP smoke | Both the return and checkout recovery routes returned 200. Verified `no-referrer`, `noindex`, the recovered booking link, and that a synthetic return marker was absent from captured server output and the local trace. |

An earlier full backend run failed because the temporary database was no longer listening.
Starting it in the verification run resolved the connection errors. The first isolated frontend
build also needed a physical copy of the existing dependencies because Turbopack does not
resolve an out-of-root junction. No project build settings were changed. Independent review led
to tested fixes for lock order, a concurrently paid invoice, checkout reload recovery, and
concurrent session refresh.

**Not tested:** actual payment at VNPay, VNPay calling a registered public URL, production
ingress log redaction, or interactive browser checkout. The local HTTP smoke and synthetic
callback tests do not establish that a Sandbox payment occurred.

## Manual Sandbox checklist

- [ ] Configure the backend with `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_PAY_URL`, and
      `VNPAY_RETURN_URL`. Keep the local browser return at
      `http://localhost:3000/payment/vnpay-return`. Generate a separate URL-safe, random
      `VNPAY_IPN_TOKEN` and supply it as a deployment secret. Do not share it or the hash
      secret with the frontend.
- [ ] Deploy the callback through public HTTPS and register the exact
      `https://<backend-host>/api/payments/vnpay/ipn/<route-token>` in VNPay Sandbox. VNPay
      cannot call localhost. Ensure the ingress forwards GET paths and queries unchanged,
      redacts the credential-bearing path and query, and does not publish unnecessary routes.
      Verify return query redaction separately on frontend infrastructure.
- [ ] For local tests, start the frontend with `npm run build` then
      `npm run start -- --port 3000`. Do not use `npm run dev` for genuine returns. Confirm
      browser API origins and CORS allow the local frontend to reach the test backend.
- [ ] Sign in as an owner and pay with a VNPay-provided Sandbox test method. Verify the hosted
      amount matches the booking balance including any promotion. Compare a returned browser
      before IPN with the final recorded state after IPN; verify one payment, one paid invoice,
      and no duplicate transition after redelivery.
- [ ] Cancel or let an attempt expire. Check that a verified nonpayment result leaves the
      booking unpaid, and that a missing IPN leaves it pending and blocks a fresh charge.
      Check operator reconciliation with the merchant portal before any manual resolution.
- [ ] Expire the user session before return. Check both silent refresh and reauthentication;
      verify the login URL carries no signed fields, and signing in as another user does not
      disclose the booking.
- [ ] Reload checkout after the booking was created but before the payment link opened.
      Continue from booking details and confirm there is no second reservation or unresolved
      reference. Check reserve-only checkout still works without contacting VNPay.

No `.env` file was accessed or modified. No changes were committed or pushed.
