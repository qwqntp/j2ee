# Review 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the two blocking findings from the whole-repo review (500s for 404/405, and a database that cannot bootstrap from migrations) and clear the remaining should-fix and low-cost cleanup items.

**Architecture:** Backend fixes stay inside the existing layers: two handlers in `GlobalExceptionHandler`, a Flyway callback plus one idempotent baseline migration, one repository query change for tour detail, one join fetch for the admin list, and one scheduled purge. Frontend work adds a minimal Vitest setup for pure helpers, then three surgical edits (contrast, images, dependencies).

**Tech Stack:** Spring Boot 4.1.1, Java 21, PostgreSQL, Flyway 11, Next.js 16.4.3, React 19, TypeScript, Tailwind CSS 4, Vitest (dev-only).

**Spec:** This document. The findings table below is the source of truth; each task references one or more finding numbers.

## Global Constraints

- Existing migration `V1__create_refresh_tokens.sql` must not be edited or renamed: the shared Supabase database has version `1` applied, and a checksum change blocks startup.
- Keep `spring.jpa.hibernate.ddl-auto=validate`. Never switch to `update` or `create`.
- Keep the `ApiResponse` envelope for every API response, including errors.
- No new production dependencies. Vitest is the only new dependency and stays in `devDependencies`.
- Do not change money or date semantics: money is VND, dates are `LocalDateTime` wall-clock in `Asia/Ho_Chi_Minh`.
- Backend commands run from `tour/` with `.\mvnw.cmd`. Frontend commands run from `fe-tour/` with `npm`.
- `TourApplicationTests.contextLoads` stays skipped; it needs PostgreSQL. All other tests must pass.
- Commit after each task. Keep commits small enough to revert one task.

## Review Findings

| # | Finding | Severity | Task |
|---|---|---|---|
| 1 | Unknown paths and wrong HTTP methods return 500 instead of 404/405 | Must fix | 1 |
| 2 | A fresh database cannot be created from migrations; only `V1` exists and it depends on `accounts` | Must fix | 2 |
| 3 | No database unique constraint for case-insensitive email; concurrent registration can duplicate | Should fix | 3 |
| 4 | Frontend has no automated tests | Should fix | 7 |
| 5 | Tour detail loads every departure and filters in memory | Should fix | 4 |
| 6 | Admin account list may issue one role select per row | Should fix | 5 |
| 7 | Expired refresh tokens are never deleted | Should fix | 6 |
| 8 | Seven backend dependencies are declared but unused | Nice to have | 8 |
| 9 | `OPEN`/`FULL` constants are duplicated in `TourService` and `TourMapper` | Nice to have | 4 (removed with the filter) |
| 10 | Footer bottom bar text is about 3.4:1 contrast, below WCAG AA | Nice to have | 9 |
| 11 | Hero and CTA JPEGs are unoptimized (797 KB and 447 KB) | Nice to have | 10 |

## Review Focus

These are the boundary cases a reviewer should check first. Each one is pinned by a test or verification step in the named task.

1. A request to an unknown path returns 404, not 500 — Task 1.
2. A request with the wrong HTTP method on a real path returns 405, not 500 — Task 1.
3. An empty database starts cleanly and ends with all 20 application tables — Task 2.
4. Two accounts with emails differing only by case are rejected by the database — Task 3.
5. The purge cutoff is exactly `now` from the injected clock, not a hard-coded TTL — Task 6.

---

### Task 1: Return 404 and 405 instead of 500

**Files:**
- Modify: `tour/src/main/java/com/j2ee/tour/common/GlobalExceptionHandler.java`
- Modify: `tour/src/test/java/com/j2ee/tour/controller/TourControllerTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `handleNotFound(Exception)` and `handleMethodNotAllowed(HttpRequestMethodNotSupportedException)`, both returning `ResponseEntity<ApiResponse<Void>>`.

- [ ] **Step 1: Add the two failing tests**

Append these tests to `TourControllerTest` (import `post` from `MockMvcRequestBuilders`):

```java
@Test
void unknownPathReturnsNotFound() throws Exception {
    mockMvc.perform(get("/api/does-not-exist"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
}

@Test
void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
    mockMvc.perform(post("/api/tours"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.success").value(false));
}
```

- [ ] **Step 2: Run the tests to confirm they fail with 500**

Run: `.\mvnw.cmd test "-Dtest=TourControllerTest"`
Expected: FAIL. Both new tests report status 500 instead of 404/405.

- [ ] **Step 3: Add the handlers**

Add these imports to `GlobalExceptionHandler`:

```java
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
```

Add these two methods above the catch-all handler:

```java
@ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.failure("Resource not found"));
}

@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
        HttpRequestMethodNotSupportedException exception) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ApiResponse.failure("Method not allowed"));
}
```

Spring picks the most specific handler, so the catch-all no longer sees either exception type. Do not catch them inside the catch-all method.

- [ ] **Step 4: Run the tests to confirm they pass**

Run: `.\mvnw.cmd test "-Dtest=TourControllerTest"`
Expected: PASS.

- [ ] **Step 5: Run the full backend suite**

Run: `.\mvnw.cmd test`
Expected: all tests pass; `TourApplicationTests.contextLoads` is still skipped.

- [ ] **Step 6: Commit**

```powershell
git add tour/src/main/java/com/j2ee/tour/common/GlobalExceptionHandler.java tour/src/test/java/com/j2ee/tour/controller/TourControllerTest.java
git commit -m "fix: return 404 and 405 instead of 500"
```

---

### Task 2: Bootstrap a fresh database from Flyway

**Why this shape:** `V1` is already applied on the shared database and creates `refresh_tokens` with a foreign key to `accounts`. On an empty database, `V1` runs first and fails because `accounts` does not exist yet. Editing `V1` breaks the shared database. The smallest safe fix is a `beforeMigrate` callback that creates only the two tables `V1` needs, then a baseline migration that creates the remaining 17 tables. `IF NOT EXISTS` makes the baseline a no-op on the shared database, which already has every table.

**Files:**
- Create: `tour/src/main/resources/db/callback/beforeMigrate.sql`
- Create: `tour/src/main/resources/db/migration/V2__create_schema.sql`
- Modify: `tour/src/main/resources/application.properties`
- Modify: `tour/src/main/resources/db/quanlybantour_booking_floor.sql`

**Interfaces:**
- Consumes: nothing.
- Produces: a migrated schema with 20 application tables (`19` from the manual schema plus `refresh_tokens`), usable by Tasks 3–6.

- [ ] **Step 1: Create the callback with the two prerequisite tables**

Copy the `account_roles` and `accounts` DDL from `quanlybantour_booking_floor.sql:15-33` into `db/callback/beforeMigrate.sql`. Change both to `CREATE TABLE IF NOT EXISTS public.<name>`. Keep the columns, defaults, foreign key, and checks exactly as they are. Add this header:

```sql
-- V1 creates refresh_tokens with a FK to accounts. On an empty database V1 runs
-- first, so these two tables must exist before migrations start.
```

Do not create indexes, the trigger, or any other table here.

- [ ] **Step 2: Create `V2__create_schema.sql`**

Copy from `quanlybantour_booking_floor.sql:35-319` (everything after `accounts`), with these edits:

- `CREATE TABLE` → `CREATE TABLE IF NOT EXISTS`
- `CREATE INDEX` → `CREATE INDEX IF NOT EXISTS`
- `CREATE UNIQUE INDEX` → `CREATE UNIQUE INDEX IF NOT EXISTS`
- Keep `CREATE OR REPLACE FUNCTION set_account_updated_at()`.
- Before `CREATE TRIGGER trg_accounts_updated_at`, add `DROP TRIGGER IF EXISTS trg_accounts_updated_at ON public.accounts;`
- Replace the `ALTER TABLE invoices ADD CONSTRAINT fk_invoice_booking ...` block with:

```sql
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_invoice_booking'
          AND conrelid = 'public.invoices'::regclass
    ) THEN
        ALTER TABLE public.invoices
            ADD CONSTRAINT fk_invoice_booking
            FOREIGN KEY (booking_id) REFERENCES public.bookings (booking_id);
    END IF;
END $$;
```

Add this comment at the top:

```sql
-- Baseline for databases that predate Flyway. IF NOT EXISTS keeps this a no-op
-- on the shared database; fresh databases get the full schema.
```

Do not copy any `INSERT` statements into this migration.

- [ ] **Step 3: Register the callback folder**

In `application.properties`, change:

```properties
spring.flyway.locations=classpath:db/migration
```

to:

```properties
spring.flyway.locations=classpath:db/migration,classpath:db/callback
```

Leave `baseline-on-migrate` and `baseline-version` unchanged.

- [ ] **Step 4: Make the manual SQL file seed-only**

Edit `tour/src/main/resources/db/quanlybantour_booking_floor.sql`:

- Delete everything from `-- PHAN 1: BANG HIEN CO` through the end of the `refunds` index block.
- Keep the header and the seed section (`INSERT` statements and `setval` calls).
- Replace the header with:

```sql
-- QUANLYBANTOUR - DEV SEED ONLY (PostgreSQL)
-- Run once on an empty database AFTER Flyway migrations:
--   psql -U <user> -d <database> -f quanlybantour_booking_floor.sql
-- Schema lives in db/migration and db/callback. Do not add DDL here.
```

Do not make the seed idempotent; it is a one-shot dev file and the header says so.

- [ ] **Step 5: Verify against an empty database**

Create a disposable database (local PostgreSQL, or a throwaway Supabase project — never the shared database):

```powershell
createdb -U postgres tour_migration_check
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/tour_migration_check"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="postgres"
cd tour
.\mvnw.cmd spring-boot:run
```

Expected: the application starts, Flyway applies `V1` and `V2` without error, and Hibernate schema validation passes. Then:

```powershell
psql -U postgres -d tour_migration_check -c "\dt public.*"
```

Expected: 20 application tables plus `flyway_schema_history`. Stop the application. If PostgreSQL is unavailable, stop and report that this task is unverified; do not claim it works.

- [ ] **Step 6: Verify the seed file**

```powershell
psql -U postgres -d tour_migration_check -f src/main/resources/db/quanlybantour_booking_floor.sql
```

Expected: inserts succeed with no "relation does not exist" errors.

- [ ] **Step 7: Verify the shared-database upgrade path**

Start the application with the normal `.env` against the shared database. Expected: Flyway applies `V2` once, no data changes, and startup succeeds. If the shared database already reports a failed migration, stop and report it; do not run `repair` blindly.

- [ ] **Step 8: Commit**

```powershell
git add tour/src/main/resources/db tour/src/main/resources/application.properties
git commit -m "fix: bootstrap the full schema with Flyway"
```

---

### Task 3: Add the case-insensitive email unique index

**Files:**
- Create: `tour/src/main/resources/db/migration/V3__unique_account_email.sql`
- Modify: `tour/src/main/java/com/j2ee/tour/service/AuthService.java`
- Modify: `docs/progress.md`

**Interfaces:**
- Consumes: `accounts` from Task 2.
- Produces: index `uq_accounts_email_lower`. `GlobalExceptionHandler` already maps `DataIntegrityViolationException` to 409, so no new controller code.

- [ ] **Step 1: Create the migration**

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uq_accounts_email_lower
    ON public.accounts (lower(account_email))
    WHERE account_email IS NOT NULL;
```

- [ ] **Step 2: Update the stale comment and doc**

In `AuthService.register`, replace the line `// Known race: two concurrent registrations can both pass this check.` with:

```java
// The app check gives a friendly message; the DB unique index is the final guard.
```

In `docs/progress.md`, replace the sentence that says there is no unique database constraint with a sentence stating that `uq_accounts_email_lower` now enforces it. Keep the note that the service check is still the friendly path.

- [ ] **Step 3: Verify the index and the conflict**

Run against the Task 2 database:

```powershell
psql -U postgres -d tour_migration_check -c "SELECT indexdef FROM pg_indexes WHERE indexname = 'uq_accounts_email_lower';"
```

Expected: one row showing the `lower(account_email)` unique index.

Before deploying V3 to the shared database, run this query and expect zero rows:

```sql
SELECT lower(account_email), count(*)
FROM accounts
WHERE account_email IS NOT NULL
GROUP BY 1
HAVING count(*) > 1;
```

If rows exist, resolve the duplicates first; V3 will fail at startup otherwise.

Then confirm the database rejects duplicates differing only by case (run each insert separately; the second must fail):

```sql
INSERT INTO accounts (username, password, email_verified, status, account_created_at, account_updated_at, role_id, account_email)
VALUES ('case-test-1', 'x', 0, 1, NOW(), NOW(),
        (SELECT account_role_id FROM account_roles WHERE role_name = 'ROLE_USER'), 'Case@Test.com');
INSERT INTO accounts (username, password, email_verified, status, account_created_at, account_updated_at, role_id, account_email)
VALUES ('case-test-2', 'x', 0, 1, NOW(), NOW(),
        (SELECT account_role_id FROM account_roles WHERE role_name = 'ROLE_USER'), 'case@test.com');
```

Expected: the second insert fails with `duplicate key value violates unique constraint "uq_accounts_email_lower"`. Delete the first row afterward if the database is reused.

- [ ] **Step 4: Run the backend suite**

Run: `.\mvnw.cmd test`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add tour/src/main/resources/db/migration/V3__unique_account_email.sql tour/src/main/java/com/j2ee/tour/service/AuthService.java docs/progress.md
git commit -m "fix: enforce unique account email in the database"
```

---

### Task 4: Filter tour departures in the database

**Files:**
- Modify: `tour/src/main/java/com/j2ee/tour/service/TourService.java`
- Modify: `tour/src/main/java/com/j2ee/tour/mapper/TourMapper.java`
- Modify: `tour/src/test/java/com/j2ee/tour/service/TourServiceTest.java`

**Interfaces:**
- Consumes: `TourDepartureRepository.findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(Long, Collection<String>, LocalDateTime)`.
- Produces: `TourMapper.toDetail(Tour tour, List<TourDeparture> departures)`; the `now` parameter is removed.

- [ ] **Step 1: Rewrite the failing test**

Replace `getTourIncludesOnlyUpcomingOpenAndFullDepartures` with:

```java
@Test
void getTourUsesUpcomingOpenAndFullDeparturesQuery() {
    Tour tour = tour();
    List<TourDeparture> departures = List.of(
            departure(1L, "OPEN", LocalDateTime.of(2026, 11, 1, 8, 0)),
            departure(2L, "FULL", LocalDateTime.of(2026, 11, 5, 8, 0)));
    when(tourRepository.findByTourIdAndTourStatus(1L, "OPEN")).thenReturn(Optional.of(tour));
    when(departureRepository
            .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                    eq(1L), eq(List.of("OPEN", "FULL")), any(LocalDateTime.class)))
            .thenReturn(departures);

    TourResponse result = tourService.getTour(1L);

    assertThat(result.departures())
            .extracting(TourDepartureResponse::id)
            .containsExactly(1L, 2L);
    verify(departureRepository)
            .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                    eq(1L), eq(List.of("OPEN", "FULL")), eq(NOW));
}
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `.\mvnw.cmd test "-Dtest=TourServiceTest"`
Expected: FAIL, because `getTour` still reads `tour.getDepartures()` and never calls the repository query.

- [ ] **Step 3: Change the service and mapper**

In `TourService`, add a private helper and use it in both public methods:

```java
private List<TourDeparture> upcomingDepartures(Long tourId) {
    return departureRepository
            .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                    tourId, PUBLIC_DEPARTURE_STATUSES, now());
}
```

In `getTour`, replace `TourMapper.toDetail(tour, now())` with:

```java
return TourMapper.toDetail(tour, upcomingDepartures(tourId));
```

In `getDepartures`, replace the repository call with `upcomingDepartures(tourId).stream()`.

In `TourMapper`, change the signature to `toDetail(Tour tour, List<TourDeparture> departures)` and replace the filtered/sorted stream with this block, then pass `responses` as the last argument of `new TourResponse(...)`:

```java
public static TourResponse toDetail(Tour tour, List<TourDeparture> departures) {
    ...
    List<TourDepartureResponse> responses = departures.stream()
            .map(departure -> toDeparture(tour.getTourId(), departure))
            .toList();
    ...
}
```

Remove `PUBLIC_DEPARTURE_STATUSES`, the `Comparator` import, and the now-unused `LocalDateTime` import from `TourMapper`. This removes the duplicate status constant (finding 9).

- [ ] **Step 4: Run the tests**

Run: `.\mvnw.cmd test "-Dtest=TourServiceTest"`
Expected: PASS.

Run: `.\mvnw.cmd test`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add tour/src/main/java/com/j2ee/tour/service/TourService.java tour/src/main/java/com/j2ee/tour/mapper/TourMapper.java tour/src/test/java/com/j2ee/tour/service/TourServiceTest.java
git commit -m "perf: filter tour departures in the database"
```

---

### Task 5: Fetch roles with the admin account list

**Files:**
- Modify: `tour/src/main/java/com/j2ee/tour/repository/AccountRepository.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `Page<Account> findAll(Pageable pageable)` with a role fetch graph.

- [ ] **Step 1: Add the fetch graph**

Add the import and override to `AccountRepository`:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

@EntityGraph(attributePaths = "role")
Page<Account> findAll(Pageable pageable);
```

Keep the existing derived query methods.

- [ ] **Step 2: Verify the query shape**

Start the application against a database with at least two accounts, enable SQL logging for the check only (`spring.jpa.show-sql=true`), and call `GET /api/admin/accounts` as an admin.

Expected: one select for the page plus at most one count query; the page select joins `account_roles`. Do not commit the logging change.

- [ ] **Step 3: Run the backend suite**

Run: `.\mvnw.cmd test`
Expected: all tests pass.

- [ ] **Step 4: Commit**

```powershell
git add tour/src/main/java/com/j2ee/tour/repository/AccountRepository.java
git commit -m "perf: fetch roles with the admin account list"
```

---

### Task 6: Purge expired refresh tokens

**Files:**
- Modify: `tour/src/main/java/com/j2ee/tour/repository/RefreshTokenRepository.java`
- Modify: `tour/src/main/java/com/j2ee/tour/service/RefreshTokenService.java`
- Modify: `tour/src/main/java/com/j2ee/tour/TourApplication.java`
- Modify: `tour/src/test/java/com/j2ee/tour/service/RefreshTokenServiceTest.java`

**Interfaces:**
- Consumes: `Clock` and `RefreshTokenRepository` already injected into `RefreshTokenService`.
- Produces: `int deleteExpiredBefore(LocalDateTime cutoff)` and `void purgeExpired()`.

- [ ] **Step 1: Write the failing test**

Add to `RefreshTokenServiceTest`:

```java
@Test
void purgeExpiredUsesNowFromTheInjectedClock() {
    service.purgeExpired();

    verify(repository).deleteExpiredBefore(NOW);
}
```

The repository query uses `<`, so a token expiring exactly at `NOW` is kept; this test pins the cutoff value passed to it.

- [ ] **Step 2: Run the test to confirm it fails**

Run: `.\mvnw.cmd test "-Dtest=RefreshTokenServiceTest"`
Expected: FAIL with "cannot find symbol: purgeExpired".

- [ ] **Step 3: Add the query and the scheduled method**

In `RefreshTokenRepository`, add:

```java
@Modifying
@Query("delete from RefreshToken t where t.expiresAt < :cutoff")
int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
```

Add the `java.time.LocalDateTime` import.

In `RefreshTokenService`, add:

```java
@Scheduled(cron = "0 0 3 * * *")
@Transactional
public void purgeExpired() {
    repository.deleteExpiredBefore(LocalDateTime.now(clock));
}
```

Add the `org.springframework.scheduling.annotation.Scheduled` import.

On `TourApplication`, add `@EnableScheduling` and the `org.springframework.scheduling.annotation.EnableScheduling` import.

- [ ] **Step 4: Run the tests**

Run: `.\mvnw.cmd test "-Dtest=RefreshTokenServiceTest"`
Expected: PASS.

Run: `.\mvnw.cmd test`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add tour/src/main/java/com/j2ee/tour/repository/RefreshTokenRepository.java tour/src/main/java/com/j2ee/tour/service/RefreshTokenService.java tour/src/main/java/com/j2ee/tour/TourApplication.java tour/src/test/java/com/j2ee/tour/service/RefreshTokenServiceTest.java
git commit -m "chore: purge expired refresh tokens daily"
```

---

### Task 7: Add minimal frontend tests

**Files:**
- Modify: `fe-tour/package.json`
- Create: `fe-tour/lib/format.test.ts`
- Create: `fe-tour/lib/demo-tours.test.ts`

**Interfaces:**
- Consumes: `lib/format.ts` and `lib/demo-tours.ts` as they exist.
- Produces: `npm test` runs Vitest once and exits non-zero on failure.

- [ ] **Step 1: Install Vitest and add the script**

From `fe-tour`:

```powershell
npm install --save-dev vitest
```

Add to `package.json` scripts:

```json
"test": "vitest run"
```

No Vitest config file is needed: the tests are pure TypeScript with no JSX and no path aliases.

- [ ] **Step 2: Write `lib/format.test.ts`**

```ts
import { describe, expect, it } from "vitest";
import {
  durationDays,
  formatDate,
  formatDateRange,
  formatDuration,
  formatPrice,
  isSoldOut,
  seatsLabel,
} from "./format";

describe("formatDate", () => {
  it("reads wall-clock parts without a timezone shift", () => {
    expect(formatDate("2026-11-01T23:30:00")).toBe("1 Nov 2026");
  });

  it("falls back when the value is missing", () => {
    expect(formatDate(null)).toBe("Dates to be announced");
  });
});

describe("formatDateRange", () => {
  it("formats a range in the same month", () => {
    expect(formatDateRange("2026-11-01T08:00:00", "2026-11-03T17:00:00")).toBe(
      "1–3 Nov 2026",
    );
  });

  it("formats a range across months", () => {
    expect(formatDateRange("2026-11-28", "2026-12-02")).toBe("28 Nov – 2 Dec 2026");
  });

  it("formats a range across years", () => {
    expect(formatDateRange("2026-12-30", "2027-01-02")).toBe("30 Dec 2026 – 2 Jan 2027");
  });
});

describe("durationDays", () => {
  it("counts both the start and end day", () => {
    expect(durationDays("2026-11-01T08:00:00", "2026-11-03T17:00:00")).toBe(3);
  });

  it("returns null for missing or reversed ranges", () => {
    expect(durationDays(null, "2026-11-03")).toBeNull();
    expect(durationDays("2026-11-03", "2026-11-01")).toBeNull();
  });

  it("formats durations", () => {
    expect(formatDuration("2026-11-01", "2026-11-01")).toBe("1 day");
    expect(formatDuration(null, null)).toBe("Flexible");
  });
});

describe("formatPrice", () => {
  it("groups VND without decimals", () => {
    expect(formatPrice(4500000)).toBe("₫4,500,000");
  });

  it("handles missing prices", () => {
    expect(formatPrice(undefined)).toBe("Price on request");
  });
});

describe("seats", () => {
  it("labels sold-out departures", () => {
    expect(seatsLabel(0)).toBe("Sold out");
    expect(isSoldOut("FULL", 5)).toBe(true);
    expect(isSoldOut("OPEN", 5)).toBe(false);
  });
});
```

- [ ] **Step 3: Write `lib/demo-tours.test.ts`**

```ts
import { describe, expect, it } from "vitest";
import { demoFeaturedTours, demoTour, demoToursPage } from "./demo-tours";

describe("demoToursPage", () => {
  it("searches name, description and destinations case-insensitively", () => {
    const page = demoToursPage(0, "LAN HA");

    expect(page.totalElements).toBe(1);
    expect(page.content[0]?.name).toBe("Lan Ha Bay Overnight");
  });

  it("returns the same page shape as the API", () => {
    const page = demoToursPage(0, "");

    expect(page.size).toBe(9);
    expect(page.page).toBe(0);
    expect(page.totalPages).toBeGreaterThan(0);
  });
});

describe("demoFeaturedTours", () => {
  it("caps the featured list", () => {
    expect(demoFeaturedTours(2)).toHaveLength(2);
  });
});

describe("demoTour", () => {
  it("returns a known tour and null for an unknown id", () => {
    expect(demoTour(9001)?.name).toBe("Lan Ha Bay Overnight");
    expect(demoTour(1)).toBeNull();
  });
});
```

- [ ] **Step 4: Run the tests**

Run: `npm test`
Expected: all tests pass.

Run: `npm run lint`
Expected: no errors.

- [ ] **Step 5: Commit**

```powershell
git add fe-tour/package.json fe-tour/package-lock.json fe-tour/lib/format.test.ts fe-tour/lib/demo-tours.test.ts
git commit -m "test: cover frontend formatting and demo data"
```

---

### Task 8: Remove unused backend dependencies

**Files:**
- Modify: `tour/pom.xml`
- Modify: `tour/src/main/resources/application.properties`

**Interfaces:**
- Consumes: nothing.
- Produces: a smaller dependency tree; no runtime behavior change.

- [ ] **Step 1: Delete the unused dependency blocks**

From `pom.xml`, delete:

- `spring-boot-starter-oauth2-client`
- `spring-boot-starter-oauth2-resource-server`
- `spring-boot-starter-amqp`
- `spring-boot-starter-data-redis`
- `spring-boot-starter-websocket`
- `cloudinary-http5` and the `cloudinary.version` property
- `mapstruct`, `mapstruct-processor`, `lombok-mapstruct-binding`, and their version properties
- The `lombok-mapstruct-binding` and `mapstruct-processor` entries from `annotationProcessorPaths`

Keep Lombok, JJWT, springdoc, Flyway, security, JPA, validation, actuator, and the test starters.

- [ ] **Step 2: Remove the now-dead properties**

From `application.properties`, delete the comment about Redis/RabbitMQ, the `spring.autoconfigure.exclude` line, and `management.health.redis.enabled` / `management.health.rabbit.enabled`. Keep `management.endpoints.web.exposure.include=health`.

- [ ] **Step 3: Run the backend suite**

Run: `.\mvnw.cmd test`
Expected: all tests pass and compilation succeeds.

- [ ] **Step 4: Commit**

```powershell
git add tour/pom.xml tour/src/main/resources/application.properties
git commit -m "chore: remove unused backend dependencies"
```

---

### Task 9: Fix the footer contrast

**Files:**
- Modify: `fe-tour/components/site-footer.tsx`

**Interfaces:**
- Consumes: the `ivory` and `forest` tokens in `app/globals.css`.
- Produces: footer bottom bar at `text-ivory/70`, about 5.1:1 on `#214e45`, which passes WCAG AA.

- [ ] **Step 1: Change the class**

In `site-footer.tsx`, change the bottom bar's `text-xs text-ivory/50` to `text-xs text-ivory/70`.

- [ ] **Step 2: Run lint**

Run: `npm run lint`
Expected: no errors.

- [ ] **Step 3: Commit**

```powershell
git add fe-tour/components/site-footer.tsx
git commit -m "fix: raise footer text contrast to WCAG AA"
```

---

### Task 10: Serve the hero and CTA through `next/image`

**Files:**
- Modify: `fe-tour/components/photo.tsx`

**Interfaces:**
- Consumes: local files in `public/images/`. All four `Photo` call sites sit inside a positioned container and already pass `absolute inset-0 h-full w-full`.
- Produces: the same `Photo` API (`src`, `alt`, `className`, `eager`), now using `next/image` with `fill`, `sizes`, and `preload`.

- [ ] **Step 1: Replace the raw img**

Rewrite `photo.tsx`:

```tsx
import Image from "next/image";

type Props = {
  src: string;
  alt: string;
  className?: string;
  eager?: boolean;
};

export function Photo({ src, alt, className, eager = false }: Props) {
  return (
    <Image
      src={src}
      alt={alt}
      fill
      sizes="100vw"
      preload={eager}
      className={`object-cover ${className ?? ""}`}
    />
  );
}
```

Do not change `TourImage`: it renders remote Cloudinary URLs with per-deployment hosts, so it stays a raw `<img>`.

- [ ] **Step 2: Verify the build and lint**

Run: `npm run lint`
Expected: no errors, and the `@next/next/no-img-element` disable comment is gone.

Run: `npm run build`
Expected: build succeeds. Open the home page and confirm the hero and CTA images still fill their sections.

- [ ] **Step 3: Commit**

```powershell
git add fe-tour/components/photo.tsx
git commit -m "perf: optimize local editorial images with next/image"
```

---

## Self-Review

- **Spec coverage:** Findings 1–11 map to Tasks 1–10. Finding 9 is removed by the mapper change in Task 4; finding 11 is fixed by `next/image` in Task 10.
- **Step scan:** Every step names the file, the exact change, and a checkable result. SQL tasks copy from named line ranges instead of transcribing 300 lines.
- **Type consistency:** `toDetail(Tour, List<TourDeparture>)` is introduced in Task 4 and used only there. `deleteExpiredBefore(LocalDateTime)` is introduced and tested in Task 6. `Photo` keeps its public props in Task 10.
- **Review Focus:** 404, 405, empty-database bootstrap, case-insensitive email, and the purge cutoff each have a test or verification step.
- **Proportion:** The only large file is the schema baseline, and the plan points at the existing SQL instead of repeating it.

## Execution Handoff

Plan complete and saved to `docs/review_1.md`. Please review it. Which execution approach would you prefer?

- **Subagent-driven** — a fresh subagent implements each task and a fresh reviewer checks it before the next one starts. Best when the schema task is likely to need back-and-forth and you want each diff reviewed in isolation.
- **Native** — I implement every task in this session, then one fresh reviewer checks the whole branch. Cheapest and fastest; the plan carries the design.

For this plan I recommend **Native**, because Tasks 1–10 are small and mostly independent, and the one risky task (Task 2) is already pinned by explicit verification steps. Does the plan capture what you want, and which approach should we use?
