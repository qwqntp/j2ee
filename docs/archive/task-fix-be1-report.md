# Admin data-fetching optimization — Phase 2–4 report

Date: 2026-11-22
Scope: `tour/` (Spring Boot) and `fe-tour/` (Next.js)
Status: implementation complete; all changes uncommitted; no commit or push performed.

## 1. Objective

Reduce the cost of the Admin Bookings and Admin Invoices screens by:

- returning only the scalar fields those tables render;
- moving filtering, sorting and paging to the backend;
- loading booking detail (notes, promotion, travelers) only for an expanded row;
- stopping obsolete requests and duplicate detail reads;
- keeping customer booking/invoice APIs and all payment/auth behavior unchanged.

## 2. Root causes confirmed in Phase 1

- **Bookings list:** the admin table used the customer list endpoint and mapped travelers and tour images the table never renders.
- **Invoices list:** `Invoice.promotions` was loaded per row (N+1) and the response carried unused tax/service-fee/promotion data.
- **Frontend paging:** Admin Invoices requested 100 rows and filtered only that page in the browser. Long responses exceeded the existing 30 s client timeout.
- **Invoice detail:** the detail screen could dispatch duplicate reads before an effect was known to be current.

## 3. Backend changes

### 3.1 Admin booking list projection

- New `GET /api/admin/bookings`, protected by the existing `/api/admin/**` guard.
- Response row: `id`, `bookingCode`, `tourName`, `startDate`, `endDate`, `guestCount`, `totalAmount`, `status`.
- Filters: `status`, `from`, `to`, `q`; paging: `page` (0-based), `size` (default 10); sort: allowlisted `createdAt`/`totalAmount`.
- Detail-only data stays on the existing booking detail endpoint.

Files:

- `tour/src/main/java/com/j2ee/tour/controller/AdminBookingController.java`
- `tour/src/main/java/com/j2ee/tour/service/AdminBookingQueryService.java`
- `tour/src/main/java/com/j2ee/tour/repository/AdminBookingListRepository.java`
- `tour/src/main/java/com/j2ee/tour/dto/response/AdminBookingListItem.java`
- `tour/src/main/java/com/j2ee/tour/common/AdminListPageRequest.java`

### 3.2 Admin invoice list projection

- `GET /api/invoices` now returns `AdminInvoiceListItem`: `id`, `bookingId`, `createdAt`, `status`, `totalAmount`, `paymentMethod`, `customerName`, `customerEmail`.
- `customerEmail` is retained because the admin table renders it. Phone, tax, service fee and promotions are detail-only.
- Server filters: `status` (`PAID`/`UNPAID`), `q` (booking id, customer, email, payment method); allowlisted sort; default size 10.
- Explicit matching count query; no `Invoice.promotions` join in the admin page query.
- `@BatchSize(size = 50)` added only to the promotions association so the retained full `/me` response still batches collection reads.

Files:

- `tour/src/main/java/com/j2ee/tour/controller/InvoiceController.java`
- `tour/src/main/java/com/j2ee/tour/service/InvoiceService.java`
- `tour/src/main/java/com/j2ee/tour/repository/InvoiceRepository.java`
- `tour/src/main/java/com/j2ee/tour/dto/response/AdminInvoiceListItem.java`
- `tour/src/main/java/com/j2ee/tour/model/Invoice.java`

### 3.3 Shared paging rules

`AdminListPageRequest`:

- clamps page size to the configured maximum (default 50);
- rejects non-allowlisted sort properties with HTTP 400;
- always appends a unique ID tie-breaker for stable pages;
- defaults to newest-first when no sort is supplied.

## 4. Frontend changes

### 4.1 Admin Bookings

File: `fe-tour/components/admin-bookings.tsx`

- Requests 10 rows initially; 10/20/50 selectable; pagination driven by the server envelope.
- Filters/sort/page are stored together so a reset cannot fetch stale parameters.
- Search is debounced at 400 ms; status/date/sort/size changes commit immediately and reset page 0.
- Aborts obsolete list requests; failed lists show an explicit “Try again” action.
- Row details load only on expand, through `getBooking`; no payments request on expand.
- Closing a row invalidates its in-flight detail read; a newer read wins per row.
- Detail failures are local to that row and retryable.
- Cash payment, status transitions, payment-history invalidation and permission behavior are preserved.
- After a successful write, cached detail for that booking is invalidated.

### 4.2 Admin Invoices

File: `fe-tour/components/admin-invoices.tsx`

- Sends only the visible page with server `status`, `q` and `sort`.
- Client-side filtering and the “current page only” copy were removed.
- Server totals now drive “Page X of Y” and the invoice count.
- Search debounced 400 ms; status/sort/size reset page 0 immediately.
- Aborts stale requests; explicit retry; email column retained.
- Detail link unchanged.

### 4.3 Invoice detail deduplication

File: `fe-tour/components/invoice-view.tsx`

- Both detail GETs run in parallel with an abort signal.
- Dispatch is deferred until the effect is still active after session resolution.
- Cleanup aborts obsolete reads.
- Customer detail path and the global HTTP timeout are unchanged.

### 4.4 API client and helpers

Files:

- `fe-tour/lib/api.ts` — `listAdminBookings`, new `listInvoices` contract, signals on `getBooking`, `getInvoiceByBooking`, `listMyInvoices`.
- `fe-tour/lib/types.ts` — `AdminListSort`, `AdminBookingListItem`, `AdminInvoiceListItem`.
- `fe-tour/lib/booking-list.ts` — sort validation, `createRequestTracker`, `createKeyedRequestTracker`, `createDebouncedCommit`, `resetAdminPage`.
- `fe-tour/lib/admin-invoices.ts` — invoice query builder and `All`→`undefined` mapping.

## 5. Tests added or extended

- `tour/src/test/java/com/j2ee/tour/AdminBookingListIntegrationTests.java` — 6 tests.
- `tour/src/test/java/com/j2ee/tour/AdminInvoiceListIntegrationTests.java` — 6 tests.
- `tour/src/test/java/com/j2ee/tour/common/AdminListPageRequestTest.java` — 5 tests.
- `tour/src/test/java/com/j2ee/tour/security/SecurityConfigTest.java` — admin/user/anonymous list/detail access and JSON-field assertions.
- `fe-tour/lib/booking-list.test.ts` — query, debounce, stale-request and page-reset behavior.
- `fe-tour/lib/admin-invoices.test.ts` — invoice query contract.
- `fe-tour/lib/api.test.ts` — endpoint, paging, filter/sort encoding and abort forwarding.

## 6. Verification results

### 6.1 Backend

Command: `.\mvnw.cmd test` from `tour/`

- Tests run: **417**
- Failures: **1**
- Errors: **0**
- Skipped: **1**
- Exit code: **1**

New tests inside the full run all passed:

- `AdminBookingListIntegrationTests`: 6/6
- `AdminInvoiceListIntegrationTests`: 6/6
- `AdminListPageRequestTest`: 5/5
- `SecurityConfigTest`: 46/46
- `BookingFlowIntegrationTests`: 25/25

The one failure is:

```text
VnpayPaymentIntegrationTests.lateContradictoryIpnDoesNotWaitOnTheDepartureLock
Terminal IPN should not wait on the departure lock
```

Analysis:

- The test asserts a 2-second cutoff for a late contradictory IPN.
- It fails consistently when run alone as well as in the full suite (18.07 s elapsed in the full run).
- Generated SQL locks only the `bookings` row (`for no key update of b1_0`) and never the departure row.
- The IPN path does not call the changed invoice-list code.
- The test file is unmodified; no timeout was weakened or bypassed.

This is reported as a pre-existing/environmental timing failure against the remote PostgreSQL instance, not a regression from this work.

### 6.2 Frontend

From `fe-tour/`:

| Command | Result |
|---|---|
| `npm test` | 189/189 passed across 23 files |
| `npm test -- lib/booking-list.test.ts lib/api.test.ts lib/admin-invoices.test.ts` | 31/31 passed |
| `npx --no-install tsc --noEmit` | pass (no output) |
| `npm run lint` | pass (no output) |
| `npm run build` | compiled successfully and generated 28/28 pages; on 16.4.3 the process exited 1 on the Next.js security advisory gate. Resolved by the upgrade in section 11: 16.4.6 exits 0. |

The build produced the full route table and static output. The nonzero exit is raised by Next.js 16.4.3 after a successful build:

```text
Error [SecurityFatalError]: Your version of Next.js is affected by a known security vulnerability.
```

The documented retry did not clear it.

## 7. Before/after measurements

### Phase 1 baseline (authenticated browser)

| Read | Observed |
|---|---|
| Admin Bookings, size 10 | 6.46 s / 6.54 s |
| Admin Bookings, size 20 | 16.22 s |
| Admin Invoices, size 100 | browser timeout at 30.90 s |
| Booking detail | 1.64 s / 2.88 s |
| Invoice detail | 1.61 s / 2.94 s |
| Invoice sizes 1/10/20 | unmeasured |

### After changes

- Post-change authenticated browser timings: **unmeasured**. The paired browser reported `paired-offline`, so the baseline protocol could not be repeated.
- Browser request-count acceptance, duplicate-detail trace and keyboard walkthrough: **unmeasured** for the same reason.
- No runtime timing, speedup percentage or benchmark number is claimed.

### Code-level query evidence (Hibernate statistics)

From the Phase 2 integration runs:

- Admin invoice list: at most 2 prepared statements per page for sizes 1, 10 and 20.
- Admin invoice list: 0 `Invoice.promotions` collection loads, even with two promotions per invoice.
- Admin booking list: at most 2 prepared statements per page for sizes 1, 10 and 20.

No index or schema change was made.

## 8. API contract summary

### `GET /api/admin/bookings`

Query parameters:

- `page` (0-based, default 0)
- `size` (default 10, capped at 50)
- `status` (optional)
- `from`, `to` (optional ISO dates)
- `q` (optional)
- `sort` (allowlisted, e.g. `createdAt,desc`, `totalAmount,asc`)

Response: `PageResponse<AdminBookingListItem>`.

### `GET /api/invoices`

Query parameters:

- `page` (0-based, default 0)
- `size` (default 10, capped at 50)
- `status` (`PAID`/`UNPAID`, optional)
- `q` (optional)
- `sort` (allowlisted)

Response: `PageResponse<AdminInvoiceListItem>`.

### Unchanged

- `GET /api/bookings` and all customer booking behavior.
- `GET /api/invoices/me` and customer invoice detail.
- Auth/authorization, booking lifecycle, VNPay verification, payment status rules, refund rules, invoice creation and financial calculations.
- Global frontend HTTP timeout.

## 9. Remaining concerns and gaps

1. The Next.js security advisory that blocked the 16.4.3 build was resolved by the 16.4.6 upgrade in section 11. The build now exits 0.
2. Post-change browser measurements and request-count acceptance are unmeasured because the paired browser was offline.
3. `VnpayPaymentIntegrationTests.lateContradictoryIpnDoesNotWaitOnTheDepartureLock` remains failing for the timing reason described above.
4. `docs/archive/skills.md` is an untouched, pre-existing untracked file and was not modified by this task.

## 10. Repository state

- `git diff --check`: clean.
- No stage, commit or push was performed.
- Only the scoped backend/frontend files and the plan/spec documents were added or modified.

## 11. Next.js security upgrade (follow-up)

Requested after the Phase 4 report.

- Target: **Next.js 16.4.3 → 16.4.6**.
- `experimental.agentUpgrade: "security"` added to `next.config.ts`; existing `agentFeedback`, `cacheComponents`, `partialPrefetching` and Turbopack rules preserved.
- `next` and `eslint-config-next` are pinned to 16.4.6 in `package.json`; `package-lock.json` regenerated.
- Isolated verification worktree: branch `security/next-16.4.6` at `C:\Users\phulo\.local\share\opencode\worktree\9a9a46\next-security-upgrade`.
- The same three files were applied to the main checkout so the working app is upgraded in place.
- Duplicate checks: no `next-upgrade` markers, no matching “next”/“upgrade” commits in local or remote-tracking history. The GitHub PR lookup was unavailable because `gh` is not installed; that check is reported as unavailable.
- No commit, push or PR was made, per the user constraint.

### Verification on 16.4.6

| Check | Isolated worktree (HEAD) | Main checkout (with Phase 3 changes) |
|---|---|---|
| `next --version` | 16.4.6 | 16.4.6 |
| `npm install` | exit 0 | exit 0 |
| `npm test` | 178/178 passed | 189/189 passed |
| `npx tsc --noEmit` | pass | pass |
| `npm run lint` | pass | pass |
| `npm run build` | exit 0, 28/28 pages | exit 0, 28/28 pages |

The previous `SecurityFatalError` gate no longer occurs.
