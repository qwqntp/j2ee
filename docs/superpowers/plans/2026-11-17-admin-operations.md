# Admin Operations MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the requested admin dashboard, account/promotion management, booking operations filters, invoice admin and catalog CRUD to the existing console.

**Architecture:** Extend the Spring Boot API with dedicated admin statistics and filtered admin booking reads while reusing existing admin-only CRUD endpoints. Add typed frontend API calls and focused Next.js client components under `/admin/*`, reusing `AdminGate` and the existing admin shell.

**Tech Stack:** Spring Boot 4.1, Java 21, Spring Data JPA, PostgreSQL, Flyway; Next.js 16, React 19, TypeScript strict mode, Tailwind 4.

**Spec:** `docs/admin-gap-checklist.md` — implement only P1 items **1, 2, 3**, P2 item **7**, P2 item **11**, and P2 item **12**.

## Global Constraints

- Do **not** implement P1 items 5–6 or P2 items 8–10; they are deliberately out of scope.
- Do **not** implement P3 polish items, tour-relation editing, Cloudinary upload/picker, exports, cache revalidation, audit log, invoice PDF generation, or invoice void/reissue automation.
- Backend uses constructor injection, DTO records with Jakarta Validation, `ResponseEntity<ApiResponse<T>>` or `ApiResponse<T>`, and existing service-layer `@Transactional` boundaries.
- Backend security must keep every new administrative endpoint under `ROLE_ADMIN`; normal users must receive 401/403, never data.
- Frontend uses strict TypeScript, the `@/*` alias, npm, and existing client/server component conventions; admin interactivity is client-side.
- Follow existing `AdminTours`/`AdminBookings` component patterns: `ensureSession()`, `ApiError`, `PAGE_SIZE = 20`, native form controls, `btn-primary`, `btn-secondary`, and Tailwind utility classes.
- Do not point tests at shared Supabase. Backend tests must use `@WebMvcTest`, Mockito service tests, or explicitly configured local PostgreSQL only; no H2/Testcontainers.
- Do not weaken or remove Flyway migrations. This scope needs no schema changes.
- Keep user-facing copy in English, except existing project names/terms.
- Update `docs/admin-gap-checklist.md` checkboxes only for work verified by this plan.
- Frequent commits: one commit per task.

## Review Focus

The plan must not quietly ship these failure modes:

1. Stats returns nulls/zeros ambiguously for an empty payment/booking dataset — Task 1 tests a zero-state snapshot and explicit zero revenue.
2. Booking filters accept arbitrary status/date input or become unindexed SQL-like string concatenation — Task 7 implements parameterized JPA Specification predicates and rejects invalid status/from > to.
3. An admin locks or demotes the final active administrator — Task 4 blocks the last active `ROLE_ADMIN` from being disabled or demoted.
4. Catalog deletion of a referenced row surfaces a generic 500 as success — Tasks 11–12 surface the existing 409 `DataIntegrityViolationException` handling and keep the row/editor state consistent.
5. Admin invoice screen reuses the customer-own invoice path and leaks missing-owner 404s — Task 10 uses the admin-only `/api/invoices` and `/api/invoices/by-booking/{bookingId}` endpoints.
6. New admin endpoints land outside the `ROLE_ADMIN` security matrix — Task 1 adds an `/api/admin/stats` `ROLE_USER` → 403 case, and Task 8 adds the booking payment-history `ROLE_USER` → 403 and controller route tests.

---

## File Structure

### Backend

- Create `tour/src/main/java/com/j2ee/tour/dto/response/AdminStatsResponse.java` — admin dashboard DTO with nested stats records.
- Create `tour/src/main/java/com/j2ee/tour/service/AdminStatsService.java` — one `@Transactional(readOnly = true)` stats assembler.
- Create `tour/src/test/java/com/j2ee/tour/service/AdminStatsServiceTest.java` — zero-state and populated aggregate tests.
- Modify `tour/src/main/java/com/j2ee/tour/controller/AdminController.java` — add `GET /api/admin/stats`.
- Modify `tour/src/main/java/com/j2ee/tour/repository/BookingRepository.java` — Specification executor plus aggregate/lookup methods if repositories stay repository-driven.
- Create `tour/src/main/java/com/j2ee/tour/repository/BookingSpecifications.java` — status/from/to/q predicates.
- Modify `tour/src/main/java/com/j2ee/tour/service/BookingService.java` — filter-aware `list(...)`; server-side defaults stay unchanged for normal callers.
- Modify `tour/src/main/java/com/j2ee/tour/controller/BookingController.java` — accept filter params and use a bounded page size.
- Create `tour/src/main/java/com/j2ee/tour/dto/request/AdminBookingFilter.java` — parsed filter record after validation.
- Create `tour/src/main/java/com/j2ee/tour/dto/response/PaymentResponse` consumers only; reuse existing `PaymentResponse`.
- Modify `tour/src/main/java/com/j2ee/tour/controller/PaymentController.java` — add `GET /api/bookings/{bookingId}/payments` for admins; existing `SecurityConfig` matcher `/api/bookings/*/payments/**` remains the authority.
- Create `tour/src/test/java/com/j2ee/tour/controller/PaymentControllerTest.java` and extend `tour/src/test/java/com/j2ee/tour/security/SecurityConfigTest.java` — cover missing payment-history route/security.
- Modify `tour/src/main/java/com/j2ee/tour/service/AccountService.java` — block disabling/demoting the final active admin.
- Modify `tour/src/main/java/com/j2ee/tour/controller/AccountController.java` and `tour/src/test/java/com/j2ee/tour/service/AccountServiceTest.java` — pass actor context where required and test last-admin protection.
- Modify `tour/src/main/java/com/j2ee/tour/repository/AccountRepository.java` and `tour/src/main/java/com/j2ee/tour/repository/AccountRoleRepository.java` — count active admins/resolve requested role.

### Frontend

- Modify `fe-tour/lib/types.ts` — `AdminStats`, `AdminAccount`, `Promotion`, catalog input/response types.
- Modify `fe-tour/lib/api.ts` — typed admin API functions for stats, accounts, promotions, invoice admin read, booking filters and booking payments.
- Create `fe-tour/lib/admin-stats.ts` and `fe-tour/lib/admin-stats.test.ts` — pure dashboard data helpers.
- Create `fe-tour/lib/admin-accounts.ts` and `fe-tour/lib/admin-accounts.test.ts` — pure last-active-admin/UI normalization helpers.
- Create `fe-tour/lib/promotions.ts` and `fe-tour/lib/promotions.test.ts` — pure promotion status/normalization helpers.
- Create `fe-tour/lib/booking-list.ts` and `fe-tour/lib/booking-list.test.ts` — URL filter normalization shared by user/admin booking lists.
- Create `fe-tour/lib/admin-invoices.ts` and `fe-tour/lib/admin-invoices.test.ts` — invoice list filter helpers.
- Create `fe-tour/lib/admin-catalog.ts` and `fe-tour/lib/admin-catalog.test.ts` — catalog row/form normalization helpers.
- Modify `fe-tour/app/admin/layout.tsx` — add Dashboard, Accounts, Promotions, Invoices, Catalog links.
- Replace `fe-tour/app/admin/page.tsx` redirect with `AdminDashboard`.
- Create `fe-tour/components/admin-dashboard.tsx`.
- Create `fe-tour/app/admin/accounts/page.tsx` and `fe-tour/components/admin-accounts.tsx`.
- Create `fe-tour/app/admin/promotions/page.tsx` and `fe-tour/components/admin-promotions.tsx`.
- Modify `fe-tour/components/admin-bookings.tsx` — status/date/query filters, total-pages footer, note/travelers expansion, on-demand payment history.
- Modify `fe-tour/components/invoice-view.tsx` — optional `mode="admin"` and `backHref`; keep customer behavior unchanged.
- Create `fe-tour/app/admin/invoices/page.tsx` and `fe-tour/components/admin-invoices.tsx`.
- Create `fe-tour/app/admin/invoices/[bookingId]/page.tsx` — admin printable invoice route.
- Create `fe-tour/app/admin/catalog/page.tsx` and `fe-tour/components/admin-catalog.tsx` — tabbed CRUD for destinations, accommodations, vehicles, guides and tour images.

### Tracking

- Modify `docs/admin-gap-checklist.md` — mark only verified items, leave deferred items unchecked.

---

## Task 1: Backend admin stats endpoint

**Spec:** P1 item 1 — backend dashboard statistics.

**Files:**
- Create: `tour/src/main/java/com/j2ee/tour/dto/response/AdminStatsResponse.java`
- Create: `tour/src/main/java/com/j2ee/tour/service/AdminStatsService.java`
- Create: `tour/src/test/java/com/j2ee/tour/service/AdminStatsServiceTest.java`
- Modify: `tour/src/main/java/com/j2ee/tour/controller/AdminController.java`
- Modify: `tour/src/main/java/com/j2ee/tour/repository/BookingRepository.java`
- Create: `tour/src/main/java/com/j2ee/tour/repository/BookingSpecifications.java`
- Review against: `tour/src/test/AGENTS.md` test stack (Mockito; no DB required for service unit test).

**Interfaces:**
- Consumes: `ApiResponse<AdminStatsResponse>`.
- Produces: `GET /api/admin/stats` returning `ApiResponse<AdminStatsResponse>` with these exact sections:
  - `generatedAt: LocalDateTime`
  - `totalTours: long`, `openTours: long`
  - `bookingsByStatus: Map<String, Long>` with all five booking status keys
  - `successfulRevenue: BigDecimal`, `successfulPayments: long`, `averageSuccessfulPayment: BigDecimal`
  - `upcomingDepartures: long`, `lowCapacityDepartures: long`, `soldOutDepartures: long`
  - `topTours: List<TourPerformance>` and `capacityRisks: List<CapacityRisk>`, each at most 5 rows.

- [ ] **Step 1: Write the failing zero-state test**

```java
@Test
void statsReturnsExplicitZeroState() {
    when(bookingRepository.count()).thenReturn(0L);
    when(bookingRepository.countByBookingStatus("PENDING")).thenReturn(0L);
    when(bookingRepository.countByBookingStatus("PAID")).thenReturn(0L);
    when(bookingRepository.countByBookingStatus("CONFIRMED")).thenReturn(0L);
    when(bookingRepository.countByBookingStatus("COMPLETED")).thenReturn(0L);
    when(bookingRepository.countByBookingStatus("CANCELLED")).thenReturn(0L);
    when(paymentRepository.countByPaymentStatus("SUCCESS")).thenReturn(0L);
    when(paymentRepository.sumByPaymentStatus("SUCCESS")).thenReturn(null);
    when(tourRepository.count()).thenReturn(0L);
    when(tourRepository.countByTourStatus("OPEN")).thenReturn(0L);
    when(departureRepository.countUpcomingOpen(any(), any())).thenReturn(0L);
    when(departureRepository.countUpcomingCapacityAtMost(eq(1), any(), any())).thenReturn(0L);
    when(departureRepository.countUpcomingSoldOut(any(), any())).thenReturn(0L);
    when(bookingRepository.findTopToursByBookingCount(any())).thenReturn(List.of());
    when(departureRepository.findCapacityRisks(any(), any(), any())).thenReturn(List.of());

    AdminStatsResponse stats = service.stats();

    assertThat(stats.successfulRevenue()).isEqualByComparingTo("0");
    assertThat(stats.averageSuccessfulPayment()).isEqualByComparingTo("0");
    assertThat(stats.bookingsByStatus()).containsEntry("PENDING", 0L);
    assertThat(stats.bookingsByStatus()).containsEntry("PAID", 0L);
    assertThat(stats.bookingsByStatus()).containsEntry("CONFIRMED", 0L);
    assertThat(stats.bookingsByStatus()).containsEntry("COMPLETED", 0L);
    assertThat(stats.bookingsByStatus()).containsEntry("CANCELLED", 0L);
    assertThat(stats.topTours()).isEmpty();
    assertThat(stats.capacityRisks()).isEmpty();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tour; .\mvnw.cmd -Dtest=AdminStatsServiceTest test`
Expected: FAIL — `AdminStatsService` or `AdminStatsResponse` does not exist.

- [ ] **Step 3: Add `AdminStatsResponse`**

Use records and no Lombok. Required shape:

```java
public record AdminStatsResponse(
    LocalDateTime generatedAt,
    long totalTours,
    long openTours,
    Map<String, Long> bookingsByStatus,
    BigDecimal successfulRevenue,
    long successfulPayments,
    BigDecimal averageSuccessfulPayment,
    long upcomingDepartures,
    long lowCapacityDepartures,
    long soldOutDepartures,
    List<TourPerformance> topTours,
    List<CapacityRisk> capacityRisks
) {
    public record TourPerformance(Long tourId, String tourName, long bookingCount, BigDecimal revenue) {}
    public record CapacityRisk(Long departureId, Long tourId, String tourName,
                               LocalDateTime startDate, int capacity,
                               int remainingSeats, String status) {}
}
```

- [ ] **Step 4: Implement repository aggregate methods**

Add to `BookingRepository`, preserving existing methods:

```java
long countByBookingStatus(String status);

@Query("select b.departure.tour.tourId, b.departure.tour.tourName, count(b), coalesce(sum(b.bookingTotalAmount), 0) from Booking b where b.bookingStatus <> 'CANCELLED' group by b.departure.tour.tourId, b.departure.tour.tourName order by count(b) desc")
List<Object[]> findTopToursByBookingCount(Pageable pageable);
```

Add to `PaymentRepository`:

```java
long countByPaymentStatus(String status);

@Query("select coalesce(sum(payment.paymentAmount), 0) from Payment payment where payment.paymentStatus = :status")
BigDecimal sumByPaymentStatus(@Param("status") String status);
```

Add to `TourRepository`:

```java
long countByTourStatus(String tourStatus);
```

Add to `TourDepartureRepository`:

```java
@Query("select count(d) from TourDeparture d where d.departureStartDate >= :from and d.departureStartDate < :to and d.departureStatus in ('OPEN','FULL')")
long countUpcomingOpen(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

@Query("select count(d) from TourDeparture d where d.departureStartDate >= :from and d.departureStartDate < :to and d.departureCapacity - d.departureBookedSlots <= :remaining and d.departureStatus in ('OPEN','FULL')")
long countUpcomingCapacityAtMost(@Param("remaining") int remaining, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

@Query("select count(d) from TourDeparture d where d.departureStartDate >= :from and d.departureStartDate < :to and d.departureCapacity - d.departureBookedSlots <= 0 and d.departureStatus in ('OPEN','FULL')")
long countUpcomingSoldOut(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

@Query("select d.departureId, d.tour.tourId, d.tour.tourName, d.departureStartDate, d.departureCapacity, d.departureCapacity - d.departureBookedSlots, d.departureStatus from TourDeparture d where d.departureStartDate >= :from and d.departureStartDate < :to and d.departureCapacity - d.departureBookedSlots <= :remaining and d.departureStatus in ('OPEN','FULL') order by d.departureStartDate")
List<Object[]> findCapacityRisks(@Param("from") LocalDateTime from, @Param("remaining") int remaining, @Param("to") LocalDateTime to);
```

Use `Clock` for the `from`/`to` window; show the next 90 days.

- [ ] **Step 5: Implement `AdminStatsService.stats()`**

`@Service @Transactional(readOnly = true)` constructor injection. Use `Instant.now(clock)` converted to `LocalDateTime` in system zone for the 90-day window. Build the status map explicitly:

```java
Map<String, Long> bookingsByStatus = new HashMap<>();
for (String status : List.of("PENDING", "PAID", "CONFIRMED", "COMPLETED", "CANCELLED")) {
    bookingsByStatus.put(status, bookingRepository.countByBookingStatus(status));
}
```

Normalize null sums to `BigDecimal.ZERO`. `averageSuccessfulPayment` is `successfulRevenue.divide(BigDecimal.valueOf(successfulPayments), 2, RoundingMode.HALF_UP)` or `ZERO` when no successful payments. Limit `topTours` and `capacityRisks` to 5.

- [ ] **Step 6: Add controller endpoint**

In `AdminController`:

```java
@GetMapping("/stats")
@Operation(summary = "Admin dashboard summary")
public ApiResponse<AdminStatsResponse> stats() {
    return ApiResponse.success(adminStatsService.stats());
}
```

Do not add a new security route; `/api/admin/stats` must continue to match `/api/admin/**`. Modify `tour/src/test/java/com/j2ee/tour/security/SecurityConfigTest.java` to add `GET /api/admin/stats` as `ROLE_USER` expecting `403`.

- [ ] **Step 7: Add populated-state test**

Mock one successful payment of 100 and another of 50; booking counts 2/1/0/1/0; open tour count 1; verify `successfulRevenue == 150`, `averageSuccessfulPayment == 75`, `topTours` mapped, `capacityRisks` mapped with negative remaining seats possible only if data corrupt, and `generatedAt` is not null.

- [ ] **Step 8: Run backend stats tests**

Run: `cd tour; .\mvnw.cmd -Dtest=AdminStatsServiceTest,SecurityConfigTest test`
Expected: PASS with stats tests and the stats 403 case included.

- [ ] **Step 9: Commit**

```bash
git add tour/src/main/java/com/j2ee/tour/dto/response/AdminStatsResponse.java \
        tour/src/main/java/com/j2ee/tour/service/AdminStatsService.java \
        tour/src/test/java/com/j2ee/tour/service/AdminStatsServiceTest.java \
        tour/src/main/java/com/j2ee/tour/controller/AdminController.java \
        tour/src/main/java/com/j2ee/tour/repository \
        tour/src/test/java/com/j2ee/tour/security/SecurityConfigTest.java

git commit -m "feat(admin): add dashboard statistics endpoint"
```

---

## Task 2: Frontend dashboard route and stat types

**Spec:** P1 item 1 — dashboard UI and route.

**Files:**
- Modify: `fe-tour/lib/types.ts`
- Modify: `fe-tour/lib/api.ts`
- Create: `fe-tour/lib/admin-stats.ts`
- Create: `fe-tour/lib/admin-stats.test.ts`
- Create: `fe-tour/components/admin-dashboard.tsx`
- Replace: `fe-tour/app/admin/page.tsx`
- Modify: `fe-tour/app/admin/layout.tsx`

**Interfaces:**
- Consumes: `GET /api/admin/stats` from Task 1; `ensureSession()` from `@/lib/auth`; `ApiError` from `@/lib/api` as called by existing admin pages.
- Produces: `/admin` renders `AdminDashboard`; `AdminStats` is exported from `@/lib/types`; `getAdminStats(token: string): Promise<AdminStats>`.

- [ ] **Step 1: Write failing pure-helper tests**

```ts
import { describe, expect, it } from "vitest";
import { buildDashboardCards } from "./admin-stats";
import type { AdminStats } from "./types";

const stats: AdminStats = {
  generatedAt: "2026-11-17T08:00:00",
  totalTours: 12,
  openTours: 4,
  bookingsByStatus: { PENDING: 2, PAID: 3, CONFIRMED: 1, COMPLETED: 5, CANCELLED: 1 },
  successfulRevenue: 1500,
  successfulPayments: 3,
  averageSuccessfulPayment: 500,
  upcomingDepartures: 6,
  lowCapacityDepartures: 2,
  soldOutDepartures: 1,
  topTours: [],
  capacityRisks: [],
};

it("builds seven dashboard cards with exact labels", () => {
  expect(buildDashboardCards(stats).map((card) => card.label)).toEqual([
    "Open tours", "Pending bookings", "Revenue (SUCCESS)", "Avg. successful payment",
    "Upcoming departures", "At-risk capacity", "Sold out",
  ]);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd fe-tour; npm test -- lib/admin-stats.test.ts`
Expected: FAIL — `buildDashboardCards` is not defined.

- [ ] **Step 3: Add `AdminStats` types**

In `fe-tour/lib/types.ts` add:

```ts
export type AdminStats = {
  generatedAt: string;
  totalTours: number;
  openTours: number;
  bookingsByStatus: Record<"PENDING" | "PAID" | "CONFIRMED" | "COMPLETED" | "CANCELLED", number>;
  successfulRevenue: number;
  successfulPayments: number;
  averageSuccessfulPayment: number;
  upcomingDepartures: number;
  lowCapacityDepartures: number;
  soldOutDepartures: number;
  topTours: Array<{ tourId: number; tourName: string; bookingCount: number; revenue: number }>;
  capacityRisks: Array<{
    departureId: number;
    tourId: number;
    tourName: string;
    startDate: string;
    capacity: number;
    remainingSeats: number;
    status: string;
  }>;
};
```

- [ ] **Step 4: Implement `lib/admin-stats.ts`**

Export:

```ts
import type { AdminStats } from "./types";

export type DashboardCard = { label: string; value: string; tone: "forest" | "terracotta" | "charcoal" };

export function buildDashboardCards(stats: AdminStats): DashboardCard[] { /* fixed labels from test */ }
```

Use `formatPrice(stats.successfulRevenue)` from `./format` for money. Output labels must exactly match Step 1.

- [ ] **Step 5: Add `getAdminStats`**

In `fe-tour/lib/api.ts`:

```ts
export function getAdminStats(token: string): Promise<AdminStats> {
  return apiRequest<AdminStats>("/api/admin/stats", { token });
}
```

- [ ] **Step 6: Implement `AdminDashboard`**

Create a client component. It must:
- load stats with `ensureSession()`;
- show a retry button and `ApiError` message on failure;
- render exactly the seven cards from `buildDashboardCards`;
- render the full `bookingsByStatus` status table for `PENDING`, `PAID`, `CONFIRMED`, `COMPLETED`, `CANCELLED`;
- render `topTours` with tour name, booking count and formatted revenue; empty state says `No booking performance yet.`;
- render `capacityRisks` with tour/departure date/remaining seats; empty state says `No low-capacity departures in the next 90 days.`;
- never call arbitrary external endpoints.

Use class names consistent with existing admin pages: `eyebrow text-forest`, `rounded-[4px] border border-line bg-white p-6`.

- [ ] **Step 7: Replace `/admin` redirect and add nav link**

`fe-tour/app/admin/page.tsx` becomes the dashboard route with:

```tsx
import type { Metadata } from "next";
import { AdminDashboard } from "@/components/admin-dashboard";

export const metadata: Metadata = { title: "Admin dashboard" };
export default function AdminPage() { return <AdminDashboard />; }
```

Add `<Link href="/admin">Dashboard</Link>` as first nav item in `app/admin/layout.tsx`.

- [ ] **Step 8: Run frontend dashboard tests**

Run: `cd fe-tour; npm test -- lib/admin-stats.test.ts`
Expected: PASS.

Then run: `cd fe-tour; npx tsc --noEmit`
Expected: `Found 0 errors`.

- [ ] **Step 9: Commit**

```bash
git add fe-tour/lib/types.ts fe-tour/lib/api.ts fe-tour/lib/admin-stats.ts \
        fe-tour/lib/admin-stats.test.ts fe-tour/components/admin-dashboard.tsx \
        fe-tour/app/admin/page.tsx fe-tour/app/admin/layout.tsx

git commit -m "feat(admin): render admin dashboard"
```

---

## Task 3: Frontend list/update API for accounts and pure normalization

**Spec:** P1 item 2 — admin account list/lock/role foundation.

**Files:**
- Modify: `fe-tour/lib/types.ts`
- Modify: `fe-tour/lib/api.ts`
- Create: `fe-tour/lib/admin-accounts.ts`
- Create: `fe-tour/lib/admin-accounts.test.ts`
- Read-only reference: `tour/src/main/java/com/j2ee/tour/controller/AccountController.java`
- Read-only reference: `tour/src/main/java/com/j2ee/tour/controller/AccountRoleController.java`

**Interfaces:**
- Consumes: `GET /api/accounts?page=0&size=100`, `GET /api/account-roles`, `PUT /api/accounts/{id}` (existing endpoints, admin-only in `SecurityConfig`).
- Produces:

```ts
export type AdminAccount = {
  id: number;
  username: string;
  email: string | null;
  emailVerified: number | null;
  status: number | null;
  roleId: number | null;
  roleName: string | null;
  createdAt?: string;
  updatedAt?: string;
};

export type AccountUpdateInput = {
  username: string;
  password?: string;
  email: string | null;
  emailVerified: number;
  status: number;
  roleId: number;
};
```

- [ ] **Step 1: Write failing normalization tests**

```ts
import { describe, expect, it } from "vitest";
import { accountStatusLabel, canChangeAdminRole } from "./admin-accounts";

it("labels account status values", () => {
  expect(accountStatusLabel(1)).toBe("Active");
  expect(accountStatusLabel(0)).toBe("Locked");
});

it("blocks demoting the only active admin in the loaded list", () => {
  expect(canChangeAdminRole([{ roleName: "ROLE_ADMIN", status: 1 }], "ROLE_USER")).toBe(false);
  expect(canChangeAdminRole([
    { roleName: "ROLE_ADMIN", status: 1 },
    { roleName: "ROLE_ADMIN", status: 1 },
  ], "ROLE_USER")).toBe(true);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd fe-tour; npm test -- lib/admin-accounts.test.ts`
Expected: FAIL — helpers not defined.

- [ ] **Step 3: Implement helpers**

`accountStatusLabel(status)` returns exactly `Active` for `1`, `Locked` otherwise. `canChangeAdminRole(accounts, nextRoleName)` returns `false` when the loaded active-admin count is 1 and the requested role is not `ROLE_ADMIN`. The UI may disable itself using the loaded list, but Task 4 enforces the same rule server-side.

- [ ] **Step 4: Add API types and functions**

Add `AdminAccount`, `AccountRoleResponse`, and `AccountUpdateInput` to `types.ts`. Add to `api.ts`:

```ts
export function listAdminAccounts(token: string, page = 0, size = 100) {
  return apiRequest<PageData<AdminAccount>>(`/api/accounts?page=${page}&size=${size}`, { token });
}
export function listAccountRoles(token: string) {
  return apiRequest<PageData<{ id: number; roleName: string }>>(
    "/api/account-roles?page=0&size=100",
    { token },
  ).then((page) => page.content);
}
export function updateAdminAccount(token: string, id: number, input: AccountUpdateInput) {
  return apiRequest<AdminAccount>(`/api/accounts/${id}`, { method: "PUT", token, body: input });
}
```

Check `AccountRoleResponse` before fixing the `Role[]` member names; the endpoint is `PageResponse<AccountRoleResponse>` with `content`, default page size 20, and members `id`/`roleName`, never a bare array.

- [ ] **Step 5: Run account normalization tests**

Run: `cd fe-tour; npm test -- lib/admin-accounts.test.ts`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add fe-tour/lib/types.ts fe-tour/lib/api.ts fe-tour/lib/admin-accounts.ts fe-tour/lib/admin-accounts.test.ts

git commit -m "feat(admin): add account administration API helpers"
```

---

## Task 4: Enforce final-active-admin rule in backend account updates

**Spec:** P1 item 2 — admin lock/role changes cannot lock the business out.

**Files:**
- Modify: `tour/src/main/java/com/j2ee/tour/service/AccountService.java`
- Modify: `tour/src/main/java/com/j2ee/tour/controller/AccountController.java`
- Modify: `tour/src/main/java/com/j2ee/tour/dto/request/AccountUpdateRequest.java` only if the current record cannot preserve `email`/`emailVerified` fields; do not replace it with a partial request.
- Modify: `tour/src/main/java/com/j2ee/tour/repository/AccountRepository.java`
- Create/modify: `tour/src/test/java/com/j2ee/tour/service/AccountServiceTest.java`

**Interfaces:**
- Consumes: `AccountUpdateRequest(username, password, email, emailVerified, status, roleId)`, existing `AuthPrincipal`.
- Produces: `AccountService.update(id, request, actorAccountId)` or an equivalently explicit admin-only update path. The existing controller passes `principal.id()`; no silent trust of plain `id` alone for dangerous transitions.

- [ ] **Step 1: Write failing service tests**

Create `src/test/java/com/j2ee/tour/service/AccountServiceTest.java`. Test names:

```java
@Test demotingTheOnlyActiveAdminIsRejectedWithConflict()
@Test lockingTheOnlyActiveAdminIsRejectedWithConflict()
@Test updatingANonAdminAccountDoesNotRequireActiveAdminCount()
```

Use an active target account with role `ROLE_ADMIN`; for first test set requested `roleId` to a `ROLE_USER` role; for second set `status = 0`; mock `AccountRepository` to report one active admin; expect `IllegalStateException` with message `"Cannot remove the last active administrator"`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tour; .\mvnw.cmd -Dtest=AccountServiceTest test`
Expected: FAIL because `AccountService` currently exposes only `update(id, request)` and has no actor/last-admin rule.

- [ ] **Step 3: Add role resolution helpers**

Inject `AccountRoleRepository` into `AccountService`. Resolve the requested role by `roleId`; throw `NoSuchElementException("Account role not found")` when absent.

- [ ] **Step 4: Add locked active-admin query**

Add to `AccountRepository`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select a from Account a join a.role role where role.roleName = :roleName and a.status = :status")
List<Account> findActiveAdminsForUpdate(@Param("roleName") String roleName,
                                        @Param("status") Integer status);
```

Use the list size inside the same `@Transactional` update boundary, not a non-blocking `count` query. The lock serializes concurrent last-admin demotion/lock operations on the active admin rows before the update is written.

- [ ] **Step 5: Implement the guard**

Before saving the updated target:

```java
boolean targetWasActiveAdmin = Objects.equals(target.getStatus(), 1)
    && target.getRole() != null
    && "ROLE_ADMIN".equals(target.getRole().getRoleName());
boolean removingAdmin = !"ROLE_ADMIN".equals(requestedRole.getRoleName()) || !Objects.equals(request.status(), 1);
List<Account> activeAdmins = accountRepository.findActiveAdminsForUpdate("ROLE_ADMIN", 1);
if (targetWasActiveAdmin && removingAdmin && activeAdmins.size() <= 1) {
    throw new IllegalStateException("Cannot remove the last active administrator");
}
```

Keep password update behavior unchanged. `GlobalExceptionHandler` already maps `IllegalStateException` to 409.

- [ ] **Step 6: Wire actor context in controller**

Change `AccountController.update` to accept `@AuthenticationPrincipal AuthPrincipal principal` and call the guarded update. Keep existing route and request shape.

- [ ] **Step 7: Run account backend tests**

Run: `cd tour; .\mvnw.cmd -Dtest=AccountServiceTest test`
Expected: PASS with 3 tests, 0 failures.

- [ ] **Step 8: Commit**

```bash
git add tour/src/main/java/com/j2ee/tour/service/AccountService.java \
        tour/src/main/java/com/j2ee/tour/controller/AccountController.java \
        tour/src/main/java/com/j2ee/tour/repository/AccountRepository.java \
        tour/src/test/java/com/j2ee/tour/service/AccountServiceTest.java

git commit -m "fix(admin): protect the last active administrator"
```

---

## Task 5: Build `/admin/accounts` management UI

**Spec:** P1 item 2 — visible admin account management.

**Files:**
- Create: `fe-tour/app/admin/accounts/page.tsx`
- Create: `fe-tour/components/admin-accounts.tsx`
- Modify: `fe-tour/app/admin/layout.tsx`
- Read-only reference: `fe-tour/components/admin-tours.tsx` and `fe-tour/components/admin-bookings.tsx` for client component conventions.

**Interfaces:**
- Consumes: Task 3 API helpers and types; Task 4 last-admin rule.
- Produces: route `/admin/accounts`; admin nav item `Accounts`.

- [ ] **Step 1: Write failing route smoke test**

If the project has no page-component test harness, do not introduce one. Instead assert the pure helper used by this page:

```ts
it("keeps account filter matching case-insensitively across username and email", () => {
  expect(filterAccounts([
    { username: "alice", email: "ALICE@EXAMPLE.COM", roleName: "ROLE_USER", status: 1 },
    { username: "bob", email: null, roleName: "ROLE_ADMIN", status: 0 },
  ], { query: "alice", roleName: "", status: "all" })).toHaveLength(1);
});
```

Place the helper and test in `lib/admin-accounts.ts` / `lib/admin-accounts.test.ts` if they were not added in Task 3; otherwise extend that test file.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd fe-tour; npm test -- lib/admin-accounts.test.ts`
Expected: FAIL — filter helper not exported.

- [ ] **Step 3: Implement `filterAccounts`**

```ts
export type AdminAccountFilter = { query: string; roleName: string; status: "all" | "0" | "1" };
export function filterAccounts(accounts: AdminAccount[], filter: AdminAccountFilter): AdminAccount[]
```

Use case-insensitive `String.includes` on username/email/roleName. Empty query returns all role/status matches. Do not mutate input.

- [ ] **Step 4: Create `/admin/accounts` route**

Route metadata title `Admin accounts`; render `<AdminAccounts />`.

- [ ] **Step 5: Build `AdminAccounts` component**

Requirements:
- load `listAdminAccounts(token, 0, 100)` and `listAccountRoles(token)` in parallel;
- client-side search input, role select, status select;
- table columns: username, email, role, status, email verified, created;
- controls per row: pending role select, pending Active/Locked select, and pending email-verified select (`Unverified`/`Verified`); all three stage one `Apply changes` workflow with inline confirmation; controls are disabled and show `Final active admin cannot be disabled or demoted.` when the staged change would remove the only active loaded `ROLE_ADMIN`;
- `Set temporary password` button: generate a random 16-character password with `crypto.getRandomValues`, call `updateAdminAccount` with the existing username/email/emailVerified/status/roleId plus password, then show the temporary password once in a dismissible banner; no password is persisted in component state after dismissal;
- confirmation uses inline button (`Click again to confirm`) not `window.confirm`;
- failed operations surface `ApiError.message`; successful saved row reloads the account list.

- [ ] **Step 6: Add admin nav link**

Add `<Link href="/admin/accounts">Accounts</Link>` after Bookings.

- [ ] **Step 7: Run account UI tests and typecheck**

Run: `cd fe-tour; npm test -- lib/admin-accounts.test.ts; npx tsc --noEmit`
Expected: tests PASS and `Found 0 errors`.

- [ ] **Step 8: Commit**

```bash
git add fe-tour/lib/admin-accounts.ts fe-tour/lib/admin-accounts.test.ts \
        fe-tour/app/admin/accounts fe-tour/components/admin-accounts.tsx fe-tour/app/admin/layout.tsx

git commit -m "feat(admin): add account management console"
```

---

## Task 6: Promotions typed API and pure helpers

**Spec:** P1 item 3 — promotion creation/edit foundation.

**Files:**
- Modify: `fe-tour/lib/types.ts`
- Modify: `fe-tour/lib/api.ts`
- Create: `fe-tour/lib/promotions.ts`
- Create: `fe-tour/lib/promotions.test.ts`
- Read-only reference: `tour/src/main/java/com/j2ee/tour/dto/request/PromotionRequest.java`
- Read-only reference: `tour/src/main/java/com/j2ee/tour/dto/response/PromotionResponse.java`

**Interfaces:**
- Consumes: `GET/POST/PUT/DELETE /api/promotions` (admin-only in `SecurityConfig`).
- Produces:

```ts
export type Promotion = {
  id: number;
  name: string;
  discountPercentage: number;
  startDate: string;
  endDate: string;
  description: string | null;
  code: string | null;
  active: boolean | null;
  usageLimit: number | null;
  usedCount: number;
  minOrderAmount: number | null;
};

export type PromotionInput = Omit<Promotion, "id" | "usedCount">;
```

- [ ] **Step 1: Write failing promotion-state tests**

```ts
import { describe, expect, it } from "vitest";
import { promotionLifecycle } from "./promotions";

it("classifies lifecycle deterministically", () => {
  const now = new Date("2026-11-17T00:00:00");
  expect(promotionLifecycle({ active: true, startDate: "2026-11-01", endDate: "2026-11-30", code: "SALE" }, now)).toBe("Active");
  expect(promotionLifecycle({ active: false, startDate: "2026-11-01", endDate: "2026-11-30", code: "SALE" }, now)).toBe("Inactive");
  expect(promotionLifecycle({ active: true, startDate: "2026-11-18", endDate: "2026-11-30", code: "SALE" }, now)).toBe("Scheduled");
  expect(promotionLifecycle({ active: true, startDate: "2026-10-01", endDate: "2026-10-31", code: "SALE" }, now)).toBe("Expired");
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd fe-tour; npm test -- lib/promotions.test.ts`
Expected: FAIL — `promotionLifecycle` not defined.

- [ ] **Step 3: Implement `promotionLifecycle`**

Use date-only string comparison via `new Date("YYYY-MM-DDT00:00:00")`; do not use time-of-day. Precedence: `active === false` → `Inactive`; `startDate > now` → `Scheduled`; `endDate < today` → `Expired`; else `Active`.

- [ ] **Step 4: Add Promotion types and API functions**

Add `listPromotions`, `createPromotion`, `updatePromotion`, `deletePromotion` to `api.ts`; list uses `page=0&size=100`. API request body must never include `usedCount`; UI sends the `PromotionInput` fields only.

- [ ] **Step 5: Run promotion helper tests**

Run: `cd fe-tour; npm test -- lib/promotions.test.ts`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add fe-tour/lib/types.ts fe-tour/lib/api.ts fe-tour/lib/promotions.ts fe-tour/lib/promotions.test.ts

git commit -m "feat(admin): add promotion API helpers"
```

---

## Task 7: Build `/admin/promotions` CRUD page

**Spec:** P1 item 3 — visible promotion management.

**Files:**
- Create: `fe-tour/app/admin/promotions/page.tsx`
- Create: `fe-tour/components/admin-promotions.tsx`
- Modify: `fe-tour/app/admin/layout.tsx`

**Interfaces:**
- Consumes: Task 6 API helpers/types.
- Produces: route `/admin/promotions`; admin nav item `Promotions`.

- [ ] **Step 1: Write failing list-transform test**

Extend `lib/promotions.test.ts`:

```ts
it("filters promotions by lifecycle and case-insensitive code/name", () => {
  const rows = [
    { id: 1, name: "Winter Sale", code: "WIN2026", active: true, startDate: "2026-11-01", endDate: "2026-12-01", usedCount: 3 },
    { id: 2, name: "Expired Deal", code: "OLD", active: true, startDate: "2026-10-01", endDate: "2026-10-31", usedCount: 0 },
  ] as Promotion[];
  expect(filterPromotions(rows, { lifecycle: "Active", query: "win" }, new Date("2026-11-17T00:00:00"))).toHaveLength(1);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd fe-tour; npm test -- lib/promotions.test.ts`
Expected: FAIL — `filterPromotions` not exported.

- [ ] **Step 3: Implement `filterPromotions`**

Add `filterPromotions(rows, { lifecycle, query }, now)` to `lib/promotions.ts`. Lifecycle `"All"` returns all matches; exact lifecycle values are `Active`, `Scheduled`, `Expired`, `Inactive`. Query checks lowercased name/code/description.

- [ ] **Step 4: Create route and metadata**

`fe-tour/app/admin/promotions/page.tsx` imports `<AdminPromotions />` and sets metadata title `Admin promotions`.

- [ ] **Step 5: Build `AdminPromotions`**

UI must include:
- list with name, code, discount, usage `usedCount/usageLimit`, lifecycle, active toggle state and min order amount;
- create/edit form with name, code, discountPercentage (`0–100`), startDate, endDate, active, usageLimit optional, minOrderAmount optional, description;
- date fields use `<input type="date">`;
- inline delete confirmation (`Click again to delete`) for rows not needed by past bookings; if backend rejects because referenced by bookings, show `ApiError.message`;
- on save/delete reload list and show exact success text `Promotion saved.` or `Promotion deleted.`;
- empty state message `No promotions yet.`

- [ ] **Step 6: Add nav link**

Add `<Link href="/admin/promotions">Promotions</Link>` after Accounts.

- [ ] **Step 7: Run promotion tests and typecheck**

Run: `cd fe-tour; npm test -- lib/promotions.test.ts; npx tsc --noEmit`
Expected: tests PASS and `Found 0 errors`.

- [ ] **Step 8: Commit**

```bash
git add fe-tour/lib/promotions.ts fe-tour/lib/promotions.test.ts \
        fe-tour/app/admin/promotions fe-tour/components/admin-promotions.tsx fe-tour/app/admin/layout.tsx

git commit -m "feat(admin): add promotion management console"
```

---

## Task 8: Add filtered booking reads and payment history endpoint

**Spec:** P2 item 7 — booking filters/search and payment status/history foundation.

**Files:**
- Create: `tour/src/main/java/com/j2ee/tour/dto/request/AdminBookingFilter.java` — parsed filter record passed to service.
- Create: `tour/src/main/java/com/j2ee/tour/repository/BookingSpecifications.java`
- Modify: `tour/src/main/java/com/j2ee/tour/repository/BookingRepository.java` — extend `JpaSpecificationExecutor<Booking>`.
- Modify: `tour/src/main/java/com/j2ee/tour/service/BookingService.java`
- Modify: `tour/src/main/java/com/j2ee/tour/controller/BookingController.java`
- Modify: `tour/src/main/java/com/j2ee/tour/controller/PaymentController.java` — add `GET /api/bookings/{bookingId}/payments`.
- Create: `tour/src/test/java/com/j2ee/tour/controller/PaymentControllerTest.java` — route/200 assertion.
- Create/modify: `tour/src/test/java/com/j2ee/tour/service/BookingServiceTest.java`
- Modify: `tour/src/test/java/com/j2ee/tour/security/SecurityConfigTest.java`

**Interfaces:**
- Consumes: existing `PageResponse<BookingResponse>` and `PaymentResponse`.
- Produces:

```java
public record AdminBookingFilter(String status, LocalDate from, LocalDate to, String query) { }
```

`BookingService.list(pageable, principal, filter)` returns the same response type; non-admin callers are still account-scoped.

- [ ] **Step 1: Write failing filter validation tests**

In `BookingServiceTest` or the closest existing unit test fixture, add:

```java
@Test
void invalidBookingStatusReturnsBadRequest() {
    assertThrows(ApiException.class,
        () -> service.list(PageRequest.of(0, 20), adminPrincipal,
            new AdminBookingFilter("BOGUS", null, null, null)));
}

@Test
void reversedDateRangeReturnsBadRequest() {
    assertThrows(ApiException.class,
        () -> service.list(PageRequest.of(0, 20), adminPrincipal,
            new AdminBookingFilter(null, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 1), null)));
}
```

Add a `SecurityConfigTest` case: `GET /api/bookings/42/payments` as `ROLE_USER` is 403 and as anonymous is 401. Create `PaymentControllerTest.java` with `@WebMvcTest(PaymentController.class)` and `@AutoConfigureMockMvc(addFilters = false)`; mock `PaymentService.findByBooking(42L)` to return one `PaymentResponse`, call `GET /api/bookings/42/payments`, and expect status 200 and `$.data[0].status == "SUCCESS"`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd tour; .\mvnw.cmd -Dtest=BookingServiceTest,SecurityConfigTest test`
Expected: FAIL — `AdminBookingFilter` and filter-aware `list` do not exist.

- [ ] **Step 3: Implement `BookingSpecifications`**

Static methods only. Predicate rules:
- status exact match when non-blank and within `PENDING|PAID|CONFIRMED|COMPLETED|CANCELLED`, otherwise throw `ApiException(HttpStatus.BAD_REQUEST, "Invalid booking status")`;
- `from` maps to `bookingCreatedAt >= from.atStartOfDay()`; `to` maps to `< to.plusDays(1).atStartOfDay()`; reject from > to with `ApiException(HttpStatus.BAD_REQUEST, "from must be on or before to")`;
- `query` trims; if non-blank, match lowercased substring against `bookingCode`, `account.username`, `account.accountEmail`, `departure.tour.tourName`; use JPA `CriteriaBuilder` conditions, never SQL string concatenation;
- `from/to` shorter query overloads must keep existing list signatures available for call sites that do not care about filters.

- [ ] **Step 4: Make repository specification-ready and update service**

`BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking>`. In `BookingService.list`:
- normalize pageable as before;
- if `filter == null`, preserve current behavior;
- if present, compose specification with owner scope for non-admin (`account.accountId = principal.id()`) or no owner scope for admin;
- return `PageResponse.from(page, BookingResponse::from)`.

- [ ] **Step 5: Update controller binding**

`BookingController.list` accepts `status`, `from`, `to`, and `q`:

```java
@RequestParam(required = false) String status,
@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
@RequestParam(required = false) String q
```

Construct `AdminBookingFilter` after test validation. Enforce `pageable.getPageSize() <= 100` by throwing `ApiException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100")` before calling service.

- [ ] **Step 6: Add payment-history endpoint**

In `PaymentController` add:

```java
@GetMapping
public ResponseEntity<ApiResponse<List<PaymentResponse>>> payments(@PathVariable Long bookingId) {
    List<PaymentResponse> payments = paymentService.findByBooking(bookingId);
    return ResponseEntity.ok(ApiResponse.success(payments));
}
```

Inject/append `paymentService.findByBooking(bookingId)` implemented as repository `findByBookingBookingId` sorted by `paymentCreatedAt` then `paymentId`. Keep `PaymentService` constructor style; do not add a second booking relationship.

- [ ] **Step 8: Run backend filter/security tests**

Run: `cd tour; .\mvnw.cmd -Dtest=BookingServiceTest,SecurityConfigTest,PaymentControllerTest test`
Expected: PASS, with new tests included. The existing `/api/bookings/*/payments/**` admin matcher already covers `/api/bookings/42/payments`; `SecurityConfig.java` must not be edited for this route.

- [ ] **Step 9: Commit**

```bash
git add tour/src/main/java/com/j2ee/tour/dto/request/AdminBookingFilter.java \
        tour/src/main/java/com/j2ee/tour/repository/BookingSpecifications.java \
        tour/src/main/java/com/j2ee/tour/repository/BookingRepository.java \
        tour/src/main/java/com/j2ee/tour/service/BookingService.java \
        tour/src/main/java/com/j2ee/tour/controller/BookingController.java \
        tour/src/main/java/com/j2ee/tour/controller/PaymentController.java \
        tour/src/test/java/com/j2ee/tour/controller/PaymentControllerTest.java \
        tour/src/test/java/com/j2ee/tour/service/BookingServiceTest.java \
        tour/src/test/java/com/j2ee/tour/security/SecurityConfigTest.java

git commit -m "feat(admin): add filtered booking queries and payment history"
```

---

## Task 9: Frontend booking filter UI and payment history drawer

**Spec:** P2 item 7 — admin can filter/search bookings and inspect payment/notes/travelers.

**Files:**
- Modify: `fe-tour/lib/types.ts` (add only missing payment-history types if they are absent).
- Modify: `fe-tour/lib/api.ts` — `listBookings` accepts filters and `listBookingPayments`.
- Create: `fe-tour/lib/booking-list.ts`
- Create: `fe-tour/lib/booking-list.test.ts`
- Modify: `fe-tour/components/admin-bookings.tsx`
- Read-only reference: `fe-tour/lib/bookings.ts` for `statusLabel/statusTone`.

**Interfaces:**
- Consumes: `GET /api/bookings?page=&size=&status=&from=&to=&q=` and `GET /api/bookings/{bookingId}/payments` from Task 8.
- Produces: admin-bookings filter query, deterministic `buildBookingListQuery`, expanded per-row detail panel.

- [ ] **Step 1: Write failing URL-builder tests**

```ts
import { describe, expect, it } from "vitest";
import { buildBookingListQuery } from "./booking-list";

it("omits empty filters and trims query", () => {
  expect(buildBookingListQuery({ page: 0, size: 20 })).toBe("page=0&size=20");
  expect(buildBookingListQuery({ page: 1, size: 20, status: "PAID", q: "  han-01  " }))
    .toBe("page=1&size=20&status=PAID&q=han-01");
});

it("rejects reversed date ranges", () => {
  expect(() => buildBookingListQuery({ page: 0, size: 20, from: "2026-11-20", to: "2026-11-01" }))
    .toThrow("from must be on or before to");
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd fe-tour; npm test -- lib/booking-list.test.ts`
Expected: FAIL — `buildBookingListQuery` is not defined.

- [ ] **Step 3: Implement `booking-list.ts`**

Export:

```ts
export type BookingListFilters = {
  status?: "" | "PENDING" | "PAID" | "CONFIRMED" | "COMPLETED" | "CANCELLED";
  from?: string;
  to?: string;
  q?: string;
};
```

`buildBookingListQuery({ page, size, ...filters })` validates status and from/to only via date-string lexical comparison (`YYYY-MM-DD`), trims query, omits empty filters/deletes and encodes values with `URLSearchParams`.

- [ ] **Step 4: Extend `listBookings` without breaking callers**

```ts
export function listBookings(
  token: string,
  page = 0,
  size = 10,
  filters?: BookingListFilters,
): Promise<PageData<Booking>>
```

Callers that omit filters retain current URLs. Add:

```ts
export function listBookingPayments(token: string, bookingId: number): Promise<Payment[]> {
  return apiRequest<Payment[]>(`/api/bookings/${bookingId}/payments`, { token });
}
```

- [ ] **Step 5: Add filter bar and expandable row detail to `AdminBookings`**

UI requirements:
- filters: status select, from date, to date, text search `q`, page-size select (`10`, `20`, `50`), fixed `Apply` and `Clear` buttons;
- reset page to 0 when filters or page size change;
- pagination footer shows `Page {page + 1} of {totalPages}` and `{totalElements} bookings`, with Previous/Next disabled at edges;
- each row has `Details` toggle;
- details panel shows booking note or `No note.`; travelers list (`name`, DOB, email, phone); promotion name/code if present; and a `View invoice` link to `/admin/invoices/${booking.id}` (disabled copy `No invoice yet` is not knowable until opened, so this link is always available for admin lookup);
- `Payment history` button loads `/api/bookings/{id}/payments` once and renders amount, method, status, paidAt/createdAt in a nested table; empty state `No payments recorded.`;
- leave existing Record cash/Confirm/Complete/Cancel buttons and their allowed states unchanged.

- [ ] **Step 6: Run booking-list tests and typecheck**

Run: `cd fe-tour; npm test -- lib/booking-list.test.ts; npx tsc --noEmit`
Expected: tests PASS and `Found 0 errors`.

- [ ] **Step 7: Commit**

```bash
git add fe-tour/lib/booking-list.ts fe-tour/lib/booking-list.test.ts fe-tour/lib/api.ts fe-tour/components/admin-bookings.tsx

git commit -m "feat(admin): add booking filters and payment details"
```

---

## Task 10: Add invoice admin read/print UI

**Spec:** P2 item 11 — admin invoice lookup and print view, without new financial semantics.

**Files:**
- Modify: `fe-tour/lib/api.ts` — admin invoice read functions.
- Modify: `fe-tour/lib/types.ts` — no new invoice fields required.
- Create: `fe-tour/lib/admin-invoices.ts`
- Create: `fe-tour/lib/admin-invoices.test.ts`
- Create: `fe-tour/components/admin-invoices.tsx`
- Modify: `fe-tour/components/invoice-view.tsx` — controlled `mode` and `backHref`.
- Create: `fe-tour/app/admin/invoices/page.tsx`
- Create: `fe-tour/app/admin/invoices/[bookingId]/page.tsx`
- Modify: `fe-tour/app/admin/layout.tsx`

**Interfaces:**
- Consumes: `GET /api/invoices?page=&size=`, `GET /api/invoices/by-booking/{bookingId}`, `GET /api/bookings/{id}`; all invoice endpoints are admin-only.
- Produces: admin routes `/admin/invoices` and `/admin/invoices/[bookingId]`; `InvoiceView` gains optional `mode?: "mine" | "admin"` and `backHref?: string` props.

- [ ] **Step 1: Write failing invoice-list filter tests**

```ts
import { describe, expect, it } from "vitest";
import { filterInvoices } from "./admin-invoices";
import type { Invoice } from "./types";

it("matches status and case-insensitive customer/email/booking id", () => {
  const rows = [
    { id: 11, bookingId: 7, createdAt: "2026-11-17T08:00:00", status: "PAID", discountAmount: 0, taxAmount: 0, serviceFee: 0, totalAmount: 500, paymentMethod: "CASH", customerName: "An", customerPhone: null, customerEmail: "AN@EXAMPLE.COM", promotionIds: [] },
    { id: 12, bookingId: 8, createdAt: "2026-11-17T09:00:00", status: "UNPAID", discountAmount: 0, taxAmount: 0, serviceFee: 0, totalAmount: 900, paymentMethod: "CARD", customerName: "Bình", customerPhone: null, customerEmail: "binh@example.com", promotionIds: [] },
  ] as Invoice[];
  expect(filterInvoices(rows, { status: "PAID", query: "an@" })).toHaveLength(1);
  expect(filterInvoices(rows, { status: "All", query: "8" })).toHaveLength(1);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd fe-tour; npm test -- lib/admin-invoices.test.ts`
Expected: FAIL — `filterInvoices` not defined.

- [ ] **Step 3: Implement `filterInvoices`**

Export `filterInvoices(rows, { status, query })` matching exact status (`All|PAID|UNPAID`) and substring against `bookingId.toString()`, `customerName`, `customerEmail`, `paymentMethod`.

- [ ] **Step 4: Add admin invoice API functions**

Add:

```ts
export function listInvoices(token: string, page = 0, size = 100): Promise<PageData<Invoice>> {
  return apiRequest<PageData<Invoice>>(`/api/invoices?page=${page}&size=${size}`, { token });
}
export function getInvoiceByBooking(token: string, bookingId: number): Promise<Invoice> {
  return apiRequest<Invoice>(`/api/invoices/by-booking/${bookingId}`, { token });
}
```

Do not add create/update/delete/void/reissue functions in this task.

- [ ] **Step 5: Extend `InvoiceView` safely**

Change props to:

```ts
export function InvoiceView({ bookingId, mode = "mine", backHref }: {
  bookingId: number;
  mode?: "mine" | "admin";
  backHref?: string;
})
```

In `admin` mode:
- fetch `getBooking(...)` and `getInvoiceByBooking(...)`;
- do **not** call `listMyInvoices`;
- send unauthenticated/unauthorized API errors to `/admin` via `clearSession()` only when status is 401, or show the existing error EmptyState for 403/404/other errors;
- use `backHref ?? "/admin/invoices"` for the back button;
- change heading label to `Admin invoice` while preserving the printable document and `window.print()` button.

Member `mine` behavior must remain byte-compatible for current call sites.

- [ ] **Step 6: Build `AdminInvoices` list component and pages**

`/admin/invoices` columns: Invoice, Booking, Created, Customer, Payment, Total, Status, Action. Filters: status select and text search. Each action links to `/admin/invoices/{bookingId}` and reads opened invoices. Empty state `No invoices yet.` Pagination is read-only Next/Previous using page state and `hasNext`.

`/admin/invoices/[bookingId]/page.tsx` must:
- `const { bookingId } = await params;`
- parse with `Number(bookingId)`; invalid values return `notFound()`;
- render `<InvoiceView bookingId={bookingId} mode="admin" backHref="/admin/invoices" />`.

- [ ] **Step 7: Add nav link and run invoice tests/typecheck**

Add `<Link href="/admin/invoices">Invoices</Link>` after Promotions.

Run: `cd fe-tour; npm test -- lib/admin-invoices.test.ts; npx tsc --noEmit`
Expected: tests PASS and `Found 0 errors`.

- [ ] **Step 8: Commit**

```bash
git add fe-tour/lib/api.ts fe-tour/lib/admin-invoices.ts fe-tour/lib/admin-invoices.test.ts \
        fe-tour/components/invoice-view.tsx fe-tour/components/admin-invoices.tsx \
        fe-tour/app/admin/invoices fe-tour/app/admin/layout.tsx

git commit -m "feat(admin): add invoice lookup and print view"
```

---

## Task 11: Catalog typed API and normalization helpers

**Spec:** P2 item 12 — backend catalog CRUD from admin console, minus customers/travelers for privacy and minus tour-relation editing/Cloudinary upload.

**Files:**
- Modify: `fe-tour/lib/types.ts`
- Modify: `fe-tour/lib/api.ts`
- Create: `fe-tour/lib/admin-catalog.ts`
- Create: `fe-tour/lib/admin-catalog.test.ts`
- Read-only reference: `tour/src/main/java/com/j2ee/tour/dto/request/TouristDestinationRequest.java`
- Read-only reference: `tour/src/main/java/com/j2ee/tour/dto/request/AccommodationRequest.java`
- Read-only reference: `tour/src/main/java/com/j2ee/tour/dto/request/TravelVehicleRequest.java`
- Read-only reference: `tour/src/main/java/com/j2ee/tour/dto/request/TourGuideRequest.java`
- Read-only reference: `tour/src/main/java/com/j2ee/tour/dto/request/TourImageRequest.java`

**Interfaces:**
- Consumes: exact field names:
  - destinations: `name`, `location`, `description`, `entryFee`
  - accommodations: `name`, `location`, `rating`, `pricePerNight`, `type` (`HOTEL|RESORT|HOMESTAY`)
  - vehicles: `type`, `capacity`, `rentalPricePerDay`
  - guides: `name`, `email`, `phone`, `experienceYears`
  - images: `imageUrl`, `tourId`
- Produces:

```ts
export type CatalogResource = "destinations" | "accommodations" | "vehicles" | "guides" | "images";
export type CatalogRow = DestinationRow | AccommodationRow | VehicleRow | GuideRow | ImageRow;
```

Names may be adjusted during implementation, but request bodies must match backend exactly.

- [ ] **Step 1: Write failing catalog conversion tests**

```ts
import { describe, expect, it } from "vitest";
import { catalogInput, catalogResourceFromPath } from "./admin-catalog";

it("converts numeric catalog fields to API numbers", () => {
  expect(catalogInput("vehicles", { type: "Bus", capacity: "40", rentalPricePerDay: "3000000" })).toEqual({
    type: "Bus", capacity: 40, rentalPricePerDay: 3000000,
  });
});

it("maps resource names to API paths", () => {
  expect(catalogResourceFromPath("destinations")).toEqual({ path: "/api/tourist-destinations", idField: "id" });
  expect(catalogResourceFromPath("images")).toEqual({ path: "/api/tour-images", idField: "id" });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd fe-tour; npm test -- lib/admin-catalog.test.ts`
Expected: FAIL — helpers do not exist.

- [ ] **Step 3: Implement typed rows and conversions**

Add interfaces for all five row types using exact backend response names. Implement:

```ts
export function catalogResourceFromPath(resource: CatalogResource): { path: string; idField: "id" }
export function catalogInput(resource: CatalogResource, form: Record<string, string>): CatalogInput
```

Use trimmed strings; convert numeric fields with `Number(...)`; reject empty required fields inside `catalogInput` with `Error("... required")` matching the mandatory field; for `images`, require numeric `tourId`.

- [ ] **Step 4: Add API functions**

Add to `api.ts`:

```ts
listDestination/createDestination/updateDestination/deleteDestination(
  accommodation equivalent,
  vehicle equivalent,
  guide equivalent,
  image equivalent,
)
```

All list calls use `page=0&size=100`. Add `deleteCatalogResource(resource, token, id)` only if the generated TypeScript types stay exact; otherwise keep explicit typed functions.

- [ ] **Step 5: Run catalog helper tests**

Run: `cd fe-tour; npm test -- lib/admin-catalog.test.ts`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add fe-tour/lib/types.ts fe-tour/lib/api.ts fe-tour/lib/admin-catalog.ts fe-tour/lib/admin-catalog.test.ts

git commit -m "feat(admin): add catalog API helpers"
```

---

## Task 12: Build tabbed `/admin/catalog` CRUD console

**Spec:** P2 item 12 — catalog CRUD UI for destinations/accommodations/vehicles/guides/images.

**Files:**
- Create: `fe-tour/app/admin/catalog/page.tsx`
- Create: `fe-tour/components/admin-catalog.tsx`
- Modify: `fe-tour/app/admin/layout.tsx`
- Defer: delete referenced rows must show the backend's 409 message; no backend change in this task.

**Interfaces:**
- Consumes: Task 11 helpers/APIs and `listAdminTours` for the image tour dropdown.
- Produces: `/admin/catalog` route and admin nav item `Catalog`.

- [ ] **Step 1: Write failing tab persistence test**

Extend `lib/admin-catalog.test.ts`:

```ts
it("returns only supported resources in tab order", () => {
  expect(catalogTabs().map((tab) => tab.resource)).toEqual([
    "destinations", "accommodations", "vehicles", "guides", "images",
  ]);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd fe-tour; npm test -- lib/admin-catalog.test.ts`
Expected: FAIL — `catalogTabs` not exported.

- [ ] **Step 3: Implement `catalogTabs`**

Export `catalogTabs()` returning the exact five ordered tabs and labels `Destinations`, `Accommodations`, `Travel vehicles`, `Tour guides`, `Tour images`.

- [ ] **Step 4: Create route and nav link**

`fe-tour/app/admin/catalog/page.tsx` sets metadata title `Admin catalog` and renders `<AdminCatalog />`. Add nav link `<Link href="/admin/catalog">Catalog</Link>` after Invoices.

- [ ] **Step 5: Build `AdminCatalog` tabs**

The component must contain:
- one selected resource tab at a time using normal buttons with `aria-pressed`;
- load the selected resource's rows from its typed API; images also loads admin tours with `listAdminTours(token, 0, 100)`;
- generic add/edit form with field config: text/number/select/textarea fields per resource;
- destinations: name, location, description, entryFee;
- accommodations: name, location, rating, pricePerNight, type select;
- vehicles: type, capacity, rentalPricePerDay;
- guides: name, email, phone, experienceYears;
- images: imageUrl and a tour `<select>` populated with tour names;
- row actions: Edit, Delete; delete uses two-click inline confirmation (`Click again to delete`), never browser `confirm`;
- on any `ApiError`, show `error.message` verbatim; for 409 conflicts, append `This record is referenced elsewhere and cannot be deleted.`;
- success messages: `Saved.` and `Deleted.`;
- empty states name the resource (`No destinations yet.`, etc.).

- [ ] **Step 6: Typecheck the complete catalog feature**

Run: `cd fe-tour; npm test -- lib/admin-catalog.test.ts; npx tsc --noEmit; npm run lint`
Expected: tests PASS, `Found 0 errors`, ESLint exits 0.

- [ ] **Step 7: Commit**

```bash
git add fe-tour/lib/admin-catalog.ts fe-tour/lib/admin-catalog.test.ts \
        fe-tour/app/admin/catalog fe-tour/components/admin-catalog.tsx fe-tour/app/admin/layout.tsx

git commit -m "feat(admin): add catalog management console"
```

---

## Task 13: Update checklist and verify the complete admin slice

**Files:**
- Modify: `docs/admin-gap-checklist.md`
- Create/replace: `docs/superpowers/plans/2026-11-17-admin-operations.md` only if self-review fixes require it.

- [ ] **Step 1: Update only verified checklist boxes**

Do **not** check the six top-level feature boxes merely because tasks landed. Append a `## 2026-11-17 implementation scope` note to `docs/admin-gap-checklist.md` with explicit delivered/deferred bullets. If the note is written, the top-level checkboxes for P1 items 1, 2, 3 and P2 items 7, 11, 12 stay unchecked unless every bullet under that feature is verified; the note is the source of truth for partial delivery.

Delivered by this plan must be listed as:
- P1 item 1: stats endpoint, `/admin` dashboard, status map, revenue/payment cards, upcoming/low-capacity/sold-out cards, `topTours`, `capacityRisks`.
- P1 item 2: `/admin/accounts`, list/search/filter, role/status/email-verification edits, locked admin safeguard, set-temporary-password flow.
- P1 item 3: `/admin/promotions`, list/filter/create/edit/delete, discount/usage/date/status fields.
- P2 item 7: booking status/date/query filters, total-pages/page-size footer, expandable booking details, payment-history drawer, admin invoice link.
- P2 item 11: `/admin/invoices` search and open/print detail via admin endpoints.
- P2 item 12: `/admin/catalog` CRUD for destinations, accommodations, travel vehicles, tour guides and tour images; 409 conflict handling.

Explicitly leave these deferred/unchecked, with the reason copied into the note:
- P1 item 1: recent bookings panel; failed payments panel; low-selling tours and capacity-utilization percentage; stats date-range filter; SQL-fixture/DB verification.
- P1 item 2: none deferred by this plan after email-verification editing is included.
- P1 item 3: none deferred.
- P2 item 7: dedicated tour/departure selector; exact-account dropdown (the keyword search covers username/email/tour); traveler phone search; sort control; distinct payment-status filter.
- P2 item 11: search by booking code (`InvoiceResponse` carries `bookingId`, not booking code); date-range filter; PDF download beyond browser print; invoice void/reissue.
- P2 item 12: tour-relation selectors in the tour form (P1 item 5); customers/travelers viewer (marked `if operationally needed` and exposes personal data).
- P1 item 1 “decision on notification preview/resend” belongs to deferred notifications.


- [ ] **Step 2: Run targeted frontend verification**

Run:

```powershell
cd fe-tour
npm test -- lib/admin-stats.test.ts lib/admin-accounts.test.ts lib/promotions.test.ts `
            lib/booking-list.test.ts lib/admin-invoices.test.ts lib/admin-catalog.test.ts
npx tsc --noEmit
npm run lint
```

Expected: all tests pass, TypeScript exits 0, ESLint exits 0.

- [ ] **Step 3: Run production build**

Run: `cd fe-tour; npm run build`
Expected: `next build` completes without errors.

- [ ] **Step 4: Run backend non-DB verification**

Run:

```powershell
cd tour
.\mvnw.cmd -Dtest=AdminStatsServiceTest,AccountServiceTest,BookingServiceTest,SecurityConfigTest test
```

Expected: selected tests pass. Integration/DB-backed Flyway tests are not run in this phase; do not run them against the remote Supabase `.env`.

- [ ] **Step 5: Commit checklist update**

```bash
git add docs/admin-gap-checklist.md docs/superpowers/plans/2026-11-17-admin-operations.md

git commit -m "docs(admin): record admin operations MVP checklist"
```

- [ ] **Step 6: Final manual smoke path**

With both apps running and an `ROLE_ADMIN` seed account:

1. Sign in → `/admin` → non-zero stat cards/no crash.
2. `/admin/accounts` → filter by role/status; try disabling the last admin → conflict message.
3. `/admin/promotions` → create a disabled promotion, edit discount, delete it.
4. `/admin/bookings` → filter status=`PAID`, date range, query a booking code; open details and payment history.
5. `/admin/invoices` → open the booking invoice and print preview.
6. `/admin/catalog` → create and delete one destination no other table references; attempt deleting a referenced destination and confirm 409 copy.

Report which smoke steps were or were not runnable and why.

---

## Self-review

**Spec coverage:**

| Spec item | Task(s) |
|---|---|
| P1 #1 Dashboard endpoint/UI | 1, 2 — MVP only; defer list in Task 13 |
| P1 #2 Account list/lock/role/reset | 3, 4, 5 — email-verification and temp-password flow included |
| P1 #3 Promotion list/create/edit/delete | 6, 7 |
| P2 #7 Booking status/date/query filters, pagination, payment history, details | 8, 9 — page size included; tour/account/phone/sort/payment-status filter deferred |
| P2 #11 Admin invoice lookup, print | 10 — code search, date range/PDF/void/reissue deferred |
| P2 #12 Catalog CRUD | 11, 12 — tour-relation selectors and customers/travelers deferred |

Explicitly not covered: checklist items 5, 6, 8, 9, 10, 13–18 and deferred subparts named in Task 13; the user did not select them.

**Type consistency:**
- `AdminStats` shape used by `lib/types.ts`, `lib/admin-stats.ts`, `components/admin-dashboard.tsx`.
- `BookingListFilters` is shared by `lib/booking-list.ts` and `lib/api.ts`; `listBookings` remains backward compatible.
- backend `AdminStatsResponse` member names match frontend exactly: `generatedAt`, `totalTours`, `openTours`, `bookingsByStatus`, `successfulRevenue`, `successfulPayments`, `averageSuccessfulPayment`, `upcomingDepartures`, `lowCapacityDepartures`, `soldOutDepartures`, `topTours`, `capacityRisks`.
- backend `AdminBookingFilter` is the validation boundary; frontend uses `BookingListFilters` + `buildBookingListQuery`.
- catalog request member names match backend records exactly.

**Global Constraints compliance:**
- No schema/Flyway changes.
- No raw SQL string concatenation.
- No test DB against shared Supabase.
- No financial invoice mutations/void/reissue semantics introduced.
- No tour relation editing/Cloudinary upload work mixed in.
- Partial implementations do not get checked off; Task 13 records delivered and deferred scope explicitly.

**Review Focus mapping:**
- Empty stats zero-state → Task 1 Step 1/Step 7.
- Invalid arbitrary filters/from > to → Task 8 Step 1/Step 3.
- Last active admin protection → Task 4 Step 1/Step 5, Task 5 Step 5.
- Referenced catalog delete → Task 12 Step 5, Task 13 smoke step 6.
- Admin-only stats/payment history → Task 8 Steps 1, 6, 7; Task 13 verification.
