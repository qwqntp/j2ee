# VNPay Sandbox Payment Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. The user prohibits commits and pushes; leave every task uncommitted.

**Goal:** Let a booking owner pay through VNPay Sandbox without allowing a browser return, a duplicate notification, or an unresolved earlier attempt to settle or double-charge a booking.

**Architecture:** Reuse `payments` for durable VNPay attempts and the existing booking write lock, successful-payment sum, and invoice logic for settlement. Separate the signed, owner-only browser return from the credential-gated IPN; checkout and the return page use the typed API client.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Security, Spring Data JPA/PostgreSQL, JUnit/MockMvc, Next.js 16, React 19, TypeScript, Vitest. No new dependencies.

**Spec:** [2026-11-21-vnpay-sandbox-design.md](../specs/2026-11-21-vnpay-sandbox-design.md)

## Global Constraints

- Do not read, open, print, modify, copy, or implicitly load any project `.env` file. Do not log secrets or sensitive payment data. No commits or pushes.
- Use `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_PAY_URL`, `VNPAY_RETURN_URL`, and the separately approved deployment-only `VNPAY_IPN_TOKEN`. Do not put credentials in frontend code or docs.
- Use VNPay PAY **2.1.0**, HMAC-SHA512, VND × 100 exactly, GMT+7 timestamps, and a **15-minute** payment window. The local browser return is `http://localhost:3000/payment/vnpay-return`.
- Reuse the `payments` table and its unique provider-reference index. No migration planned; do not modify applied migrations. Use PostgreSQL (not H2 or Testcontainers) for transaction/locking assertions, and never run tests against a shared or production database.
- Preserve the existing reserve-only checkout and admin payment behavior outside managed attempts. Only an authenticated ingress notification with a verified payload may finalize a managed VNPay payment.
- Work in the existing checkout unless the user requests isolation; preserve the uncommitted G1 work. Run tools that load application configuration in a temporary **dotenv-free source copy** populated from a file list filtered before copying. Use only synthetic payment settings and an isolated datasource; never reuse ambient datasource settings.

## Review Focus

1. A caller submits a legitimately signed browser return to the IPN endpoint: reject it without the separate ingress credential.
2. A timeout or expiry leaves VNPay's outcome unknown: creation must not allocate another reference and an alternate payment must not change the owed amount.
3. An already-terminal attempt receives a callback with the opposite outcome: return `99`, do not overwrite the attempt, booking, or invoice.
4. The session expires mid-return, or tab storage is disabled: the login URL keeps only the opaque reference, and the visitor can still recover the owner-scoped status.
5. Booking creation succeeds but VNPay initiation times out or returns an unexpected redirect URL: preserve the booking and do not create another reservation or navigate to an unapproved host.

---

## File map

| Area | Main files | Responsibility |
|---|---|---|
| Protocol | `tour/src/main/java/com/j2ee/tour/integration/VnpayGateway.java`, `tour/src/main/resources/application.properties` | Sandbox configuration, canonicalization, URL generation, status decoding and verification |
| Domain | `tour/src/main/java/com/j2ee/tour/service/VnpayPaymentService.java`, `PaymentService.java`, `InvoiceService.java`, `BookingService.java`, `tour/src/main/java/com/j2ee/tour/repository/PaymentRepository.java` | Attempt lifecycle, lock ordering, competing operations, shared settlement |
| Web | `tour/src/main/java/com/j2ee/tour/controller/VnpayPaymentController.java`, `tour/src/main/java/com/j2ee/tour/security/SecurityConfig.java`, `tour/src/main/java/com/j2ee/tour/dto/response/VnpayStartResponse.java`, `VnpayStatusResponse.java` | Route permissions, request/response shape, sanitized failures |
| Checkout | `fe-tour/lib/api.ts`, `fe-tour/lib/types.ts`, `fe-tour/lib/vnpay.ts`, `fe-tour/components/payment-form.tsx`, `fe-tour/components/trip-detail.tsx`, `fe-tour/app/payment/page.tsx` | API calls and guarded redirects from new or existing bookings |
| Browser return | `fe-tour/app/payment/vnpay-return/page.tsx`, `fe-tour/components/vnpay-return.tsx`, `fe-tour/lib/vnpay.ts` | Signed return, status, reauthentication and navigation |
| Docs | `docs/api-spec.md`, `docs/archive/task-g2-report.md` | API, deployment, verification evidence and manual checklist |

Task order: protocol → payment lifecycle → HTTP contract → checkout → browser return → full verification. Check the backend contract and focused Maven tests before moving to frontend work; check the two frontend paths together before full verification.

### Task 1: Implement the VNPay wire protocol

**Files:** Create `tour/src/main/java/com/j2ee/tour/integration/VnpayGateway.java` and `tour/src/test/java/com/j2ee/tour/integration/VnpayGatewayTest.java`; modify `tour/src/main/resources/application.properties`.

**Interfaces:** `VnpayGateway.requireConfigured(): void`; `createPaymentUrl(String reference, BigDecimal amount, Instant createdAt, String clientIp): URI`; `verify(Map<String, List<String>> params): VerifiedNotification`; `hasTrustedIpnToken(String supplied): boolean`. `VerifiedNotification(String reference, long amountTimes100, Outcome outcome)`; `Outcome = SUCCESS | FAILED | CANCELLED | EXPIRED | UNRESOLVED`; `VerificationException` carries only an enum reason (`INVALID_SIGNATURE`, `INVALID_AMOUNT`, `MALFORMED`), no provider values. Expose `encodeAmount(BigDecimal)` for exact integer comparison and `PAYMENT_WINDOW = Duration.ofMinutes(15)`.

- [ ] **RED:** In `VnpayGatewayTest` pin the lower-case HMAC for fixture key `protocol-test-only` and canonical payload `vnp_Amount=1000000&vnp_OrderInfo=Tour+booking&vnp_TxnRef=VNPTEST`: `34259f933e61574c37c1b89082b5926dcc4fd64520edda153c4b230f5db76c5aea6a4c2a710cbf52d9925293aac71c29338921211325ea8341fbae74c03413bc`. Use an independently signed callback fixture for valid, tampered, duplicate field, wrong merchant, malformed amount and reference, and absent signature. Assert `assertEquals(1000050L, encodeAmount(new BigDecimal("10000.50")))`; reject zero, fractional scaled values and 13 digits. With instant `2026-11-21T02:00:00Z`, assert request creation `20261121090000` and expiry `20261121091500`. Test `00/00 -> SUCCESS`, `00/02 -> UNRESOLVED`, `07/00 -> UNRESOLVED`, `24/02 -> CANCELLED`, `11/02 -> EXPIRED`, and `51/02 -> FAILED`.
- [ ] Run `.\mvnw.cmd "-Dtest=VnpayGatewayTest" test` in the isolated backend copy. Expect the missing gateway or expected protocol assertions to fail.
- [ ] Implement form canonicalization from decoded single-valued fields: bounded ASCII field names, sort `vnp_` keys, exclude `vnp_SecureHash` and `vnp_SecureHashType` from signing, skip empty optional values, URL-encode using the official Java example convention, and compare decoded digest bytes in constant time. Validate `vnp_TmnCode`, an alphanumeric `vnp_TxnRef`, amount `1..12` decimal digits and positive, both two-digit statuses, and a positive numeric `vnp_TransactionNo` of at most 15 digits for success. Use known non-charge response codes (`09`, `10`, `11`, `12`, `13`, `24`, `51`, `65`, `75`, `79`) only with terminal failed transaction status `02`; classify other combinations as unresolved. Bind settings safely via Spring placeholders. Require HTTPS `sandbox.vnpayment.vn` `/paymentv2/vpcpay.html` without query, fragment, user info, or a non-default port for the pay base and a valid configured return URI. Permit HTTP return only on localhost/loopback. Require a distinct high-entropy IPN credential before issuing an attempt or verifying settlement. Use the servlet remote IP; do not trust caller-supplied forwarding headers.
- [ ] Rerun `VnpayGatewayTest`; confirm green.

### Task 2: Persist attempts and enforce settlement invariants

**Files:** Create `tour/src/main/java/com/j2ee/tour/service/VnpayPaymentService.java`, `tour/src/main/java/com/j2ee/tour/dto/response/VnpayStartResponse.java`, `tour/src/main/java/com/j2ee/tour/dto/response/VnpayStatusResponse.java`, `tour/src/test/java/com/j2ee/tour/VnpayPaymentIntegrationTests.java`; modify `PaymentRepository.java`, `PaymentService.java`, `BookingService.java`, `InvoiceService.java` in their mapped directories; amend affected `PaymentServiceTest`/`BookingServiceTest` fixtures only where the existing path now locks first.

**Interfaces:** `start(Long bookingId, AuthPrincipal principal, String clientIp): VnpayStartResponse(String reference, URI paymentUrl, Instant expiresAt)`; `verifyReturn(Map<String, List<String>> params, AuthPrincipal principal): VnpayStatusResponse`; `status(String reference, AuthPrincipal principal): VnpayStatusResponse(Long bookingId, String reference, String status, boolean expired, VnpayGateway.Outcome returnOutcome)` (`status`: `PENDING | SUCCESS | FAILED`; `returnOutcome` null for status lookup); `applyIpn(VnpayGateway.VerifiedNotification notification): IpnCode` (`APPLIED | ALREADY_PROCESSED | NOT_FOUND | INVALID_AMOUNT | UNRESOLVED`). All entrypoints use Spring transactions; the read paths are read-only. `PaymentService.recomputeBookingStatus(Booking booking, String settlementMethod)` is shared within the service package; existing callers use the current no-method behavior.

- [ ] **RED:** With isolated PostgreSQL fixtures, assert non-owner `start` and status return `404`; `start` uses database balance (`10000.50` -> `1000050`), creates one `PENDING` row and no invoice, and two starts use the same reference. Fixed clock or aged row: expired unresolved start gives `409`, row count remains one; mock/manual/generic payments and customer cancellation/repricing cannot change the booking while unresolved. Assert a valid `verifyReturn` leaves payment and booking `PENDING`; `applyIpn` yields `INVALID_AMOUNT` for a signed wrong amount and `NOT_FOUND` for an unknown reference. Signed definitive failure/cancellation/expiry leaves booking unpaid and enables exactly one new attempt. Indeterminate `07` does not. Simultaneous independent IPN transactions on the same attempt produce `{APPLIED, ALREADY_PROCESSED}`, one payment, one `PAID` invoice, and a `PAID` booking; a contradictory notification produces `UNRESOLVED`. Assert an expired URL alone does not reject a valid delayed IPN. Verify existing unpaid invoice goes to `PAID` and already-paid invoice remains unchanged. Race success IPN against a manual success request while the attempt is pending and assert there is never a second successful payment, independent of which operation gets the lock first.
- [ ] Run `.\mvnw.cmd "-Dtest=VnpayPaymentIntegrationTests" test` against the isolated PostgreSQL database. Confirm red. If isolated PostgreSQL is unavailable, record that the DB tests have not run; compile and run applicable non-DB tests without substituting mocks for locking coverage.
- [ ] Implement managed alphanumeric references (`VNP` + 32 upper-case hex UUID digits) and repository lookups scoped to managed method and prefix. Use a scalar reference-to-booking locator before locking; reread the payment after taking the lock. Creation and callbacks serialize on the booking row. The departure cancellation cascade locks each booking before it checks for successful payments; IPN does not take the departure lock. Check pending-attempt guards before booking update/cancel acquires any departure lock; harmless note edits need not be blocked. Apply no JVM-local concurrency assumptions. Capture late ineligible success as unresolved for reconciliation.
- [ ] Route alternative payment writes through a booking-first lock and a pending-attempt guard, preserving non-VNPay behavior. Reject additional paid writes after managed settlement when the successful-payment sum already covers the amount; keep the existing mock replay response if it does not mutate state. Reserve the managed reference namespace and prevent legacy routes from mutating its rows. Reuse the sum/invoice path after marking a managed payment successful; create or promote the invoice with method `VNPAY` without modifying an already-paid invoice. Save in the same transaction; no second invoice or payment insert during callback.
- [ ] Rerun `VnpayPaymentIntegrationTests` and `PaymentServiceTest,BookingServiceTest,PaymentControllerTest` as applicable; confirm observed outcomes and preserve any unavailable verification in the report.

### Task 3: Publish and secure the backend HTTP endpoints

**Files:** Create `tour/src/main/java/com/j2ee/tour/controller/VnpayPaymentController.java` and `tour/src/test/java/com/j2ee/tour/controller/VnpayPaymentControllerTest.java`; modify `tour/src/main/java/com/j2ee/tour/security/SecurityConfig.java`.

**Interfaces:** Implement the exact four methods and paths from the spec. Return verification accepts JSON `{ "query": string }`, where `query` is the bounded serialized `vnp_` query without `?`; parse into a multimap without collapsing duplicates. The three user routes return `ApiResponse`; IPN returns `{ "RspCode": string, "Message": string }`.

- [ ] **RED:** MockMvc with real security/gateway and a controlled settlement dependency: assert `401` for unsigned-in start/return/status, `200` for a normal owner's authenticated request, and `403` for a regular user on the pre-existing admin payment routes. Submit an independently signed browser payload to the IPN route without the registered route credential; assert HTTP 200 with `$.RspCode == "97"` and no settlement. Include repeated fields, oversized return data, invalid amount `04`, unknown ref `01`, applied `00`, duplicate `02`, processing failure `99`, the capitalized keys and absence of `$.data`. Check `Cache-Control: no-store`.
- [ ] Run `.\mvnw.cmd "-Dtest=VnpayPaymentControllerTest,SecurityConfigTest" test`; confirm the new contract is red before implementing it.
- [ ] Order narrow security exceptions for the exact methods/paths before `/api/payments/**` and `/api/bookings/*/payments/**` admin rules. Enforce the registered path credential at the IPN boundary before calling the gateway, with no custom provider header. Limit serialized query length to `8192` characters and return JSON body to `16 KiB` including JSON overhead; preserve duplicate detection at both paths. Map known errors to safe generic messages (`400` invalid return, `404` foreign attempt, `409` ineligible, `503` configuration). Return the provider acknowledgement only after the transactional service call returns; catch processing/commit errors outside the transaction and return `99` without logging exception contents or payloads.
- [ ] Rerun controller and security tests; confirm green.

### Task 4: Integrate checkout and owner re-entry

**Files:** Modify `fe-tour/lib/api.ts`, `fe-tour/lib/types.ts`, `fe-tour/lib/api.test.ts`, `fe-tour/app/payment/page.tsx`, `fe-tour/components/payment-form.tsx`, `fe-tour/components/trip-detail.tsx`. Create `fe-tour/lib/vnpay.ts` and `fe-tour/lib/vnpay.test.ts`.

**Interfaces:** `startVnpay(token: string, bookingId: number): Promise<VnpayStart>`; `verifyVnpayReturn(token: string, query: string): Promise<VnpayStatus>`; `getVnpayStatus(token: string, reference: string): Promise<VnpayStatus>`; copy DTO shapes from Tasks 2–3. `sandboxPaymentUrl(value: string): string | null` validates the configured Sandbox destination before browser navigation.

- [ ] **RED:** Vitest verifies requests are owner-authenticated, URL creation sends no amount or return target, signed browser parameters go in the POST body (not backend query), and recheck uses only the reference. Assert `sandboxPaymentUrl(...)` allows canonical HTTPS Sandbox URL and rejects non-Sandbox host, user info, HTTP, or unsafe scheme. Test the checkout recovery decision on the success-create/fail-start case: preserve `bookingId=42`, and recovery path is `/account/bookings/42`, not a new booking submission.
- [ ] Run `npm.cmd test -- lib/api.test.ts lib/vnpay.test.ts`; confirm red.
- [ ] Implement the API wrappers and typed shapes. In checkout, add VNPay and reserve-only choices following existing styles. Use a sync `useRef` guard before any network request; persist the confirmed booking ID in the current checkout URL before VNPay initiation and treat payment initiation errors separately from booking errors. Reuse the existing pending booking after reload or retry and surface a clear recovery link. Add guarded VNPay continuation to owned pending booking details, relying on the backend to determine actual eligibility and unresolved status. Preserve reserve-only behavior; only call `window.location.assign` with the validated URL. Use a production frontend for genuine Sandbox redirects to avoid the Next dev request log/trace.
- [ ] Rerun the two Vitest files; confirm green.

### Task 5: Verify the browser return and survive reauthentication

**Files:** Create `fe-tour/app/payment/vnpay-return/page.tsx`, `fe-tour/components/vnpay-return.tsx`; extend `fe-tour/lib/vnpay.ts` and `fe-tour/lib/vnpay.test.ts`. Read the installed Next App Router docs for `useSearchParams`, Suspense, navigation, and metadata before code edits.

**Interfaces:** `readVnpayReturn(params: URLSearchParams): { reference: string; query: string | null } | null`; `stashVnpayReturn(storage: Storage, reference: string, query: string, now: number): void`; `restoreVnpayReturn(storage: Storage, reference: string, now: number): string | null`; `vnpayReturnLoginHref(reference: string): string`; `vnpayResultState(status: VnpayStatus): "success" | "failed" | "cancelled" | "expired" | "pending"`.

- [ ] **RED:** Test signed params survive form reserialization without folding duplicates; require a single valid merchant reference. Assert `PENDING` + `returnOutcome:"SUCCESS"` renders pending, verified cancellation/expiry retains an IPN warning, and only backend `SUCCESS` renders success. Test login redirect contains only `/payment/vnpay-return?ref=…`, storage restores just the matching unexpired entry, removes it after consumption, expires at 15 minutes, and an unavailable storage implementation leaves the reference-only route usable. Test raw payment fields are absent from redirect output.
- [ ] Run `npm.cmd test -- lib/vnpay.test.ts`; confirm red.
- [ ] Add a client display under a Suspense boundary and set a no-referrer result page policy. Use the existing session helper and share the in-flight check across Strict Mode effect replay and manual recheck. On a stale token, refresh once, retry once, then retain the short-lived tab context and navigate to login if needed. Verify first and use the reference-only owner status lookups afterwards; clean the signed query from the address bar. Render a retryable network state, distinct provider hints with no premature success, and a booking link only after the owner-scoped API returned a booking ID. Provide a manual recheck for pending/expired unresolved results.
- [ ] Rerun the return helper tests; confirm green.

### Task 6: Verify and document what actually ran

**Files:** Update `docs/api-spec.md`; create `docs/archive/task-g2-report.md`.

- [ ] From the dotenv-free backend copy, run `.\mvnw.cmd test` if a loopback-isolated PostgreSQL instance is available. Check actual run/failure/error/skip totals. If unavailable, run `.\mvnw.cmd "-Dtest=*Test" test`, confirm which DB-backed classes were excluded, and report them. Do not read raw logs if they can contain sensitive requests.
- [ ] From the dotenv-free frontend copy, run `npm.cmd run lint`, `npx.cmd tsc --noEmit`, `npm.cmd test`, and `npm.cmd run build`. Regenerate framework types in that copy if needed; verify the build actually emits `/payment/vnpay-return`.
- [ ] Update `docs/api-spec.md`: endpoint methods/auth/payloads and response states; IPN verification order and `RspCode`s; same booking lock and safe retry policy; public HTTPS IPN registration and path/query log redaction; the localhost browser return; remove contradictory historical/current claims.
- [ ] Run `git diff --check` on in-scope non-dotenv paths and inspect relevant source changes for leakage or uncommitted G1 overwrites. Use review and verification skills as applicable; no commits or pushes.
- [ ] Write `docs/archive/task-g2-report.md` with modified files, complete flow, migration decision, security choices, actual checks, and a manual Sandbox checklist (owner checkout, return/IPN ordering, cancelled/expired, unauthorized/invalid and duplicate, expired session, invoices, missing IPN/reconciliation). State explicitly whether an actual Sandbox payment was performed.
