# G2 — VNPay Sandbox design

## Scope and constraints

Add VNPay hosted payment to the existing authenticated tour checkout and booking detail.
Preserve reserve-only checkout, server-owned pricing, the existing booking lifecycle, and the
existing admin/manual payment paths outside managed VNPay attempts. Do not collect card or
bank details, access `.env` files, log secrets or payment payloads, commit, or push.
Use the [official VNPay PAY 2.1.0 specification](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html)
and [response code reference](https://sandbox.vnpayment.vn/apis/docs/bang-ma-loi/).

## Backend contract

| Endpoint | Access | Purpose |
|---|---|---|
| `POST /api/bookings/{bookingId}/payments/vnpay` | Bearer; booking owner | No request body. Create or reuse an outstanding attempt; return its HTTPS Sandbox URL, merchant reference, and expiry. |
| `POST /api/payments/vnpay/return` | Bearer; booking owner | Verify the browser's signed provider fields from a bounded JSON body, preserving duplicate-field detection. Return the attempt status from the database, with a safe display hint about the provider return. Never write. |
| `GET /api/payments/vnpay/status/{reference}` | Bearer; booking owner | Read the stored attempt status by opaque reference. Never write. Supports safe rechecks after the browser URL has been cleaned. |
| `GET /api/payments/vnpay/ipn/{routeToken}` | No app bearer; registered route credential plus signed VNPay fields | Process provider notification and reply with the provider's JSON acknowledgement, not `ApiResponse`. |

Missing or foreign bookings/attempts return `404`. Ineligible bookings or attempts that cannot be
restarted safely return `409`. Missing or invalid Sandbox configuration fails closed; invalid
browser-return parameters never grant access to booking information. Private and provider
responses are not cacheable.

## Payment creation and storage

- Reuse `payments`: one row per attempt, `VNPAY/PENDING` at initiation, and an opaque, unique
  alphanumeric application-owned reference in `payment_provider_txn_id`. Use the existing
  unique index. The initial design needs no migration; existing amount and creation time
  are enough to validate callbacks and derive the expiry. Protect the managed reference
  namespace from legacy admin create/update/delete and generic callback routes.
- Acquire the booking write lock before inspecting or creating the payment attempt. Require an
  owned, payable `PENDING` booking. Derive the positive outstanding amount from the database
  total less successful payments. Encode it exactly as amount × 100, with no rounding and no
  overflow of VNPay's 12-digit field. Do not change capacity or price on initiation.
- Sign the sorted, form-encoded VNPay 2.1.0 fields with HMAC-SHA512. Set VND, `pay`, a generic
  order description, the resolved request IP, the configured return URL, and GMT+7 creation
  and expiry timestamps. Use a 15-minute window; the same attempt always has the same amount,
  reference, and expiry. VNPay, not this app, collects payment details.
- Reuse an open attempt rather than generating a second reference. Once its URL expires, do not
  issue a new attempt until a verified IPN establishes a terminal nonpayment outcome. Local
  clock expiry, browser cancellation, or a missing callback is not proof that funds did not move.
  Do not let customer cancellation or booking edits invalidate an unresolved attempt. Serialize
  other payment paths that could change the balance against the same booking lock; do not accept an
  alternative payment while a managed attempt can still settle. This does not change behavior
  for bookings without such an attempt.

## IPN verification and payment finalization

1. Require a dedicated ingress credential before accepting any settlement. The browser return
   and IPN use the same signature scheme and fields; a valid return is not proof that the request
   arrived through VNPay's server-notification channel. VNPay documents a standard HTTPS GET
   to a merchant-supplied URL, not custom headers. Register the high-entropy path
   `/api/payments/vnpay/ipn/{routeToken}` at the public HTTPS ingress and forward the path and
   query as-is without logging either. Backend validation fails closed for a bare path or an
   unset or incorrect credential.
   This requires one additional deployment-only value, `VNPAY_IPN_TOKEN`; do not reuse the
   hash secret or expose the ingress route or token to the frontend. Do not log the ingress
   path, signatures, or provider query string. Protect direct backend access accordingly.
2. Validate the HMAC in constant time, canonicalize a bounded set of `vnp_` parameters while
   rejecting duplicates, and use only verified fields. Require the configured merchant code,
   the known managed reference, the exact amount stored for the attempt, correctly formed
   response and transaction statuses, and a valid provider transaction number for success.
   Read-only return verification uses the same field and signature checks, but not the IPN
   ingress credential.
3. Serialize processing on the booking lock, then reread and check the attempt while holding it.
   The departure cancellation cascade uses the same booking lock before it checks for payments
   to refund. IPN does not acquire the departure lock.
   Success requires both status codes `00` and a booking that can still accept the exact outstanding
   amount. Record the existing row as `SUCCESS`, set the paid timestamp, and reuse the payment
   sum and invoice logic to mark the booking `PAID`. Create a paid invoice or lock and transition
   an existing unpaid invoice; do not overwrite an already-paid invoice. Commit all updates
   together. Only the IPN can move a managed attempt to success.
4. Definitive provider nonpayment (including cancellation `24` and expiry `11`) marks the
   attempt `FAILED` without paying the booking or generating an invoice. A potentially charged
   or ambiguous outcome (for example response `07`, conflicting status fields, or generic
   unknown error) stays unresolved. If a success arrives late and the booking cannot accept
   it, leave it unresolved for operator reconciliation rather than reporting it as a failure
   or opening a retry. Do not reject a delayed notification based solely on the URL expiry
   time. An automatic provider-query, refund, or seat-release workflow is out of scope.
5. Respond with HTTP 200 and the exact `RspCode`/`Message` JSON keys for IPN outcomes:
   `00` applied, `02` already processed, `01` unknown reference, `04` invalid amount,
   `97` invalid signature or untrusted request, `99` malformed/unresolved data or processing
   failure. An applied acknowledgement is sent only after the transaction commits. Duplicates
   are acknowledged without side effects after verifying the callback, reference and amount.
   A callback that conflicts with a recorded terminal result is not a duplicate; return `99`
   for reconciliation without overwriting the result.

## Frontend and session recovery

- Offer **Pay with VNPay** and **Reserve only** in the existing checkout. Create the booking
  first, then call VNPay initiation if chosen. Once booking creation succeeds, keep its ID in
  the checkout URL before starting the separate payment request so reload can resume,
  and use the booking details as the recovery path if initiation or redirect fails. Expose
  the same VNPay action on an eligible existing booking. Use synchronous in-flight guards and
  the API client's timeout; never send the booking amount or a custom redirect target.
- At `http://localhost:3000/payment/vnpay-return`, verify the signed return using the backend.
  Display success only for a recorded successful attempt. Show failure, cancellation, expiry,
  invalid return, or pending/reconciliation with distinct copy. A verified provider hint may
  explain cancellation or expiry before IPN, but must not imply retry is safe. Support bounded
  rechecks and links to verified booking details or order history. Do not expose raw provider
  fields in the rendered result, browser logs, or analytics.
- Try the existing session refresh first. If sign-in is needed, retain unverified return
  parameters only as bounded, one-time tab context in `sessionStorage` for up to 15 minutes. Navigate
  to login with only the internal return path and opaque reference, never the signed payload.
  Restore the matching context after login; remove it after successful verification, terminal
  rejection, or expiry. If storage is unavailable, preserve the reference in the internal
  return route and use the owner-scoped status endpoint; no unverified result is displayed.
  After verification, use the opaque reference for reload and status rechecks.
- The production frontend serves genuine Sandbox returns. Do not use `next dev` for
  those returns: it can log the signed query before React runs. Dev checkout disables VNPay.

## Verification and documentation

- Add synthetic protocol fixtures for canonicalization, known HMAC, signed amount/reference
  and merchant validation, altered and repeated fields, and both success codes. Exercise the
  web authorization boundaries, IPN credential and acknowledgement shape. Add integration tests
  for owned booking creation, exact payable amount, unresolved retries, failure/cancellation,
  no mutation on Return, and duplicate/concurrent IPNs with one booking/invoice transition.
- Use an isolated PostgreSQL test database for locking and invoice checks, if available. Do not
  point tests at a shared deployment. Run verification without loading real project `.env`
  files or leaking output containing secret data. Add frontend tests for return status,
  session-context recovery and API request shape, and run lint, typecheck, full test suites
  where available, and production build. Report any unavailable checks accurately.
- Update `docs/api-spec.md` and write `docs/archive/task-g2-report.md` with actual results and a manual
  checklist. Backend payment URL settings remain `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`,
  `VNPAY_PAY_URL`, and `VNPAY_RETURN_URL`. VNPay must separately register the public HTTPS
  IPN ingress address; the local browser-return URL is not an IPN address. No genuine Sandbox
  payment is implied by automated tests.
