# Admin Data Fetching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Admin Bookings and Invoices fetch lightweight, correctly filtered pages without changing business or authorization behavior.

**Architecture:** Use database-paged scalar reads for each admin list and leave full detail and write paths intact. Move Booking expansion data off the main list, and drive Invoice results from server-side search and filters. Deliver and verify each module independently, with a report before moving to the next phase.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA/Hibernate, PostgreSQL, Next.js 16, React 19, TypeScript, Vitest.

**Spec:** [Admin Bookings and Invoices design and Phase 1 baseline](../specs/2026-11-22-admin-data-fetching-design.md). Draft for review; no implementation authorized in this turn.

## Global Constraints

- Do not read, print, or modify `.env` files or secrets; use existing config/env vars without echoing them.
- Do not run `git commit` or `git push`.
- Do not touch unrelated modules. Avoid schema changes unless a query plan proves an index is needed.
- Do not change auth/authorization rules, booking lifecycle, VNPay IPN verification, payment status validation, refund security, invoice creation rules or financial calculations. Payment `SUCCESS` must never be settable outside the existing verified flow.
- No fake/hardcoded runtime data. No sensitive/internal fields in new DTOs. Do not raise the frontend timeout.
- Zero-based pages, default size 10, configurable cap `app.admin.list.max-page-size=50`; no in-memory pagination or paged collection fetch joins. Keep the existing `ApiResponse`/`PageResponse` envelope.

## Review Focus

1. Tied sort keys must not cause overlaps or missing rows in a fixed dataset (Tasks 1–2).
2. Literal `%`, `_` and `\` in search must not widen results (Tasks 1–2).
3. An over-cap request on page 1 must use the clamped size for both offset and metadata (Tasks 1–2).
4. Obsolete requests must not overwrite a newer page, re-show a closed row, or duplicate same-booking detail requests (Tasks 3–4).
5. Empty filtered pages must not lose correct totals on an out-of-range page, and unavailable detail must still show the existing error (Tasks 1–4).

---

## Before Phase 2: finish the baseline when an isolated runtime is available

- [ ] On an isolated dev backend with a representative PostgreSQL dataset and approved authenticated caller, explicitly activate `dev` and pass process-only overrides `--spring.jpa.properties.hibernate.generate_statistics=true`, `--logging.level.org.hibernate.SQL=DEBUG`, and `--logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=INFO`. Ensure the process uses injected config and does not import any `.env`; do not run against a shared or production DB. Do not enable bind-parameter or result logging. If session statistics are unavailable at that logger in the resolved Hibernate version, use only the isolated process’s supported statistics logger or collect prepared-statement deltas with no concurrent traffic. Do not restart the currently running app just to do this.
- [ ] Sequentially sample each read in the baseline table, including invoice sizes `1,10,20,100`, Booking sizes `10,20,50`, Booking detail and payments, and both invoice detail paths. Use the same authenticated caller, fixture data and client cutoff before/after; do one warm-up then five trials per case. Capture HTTP status, wall time and statement count; separate endpoint data SQL from auth SQL where identifiable. Use an approved caller that does not put bearer tokens in commands or output, and do not retain headers, payloads, SQL bind values or customer data. Record a timeout as a censored result, not a successful response duration. If any run is blocked, keep that cell explicitly unmeasured and report the reason.
- [ ] For comparable post-change results, note that Bookings moves to `/api/admin/bookings`, and size 100 becomes a size-50 request by contract. Compare equal effective sizes for like-for-like, and report 100-request behavior separately. Measure browser request waterfall and time to populated table for the initial page and expanded/printable detail with the same build mode and cache settings on both revisions.

## Phase 2 — backend

Paths below are relative to `tour/` unless indicated. Java main files are under `src/main/java/com/j2ee/tour/`; Java test files are under `src/test/java/com/j2ee/tour/`.

### Task 1: Bookings admin list projection

**Files:** Create `controller/AdminBookingController.java`, `service/AdminBookingQueryService.java`, `repository/AdminBookingListRepository.java`, `dto/response/AdminBookingListItem.java`, `common/AdminListPageRequest.java`; test in `AdminBookingListIntegrationTests.java` and extend `security/SecurityConfigTest.java`. Read and reuse `repository/BookingSpecifications.java`. Keep existing Booking and Payment endpoints unchanged.

**Interface:** `AdminBookingQueryService.list(Pageable, AdminBookingFilter) -> PageResponse<AdminBookingListItem>`. `AdminBookingListRepository.findPage(Pageable, Specification<Booking>) -> Page<AdminBookingListItem>`. `AdminListPageRequest.normalize(Pageable, int maxSize, Map<String,String> allowedProperties, String defaultProperty, String idProperty) -> Pageable`. Controller receives the same status/date/search parameters as `BookingController.list`, plus Spring's `page`, `size`, `sort`; row schema and sorts are in the spec.

- [x] **RED:** Add `pagingIsStableAndCountsUseAllMatches` (25 matching bookings with tied timestamps and overlapping amount; expected page lengths `10,10,5`, 25 distinct IDs in descending tie-break order; page 3 empty with `totalElements=25`, `totalPages=3`). Add `invalidInputsAndLiteralSearch` (status `INVALID`, reversed dates, unsupported sort -> `400`; literal `%`, `_`, `\` and trimmed mixed-case search match only intended rows), `sizeClampAppliesBeforeOffset` (configured max `3`, request page `1` size `100` -> size `3`, fourth row first; default `10` in a normal config), and `listSqlDoesNotGrowWithPageSize` (sizes 1, 10, 20 against full pages -> at most 2 prepared statements each, including count and excluding auth). Flush and clear before each counted service call; use nonparallel isolated Hibernate statistics. Exercise `GET /api/admin/bookings` through the real security chain: admin allowed; normal user and anonymous denied. Assert existing owner/admin vs other-user access to Booking ID/code and that notes, promotion, traveler data and payment read remain intact.
- [x] Run `.\mvnw.cmd "-Dtest=AdminBookingListIntegrationTests,SecurityConfigTest" test` on the isolated test DB; expect missing endpoint/projection or failing assertions before implementation.
- [x] **GREEN:** Add the admin controller within the existing guard, keep `@Transactional(readOnly = true)` in the service, and reuse `BookingSpecifications.from`. Select only scalar row fields with typed Criteria and preserve optional joins; apply the same predicate to the count; use the normalized offset/limit and stable order. Use `@PageableDefault(size = 10)` on this route, map allowlisted public sort keys to entity fields, and append the ID tie-breaker before repository execution. Never load Booking/travelers/tour images as list entities. Validate the configured max is positive; no security matcher edit.
- [x] Rerun the targeted Maven command; expect new and existing assertions to pass.

### Task 2: Invoice admin list projection

**Files:** Modify `controller/InvoiceController.java`, `service/InvoiceService.java`, `repository/InvoiceRepository.java`, `model/Invoice.java`; create `dto/response/AdminInvoiceListItem.java`; test in `AdminInvoiceListIntegrationTests.java` and `security/SecurityConfigTest.java`. Reuse `AdminListPageRequest` from Task 1.

**Interface:** `InvoiceService.list(Pageable, String status, String q) -> PageResponse<AdminInvoiceListItem>`; `InvoiceRepository.findAdminPage(String status, String likePattern, Pageable) -> Page<AdminInvoiceListItem>`. DTO fields and sorts are in the spec. `/me`, both detail methods, and every write signature stay unchanged.

- [x] **RED:** Add `fullDatasetFiltersAndPaging` (11 matching invoices separated from nonmatching invoices and tied by created time; page lengths `10,1`, 11 distinct IDs, second page contains the last match, page 2 empty with `totalElements=11`, `totalPages=2`; explicit amount sort ties resolve by ID); `literalSearchAndValidation` (trimming/case, booking ID substring, customer name/email/payment matching, `%`, `_`, `\` literal, invalid status/sort -> `400`); `clampControlsOffset` (configured max `3`, page `1` size `100` starts at the fourth row and reports size `3`, normal default `10`); and `listUsesConstantSqlWithoutPromotionFetches` (full pages at 1, 10, 20 -> at most two statements and no `Invoice.promotions` collection fetches, even with multiple promotions per invoice). Assert list JSON returns the displayed `customerEmail` but not promotion IDs, phone, tax or service fee. With real security filters, test admin/non-admin/anonymous list and by-ID/by-booking detail, including 404 for an unknown ID and unchanged `/me` ownership. Assert details and `/me` still return correct promotions and amounts.
- [x] Run `.\mvnw.cmd "-Dtest=AdminInvoiceListIntegrationTests,SecurityConfigTest" test`; expect the old contract or query count to fail.
- [x] **GREEN:** Use a typed scalar JPQL projection and explicit matching count query; normalize paging before the call. Use PostgreSQL-compatible text casting for booking ID search; bind escaped `LIKE` patterns with an explicit escape char, and match nullable text fields without excluding unrelated matches. Keep `Invoice.promotions` out of the admin query entirely; use `@BatchSize(size = 50)` only on that association to batch collection reads for the retained full-response `/me` mapping. Do not alter eager mappings used on writes or rework any calculations.
- [x] Rerun the targeted command plus `.\mvnw.cmd "-Dtest=BookingFlowIntegrationTests,VnpayPaymentIntegrationTests" test`; expect no regressions. Report Phase 2 and pause before starting Phase 3.

### Phase 2 verification record

- `.\mvnw.cmd "-Dtest=AdminBookingListIntegrationTests" test`: 6/6 passed (real PostgreSQL, one Spring context, sequential).
- `.\mvnw.cmd "-Dtest=AdminInvoiceListIntegrationTests,SecurityConfigTest" test`: 6/6 and 46/46 passed.
- `.\mvnw.cmd "-Dtest=BookingFlowIntegrationTests,VnpayPaymentIntegrationTests" test`: BookingFlow 25/25 passed; VNPay 23/24. The failure is `lateContradictoryIpnDoesNotWaitOnTheDepartureLock`, whose 2-second thread cutoff is below this remote database's observed per-request latency. Generated SQL locks only `bookings` (`for no key update of b1_0`) and never the departure row, and the IPN path does not call the changed invoice-list code. Reported as environmental, not a regression; no test or timeout was changed.
- Hibernate statistics on the invoice list: at most 2 prepared statements for sizes 1, 10 and 20, and 0 `Invoice.promotions` collection loads even with two promotions per invoice.
- Admin booking list statistics: at most 2 prepared statements for sizes 1, 10 and 20.

## Phase 3 — frontend

Paths below are relative to `fe-tour/`. Keep standalone customer consumers on their original APIs.

### Task 3: Admin Bookings pagination and on-demand details

**Files:** Modify `components/admin-bookings.tsx`, `lib/api.ts`, `lib/types.ts`, `lib/booking-list.ts` and `lib/booking-list.test.ts`; use `lib/api.test.ts` for request contracts. If the async state logic needs extraction for Vitest, keep the tested helper beside `lib/booking-list.ts` and reuse it in Task 4.

**Interface:** `listAdminBookings(token: string, page: number, size: number, filters: BookingListFilters, signal?: AbortSignal): Promise<PageData<AdminBookingListItem>>`; add `sort?: AdminListSort` to `BookingListFilters`. Export `AdminListSort` from `lib/types.ts` as the four combinations of `createdAt`/`totalAmount` and `asc`/`desc`, separated by a comma. Keep shared `listBookings` unchanged. `getBooking(token, id, signal?)` remains the detail loader.

- [x] **RED:** Assert `listAdminBookings` uses `/api/admin/bookings`, zero-based page, selected size, normalized filters/sort and forwarding of the signal. Assert filter/sort/size resets to page 0 without a fetch for stale parameters; with fake timers, a burst of search input commits exactly once at 400 ms. Add async behavior checks: an older completion can't overwrite the latest result; opening then closing a row can't re-open it when its detail finishes; a detail failure is local to that row and can be retried. In browser acceptance, no booking-detail or payment requests on initial load; expand fetches that row's detail once; explicit “Load payments” is still a separate request.
- [x] Run `npm test -- lib/booking-list.test.ts lib/api.test.ts` (include any newly extracted helper test in the command); expect initial assertions to fail.
- [x] **GREEN:** Request 10 initially, with 10/20/50 selectable; drive pagination from the server envelope. Store the committed filter/sort/page together so resetting the page cannot fetch stale parameters. Add abort handling and keep an explicit list retry. Load notes, promotion and travelers only for the expanded booking via existing detail; do not fetch payments on expand. Preserve row actions, payment history invalidation and permission/status behavior. Invalidate cached expanded detail for that booking after a successful write.
- [ ] Rerun targeted Vitest; browser-check the focused scenarios and record actual request counts. (Targeted Vitest passed; browser acceptance unmeasured because the paired browser was offline.)

### Task 4: Admin Invoice pagination and detail deduplication

**Files:** Modify `components/admin-invoices.tsx`, `components/invoice-view.tsx`, `lib/{api,types,admin-invoices}.ts`, and `lib/{api,admin-invoices}.test.ts`. Replace obsolete client-only filter tests with new query behavior.

**Interface:** `listInvoices(token: string, page = 0, size = 10, filters: InvoiceListFilters = {}, signal?: AbortSignal): Promise<PageData<AdminInvoiceListItem>>`; `InvoiceListFilters = { status?: "PAID" | "UNPAID"; q?: string; sort?: AdminListSort }`. `getInvoiceByBooking(token: string, bookingId: number, signal?: AbortSignal): Promise<Invoice>`. Customers keep current `listMyInvoices` and detail behavior.

- [x] **RED:** Assert `listInvoices` sends `page=0&size=10` by default, encodes full-dataset filters/sort, and omits the UI-only `All` filter. Check page 2 → search input → exactly one page-0 request after 400 ms; status/sort resets immediately with committed search; retry repeats the same request, while stale success/error cannot overwrite the current page. In browser acceptance, an invoice detail navigation yields exactly one request per detail resource, including development effect replay; navigation away cancels obsolete reads; 404 shows the existing detail error. Check keyboard operation and empty/filter/loading state.
- [x] Run `npm test -- lib/admin-invoices.test.ts lib/api.test.ts` (include a shared helper test if modified); expect old request/filter assertions to fail.
- [x] **GREEN:** Send only the visible page; drop client-side filtering and current-page copy; keep status, server totals, pagination, a retry action and the detail link. Apply the reviewed email-display decision from the spec. For admin detail, keep the two distinct GETs in parallel; add a signal and skip obsolete effect work before either GET starts (defer dispatch until the current effect remains active, then abort on cleanup). Verify the original duplicate trace is gone rather than assuming replay explained it. Do not change the customer path or global HTTP timeout.
- [ ] Rerun focused Vitest and verify browser acceptance; report Phase 3 before full verification. (Focused Vitest passed; browser acceptance unmeasured because the paired browser was offline.)

### Phase 3 verification record

- `npm test -- lib/booking-list.test.ts lib/api.test.ts lib/admin-invoices.test.ts`: 31/31 passed (10 helper, 17 API, 4 query).
- Full `npm test`: 189/189 passed across 23 files.
- `npx --no-install tsc --noEmit`: no output (pass).
- `npm run lint`: no output (pass).
- `npm run build`: webpack compiled successfully and generated all 28 static pages, then the process exited 1 because Next.js 16.4.3 raises `SecurityFatalError` for an npm security advisory. The retry did not clear it. No build artifact or type error is present; the advisory is independent of this change. **Follow-up: upgraded to Next.js 16.4.6; the same build now exits 0 (28/28 pages), and tests/typecheck/lint still pass.**
- Browser acceptance (request counts, duplicate-detail trace, keyboard walkthrough) is unmeasured: the paired browser reported `paired-offline`. The request contracts and race guards are covered by the unit tests above; the runtime trace is an explicit gap.
- No backend file changed in Phase 3.

## Phase 4 — verify and compare

- [x] Backend (`tour/`, isolated PostgreSQL): `.\mvnw.cmd test`; record status, failures and skips. Ensure payment and VNPay regression tests are not bypassed. — Ran: 417 tests, 1 failure, 1 skipped. The only failure is the pre-existing `VnpayPaymentIntegrationTests.lateContradictoryIpnDoesNotWaitOnTheDepartureLock` 2-second thread assertion; all 6 new booking-list, 6 new invoice-list and 46 security tests passed inside the full run.
- [x] Frontend (`fe-tour/`): `npm test`, `npx --no-install tsc --noEmit`, `npm run build`, `npm run lint`; record actual outcomes. — 189/189 tests, typecheck and lint pass; build compiles and renders all 28 pages but the process exits 1 on the Next security advisory gate (see Phase 3 record).
- [ ] Repeat the baseline protocol using the same data, sample counts, cache conditions, client cutoff and logger configuration. Check new query budgets and verify before/after actual admin UI page sizes; distinguish observed results from code-level checks. Run a PostgreSQL query plan only if a measured list/count bottleneck remains; no schema change in this plan by default. — Unmeasured: the paired browser is offline, so no authenticated runtime sample was possible. Phase 2 Hibernate statistics remain the code-level query evidence.
- [x] Final report: root causes per module, actual files and rationale, measured-only before/after (show all gaps), API contract changes, exact test/build outcomes and remaining concerns. Confirm only scoped files changed with `git status --short` and `git diff --check`; do not stage, commit or push.
