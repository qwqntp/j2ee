# Staff role implementation — progress and result

## Progress checklist

- [x] 1. Analysis of current role handling written to this file
- [x] 2. Migration seeding the three roles
- [x] 3. Backend authorization matrix and JWT authorities
- [x] 4. Role × route backend tests
- [x] 5. `GET /api/admin/departures` and tests
- [x] 6. Frontend gate, role-aware navigation, page guards and login redirect
- [x] 7. Staff departures page
- [x] 8. Hide admin-only actions for staff and check account role assignment
- [x] 9. Documentation updates
- [x] 10. Full verification: backend build and database-free tests; frontend lint, tests and build

## Approved short specification

Keep the single-role JWT model and existing customer/admin behavior. Staff may operate bookings
(read/filter, confirm, complete, cancel unpaid PENDING only), record successful CASH payments,
read payments and reconcile managed pending VNPay payments, read customer profiles/history,
read/print invoices, and read departures/passengers. Use exact HTTP method/path exceptions before
the admin-only fallback; all unlisted admin routes and all management/refund/report operations
stay admin-only. Staff non-PENDING cancellation and forbidden payment methods return 403.
Add an idempotent next-version role migration without changing old migrations or dev accounts.
Add a departures list in new controller/service/DTO/repository files: one joined SQL query,
accurate totals even for empty pages, upcoming-only default, status/name filters, nearest upcoming
first (all-dates mode: newest start first), stable pagination and passenger link IDs.
Frontend uses validated admin/staff access, an explicit page allow-list, five staff navigation
entries and role-specific login destinations for password/Google login. Keep shared business-file
edits operation-specific and additive. Preserve the last-admin lock guard; minimally block
self-demotion and renaming the admin role. No create-for-customer, dependencies, git commands,
shared/production DB access or unrelated refactors. Verify with database-free backend tests,
backend package, frontend lint/tests/build; document unverified live database/browser behavior.
User approved direct implementation on 2026-11-25; no separate spec/plan review gates.

## Workflow status

2026-11-25: implementation and requested verification complete, with live-database/provider
limitations below. No git commands, database connections, backend startup or migrations were run.
Browser checks used a production frontend with an isolated local fixture API, not the backend.
The user's access matrix takes precedence over the older audit's proposed design.

## Final summary

Three distinct groups are now enforced by backend HTTP security and reflected by frontend gates,
navigation, page guards and both login redirects. Staff gains only the requested operational
capabilities. Unlisted admin endpoints remain admin-only, including future controllers.
New read-only departures API/page uses a joined scalar query with count and page in one statement.
Migration version: **V10**, `V10__seed_account_roles.sql`; no existing migrations or dev seed edited.
No dependencies added. Only `BookingService` among the six parallel-owned services/pages changed,
with exact methods listed below. No create-for-customer or refund/report implementation added.

## Analysis — role handling at task start

- `SecurityConfig.java` uses ordered HTTP matchers, with `ROLE_ADMIN` required for
  `/api/admin/**` and protected CRUD resource prefixes. No staff allow-list exists.
- `BookingService.java` separately scopes booking reads and actions to owner or admin.
  Merely opening HTTP routes cannot grant staff operational access. Globally treating staff
  as admin would also widen update/delete and booking creation semantics, so must be avoided.
- `PaymentCrudController.java` delegates generic payment creation to `PaymentService.create`;
  opening its POST without checking the body would allow staff to record non-cash payments.
- `AccountService.java` locks active admin rows and prevents removing the last active admin.
  It does not identify the caller, so self-demotion remains possible when another admin exists.
- Highest migration inspected: `V9__google_sign_in.sql`; next version is currently V10.
  Re-check the migration directory immediately before creating the new migration.
- `AdminPassengerController`, `AdminPaymentController`, `AdminCustomerController` and
  `AdminVnpayReconcileController` already exist. The older audit's missing-passenger and
  missing-payment/customer-screen findings do not describe this branch.
- `AdminGate` checks both the validated session and stored account for `ROLE_ADMIN`.
  Navigation is currently unfiltered, and password login follows `next` or `/tours`
  without a role-specific destination.

### Authentication chain

One role per account: `accounts.role_id` → `account_roles.role_name` →
JWT `role` claim → `AuthPrincipal.role()` → `SimpleGrantedAuthority(principal.role())`.
`JwtService.issue/parse` and `JwtAuthenticationFilter` carry arbitrary role strings
without translating or restricting them. No JWT schema change is needed for staff.
`AuthService.login` and refresh issue tokens using the account's stored role.
`GoogleAuthService` keeps the stored role when signing into or linking an existing account;
only newly created Google accounts use `ROLE_USER`. Add regression tests rather than
rewriting these production paths.

No method-security annotations were found on the controllers inspected; route authorization
is centralized in `SecurityConfig`, with ownership/state rules in services.
Current `ROLE_STAFF` accounts have customer-style ownership restrictions, not operational
access. Existing customer behavior includes owner-scoped booking lifecycle operations.

### Existing route groups and staff gaps

| Existing routes | Current enforcement | Required change |
|---|---|---|
| `GET /api/admin/bookings` | Admin matcher; scalar list projection | Open exact GET to staff |
| `GET /api/bookings`, `/{id}`, `/code/{bookingCode}` | Authenticated; service owner/admin | Staff may read operational bookings without gaining generic write privileges |
| `POST /api/bookings/{id}/confirm`, `/complete`, `/cancel` | Authenticated; service owner/admin; locked state checks | Staff bypass only ownership for these operations; non-PENDING staff cancellation must return 403 |
| `POST /api/bookings`, `PUT/DELETE /api/bookings/{id}` | Authenticated; service owner/admin; only admin may set owner/status | Do not grant staff generic creation/edit/delete or status-update privileges |
| `GET /api/admin/payments` | Admin only | Open exact GET |
| `POST /api/admin/payments/{id}/reconcile` | Admin only; delegates to `VnpayPaymentService.reconcileAdminPayment` | Open exact POST; preserve managed-pending-only provider verification |
| `GET /api/bookings/{id}/payments`, `GET /api/payments`, `/{id}` | Admin only | Open explicit payment reads, not refund subpaths |
| `POST /api/payments` | Admin only; accepts method and status | Cash-only staff request validation; do not open arbitrary methods or statuses |
| `PUT/DELETE /api/payments/{id}`, signed callback, refunds | Admin only | Keep closed to staff |
| `GET /api/admin/departures/{id}/passengers` | Admin only | Open exact GET; existing manifest page already supports print/CSV |
| `GET /api/admin/customers`, `/{id}/bookings`; `GET /api/customers/{id}` | Admin only | Open exact read routes used by customer list/detail |
| `GET /api/invoices`, `/{id}`, `/by-booking/{bookingId}` | Admin only | Open exact read routes; keep invoice mutations admin-only |
| `GET /api/invoices/me` | Authenticated; own invoices | Preserve unchanged |
| Stats, admin tours, departures CRUD, catalog, promotions, accounts, roles | Admin only | Keep closed to staff |
| Any unlisted `/api/admin/**` method/path | Admin only | Preserve fallback after staff allow-list |

The new `GET /api/admin/departures` is absent. `TourDeparture` already stores capacity and
booked slots; remaining is capacity minus booked slots. A join to `tours` supplies tour name.
Existing admin query repositories use EntityManager projections and generally a separate count
query; the new endpoint must honor the stricter one-query requirement instead of copying that
two-query pattern. Pagination must retain accurate totals on empty/out-of-range pages too.

### Frontend integration points

- `components/admin/admin-layout.tsx` renders page children inside the workspace and owns
  both desktop and drawer navigation. A centralized path allow-list can block forbidden
  children before they mount without editing the dashboard or tours page.
- `lib/admin-navigation.ts` defines every navigation entry, but has no roles. Staff must see
  exactly Bookings, Payments, Customers, Invoices and Departures. Passenger and customer/invoice
  detail paths must also pass the page guard; misleading prefixes and unknown admin paths must not.
- `components/site-header.tsx` has separate desktop/mobile admin links; both currently check
  only `ROLE_ADMIN` and link to the dashboard.
- `components/auth-form.tsx` and `components/google-signin-button.tsx` both navigate to `next`
  without role logic. Redirect handling must cover both login methods and avoid sending staff
  to the dashboard via `next=/admin`.
- **Important:** booking console buttons call `lib/api.ts:updateBookingStatus`, which sends
  generic `PUT /api/bookings/{id}`. Staff must use the existing dedicated confirm/complete/cancel
  POSTs instead; opening generic PUT to staff would permit unintended mutations.
- Customer list/detail currently expose only view actions. Booking console currently offers
  PENDING cancellation, confirm and complete, but no refund or PAID cancellation controls.
  Do not invent those parallel-owned features; ensure any such controls present at edit time
  are admin-only. Staff cannot mount the tours/departure editing page.
- `components/admin/admin-account-editor.tsx` receives DB-driven role options and submits role
  IDs. `ROLE_STAFF` will be assignable after seeding; add an English Staff label and verification.
- Existing Vitest tests cover navigation, auth session handling and admin session revalidation.
  No test directly referencing `AdminGate` was found in the inspected frontend sources.
  Add gate/guard coverage using existing React/Vitest tools, without new dependencies.

### Migration and demo data

The real role table is `public.account_roles(account_role_id, role_name)`.
The bootstrap callback does not declare `role_name` unique, so an `ON CONFLICT (role_name)`
clause is not portable to fresh databases. Use `INSERT ... SELECT ... WHERE NOT EXISTS`
for each of `ROLE_USER`, `ROLE_ADMIN`, `ROLE_STAFF` (or an equivalent guarded insert),
without changing old migrations or adding accounts to the migration.

The existing dev seed already contains:

| Dev-only username | Email | Role | Password |
|---|---|---|---|
| `demo_admin` | `admin@example.com` | `ROLE_ADMIN` | `demo1234` |
| `demo_staff` | `staff@example.com` | `ROLE_STAFF` | `demo1234` |
| `demo_user1` | `demo1@example.com` | `ROLE_USER` | `demo1234` |

Login accepts username, not email. Keep these existing rows; no duplicate staff account or seed
rewrite is needed. Document credentials explicitly as development-only in `tour/README.md`.

### Account protection gaps

`AccountService.assertNotLastActiveAdmin` already locks active admins and blocks demoting,
disabling or deleting the last one. Preserve it. Add only the missing caller-aware self-demotion
protection, plus tests that assigning staff to another non-final-admin account succeeds.
`AccountRoleService.update` also permits renaming the `ROLE_ADMIN` role itself, which bypasses
account-update guards; the design must cover that minimal role-management bypass as well.

### Verification commands and safety

CI (`.github/workflows/ci.yml`) excludes `*IntegrationTests` and `TourApplicationTests`.
The test profile explicitly inherits the application datasource; therefore **do not run the full
Maven test suite or integration tests** against the configured environment.

Planned safe commands (not executed yet):

```powershell
# working directory: tour
.\mvnw.cmd "-Dtest=!*IntegrationTests,!TourApplicationTests" test
.\mvnw.cmd -DskipTests package

# working directory: fe-tour
npm run lint
npm test
npm run build
```

Use new WebMvc/Mockito tests for authorization, JWT and query invocation/mapping.
Do not claim that mocked query tests prove PostgreSQL execution, locking or migration
idempotency. Real SQL/migration verification requires an explicitly isolated disposable
PostgreSQL database; if unavailable, list that limitation in the final result.

## Constraints and verification boundaries

- No new dependencies; no git commands.
- No shared or production database access. Do not start the backend with its normal datasource.
- Preserve existing customer and admin behavior.
- Staff access must be an explicit method/path allow-list; unlisted `/api/admin/**` routes
  remain admin-only, including future reports, refunds and image upload.
- Keep shared edits small. Do not refactor `BookingService`, `RefundService`, `PaymentService`,
  `AdminStatsService`, the admin tours page or dashboard.
- Tests must follow the existing JUnit/Mockito/WebMvc and Vitest conventions.
- Track existing files changed and flag shared files in the final result.

## File inventory

Every existing file intentionally edited is listed below. **Shared** flags merge-sensitive files;
no git commands were used to inspect or modify the user's branch.

### Existing backend files modified

| File | Change | Shared? |
|---|---|---|
| `tour/src/main/java/com/j2ee/tour/security/SecurityConfig.java` | Method/path staff exceptions; numeric resource IDs; preserved admin fallback; staff generic booking writes blocked | **Yes** |
| `tour/src/main/java/com/j2ee/tour/service/BookingService.java` | Operation-specific staff reads/actions, non-PENDING cancel 403; exact methods below | **Yes — parallel-owned** |
| `tour/src/main/java/com/j2ee/tour/controller/PaymentCrudController.java` | `create`: staff CASH/SUCCESS authorization before unchanged service | **Yes — payment API boundary** |
| `tour/src/main/java/com/j2ee/tour/controller/AccountController.java` | `update/delete`: forward authenticated caller to guard overloads | No |
| `tour/src/main/java/com/j2ee/tour/service/AccountService.java` | New caller-aware `update/delete` overloads; existing last-admin methods unchanged | No |
| `tour/src/main/java/com/j2ee/tour/service/AccountRoleService.java` | `update/delete`: protect canonical admin role name | No |
| `tour/src/test/java/com/j2ee/tour/service/BookingServiceTest.java` | Staff operational/forbidden cancellation and generic-write-scope tests | **Yes — booking tests** |
| `tour/src/test/java/com/j2ee/tour/security/JwtServiceTest.java` | Staff signed-token round-trip | No |
| `tour/src/test/java/com/j2ee/tour/service/AuthServiceTest.java` | Staff password login and refresh regression | No |
| `tour/src/test/java/com/j2ee/tour/service/GoogleAuthServiceTest.java` | Existing staff Google-account role regression | No |
| `tour/src/test/java/com/j2ee/tour/service/AccountServiceTest.java` | Self-protection and assigning Staff to another account | No |

### Existing frontend files modified

| File | Change | Shared? |
|---|---|---|
| `fe-tour/lib/admin-navigation.ts` | Per-entry role allow-lists and Departures entry; five staff labels | **Yes** |
| `fe-tour/lib/admin-navigation.test.ts` | Existing route inventory extended; staff/admin/customer navigation tests | **Yes** |
| `fe-tour/components/admin-gate.tsx` | Validated/stored admin or staff access; testable permission-state view | **Yes** |
| `fe-tour/components/admin/admin-layout.tsx` | Pass role to sidebar; page boundary before protected children mount | **Yes** |
| `fe-tour/components/admin/admin-sidebar.tsx` | Filter entries and role-aware home link in desktop/drawer | **Yes** |
| `fe-tour/components/admin/admin-topbar.tsx` | Correct Staff account label and accessible name | **Yes** |
| `fe-tour/components/auth-form.tsx` | Password login uses role destination | No |
| `fe-tour/components/google-signin-button.tsx` | Google login uses same destination helper | No |
| `fe-tour/components/site-header.tsx` | Both console links admit staff and use bookings landing | **Yes** |
| `fe-tour/components/admin-bookings.tsx` | Only import + three action handlers: staff dedicated POSTs; admin behavior retained | **Yes — booking UI** |
| `fe-tour/components/admin/admin-account-editor.tsx` | Display Staff label for DB-provided ROLE_STAFF option, retaining its ID | No |

### Existing documentation modified

| File | Change | Shared? |
|---|---|---|
| `README.md` | Three-role matrix, V10 bootstrap and accurate dev login usernames | **Yes — shared docs** |
| `tour/README.md` | Roles, V10 seeding, dev-only credentials and guard behavior | **Yes — shared docs** |
| `docs/api-spec.md` | Appended departures contract and authoritative authorization matrix | **Yes — shared docs** |
| `docs/progress.md` | Short dated feature entry; existing entries retained | **Yes — shared docs** |
| `docs/feature-list.md` | Only roles/role-dependent behavior lines changed | **Yes — shared docs** |

### New repository files

- `tour/src/main/resources/db/migration/V10__seed_account_roles.sql`
- `tour/src/main/java/com/j2ee/tour/controller/AdminDepartureController.java`
- `tour/src/main/java/com/j2ee/tour/service/AdminDepartureQueryService.java`
- `tour/src/main/java/com/j2ee/tour/repository/AdminDepartureRepository.java`
- `tour/src/main/java/com/j2ee/tour/dto/response/AdminDepartureItem.java`
- `tour/src/test/java/com/j2ee/tour/security/StaffRouteSecurityTest.java`
- `tour/src/test/java/com/j2ee/tour/controller/StaffPaymentControllerTest.java`
- `tour/src/test/java/com/j2ee/tour/controller/AdminDepartureControllerTest.java`
- `tour/src/test/java/com/j2ee/tour/service/AdminDepartureQueryServiceTest.java`
- `tour/src/test/java/com/j2ee/tour/repository/AdminDepartureRepositoryTest.java`
- `tour/src/test/java/com/j2ee/tour/service/AccountRoleProtectionTest.java`
- `fe-tour/lib/admin-permissions.ts`
- `fe-tour/lib/admin-permissions.test.ts`
- `fe-tour/lib/admin-gate.test.tsx`
- `fe-tour/components/admin-page-access.tsx`
- `fe-tour/lib/admin-page-access.test.tsx`
- `fe-tour/lib/admin-departures.ts`
- `fe-tour/lib/admin-departures.test.tsx`
- `fe-tour/components/admin-departures.tsx`
- `fe-tour/components/admin-departure-table.tsx`
- `fe-tour/app/admin/departures/page.tsx`
- `fe-tour/lib/admin-booking-actions.ts`
- `fe-tour/lib/admin-booking-actions.test.ts`
- `fe-tour/lib/account-role-options.test.tsx`
- `docs/admin/task-staff-role-result.md`

Build outputs under `target/` and `.next/` were generated by verification, not hand-edited.
A throwaway fixture server was created outside the repository at
`C:\Users\phulo\AppData\Local\Temp\opencode\staff-role-check\fixtures.cjs`.
It has no database/provider integration; fixture sessions were signed out, its browser tab closed,
and both test servers stopped. No project dependency/configuration changes were needed.

## Verification results

Migration created: `V10__seed_account_roles.sql` after re-checking V1–V9.
Guarded inserts preserve existing IDs and roles. Not executed against any database.

Backend authorization slice: RED confirmed (staff shared routes denied, generic writes allowed,
and booking ownership blocked operations). GREEN: focused 393 tests passed, 0 failures/errors,
0 skipped (`StaffRouteSecurityTest`, `StaffPaymentControllerTest`, `BookingServiceTest`,
`JwtServiceTest`, `AuthServiceTest`, `GoogleAuthServiceTest`, existing `SecurityConfigTest`).
The route matrix contains 63 method/path cases × four caller types = 252 checks.
No database connected. JWT production code unchanged; regressions verify staff login/refresh/
Google token issuance and signed-token round-trip.

### Shared business-file changes (exact methods)

`BookingService.java` only:
- `list(Pageable, AuthPrincipal, AdminBookingFilter)`: staff read scope alongside admin.
- `get`, `getByCode`: staff read access without altering the generic owner check.
- `confirm`, `complete`: opt into staff ownership bypass for those specific operations.
- `cancel`: same operation-specific access; reject staff non-PENDING cancellation with 403
  while holding the existing booking lock. Existing payment/seat/email rules unchanged.
- `findOwnedForUpdate(Long, AuthPrincipal)`: delegate to a new overload with default `false`.
- New `findOwnedForUpdate(Long, AuthPrincipal, boolean)` and `isStaff`: narrowly scoped checks;
  generic update/delete do not opt in.

`PaymentService`, `RefundService`, `AdminStatsService`, admin tours page and dashboard untouched.
`PaymentCrudController.create` checks staff method/status before delegating to unchanged payment logic.

Departures slice: 260 focused tests passed (252 route matrix + 8 controller/service/repository
tests). New endpoint has exact role protection, joined scalar mapping, filters, page-size cap,
stable ordering and single-query count/page handling. Native SQL execution on PostgreSQL remains
unverified; mocked query tests prove mapping and one invocation, not SQL/database behavior.

Frontend access slice: 23 focused Vitest tests passed. Gate permission helper requires matching
validated/stored account identity and role. The page boundary tests prove forbidden children do
not mount. Staff navigation has exactly five entries; header/home links avoid the dashboard.
Password and Google login share the role destination helper. Existing auth/session tests pass.
Windows PowerShell blocks `npm.ps1`; commands use `npm.cmd` without changing execution policy.

Departures UI slice: 12 focused tests passed. New page uses existing toolbar/table/status/
pagination/skeleton components, English text, URL filters, first-page reset and passenger links.
Results/errors are keyed to their query; obsolete requests are ignored after navigation.

Account/action slice: 11 backend guard tests and 3 frontend action/role-option tests passed.
All 603 database-free backend tests then passed (0 failures/errors/skipped).
`AccountService.update(..., AuthPrincipal)` and `delete(..., AuthPrincipal)` add caller-aware
self-protection and delegate to unchanged last-admin logic; `AccountController.update/delete`
pass the authenticated principal. `AccountRoleService.update/delete` block renaming/deleting
`ROLE_ADMIN`. No changes to role assignment interfaces.
`AdminAccountEditor` displays the seeded role as Staff while submitting its original DB ID.
The existing customer/invoice pages have no edit/delete controls; staff cannot mount departure
editing at all. There are no refund or PAID-cancellation controls on this branch to hide.
`AdminBookings` only changes its three operational handlers to dispatch staff to dedicated POSTs;
admin generic-PUT behavior is preserved. No management UI or business logic was added/refactored.

## Final access matrix as implemented

All permissions remain subject to existing state, ownership (where applicable), payment and lock
rules. Anonymous callers get 401 on protected routes; authenticated forbidden callers get 403.

| Capability / routes | Customer | Staff | Admin |
|---|---|---|---|
| Public tour reads, customer profile/checkout/VNPay/own invoices | Unchanged | No new privileges beyond existing authenticated flows | Unchanged |
| Operational booking list `GET /api/admin/bookings` | 403 | Yes | Yes |
| Booking reads `GET /api/bookings`, `/{id}`, `/code/{code}` | Own only | All | All |
| Confirm PAID / complete CONFIRMED via dedicated POST | Own only | Yes | Yes |
| Cancel unpaid PENDING via dedicated POST | Own only | Yes | Existing behavior |
| Cancel non-PENDING via dedicated POST | Existing 409 | **403**, even for staff-owned booking | Existing 409 |
| Generic booking POST/PUT/DELETE | Existing customer rules | **403** | Existing behavior |
| Payment lists/detail/history | 403 except existing own VNPay flow | Read-only | Yes |
| `POST /api/payments` | 403 | Only CASH + SUCCESS; other valid methods/statuses **403** | Existing behavior |
| `POST /api/admin/payments/{numericId}/reconcile` | 403 | Managed pending VNPay only | Same provider/state rules |
| Customer list/profile/history | Own profile only | Read-only | Yes |
| Invoice list/detail/by-booking/print | Own-invoice flow only | Read/print only | Yes |
| New departures list + existing passenger manifest | Public departures only | Read-only | Yes |
| Dashboard/statistics/reports | 403 | **403** | Yes where implemented |
| Tour/departure/catalog CRUD; departure cancellation | 403 | **403** | Yes |
| Promotions, accounts/roles, customer writes | 403 | **403** | Yes |
| Refunds, invoice/payment mutations, internal signed callback | 403 | **403** | Yes |
| Any other `/api/admin/**` route or method, now or added later | 403 | **403** | Yes by role |

PAID-booking cancellation/refunds are not newly implemented; no staff path grants either.
Existing admin business validation is deliberately unchanged.
Staff reads customers through `/api/admin/customers`, `/api/admin/customers/{id}/bookings`,
`/api/customers/{numericId}`; invoice reads through `/api/invoices`, `/{numericId}`,
`/by-booking/{numericId}`. Staff views departures through the new list/manifest, not legacy CRUD.

## Design decisions and limitations

1. Reused the existing security mechanism; no method-security layer, role hierarchy, fake admin
   principal or JWT format change. Service ownership bypass is opt-in per operation.
2. Staff allow-list dynamic IDs are numeric, so future literal actions such as
   `/api/admin/payments/bulk/reconcile` do not accidentally inherit staff access.
3. Cash request authorization stays in the controller boundary; payment business logic unchanged.
4. The departures query uses a filtered CTE, paged projection and aggregate left join in one
   statement. This avoids both N+1 and a second count round-trip, even on an empty page.
5. V10 uses guarded inserts because the bootstrap role-name column has no unique constraint.
   It preserves existing IDs/accounts; it does not deduplicate legacy duplicate role names.
6. Staff page access is an explicit route allow-list, not navigation-prefix inference. Forbidden
   page children do not mount, so protected pages do not fire unauthorized data loads.
7. Admin booking PUT behavior remains unchanged; staff alone uses the dedicated lifecycle POSTs.
   Existing customer/admin pages had no refund, PAID-cancel or customer-edit controls to remove.
8. Canonical admin role rename/delete and self-demotion/disable/delete are blocked with 409.
   Existing last-admin pessimistic locking stays intact; no broad account-management refactor.
9. Existing JWT revocation model remains: previously issued access tokens can keep old privileges
   until expiry (15 minutes). After an admin assigns Staff, sign out/in to obtain a new token.
10. No real PostgreSQL execution, migration/idempotency run, locking/race test, live provider
    settlement, real seed-account password login, real browser refresh-cookie rotation or
    database-backed account assignment was attempted. PostgreSQL/Docker executables were not on
    PATH; no tools/dependencies were installed. Mock tests are not evidence of database behavior.
11. Browser checks used fixtures, not backend authorization. Real Google login was not attempted;
    the Google button logs an origin-not-allowed error for temporary port 3348, which is not
    registered with the configured client. Staff JWT/Google role behavior is unit-tested.
12. Visual checks covered desktop 1536px and mobile 375px; not an exhaustive accessibility,
    screen-reader, browser-matrix or performance audit. No live SMTP/VNPay calls were made.

## Manual test guide — real application on an isolated development database

### Setup

1. Confirm the datasource is a disposable local development database, never shared/production.
   Start the backend there and let Flyway apply V10. Check each of the three role names exists.
   On an already seeded database, verify existing role/account IDs are unchanged.
2. Optionally run the unchanged dev seed. Use `demo_user1`, `demo_staff`, `demo_admin`, all with
   dev-only password `demo1234`; login accepts usernames, not email addresses.
3. Prepare upcoming OPEN/FULL departures and customer-owned PENDING, PAID and CONFIRMED bookings.
   Use separate unpaid PENDING bookings for cash payment and cancellation. A pending managed VNPay
   attempt is needed to test reconciliation; do not fabricate provider success or use production.
4. Use separate browser profiles or sign out between roles. Use the API client/Swagger with
   each role's own access token for backend checks; never reuse an admin token for a staff test.

### Customer (`ROLE_USER`)

- Sign in normally: existing `next` is honored, otherwise `/tours`; no console navigation link.
- Own bookings/profile/VNPay/invoices behave as before. Other customers' bookings remain hidden.
- `/admin/bookings` and `/admin/departures` show the workspace access-required state.
- Protected operational/admin API reads return 403; no token returns 401.
- Existing own PENDING cancellation / PAID confirmation / CONFIRMED completion remain unchanged.

### Staff (`ROLE_STAFF`)

- Sign in (password; optionally Google on a registered origin with an existing staff account):
  land on `/admin/bookings`, even when login was opened with `next=/admin`.
- Both desktop sidebar and mobile drawer show exactly Bookings, Payments, Customers, Invoices,
  Departures. The home/header console links go to bookings, not dashboard.
- Type `/admin`, `/admin/tours`, `/admin/catalog`, `/admin/accounts`, `/admin/promotions`
  and an unknown `/admin/future` URL: see No permission; no forbidden page data should load.
- Filter operational bookings; expand another customer's booking and read travelers/payment history.
  Confirm a PAID booking, complete a CONFIRMED booking, cancel a separate unpaid PENDING booking.
  Attempts to cancel PAID/CONFIRMED through the dedicated API must return 403 without mutation.
- Record cash: existing console sends CASH/SUCCESS. Attempt CARD/VNPAY/MOMO or CASH/PENDING
  through POST `/api/payments`: expect 403. Generic booking PUT/DELETE and payment/invoice writes,
  refunds, customer edit/delete and departure cancellation must all return 403.
- Payments page shows/searches attempts. Reconcile a managed pending VNPay attempt; a signed
  provider result is authoritative. Unresolved/provider-error results must stay protected.
- Read a customer and their booking history; no edit/delete. Read/print an invoice.
- Departures defaults to upcoming only. Test status and tour-name filters, all-dates mode,
  pagination, empty results, a FULL departure with zero remaining, and a past departure.
  Each row opens the correct passenger manifest. No departure management controls appear.
- Probe `/api/admin/stats`, reports/image-upload paths and arbitrary new `/api/admin/...` routes
  with the staff token: expect 403 before handler dispatch.

### Admin (`ROLE_ADMIN`)

- Sign in: land on `/admin`; see all original sections plus Departures.
- Confirm existing dashboard, tours/departures/catalog, promotions/accounts and all original
  booking/payment/invoice actions remain available, subject to their existing business rules.
- In Accounts, choose another non-final-admin account, select Staff and save. Sign that account
  out/in and repeat the staff checks. Verify the persisted role ID corresponds to ROLE_STAFF.
- Try self-demotion, self-disable and self-delete: expect 409, no account mutation.
- With two admins, demote the other admin if desired; then attempts to remove the last active
  admin must fail. Never run destructive account checks against a shared database.
- Attempt to rename/delete ROLE_ADMIN through role CRUD: expect 409.
- Admin may pass security for future routes, but unimplemented handlers still return 404/405;
  role permission is not a claim that reports/refunds/PAID-cancel UI has been implemented.

## Final verification and review

| Check | Command / evidence | Result |
|---|---|---|
| Backend tests + package | `.\mvnw.cmd "-Dtest=!*IntegrationTests,!TourApplicationTests" package` in `tour/` | **616 passed**, 0 failures/errors/skipped; BUILD SUCCESS |
| Separate backend packaging | `.\mvnw.cmd -DskipTests package` | BUILD SUCCESS |
| Frontend lint | `npm.cmd run lint` in `fe-tour/` | Exit 0, no warnings/errors |
| Frontend tests | `npm.cmd test` | **237 passed**, 40 files |
| Frontend build / TypeScript | `npm.cmd run build` | Exit 0; 33 pages generated, departures route included |
| Browser fixture smoke | Production frontend 3348 + isolated fixture API 3349 | Three role redirects/gates; staff navigation, list/filter/empty/reset/pagination/passengers; desktop/mobile checked |
| Review mutation check | Inverted validated/stored-role equality; ran permission + real gate-view tests | 3 expected failures; restored equality, final full suite green |
| Future-route hardening | Added literal internal/export/bulk paths under allowed resource shapes | RED (3 staff grants caught); numeric ID matchers fixed it; final matrix **66 paths × 4 callers = 264 checks** |

Frontend builds used temporary process-level `API_URL` and `NEXT_PUBLIC_API_URL` overrides pointing
to loopback port 3349, not the real API. No `.env` file was modified. Rebuild with the intended API
configuration before running your normal development/production setup.

Initial verification found a test-only TypeScript inference issue (throwing test child inferred
void); explicit `never` return type fixed it without product changes. Maven emitted existing
optional-feed metadata 401 and JDK/Mockito-agent warnings; neither blocked the build.
Backend ran on installed Java 26 compiling with release 21; CI's Java 21 runtime was not separately
exercised. No test skips/suppressions/dependency changes were added.

Inline five-axis review checked correctness, role-escalation paths, future-route defaults,
SQL parameterization/query count, page-boundary mounting, merge-sensitive edits and documentation.
No outstanding implementation blocker found; the live PostgreSQL/provider limitations above remain.
