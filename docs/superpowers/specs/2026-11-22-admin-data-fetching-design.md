# Admin Bookings and Invoices data fetching

Draft for review. The user requested a read-only Phase 1 followed by a plan, not implementation.

## Phase 1: Bookings

- **List:** `BookingController.list` → `BookingService.list` → `BookingRepository.findAll(spec, pageable)` → `Booking` → `BookingResponse.from` → `lib/api.ts:listBookings` → `app/admin/bookings/page.tsx` → `AdminBookings`. Pagination and status, inclusive date range, and search are already SQL-backed via `BookingSpecifications`. There is no in-memory paging or client-side data filter. The default order is `bookingCreatedAt DESC, bookingId DESC`, but an explicit sort does not get a tie-breaker. The shared controller rejects sizes above 100.
- **Root cause found in code:** List mapping initializes `Booking.travelers` per booking despite travelers being shown only on expansion. The query also loads default-eager to-one associations (account/role, departure/tour and optional promotion); mapping touches a tour image collection merely to select its first image. `Tour.tourImages` already has batch fetching, and the actual statement count for all of this was not measured. Photos, payment-breaking-down amounts, and the rest of the detail shape are not needed for the table. No per-row repository call is made explicitly by the mapper.
- **Detail:** `GET /api/bookings/{id}` → `BookingService.get` → `BookingRepository.findById` → owner-or-admin check → `BookingResponse.from` → `lib/api.ts:getBooking` → admin `InvoiceView` and customer detail consumers. The admin Booking table currently expands locally from list data; the explicit “Load payments” button separately calls `PaymentController.list` → `PaymentService.findByBooking` → `PaymentRepository.findByBookingBookingId` → `PaymentResponse`. No evidence of one payment request per list row. The service's booking-code detail follows the same ownership and DTO path.

## Phase 1: Invoices

- **List:** `InvoiceController.list` → `InvoiceService.list` → `InvoiceRepository.findAll(pageable)` → `Invoice` → `InvoiceResponse.from` → `lib/api.ts:listInvoices` → `app/admin/invoices/page.tsx` → `AdminInvoices`. The backend pages in SQL already, but does not apply admin search/status or specify a stable default order.
- **Root cause found in code:** `InvoiceResponse.from` walks the lazy, unbatched `Invoice.promotions` collection for every invoice to get promotion IDs (N+1 pattern); the eager `Invoice.booking` association can also pull in Booking's to-one graph. The table uses neither promotions nor phone, discount, tax or service fee. It requests up to 100 rows immediately and filters only those rows by status or case-insensitive substring across booking ID, customer name/email and payment method. The precise time attributable to each of these causes was not measured.
- **Detail:** `InvoiceController.getByBooking` → `InvoiceService.getByBooking` → `InvoiceRepository.findByBookingBookingId` → `Invoice` → full `InvoiceResponse.from` → `lib/api.ts:getInvoiceByBooking` → admin invoice route `[bookingId]` → `InvoiceView`. The view also calls `getBooking` in parallel for tour/line-item data; these are different resources, not an inherently duplicate request. A single development navigation sent each request twice for the same booking. The effect ignores late results but does not abort or share requests; the exact runtime trigger was not isolated. `GET /api/invoices/{id}` uses `findById`, same DTO, and has no direct frontend consumer found. The owner-scoped `/api/invoices/me` uses the same full mapper; the public invoice view's 100-row lookup stays outside the admin frontend change.

## Baseline

Chrome authenticated resource timings, GET requests only; one observed size-20 load and two size-10 requests, no cold/warm normalization or percentile claim. Browser durations include transport and are not server processing times. No response payloads, account identifiers or credential values are needed in the report.

| Surface | Page size | Browser observation | Prior user report | SQL statements |
|---|---:|---|---|---|
| Bookings page | — | End-to-end render not measured | ~13.8 s | — |
| `GET /api/bookings?page=0` | 10 | 6,460 / 6,539 ms (200) | — | Unmeasured |
| `GET /api/bookings?page=0` | 20 | 16,219 ms (200) | — | Unmeasured |
| `GET /api/bookings/{id}` | — | 1,641 / 2,879 ms (200, overlapping) | — | Unmeasured |
| `GET /api/bookings/{id}/payments` | — | 1,013 ms (200) | — | Unmeasured |
| `GET /api/invoices?page=0` | 1 | Unmeasured | ~2 s | Unmeasured |
| `GET /api/invoices?page=0` | 10 | Unmeasured | ~7.95 s | Unmeasured |
| `GET /api/invoices?page=0` | 20 | Unmeasured | — | Unmeasured |
| `GET /api/invoices?page=0` | 100 | Browser aborted after 30,898 ms; timeout alert, no HTTP response captured | Timeout | Unmeasured |
| `GET /api/invoices/by-booking/{bookingId}` | — | 2,943 / 1,613 ms (200, overlapping) | — | Unmeasured |
| `GET /api/invoices/{id}` | — | Unmeasured | — | Unmeasured |

Hibernate statistics/SQL logging were not enabled on the existing backend. No datasource configuration was inherited in the shell for a separate dev process, and the authenticated UI could not vary invoice page size. No service was restarted, no tokens were extracted, and no `.env` files were accessed. The plan documents how to fill those gaps on an isolated dev instance before modifying queries.

## Proposed read contracts

- **Bookings:** Add `GET /api/admin/bookings` behind the **existing** `/api/admin/**` guard. Preserve shared `GET /api/bookings` (owner/admin access and response shape) and all details/writes. The admin row has `id`, `bookingCode`, `tourName`, `startDate`, `endDate`, `guestCount`, `totalAmount`, `status`. Fetch existing `GET /api/bookings/{id}` only for an expanded row; keep notes, promotion, travelers and management actions available, and keep payments explicitly on demand. Search remains booking code/account username/account email/tour name, using existing literal escaping and date/status behavior.
- **Invoices:** Change admin-only `GET /api/invoices` to a scalar projection of `id`, `bookingId`, `createdAt`, `status`, `totalAmount`, `paymentMethod`, `customerName`, `customerEmail`. The admin table displays customer email, so it stays in the list DTO and remains searchable server-side. Only `components/admin-invoices.tsx` consumes `listInvoices`; no dashboard or stats page relies on the old 100-row request. Keep invoice details, `/me`, and write DTOs intact. Do not query promotions on the admin list; batch-fetch `Invoice.promotions` for the retained full-response mapping, which still uses the established promotion ID semantics. This avoids a second query on the admin list just to get data the admin table cannot use.
- **Shared admin-list rules:** Zero-based `page`; explicit default `size=10`; module-scoped configurable cap `app.admin.list.max-page-size=50` by default (clamp before computing the SQL offset). Keep `ApiResponse<PageResponse<…>>` with `content`, `page`, `size`, `totalElements`, `totalPages`, `last`, `hasNext`, `hasPrevious`. No in-memory pagination or collection fetch joins. Allow `sort=createdAt,asc|desc` and `sort=totalAmount,asc|desc`, default `createdAt,desc`; append `id DESC` as a unique tie-breaker. Reject unknown sorts, bad status or invalid booking date ranges with 400. DB search is trimmed, case-insensitive literal substring; invoice `PAID` and `UNPAID` are exact filter values.
- **Admin UI:** Use actual page sizes (Invoices 10; Bookings defaults to 10 with existing 10/20/50 choices), one active request per committed query, and reset to page 0 on filters/sort/page size. Debounce search by 400 ms; keep a booking form submit as an immediate apply and cancel its pending timer. Preserve loading, errors, retry and write behavior. Reuse or abort superseded requests without changing session/auth logic or the global HTTP timeout.

**Alternative considered:** Modify the shared Booking list and migrate customer consumers. That widens the scope and risks dropping details those pages use. Adding a scalar admin read behind the existing guard keeps that contract unchanged. For invoices, batching promotions alone would still fetch unnecessary entities, return unused fields and leave current-page filtering incorrect.

## Constraints and acceptance

- Do not read, print, or modify `.env` files or secrets; use existing config/env vars without echoing them.
- Do not run `git commit` or `git push`.
- Do not touch unrelated modules. Avoid schema changes unless a query plan proves an index is needed.
- Do not change auth/authorization rules, booking lifecycle, VNPay IPN verification, payment status validation, refund security, invoice creation rules or financial calculations. Payment `SUCCESS` must never be settable outside the existing verified flow.
- No fake/hardcoded runtime data. No sensitive/internal fields in new DTOs. Do not raise the frontend timeout.
- Verify authorization, all detail endpoints, full-dataset filters/sort, stable paging, totals, cap and page-size-independent list SQL counts. Run backend tests, FE tests/type check/build. Re-measure with the same method and report measured results separately from code-level verification.
