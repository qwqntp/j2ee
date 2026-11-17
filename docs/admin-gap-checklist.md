# Admin console — implementation checklist

> Created: 2026-11-17  
> Baseline: `dev@04742b24`  
> Current state: `/admin` exists and redirects to `/admin/bookings`; `/admin/bookings` and `/admin/tours` are implemented.  
> Purpose: track missing admin functionality and nice-to-have improvements. Keep this unchecked until the item is implemented and verified.

## Current admin surface

- [x] `/admin` redirects to `/admin/bookings` (`fe-tour/app/admin/page.tsx`)
- [x] Shared admin shell/navigation (`fe-tour/app/admin/layout.tsx`)
- [x] Client-side `ROLE_ADMIN` gate (`fe-tour/components/admin-gate.tsx`)
- [x] Admin account shortcut in site header (`fe-tour/components/site-header.tsx`)
- [x] Booking list with record-cash, confirm, complete and pending-cancel actions (`fe-tour/components/admin-bookings.tsx`)
- [x] Tour list/create/edit and departure create/edit/delete (`fe-tour/components/admin-tours.tsx`)
- [x] Backend admin-only route matrix (`tour/src/main/java/com/j2ee/tour/security/SecurityConfig.java`)

## P1 — most important

### 1. Dashboard and statistics
- [ ] Add backend endpoint for admin summary statistics (revenue, booking counts by status, occupancy, tour performance)
- [ ] Add `/admin` dashboard UI instead of redirecting straight to `/admin/bookings`
- [ ] Show upcoming departures with low capacity / sold-out status
- [ ] Show recent bookings and failed payments
- [ ] Show tour performance: top tours, low-selling tours, capacity utilization
- [ ] Add date-range filter for stats
- [ ] Verify dashboard numbers against SQL fixtures

### 2. Account management UI
- [ ] Add `/admin/accounts` page
- [ ] List accounts with username, email, role, status, email verification state, created date
- [ ] Search/filter by username/email/role/status
- [ ] Lock/unlock account via `status`
- [ ] Change role via `roleId`
- [ ] Edit email verification state where supported
- [ ] Reset password through admin API or trigger password-reset email
- [ ] Confirm before destructive role/status changes
- [ ] Verify non-admin cannot call account-management endpoints

### 3. Promotion management UI
- [ ] Add `/admin/promotions` page
- [ ] List active/expired promotions with usage counts
- [ ] Create promotion
- [ ] Edit promotion
- [ ] Activate/deactivate or expire promotion
- [ ] Delete promotion where safe
- [ ] Validate date range, discount, usage limit and status
- [ ] Keep customer-facing promotion behavior consistent with booking calculation

### 4. Refunds and paid-booking cancellation
- [ ] Add admin flow to cancel a `PAID` booking
- [ ] Add refund creation/approval UI
- [ ] Show remaining refundable amount per payment
- [ ] Show refund status and reason
- [ ] Prevent over-refund in UI before submit
- [ ] Revalidate booked seats and promotion usage after cancellation
- [ ] Decide and surface invoice void/refund behavior
- [ ] Send customer notification for cancellation/refund status where appropriate
- [ ] Verify full flow: cash payment → PAID → cancellation → refund → booking/payment status

### 5. Editable tour relations
- [ ] Add accommodation selector to tour form
- [ ] Add tour guide selector to tour form
- [ ] Add promotion selector to tour form
- [ ] Add destinations multi-select to tour form
- [ ] Add vehicles multi-select to tour form
- [ ] Load relation options from existing backend CRUD endpoints
- [ ] Show current relation values when editing a tour
- [ ] Prevent relation removal from wiping values silently
- [ ] Verify created/updated tour relations round-trip through the API

### 6. Cloudinary image picker/upload
- [ ] Decide between backend upload endpoint and direct frontend-to-Cloudinary signed upload
- [ ] Add image picker for Cloudinary folder `tour`
- [ ] Support pasting an external URL as fallback
- [ ] Support multiple images per tour with ordering
- [ ] Show preview, remove and reorder controls
- [ ] Configure Cloudinary env securely; do not expose API secret client-side
- [ ] Verify saved `image_url` values render in the tour detail page
- [ ] Verify anonymous/non-admin users cannot upload

## P2 — practical gaps

### 7. Booking filters and operations
- [ ] Filter bookings by status
- [ ] Filter bookings by date range
- [ ] Filter bookings by tour/departure
- [ ] Filter bookings by customer/account
- [ ] Search by booking code, username, email or phone
- [ ] Sort by date, total amount, status and booking code
- [ ] Add page-size selection and full pagination metadata
- [ ] Add payment status/history panel per booking
- [ ] Add booking note/contact details inline
- [ ] Add confirmation dialogs instead of `window.confirm`

### 8. Export and reports
- [ ] Add CSV export for bookings
- [ ] Add CSV/Excel export for revenue/payments
- [ ] Add occupancy report per departure
- [ ] Add top-tours and revenue-by-tour report
- [ ] Add date-range and status filters to exports
- [ ] Decide whether export uses backend streaming endpoint or frontend-only CSV
- [ ] Protect exports with `ROLE_ADMIN`

### 9. Cache revalidation
- [ ] Identify all Next.js cache tags for tour catalogue and tour details (`tours`, `tour-[id]`)
- [ ] Add `revalidateTag("tours")` after admin tour mutations
- [ ] Add `revalidateTag("tour-[id]")` after admin tour mutations
- [ ] Add `revalidateTag("tours")` after cancellation of a departure
- [ ] Define strategy for catalogue pages beyond administered tours
- [ ] Verify public page shows updated tour/departure data immediately after admin save/cancel

### 10. Audit log
- [ ] Define admin audit-log table/schema or reuse existing event/logging mechanism
- [ ] Record tour create/update/cancel operations
- [ ] Record departure create/update/delete/cancel operations
- [ ] Record booking status changes
- [ ] Record payment recorded and refund actions
- [ ] Record account role/status changes
- [ ] Add `/admin/audit` page with filter by actor, action, target and date
- [ ] Ensure audit records are append-only
- [ ] Redact secrets and sensitive personal data

### 11. Invoice admin UI
- [ ] Add `/admin/invoices` page
- [ ] Search invoices by booking code, customer, date, status
- [ ] Open invoice detail/print view
- [ ] Download invoice PDF or render printable HTML
- [ ] Reissue invoice where required
- [ ] Void/cancel invoice where booking is cancelled/refunded
- [ ] Link invoice from admin booking detail

### 12. Catalog management UI
- [ ] Add destinations CRUD page
- [ ] Add accommodations CRUD page
- [ ] Add travel vehicles CRUD page
- [ ] Add tour guides CRUD page
- [ ] Add tour images CRUD page
- [ ] Add customers/travelers viewer if operationally needed
- [ ] Use relation selectors from these pages in the tour form
- [ ] Prevent deletion of referenced catalog rows; show dependency message

## P3 — nice to have

### 13. Confirmation and feedback polish
- [ ] Replace browser `confirm` with project confirmation dialog
- [ ] Add toast/feedback consistency across admin actions
- [ ] Add loading/empty/error states matching public UI style

### 14. Pagination and density
- [ ] Show total pages and total records
- [ ] Add row density/compact mode for large lists
- [ ] Add sticky table header and responsive fallback for mobile

### 15. Ratings and review moderation
- [ ] Add ratings panel in admin
- [ ] Show average rating and review count by tour
- [ ] Add review visibility/moderation if business rules require it
- [ ] Link ratings to booking ownership and verified purchase state

### 16. Notification operations
- [ ] Add email resend for booking confirmation
- [ ] Show email delivery status/error where available
- [ ] Add notification templates preview for admin

### 17. Sorting and bulk actions
- [ ] Sort tours/bookings by date, price, capacity, status and total amount
- [ ] Bulk export selected bookings/tours
- [ ] Bulk status update only if audit and confirmation are implemented

### 18. Departure capacity overview
- [ ] Show remaining capacity/booked seats inline on departure form
- [ ] Add warning when lowering capacity below already booked count if backend allows/disallows it
- [ ] Show sold-out automatic state alongside manual `FULL` status

## Verification checklist

- [ ] Admin-only endpoints return `401` without token and `403` for `ROLE_USER`
- [ ] `/admin` links require admin UI access and render correct denial for non-admin
- [ ] Responsive layout works at mobile, tablet and desktop widths
- [ ] Forms validate required fields and preserve server errors
- [ ] Cache invalidation verified after admin mutations
- [ ] Export tests verify access control and CSV formatting
- [ ] Run frontend `npm run lint`, `npx tsc --noEmit`, `npm test`, `npm run build`
- [ ] Run backend admin/security tests and integration tests when database is available

## Notes

- `docs/feature-audit.md` still says there is no admin UI; it predates the current `/admin` implementation. Update or replace it after this checklist is implemented.
- Admin tour mutation currently stores image URLs as plain text. Cloudinary assets live under `ibk3z2nt/image/upload/.../tour/...`.
- Relation values carried unchanged in the tour form are intentional preservation, not full editing support.

## 2026-11-17 implementation scope

This section is the source of truth for partial delivery. The six top-level feature boxes for P1 items 1, 2, 3 and P2 items 7, 11, 12 stay unchecked because not every child bullet under those features is verified. Do not check those boxes based on task completion alone.

### Delivered by this plan

- P1 item 1: stats endpoint, `/admin` dashboard, status map, revenue/payment cards, upcoming/low-capacity/sold-out cards, `topTours`, `capacityRisks`.
- P1 item 2: `/admin/accounts`, list/search/filter, role/status/email-verification edits, locked admin safeguard, set-temporary-password flow.
- P1 item 3: `/admin/promotions`, list/filter/create/edit/delete, discount/usage/date/status fields.
- P2 item 7: booking status/date/query filters, total-pages/page-size footer, expandable booking details, payment-history drawer, admin invoice link.
- P2 item 11: `/admin/invoices` search and open/print detail via admin endpoints.
- P2 item 12: `/admin/catalog` CRUD for destinations, accommodations, travel vehicles, tour guides and tour images; 409 conflict handling.

### Deferred / unchecked

- P1 item 1: recent bookings panel; failed payments panel; low-selling tours and capacity-utilization percentage; stats date-range filter; SQL-fixture/DB verification.
- P1 item 2: none deferred by this plan after email-verification editing is included.
- P1 item 3: none deferred.
- P2 item 7: dedicated tour/departure selector; exact-account dropdown (the keyword search covers username/email/tour); traveler phone search; sort control; distinct payment-status filter.
- P2 item 11: search by booking code (`InvoiceResponse` carries `bookingId`, not booking code); date-range filter; PDF download beyond browser print; invoice void/reissue.
- P2 item 12: tour-relation selectors in the tour form (P1 item 5); customers/travelers viewer (marked `if operationally needed` and exposes personal data).
- P1 item 1 "decision on notification preview/resend" belongs to deferred notifications.
