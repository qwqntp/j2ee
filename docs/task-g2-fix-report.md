# Task G2 fix — VNPay reconciliation and stuck bookings

Date: 2026-11-21

## Outcome

A booking no longer stays `PENDING` only because the VNPay IPN was missing or the customer
left the browser before IPN arrived. The backend now reconciles a managed attempt through
VNPay's signed server-to-server QueryDr API, and releases retry/cancellation only after
VNPay confirms that the attempt was **not** paid. Ambiguous or unreachable answers keep the
attempt protected.

The browser return remains evidence of what VNPay told the browser, never settlement by
itself. Settlement still requires a signed VNPay answer (IPN or QueryDr), the stored
merchant/reference/amount, and the existing booking lock.

Other fixes in this pass:

- VNPay's `10,000 VND` minimum is enforced before an attempt row exists.
- Expired or unresolved payment URLs are never reopened or reused.
- Retry/cancellation responses explain why they are blocked, and the booking page offers
  a "Check payment status" action for the traveller.

No schema change, migration, or new dependency was added. No existing Flyway migration was
edited. All changes remain uncommitted and unpushed.

## Root causes

1. **Settlement depended on IPN.** The only path that wrote `SUCCESS` was the IPN handler.
   A return page could show a verified `SUCCESS` outcome while the booking stayed `PENDING`
   forever when IPN was missing or unreachable.
2. **No authoritative fallback.** There was no server-to-server lookup, so an attempt could
   not be safely released even when VNPay said it was never paid.
3. **No minimum check.** Starting below VNPay's minimum either failed at the provider or left
   a local `PENDING` attempt that could never be paid.
4. **Confusing blocked actions.** Retry and cancellation returned conflicts without a way
   for the traveller to resolve the unresolved attempt.

## Booking 734 investigation

Booking 734 was paid in Sandbox, but `POST /api/bookings/734/payments/vnpay/reconcile`
returned HTTP `200` with `checked: true`, `status: "PENDING"`, and no explanation.

The live QueryDr answer was captured without logging any secret or payment payload:

- VNPay returned HTTP `200` (`Server: openresty`, `application/json; charset=utf-8`) with an
  **unsigned** envelope:
  `{"vnp_ResponseCode":"99","vnp_Message":"Unexpected error"}`.
- The request itself matches the published contract: `POST` JSON, `querydr`, version `2.1.0`,
  32-character request id, `vnp_TransactionDate` equal to the stored PAY create date, both dates
  in GMT+7 `yyyyMMddHHmmss`, a valid IP, and the documented pipe-delimited HMAC-SHA512 over
  `RequestId|Version|Command|TmnCode|TxnRef|TransactionDate|CreateDate|IpAddr|OrderInfo`.
- Read-only probes with unique request ids varied the order info, IP, transaction date,
  optional transaction number, hash-type field, and transport. Every valid `POST`/JSON probe
  returned the same `99`.
- Control probes with a deliberately wrong checksum and an invalid TmnCode **also** returned
  `99`, so VNPay was not reaching checksum or TmnCode validation. `GET` returned `405` and a
  form-encoded `POST` returned `500`, confirming POST/JSON is the correct transport.

Conclusion: for this Sandbox merchant/endpoint, QueryDr returns `99 Unexpected error` before
processing any request. The attempt therefore cannot be reconciled until the provider side is
fixed (QueryDr enabled for the merchant, the calling server IP registered, or a Sandbox
incident cleared). Changing the request checksum cannot fix this; it is not a checksum error.

The trace also exposed a real application defect: the client treated **any** response without
`vnp_SecureHash` as `INVALID_PROVIDER_SIGNATURE`, hiding the actual `99` rejection. That is now
fixed: an unsigned error-only envelope is reported as `PROVIDER_REJECTED` with the safe code,
while an unsigned success or any unsigned response carrying transaction data is still rejected.

Booking 734 now reports `reason: "PROVIDER_REJECTED"`, `providerCode: "99"` and remains
`PENDING`; it no longer claims a signature failure or invites another payment.

## Backend behavior

### QueryDr protocol client

`tour/src/main/java/com/j2ee/tour/integration/VnpayQueryClient.java`:

- `POST https://sandbox.vnpayment.vn/merchant_webapi/api/transaction` with `vnp_Command=querydr`.
- Request checksum follows the documented pipe-delimited formula:
  `RequestId|Version|Command|TmnCode|TxnRef|TransactionDate|CreateDate|IpAddr|OrderInfo`.
- 5-second connect/read timeouts (`VnpayHttpConfig`), no redirects of provider data to the
  client, and no provider response/query in exception text or logs.
- VNPay API-level errors are sent without a signature and with only
  `vnp_ResponseCode`/`vnp_Message`; those are surfaced as `PROVIDER_REJECTED` with the safe
  two-digit code and never settle or release an attempt. An unsigned success or a
  data-bearing unsigned response is rejected.
- Response checksum follows the official 15-field formula including
  `vnp_PromotionCode` and `vnp_PromotionAmount`. The response is decoded as UTF-8 so a
  missing charset cannot corrupt the signed Vietnamese `vnp_Message`.
- A response must be a flat JSON object of known string fields, signed with HMAC-SHA512,
  and must match this merchant, reference, and expected amount (`VND × 100`).
- `vnp_TransactionType` must be `01` (payment). A success also needs a non-zero
  `vnp_TransactionNo`.

Response handling:

| QueryDr answer | Result |
|---|---|
| `vnp_ResponseCode=00`, `vnp_TransactionStatus=00` | Authoritative `SUCCESS` (`VERIFIED_SUCCESS`) |
| `vnp_ResponseCode=00`, `vnp_TransactionStatus=02` | Authoritative `FAILED` (`VERIFIED_NONPAYMENT`) |
| `vnp_ResponseCode=00`, any other transaction status (`01`, `04`, `05`, `06`, `07`, `09`, …) | `TRANSACTION_UNRESOLVED` — attempt stays `PENDING` |
| `vnp_ResponseCode=91` (not found) | `TRANSACTION_NOT_FOUND` — not proof of nonpayment |
| Other response codes (`02`, `03`, `94`, `97`, `99`, …) | `PROVIDER_REJECTED` with the safe code — attempt stays `PENDING` |
| Invalid signature, wrong merchant/reference/amount, malformed JSON | `INVALID_PROVIDER_SIGNATURE`, `PROVIDER_MISMATCH`, or `PROVIDER_MALFORMED_RESPONSE` — attempt stays `PENDING` |
| Timeout or transport failure | `PROVIDER_TIMEOUT` or `PROVIDER_UNREACHABLE` — attempt stays `PENDING` |

### Service flow

`tour/src/main/java/com/j2ee/tour/service/VnpayPaymentService.java`:

- `start` calls QueryDr for any owned `PENDING` attempt **before** taking the booking lock and
  before creating another attempt. This keeps the provider network call outside the
  transaction.
- Only a verified `SUCCESS`/`FAILED` answer releases the old attempt. A `FAILED` answer marks
  the attempt `FAILED`, so a new attempt and cancellation become possible.
- Every remaining `PENDING` attempt blocks a new reference with the existing actionable
  conflict message. Attempt URLs are never reopened.
- Remaining balance below `VnpayGateway.MINIMUM_AMOUNT` is rejected with HTTP `409` before
  any `PENDING` row is written.
- A verified QueryDr `SUCCESS` uses the same `applyVerified` settlement path as IPN:
  booking lock, amount check, payment row, booking `PAID` recomputation, invoice settlement.
- Return, status, and the new reconcile endpoint also consult QueryDr. They can settle, but
  only when the QueryDr response is signed and matches the stored attempt.

### API

New endpoint:

`POST /api/bookings/{bookingId}/payments/vnpay/reconcile` (Bearer, owner-only)
→ `{ bookingId, reference, status, checked, reason, providerCode, providerStatus }`

- `checked=false`: no managed attempt is `PENDING`; `reason` is `null`.
- `checked=true`: `status` is the stored `PENDING`, `SUCCESS`, or `FAILED` state after the
  QueryDr attempt. `PENDING` means VNPay could not confirm either outcome.
- `reason` is a safe diagnostic such as `PROVIDER_REJECTED`, `TRANSACTION_NOT_FOUND`,
  `PROVIDER_TIMEOUT`, or `VERIFIED_SUCCESS`; `providerCode`/`providerStatus` contain only the
  documented two-digit VNPay codes. Provider text, payloads, credentials, and signed URLs are
  never returned.

`SecurityConfig` requires authentication for this path. The IPN route token, signature
model, and existing callback locking are unchanged.

## Frontend behavior

- `reconcileVnpayBooking` added to `fe-tour/lib/api.ts`; `VnpayReconcile` added to
  `fe-tour/lib/types.ts`.
- `fe-tour/lib/vnpay.ts` maps reconcile answers to clear notices:
  - success: payment confirmed and booking updated;
  - failed: VNPay confirmed nonpayment, retry or cancellation is available;
  - pending: do not pay again; retry and cancellation stay blocked, plus the safe reason
    (for example "VNPay rejected the status check (code 99)");
  - clear: nothing was waiting for confirmation.
- `components/trip-detail.tsx` adds "Check payment status" to a pending booking and to the
  cancellation-conflict panel. While VNPay still reports `PENDING`, the pay button is
  disabled in-session and the page explains why. After a verified answer the booking is
  re-read without a full-page reload, so paid/released states appear immediately.
- `components/vnpay-return.tsx` tells an unresolved traveller not to pay again, shows the
  safe reason/code, and explains where retry/cancellation will become available.

## Tests and verification

All commands below were run in an isolated copy of `tour/` against a disposable local
PostgreSQL 17.12 (`127.0.0.1:55479`), and in `fe-tour/` with its normal toolchain.

### Backend

`./mvnw package` (all tests + jar):

```text
Tests run: 390, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
Building jar: target/tour-0.0.1-SNAPSHOT.jar
```

The one skip is the pre-existing `TourApplicationTests.contextLoads` skip. The targeted
VNPay run was also green:

```text
VnpayPaymentControllerTest   11 tests
VnpayQueryClientTest         18 tests
VnpayPaymentIntegrationTests 24 tests
Tests run: 53, Failures: 0, Errors: 0, Skipped: 0
```

New/changed coverage includes:

- **Reconciliation without IPN:** `verifiedQuerySettlesWhenIpnNeverArrives`.
- **Browser return settles only through QueryDr:** `signedBrowserReturnSettlesOnlyThroughVerifiedQuery`.
- **Rejected/failed payment releases retry:** `rejectedQueryReleasesTheAttemptForRetry`,
  `verifiedDefinitiveNonpaymentAllowsNewAttempt`.
- **Cancellation only after verified nonpayment:**
  `rejectedQueryAllowsCancellationOnlyAfterReconciliation`.
- **Ambiguous/not-found/timeout protection:** `ambiguousOrUnavailableQueryKeepsAttemptProtected`,
  `notFoundTransactionStaysUnresolved`, `unfinishedTransactionStaysUnresolved`,
  `transportFailureIsAnUnavailableQuery`, `expiredUnresolvedAttemptBlocksRetriesAndFinancialMutations`.
- **Wrong attempt/amount ignored:** `queryAnswerForAnotherAttemptOrAmountIsIgnored`,
  `foreignReferenceIsRejectedEvenWhenSigned`, `foreignMerchantIsRejectedEvenWhenSigned`,
  `foreignAmountIsRejectedEvenWhenSigned`, `tamperedResponseIsRejected`.
- **Duplicate callback idempotency:** `concurrentDuplicateDelayedIpnsSettleExactlyOnce`,
  `settledAttemptAndEmptyBookingAreNotQueriedAgain`.
- **Below-minimum validation:** `belowMinimumBalanceDoesNotLeaveAProtectedAttempt`.
- **No reuse of unresolved/expired links:**
  `chargesOnlyStoredBalanceAndNeverReopensAnUnresolvedLink`.
- **Protocol:** `signsTheRequestForThisExactAttempt`,
  `signedSuccessIsAuthoritative`, `failedTransactionIsAuthoritativeNonpayment`,
  `rejectedResponseCodeIsReportedWithOnlyTheSafeCode`,
  `successWithoutTransactionNumberIsUnavailable`.
- **Safe diagnostics:** `reconciliationReportsTheActualProviderReason`,
  `unsignedProviderErrorIsReportedWithOnlyItsSafeCode`, `unsignedNotFoundStaysUnresolvedAndIsNeverAuthoritative`,
  `unsignedSuccessIsNeverAccepted`, `unsignedTransactionDataIsNotTrusted`,
  `providerTimeoutIsDistinguishedFromAnUnreachableProvider`,
  `nonStringProviderFieldsAreReportedAsMalformedWithoutEchoingThem`.

### Frontend

```text
npm test   19 files, 157 tests passed
npm run lint   clean
npx tsc --noEmit   clean
npm run build   succeeded (route table generated)
```

`next build` first stops with a Next.js security advisory for version 16.4.3 and asks for a
retry. Re-running the same command completed the build successfully (exit 0). The upgrade
recommendation still applies; see the final note.

## Manually verified / not verified

Verified locally:

- Every backend and frontend automated test above, including synthetic signed QueryDr
  requests/responses and full PostgreSQL integration.
- Production frontend build with the new reconcile UI.
- A live QueryDr call for booking 734. The request matched the contract, but VNPay returned
  the unsigned `99 Unexpected error` envelope described above; the app now reports
  `PROVIDER_REJECTED`/`99` and keeps the attempt protected.

Not verified by the assistant:

- A **successful** live Sandbox QueryDr round trip. The current Sandbox merchant/endpoint
  rejects QueryDr with `99` before validation, so a signed success/failed answer could not be
  observed live.
- Live IPN registration and reachability from VNPay to a public HTTPS backend.
- Real card/bank payment outcomes in the VNPay Sandbox UI.

These require the user's Sandbox merchant account, credentials, and a public HTTPS ingress.
No `.env` file or real credential was read or exposed.

## Manual Sandbox checklist

1. **Configure only the backend** with `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, the official
   `VNPAY_PAY_URL`, `VNPAY_RETURN_URL`, and a random `VNPAY_IPN_TOKEN` (32–128 URL-safe
   characters). Keep the secret and IPN token out of the frontend and logs.
2. **Expose the backend over HTTPS** and register exactly
   `https://<public-host>/api/payments/vnpay/ipn/<route-token>` in the VNPay Sandbox merchant
   portal. Do not use localhost for IPN. Keep the browser return URL separate.
3. **Enable QueryDr for the merchant and register the outbound IP.** In the VNPay Sandbox
   merchant portal, confirm QueryDr is enabled for this TmnCode and that the backend's public
   outbound IP is registered. Then use "Check payment status" (or call the reconcile endpoint)
   for booking 734. A healthy API returns a signed result or `91`; `PROVIDER_REJECTED` with code
   `99` means VNPay is rejecting the API before validation. The investigation showed `99` even
   for a deliberately wrong checksum and an invalid TmnCode, so if it persists, raise it with
   VNPay Sandbox support and do not let the traveller pay again.
4. **Run the frontend as a production build:** `npm run build` then
   `npm run start -- --port 3000`. Ensure `APP_CORS_ALLOWED_ORIGINS` includes the frontend
   origin. Checkout refuses to launch VNPay under `next dev`.
5. **Happy path with IPN:** create a booking with a remaining balance of at least
   `10,000 VND`, pay with the Sandbox test card, and confirm the IPN acknowledgement is
   `00`. Expect the attempt `SUCCESS`, booking `PAID` (when covered), and one paid invoice.
6. **Missing IPN:** make the IPN URL unreachable (or use a Sandbox transaction whose IPN is
   not delivered), complete payment, and open the return page. The page calls QueryDr; expect
   `Payment confirmed`, the booking to become `PAID`, and the invoice to be paid once. A
   second reload must not duplicate the payment or invoice.
7. **Failed/cancelled payment:** cancel on VNPay or use a failing test card. Expect the
   return page to report failure after QueryDr, and the booking page to allow
   "Pay with VNPay" with a new reference and cancellation. The old reference must never
   reappear as a payable URL.
8. **Ambiguous answer:** block the backend's outbound access to
   `sandbox.vnpayment.vn` while leaving IPN undelivered, then start/check payment. Expect
   `PENDING`, a clear "do not pay again" message with `PROVIDER_TIMEOUT`/`PROVIDER_UNREACHABLE`,
   and HTTP `409` for both retry and cancel. No new `PENDING` row may be created.
9. **Duplicate IPN:** replay the same signed IPN query. Expect the first response `00` and
   the replay `02`; booking and invoice stay unchanged. Do not paste the full callback URL
   (it contains the route token and signed fields) into tickets or logs.
10. **Below minimum:** create or partially pay a booking so less than `10,000 VND` remains.
    Starting VNPay must return the minimum-balance `409` and leave no attempt row.
11. **Ownership:** with a second account, call start, status, return, and reconcile for the
    first account's booking/reference. All must return `404`.

## Changed files

Backend:

- `tour/src/main/java/com/j2ee/tour/integration/VnpayQueryClient.java` (new)
- `tour/src/main/java/com/j2ee/tour/integration/VnpayHttpConfig.java` (new)
- `tour/src/main/java/com/j2ee/tour/integration/VnpayGateway.java`
- `tour/src/main/java/com/j2ee/tour/service/VnpayPaymentService.java`
- `tour/src/main/java/com/j2ee/tour/controller/VnpayPaymentController.java`
- `tour/src/main/java/com/j2ee/tour/dto/response/VnpayReconcileResponse.java` (new)
- `tour/src/main/java/com/j2ee/tour/dto/response/VnpayStatusResponse.java`
- `tour/src/main/java/com/j2ee/tour/security/SecurityConfig.java`
- `tour/src/test/java/com/j2ee/tour/integration/VnpayQueryClientTest.java` (new)
- `tour/src/test/java/com/j2ee/tour/VnpayPaymentIntegrationTests.java`
- `tour/src/test/java/com/j2ee/tour/controller/VnpayPaymentControllerTest.java`

Frontend:

- `fe-tour/lib/api.ts`
- `fe-tour/lib/types.ts`
- `fe-tour/lib/vnpay.ts`
- `fe-tour/lib/vnpay.test.ts`
- `fe-tour/lib/api.test.ts`
- `fe-tour/components/trip-detail.tsx`
- `fe-tour/components/vnpay-return.tsx`

Docs:

- `docs/api-spec.md`
- `docs/task-g2-fix-report.md` (this report)

No commit or push was performed.
