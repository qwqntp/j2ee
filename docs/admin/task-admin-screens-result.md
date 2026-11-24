# Admin management screens — result

## Progress

- [x] Passengers per departure: backend
- [x] Passengers per departure: frontend (+ print + CSV)
- [x] Payments: backend
- [x] Payments: frontend (+ reconcile action)
- [x] Customers: backend
- [x] Customers: frontend
- [x] Docs: append the new endpoints to docs/api-spec.md and an entry to docs/progress.md
- [ ] Full verification: backend build + tests, frontend lint + tests + build

## Current status

## Design and implementation plan

- Follow existing admin UI, API envelopes and zero-based pagination; use new focused admin read
  controllers/services/DTOs and repositories where existing APIs lack filters or joined data.
- Passenger slice: load the departure and non-cancelled travelers without N+1 queries; show all
  requested fields and provide browser printing and safely escaped client-side CSV.
- Payments slice: filter and paginate newest-first; add server-side summary only if cheap.
  Reconciliation must reuse verified VNPay processing, authorize admins, reject non-pending
  attempts, preserve customer ownership, and avoid changes to existing method bodies. If that
  cannot be done safely, omit the button and document why.
- Customer slice: paginated name/email/phone search and profile with linked booking history;
  use existing edits only when demonstrably safe, without new destructive behavior.
- Add focused backend and Vitest tests before behavior; avoid shared databases. Then append API
  and progress docs, run safe backend tests/build and frontend lint/tests/build, and report results.
- No git commands, migrations, dependencies, SecurityConfig, pom.xml, or unrelated business-service
  changes. Shared files receive minimal additive edits. Document each touched existing file.

## Reconciliation decision

The user approved a new admin-only reconciliation endpoint and, only if unavoidable, an additive
method in the existing VNPay service. Existing methods and customer authorization remain unchanged.

## Work log

- Passenger backend: added admin-only controller, read service, scalar traveler query and DTO.
  `AdminPassengerServiceTest` passed (2 tests); database query execution is not yet verified against
  an isolated PostgreSQL instance.
- Passenger frontend: added manifest, print layout and formula-neutralized CSV; focused Vitest passed,
  TypeScript and lint passed before the final print CSS adjustment. Browser print remains unverified.
  Shared files touched: `fe-tour/lib/api.ts` and the one-line link in
  `fe-tour/components/admin-tours.tsx`.
- Payment backend in progress: read-only query and admin reconciliation controller added. A new
  additive method was necessary in existing shared file
  `tour/src/main/java/com/j2ee/tour/service/VnpayPaymentService.java` to reuse its private verified
  reconciliation path without impersonating the booking owner. No existing method body was changed.
  Four focused test classes passed (17 tests), including the existing customer controller tests.
  `AdminVnpayReconcileControllerTest` additionally passed (2 tests): admin request succeeds and
  a normal user receives 403. The database-backed query and live VNPay round trip remain unverified.
- Payments frontend: list, filters, pagination, booking links and pending VNPay action added;
  two filter tests, TypeScript and lint passed. The summary was omitted. Shared navigation file
  `fe-tour/lib/admin-navigation.ts` and `fe-tour/lib/api.ts` received additive changes.
- Customer backend: paginated name/email/phone search and paginated booking projection added;
  two focused service tests passed. Database query execution remains unverified.
- Customer frontend: search, profile and paginated bookings added, with booking/invoice links.
  Read-only: no edit/delete actions were added because safety was not established. TypeScript,
  lint and the full frontend suite passed (201 tests). Existing shared file
  `fe-tour/lib/admin-navigation.test.ts` was updated for the two navigation entries.

## Summary and limitations

- The three admin screens and their read APIs are implemented. Passenger CSV escapes CSV syntax
  and prefixes potentially spreadsheet-executable cells; printing uses the browser.
- The optional filtered payment summary is not included. Customer editing/deletion is not exposed;
  safety of existing destructive behavior was not established.
- The manifest represents recorded traveler rows only; older bookings without traveler records
  still count toward booked seats. Customer bookings are associated through the customer's account.
- No isolated PostgreSQL instance was established, so the new queries, database locking and actual
  persisted reconciliation have not been exercised. No shared/production database was contacted.
  Browser rendering/printing, real provider reconciliation, and CSV opening in spreadsheet software
  have not been manually verified. These are required follow-up checks before deployment.
- The admin reconciliation method delegates to the existing private verified VNPay flow and does
  not change the customer method; a normal-user 403, pending-provider call, non-pending rejection,
  and foreign-owner rejection are unit/MVC tested. Full database-backed idempotency and concurrent
  reconciliation tests remain outstanding.
- New read endpoints are documented in `docs/api-spec.md`. The separately approved admin POST
  reconciliation endpoint is the only new state-changing endpoint.

## Files created

- Backend controllers: `tour/src/main/java/com/j2ee/tour/controller/AdminPassengerController.java`,
  `AdminPaymentController.java`, `AdminVnpayReconcileController.java`, `AdminCustomerController.java`.
- Backend services: `tour/src/main/java/com/j2ee/tour/service/AdminPassengerService.java`,
  `AdminPaymentQueryService.java`, `AdminCustomerQueryService.java`.
- Backend repositories: `tour/src/main/java/com/j2ee/tour/repository/AdminPassengerRepository.java`,
  `AdminPaymentRepository.java`, `AdminCustomerRepository.java`.
- Backend response DTOs: `tour/src/main/java/com/j2ee/tour/dto/response/AdminPassengerManifest.java`,
  `AdminPaymentItem.java`, `AdminCustomerBooking.java`.
- Backend tests: `tour/src/test/java/com/j2ee/tour/service/AdminPassengerServiceTest.java`,
  `AdminPaymentQueryServiceTest.java`, `AdminCustomerQueryServiceTest.java`,
  `AdminVnpayReconcileTest.java`, and
  `tour/src/test/java/com/j2ee/tour/controller/AdminVnpayReconcileControllerTest.java`.
- Frontend routes: `fe-tour/app/admin/departures/[id]/passengers/page.tsx`,
  `fe-tour/app/admin/payments/page.tsx`, `fe-tour/app/admin/customers/page.tsx`,
  `fe-tour/app/admin/customers/[id]/page.tsx`.
- Frontend components: `fe-tour/components/admin-passengers.tsx`, `admin-payments.tsx`,
  `admin-customers.tsx`, `admin-customer-detail.tsx`.
- Frontend helpers/tests: `fe-tour/lib/admin-passengers.ts`, `admin-passengers.test.ts`,
  `admin-payments.ts`, `admin-payments.test.ts`, `admin-customers.ts`.
- This report: `docs/admin/task-admin-screens-result.md`.

## Existing files modified — merge-conflict attention

- **Shared, concurrent-work hotspot:** `tour/src/main/java/com/j2ee/tour/service/VnpayPaymentService.java`
  — one new additive `reconcileAdminPayment` method; existing method bodies unchanged.
- **Shared, concurrent-work hotspot:** `fe-tour/components/admin-tours.tsx` — one passenger link.
- **Shared:** `fe-tour/lib/api.ts` — additive typed API calls.
- **Shared:** `fe-tour/lib/admin-navigation.ts` — two additive navigation entries.
- **Shared:** `fe-tour/lib/admin-navigation.test.ts` — updated expected navigation entries.
- **Shared docs:** `docs/api-spec.md`, `docs/progress.md` — appended documentation.
- No git commands, migrations, dependency changes, `SecurityConfig`, `pom.xml`,
  `PaymentService`, `BookingService`, `RefundService`, dashboard or existing controller changes.

## Manual test guide

Use an isolated database and admin account. Open Tours & departures, choose a tour and select
Passengers; compare capacity and non-cancelled traveler rows, print, and inspect CSV quoting.
Open Payments; combine status/method/date/search filters, paginate, open a booking, and reconcile
a pending managed VNPay attempt using test credentials. Confirm unknown provider results stay pending
and normal users receive 403. Open Customers, search each field, open a customer and navigate their
bookings and invoices. Test empty, error, mobile and keyboard states.

## Verification

- Backend: `.\mvnw.cmd "-Dtest=!*IntegrationTests,!TourApplicationTests" package` — success,
  321 tests, 0 failures/errors; database integration tests deliberately excluded.
- Frontend: `npm.cmd run lint` — success; `npm.cmd test` — 201 tests passed;
  `npm.cmd run build` — success (includes TypeScript).
- Full verification remains unchecked because isolated database integration and browser/manual
  checks were not available; no claim is made about those paths.
