# Redis Tour Content Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cache stable public tour content while returning live PostgreSQL availability in a single-instance demo.

**Architecture:** A proxied `TourContentCache` caches only stable batch cards and departure-free detail. `TourService` combines those values with live queries. A small after-commit invalidator clears stable content after relevant admin writes.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Cache/Data Redis, PostgreSQL, JUnit 5, Mockito, AssertJ.

**Spec:** `docs/superpowers/specs/2026-11-28-redis-tour-content-design.md`

**Status:** Implemented on `feature/redis` using approved HY4 subagents. Independent reviews, unit tests, and user-authorized current Supabase/Redis verification passed. Changes are uncommitted; permanent test guards are unchanged.

## Execution results

| Task | Result |
| --- | --- |
| 1. Stable-content cache primitives | Implemented; task review approved |
| 2. Compose live public reads | Implemented; task review approved |
| 3. Content invalidation | Implemented; task review approved |
| 4. Failure modes, integration harness, docs | Implemented; task review approved; subsequent user-authorized current-service checks passed |
| Final whole-change review | Six findings fixed; scoped re-review accepted all six |

Final controller run: 374 tests passed across 36 explicitly selected `*Test.java`
classes, zero failures/errors/skips. Subsequent explicit permission enabled
current-Supabase verification: 41 existing integration tests passed, and all
four new cache scenario methods passed through a temporary authorized adapter
with original setup/cleanup. The guarded JUnit class/initializer was not weakened.
Actual Redis smoke, disabled-cache, and unreachable-Redis checks also passed.

Migrations and mail were disabled. Only test-created rows and isolated Redis
keys were cleaned up. Observed deletion visibility was 58–135 ms; the temporary
smoke used a five-second bounded eventual check, matching the specified content
consistency contract. Application configuration and production code were not
changed during this verification. Long-duration expiry and load tests were not
run. The checklists below retain the default dedicated-service instructions.

## Global Constraints

- Java 21 and Spring Boot 4.1.1; use existing Spring Cache and Redis dependencies.
- No new Maven/npm dependencies, schema migrations, or frontend changes.
- Preserve public JSON shapes, validation, search semantics, and admin behavior.
- PostgreSQL remains the seat authority; preserve existing pessimistic booking locks.
- Keep `FixedWindowRateLimiter` and `RateLimitFilter` behavior unchanged.
- Keep `SPRING_CACHE_TYPE=none` supported and Redis failures fail-open for reads.
- Keep default TTL `PT10M`, configurable through `SPRING_CACHE_TTL`, and prefix `tour:`.
- Never commit credentials or use shared/production databases for tests.
- Do not commit or push without explicit permission.

## Review Focus

1. Warm caches must not expose a tour the database now marks nonpublic (Task 2).
2. Clock-only departure expiry must change stats and lists without a mutation (Task 2).
3. A changed/reordered ID page must not reuse old membership or page totals (Task 2).
4. Rollback and Redis clear failure must not corrupt committed-write semantics (Task 3).
5. Redis serialization/failure/disabled mode must preserve live reads and DTO types (Tasks 1 and 4).

---

## File map and interfaces

Paths below are repository-relative; commands run from `tour/`.

| File | Responsibility |
| --- | --- |
| New `tour/src/main/java/com/j2ee/tour/dto/cache/TourCardContent.java` | Five stable catalog fields only |
| New `tour/src/main/java/com/j2ee/tour/service/TourContentCache.java` | Proxied stable-content loading and nested `TourCardCatalog` wrapper |
| Existing `tour/src/main/java/com/j2ee/tour/mapper/TourMapper.java` | Stable mapping and immutable response composition |
| Existing `tour/src/main/java/com/j2ee/tour/service/TourService.java` | Live query orchestration; content mutations |
| Existing `tour/src/main/java/com/j2ee/tour/repository/TourRepository.java` | Lightweight live visibility check |
| New `tour/src/main/java/com/j2ee/tour/service/TourCacheInvalidator.java` | Safe after-commit region clear |
| Existing related services listed in Task 3 | Schedule content invalidation |
| Existing `BookingService.java`, `TourDepartureService.java` in the service directory | Remove obsolete cache eviction only |
| Existing/new tests listed per task | Cache composition, invalidation, integration |
| Existing `tour/README.md` | Operator contract and safe verification |

No changes planned to `CacheConfig`, controller signatures, API response
records, Redis configuration, auth limiter, frontend, or database schema.

### Task 1: Stable-content cache primitives

**Files:**
- Create: `tour/src/main/java/com/j2ee/tour/dto/cache/TourCardContent.java`
- Create: `tour/src/main/java/com/j2ee/tour/service/TourContentCache.java` (includes public nested `TourCardCatalog` record)
- Modify: `tour/src/main/java/com/j2ee/tour/mapper/TourMapper.java:25-95`
- Create/test: `tour/src/test/java/com/j2ee/tour/service/TourContentCacheTest.java`
- Modify/test: `tour/src/test/java/com/j2ee/tour/common/CacheConfigTest.java`

**Interfaces:**
- Consumes: existing `findAllById`, `findImageUrlsByTourIds`,
  `findByTourIdAndTourStatus`, and configured `CacheManager`.
- Produces:
  - `record TourCardContent(Long id, String name, String description, String status, String imageUrl)`.
  - `TourContentCache(TourRepository tourRepository, TourImageRepository tourImageRepository)`.
  - `public record TourCardCatalog(List<TourCardContent> items)` nested in `TourContentCache`; this wrapper is the cached catalog value.
  - `TourCardCatalog getCatalogContent(List<Long> tourIds, boolean cacheable)`; callers unwrap `.items()`.
  - `TourResponse getDetailContent(Long tourId)`.
  - `TourMapper.toCardContent(Tour tour, String imageUrl): TourCardContent`.
  - `TourMapper.toSummary(TourCardContent content, TourStatsProjection stats): TourSummaryResponse`.
  - `TourMapper.withDepartures(TourResponse content, List<TourDepartureResponse> departures): TourResponse`.
  Keep the existing `toSummary(Tour, TourStatsProjection, String)` signature,
  delegating to the same mapping rules so current callers/tests remain valid.

- [ ] **Step 1: Write cache-proxy and serializer tests.**

Use `@SpringJUnitConfig`, production `CacheConfig`, an explicit
`ConcurrentMapCacheManager("tours")`, mocked repositories, and a real proxied
`TourContentCache`. Plain Spring test contexts do not supply Boot's
`CacheProperties`: explicitly declare a
`@Bean CacheProperties cacheProperties()` using
`org.springframework.boot.cache.autoconfigure.CacheProperties`; configure its
Redis TTL to `Duration.ofMinutes(10)`, key prefix to `"tour:"`, and null caching
to false. Reuse that test configuration in every plain context importing
`CacheConfig`, including Task 4's failure/no-op contexts. Clear cache and reset
mocks before every test. Use these named cases and assertions:

| Test | Setup and required assertions |
| --- | --- |
| `cachesDetailWithoutDepartures` | Two calls for ID 1 return equal static responses; `departures()` is empty; verify OPEN detail lookup once; inspect `v2:detail:1` payload |
| `cachesCatalogInRequestedIdOrder` | IDs `[2,1]`, repository returns `[1,2]`; two calls with true return `.items()` `[2,1]`; verify tour/image batch loaders once |
| `multipleImagesKeepFirstByImageId` | Image projection query returns two rows for tour 1 in image-ID order; mapping does not throw and selects the first URL |
| `filteredCatalogBypassesWarmCache` | Warm true entry, change repository name; false call returns new `.items()` name; verify fresh batch load and unchanged warm entry |
| `emptyIdsDoNotPopulateCache` | Empty input returns empty `.items()`; verify no tour/image batch query and no empty-ID key |
| `doesNotCacheMissingDetail` | OPEN lookup empty; both calls throw 404 `"Tour not found"`; verify lookup twice |
| `mapperPreservesDetailFields` | Copy full 20-field response with new departures; assert all other record components unchanged and original departures still empty |
| `roundTripsStableCatalogContent` | Configured serializer round-trip of the exact cached `TourCardCatalog` (immutable `List.of` items, including null image) equals input; also pin that top-level `List.of(card)` and `Stream.toList()` fail deserialization so in-memory tests cannot hide the provider issue |
| `roundTripsDepartureFreeDetail` | Configured serializer round-trip of full detail with empty departures equals input; retain existing serializer tests |

Also pin first-segment/160-character truncation using existing mapper behavior;
no catalog stats, seat counts, booked slots, or departure objects may appear in
`TourCardContent` or `TourCardCatalog`.

- [ ] **Step 2: Verify the tests fail for missing primitives.**

Run: `.\mvnw.cmd "-Dtest=TourContentCacheTest,CacheConfigTest" test`
Expected: compilation failure naming the missing new types/methods.

- [ ] **Step 3: Implement the interfaces above.**

`TourContentCache` is `@Service`, `@Transactional(readOnly=true)`. Use region
`tours`, explicit SpEL keys `'v2:catalog:' + #tourIds.toString()` and
`'v2:detail:' + #tourId`. Catalog condition:
`#cacheable && !#tourIds.isEmpty()`. Catalog loading uses two existing batch
queries, maps by ID, restores input order, and wraps the immutable cards in
`TourCardCatalog`; that wrapper is the cached value, not a bare `List`.
Missing rows follow the current skip-missing behavior; do not introduce
per-tour queries. The image query
returns multiple rows per tour ordered by image ID: preserve the existing
`Collectors.toMap` merge `(first, second) -> first`, not a duplicate-key failure.
Detail mapping calls
`TourMapper.toDetail(tour, List.of())`. Ensure relation collections are realized
before leaving the transaction. Preserve current missing/nonpublic exception.

- [ ] **Step 4: Verify primitives and existing mapping tests pass.**

Run: `.\mvnw.cmd "-Dtest=TourContentCacheTest,CacheConfigTest,TourServiceTest" test`
Expected: `BUILD SUCCESS`, no failing selected tests.

- [ ] **Step 5: Inspect this slice; commit only if separately authorized.**

No commit command is preauthorized by this plan.

### Task 2: Compose live public reads

**Files:**
- Modify: `tour/src/main/java/com/j2ee/tour/service/TourService.java:53-201,270-274`
- Modify: `tour/src/main/java/com/j2ee/tour/repository/TourRepository.java`
- Modify/test: `tour/src/test/java/com/j2ee/tour/service/TourServiceTest.java`
- Modify/test: `tour/src/test/java/com/j2ee/tour/service/TourCacheTest.java`

**Interfaces:**
- Consumes: Task 1 cache and mapper interfaces.
- Produces: unchanged public service signatures, plus repository
  `boolean existsByTourIdAndTourStatus(Long tourId, String tourStatus)`.
- Constructor change: append `TourContentCache tourContentCache` to the
  existing `TourService` constructor; update both direct test construction
  sites (`TourServiceTest` and `TourCacheTest.Config`). Unwrap
  `TourCardCatalog.items()` before composing summaries.

- [ ] **Step 1: Add failing warm-read and edge-case tests.**

Replace the old test expectation that full detail is cached. Keep its content
reuse assertion but distinguish the new visibility lookup. Use a mutable test
`Clock`, not sleeps. Reset cache and mocks between tests.

| Test | Required observations |
| --- | --- |
| `detailReusesContentButReloadsDepartures` | Two detail calls, static lookup once, live visibility/departure lookup twice; seats 28 then 27 |
| `departuresAlwaysReloadWithoutContentCache` | Two endpoint-service calls return changed booked slots/seats; no content-cache entry needed |
| `warmDetailCannotExposeNonpublicTour` | Warm detail, then live exists returns false; both detail and departure reads throw existing 404 |
| `catalogReloadsStatsButReusesContent` | Two plain page calls; ID search and stats twice, tour/image batch loaders once; seats 28 then 27 |
| `catalogKeepsLiveMembershipOrderAndTotals` | Consecutive ID pages `[1,2]` total 2, then `[2,3]` total 3; response IDs/order/totals exactly match second page |
| `clockAdvanceRemovesDepartedTrip` | Start at `2026-11-28T07:00:00Z`, departure starts one second later; advance two seconds; fresh query receives new time and detail/departures exclude trip; catalog date/price null, seats/count zero |
| `emptyPageSkipsContentAndStats` | Empty ID page keeps metadata and performs no content/stats load |
| `filteredAndSortedPagesBypassCache` | Test keyword and `PRICE_ASC` separately; false cache flag, current ID order/stats preserved |

Retain current range validation, wildcard normalization, no-upcoming defaults,
description, itinerary, and admin-description tests. In unit fixtures inject
the real uncached loader or explicitly stub it; do not leave a null new dependency.

- [ ] **Step 2: Verify these tests fail with old response caching.**

Run: `.\mvnw.cmd "-Dtest=TourServiceTest,TourCacheTest" test`
Expected: failed assertions for fresh seats/query counts or missing repository method.

- [ ] **Step 3: Implement live composition in existing service methods.**

Remove all three read `@Cacheable` annotations and their obsolete comment/import.
Normalize/validate first. Search IDs per catalog request; pass normalized
`isUnfiltered()` to the Task 1 loader and unwrap `.items()`. Fetch stats once,
compose cards in live ID order, and use current `Page<Long>` metadata.
Capture one `LocalDateTime now`
for the search and aggregate query. Short-circuit empty pages.

Use the new exists query for detail/departures, then load static detail and
fresh upcoming departures. Detail composition uses `withDepartures`; departure
endpoint does not load full content. Preserve current OPEN/FULL/date predicates.
Do not read stale departures from Redis on any failure.

- [ ] **Step 4: Verify read behavior and HTTP compatibility.**

Run: `.\mvnw.cmd "-Dtest=TourContentCacheTest,TourServiceTest,TourCacheTest,TourControllerTest,CacheConfigTest" test`
Expected: `BUILD SUCCESS`; existing controller payload/validation tests still pass.

- [ ] **Step 5: Inspect this slice; commit only if separately authorized.**

### Task 3: Correct content invalidation without booking-driven eviction

**Files:**
- Create: `tour/src/main/java/com/j2ee/tour/service/TourCacheInvalidator.java`
- Modify: `tour/src/main/java/com/j2ee/tour/service/TourService.java`
- Modify: `tour/src/main/java/com/j2ee/tour/service/TourImageService.java`
- Modify: `tour/src/main/java/com/j2ee/tour/service/AccommodationService.java`
- Modify: `tour/src/main/java/com/j2ee/tour/service/TourGuideService.java`
- Modify: `tour/src/main/java/com/j2ee/tour/service/TouristDestinationService.java`
- Modify: `tour/src/main/java/com/j2ee/tour/service/TravelVehicleService.java`
- Modify: `tour/src/main/java/com/j2ee/tour/service/BookingService.java`
- Modify: `tour/src/main/java/com/j2ee/tour/service/TourDepartureService.java`
- Create/test: `tour/src/test/java/com/j2ee/tour/service/TourCacheInvalidatorTest.java`
- Create/test: `tour/src/test/java/com/j2ee/tour/service/TourContentInvalidationTest.java`
- Modify/test: `tour/src/test/java/com/j2ee/tour/service/TourCacheTest.java`
- Modify/test: `tour/src/test/java/com/j2ee/tour/service/TourServiceTest.java`

**Interfaces:**
- Consumes: `CacheManager.getCache("tours")`, transaction synchronization API.
- Produces: `TourCacheInvalidator(CacheManager cacheManager)` and
  `void invalidateAfterCommit()`.
- Append `TourCacheInvalidator` to constructors of the six content-mutating
  services above; keep `TourService`'s Task 2 dependency. Update explicit
  construction sites and Spring test bean factories accordingly.

- [ ] **Step 1: Write invalidation and wiring regression tests.**

| Test | Required assertions |
| --- | --- |
| `clearsOnlyAfterCommit` | Register callback in active synchronization; verify no immediate clear; invoke afterCommit; verify `tours` clear once |
| `rollbackDoesNotClear` | Complete callback with rollback status without afterCommit; verify no clear; clean thread-local synchronization |
| `withoutTransactionClearsImmediately` | No synchronization; clear exactly once |
| `clearFailureDoesNotEscape` | Mock cache clear throws; both immediate and afterCommit paths do not throw |
| `missingCacheIsHarmless` | `getCache("tours")` returns null; no exception |
| `contentMutationsScheduleInvalidation` | Parameterize successful create/update/delete in all six content services; verify invalidator once for each |
| `failedContentMutationDoesNotInvalidate` | Each service's representative validation/not-found error; verify invalidator never |
| `imageReassignmentInvalidatesBothViews` | Warm catalog/detail, move image between tours, clear callback; both tours reload current images |

Use Mockito fixtures for wiring; those tests do not claim to prove DB commit or
seat locking. Retain existing TourCacheTest content reload after deletion with
immediate invalidation in its nontransactional test context.

- [ ] **Step 2: Verify new tests fail before the invalidator exists.**

Run: `.\mvnw.cmd "-Dtest=TourCacheInvalidatorTest,TourContentInvalidationTest,TourCacheTest" test`
Expected: missing invalidator compilation failure or missing-clear assertions.

- [ ] **Step 3: Implement after-commit invalidation and update callers.**

Follow `AfterCommitMailDispatcher` transaction synchronization pattern, but
clear the cache rather than dispatch mail. Catch runtime cache-clear failures
inside the callback, including `getCache`. Warn without payloads or credentials.
Call after successful mutation/mapping in each of the six content services;
transaction commit failures must not run afterCommit.

Replace TourService's three `@CacheEvict` uses. Remove only cache annotations
and unused cache imports from BookingService and TourDepartureService. Do not
alter booking logic, transactions, cascade behavior, or locks. No new
invalidation for departure-only or booking-only changes.

- [ ] **Step 4: Verify invalidation and unchanged limiter behavior.**

Run: `.\mvnw.cmd "-Dtest=TourContentCacheTest,TourCacheTest,TourServiceTest,TourCacheInvalidatorTest,TourContentInvalidationTest,CacheConfigTest,FixedWindowRateLimiterTest,RateLimitFilterTest" test`
Expected: `BUILD SUCCESS`; no regressions or skipped new tests.

- [ ] **Step 5: Inspect this slice; commit only if separately authorized.**

### Task 4: Failure modes, real-provider checks, and operator documentation

**Files:**
- Modify/test: `tour/src/test/java/com/j2ee/tour/service/TourCacheTest.java`
- Create/test: `tour/src/test/java/com/j2ee/tour/TourCacheIntegrationTests.java`
- Create/test: `tour/src/test/java/com/j2ee/tour/TourCacheDatabaseGuardTest.java`
- Modify: `tour/README.md`

**Interfaces:**
- Consumes: completed public service/cache/invalidation interfaces, existing
  `BookingService.create`/`cancel`, existing request fixtures from
  `BookingFlowIntegrationTests`, and `TransactionTemplate`.
- Produces: regression coverage and documented safe demo verification.
- Test-only guard interface:
  `TourCacheIntegrationTests.DedicatedDatabaseInitializer.validatedDatasourceProperties(Map<String, String> environment): Map<String, String>`
  returns only `spring.datasource.url`, `spring.datasource.username`, and
  `spring.datasource.password`, or throws `IllegalStateException` on invalid input.
- No changes to shared datasource defaults or existing integration test profile.

- [ ] **Step 1: Add focused failure and disabled-cache tests.**

Use production `CacheConfig` with a cache that throws on get/put, then a
`NoOpCacheManager` in separate test contexts. Import Task 1's explicit
`CacheProperties` test bean so those plain Spring contexts can start. Named tests:
`redisReadFailureFallsBackToDatabase`, `redisWriteFailureKeepsLiveResponse`,
`cacheDisabledStillReturnsFreshSeats`. For two consecutive reads assert seats
28 then 27 and a live departure query each time. `databaseFailureDoesNotServeStaleSeats`
warms content then makes the departure repository throw; assert that exception
propagates, not an old successful availability response.

- [ ] **Step 2: Add database-backed cache regression tests.**

Create `TourCacheIntegrationTests` with test-only in-memory CacheManager,
production cache advice, and real PostgreSQL repositories. In an imported
`@TestConfiguration`, declare a distinct `@Bean @Primary MutableClock testClock()`
where the nested test `MutableClock extends Clock` supports `setInstant(Instant)`
and uses `ZoneId.of("Asia/Ho_Chi_Minh")`. Reset it to
`2026-11-28T07:00:00Z` before each test; build departures relative to that time.
This selects it over `ClockConfig.clock()` without bean-name overriding or
removed Boot `@MockBean` annotations.
This class must override `spring.cache.type=none` from the existing test profile
with its explicit test cache bean. Disable external mail in this class.
Use explicit transactions/EntityManager clearing between reads, unique fixtures,
and explicit cleanup of committed rows; do not rely on one test-wide rollback
to exercise afterCommit.

Require an opt-in class annotation
`@EnabledIfEnvironmentVariable(named="TOUR_CACHE_TEST_DB_URL", matches=".+")`.
Without opt-in, report this integration class as not run. Also supply a nested
`ApplicationContextInitializer<ConfigurableApplicationContext>` through
`@ContextConfiguration(initializers=TourCacheIntegrationTests.DedicatedDatabaseInitializer.class)`.
Before context refresh, the initializer must:

1. Require nonblank `TOUR_CACHE_TEST_DB_URL`, `TOUR_CACHE_TEST_DB_USERNAME`,
   and `TOUR_CACHE_TEST_DB_PASSWORD`.
2. Require the `jdbc:postgresql://` prefix, strip `jdbc:`, and parse the
   remainder with `URI.create` so host/path are available. Accept only the Java
   URI host spellings `localhost`, `127.0.0.1`, and `[::1]` (bracketed IPv6
   loopback), with database path exactly `/tour_cache_test`. After stripping
   `jdbc:`, `URI.create("postgresql://[::1]:5432/tour_cache_test").getHost()`
   returns `[::1]`, not `::1`. Reject query strings, fragments, URL user-info,
   and all other URLs with an error that does not include credentials.
3. Use `TestPropertyValues` to set `spring.datasource.url`, username, and
   password from these values before Flyway or the datasource initializes.
   Do not rely on `@BeforeAll`: Spring may connect before that hook runs.

In `TourCacheDatabaseGuardTest`, test the initializer without refreshing Spring:
missing variables and shared/nonloopback/wrong-database URLs must reject; valid
local settings must override a dummy preexisting datasource property. Include an
explicit accepted case for `jdbc:postgresql://[::1]:5432/tour_cache_test`. Pass
the environment map into `validatedDatasourceProperties`; use only synthetic
credentials. Exercise property application in an unrefreshed
`GenericApplicationContext`.
Never read `.env` secrets.
The new class's guard does not make existing integration classes safe.

Required cases:
- `bookingAndCancellationRefreshSeatsWithoutEvictingContent`: capacity 30,
  initial booked 2; warm reads show 28, a real one-guest booking shows 27, cancel
  shows 28. Static content cache entry survives both operations.
- `departureCancellationIsImmediatelyVisible`: warm content, cancel departure
  through the existing service; detail/departures omit it and catalog stats
  have null price/date and zero seats/count when it was the only trip.
- `contentCommitAndRollbackRespectCache`: committed name update reloads content;
  rolled-back update leaves original content and cache intact.

- [ ] **Step 3: Run focused failure tests against the pre-fix behavior.**

During TDD, introduce these tests in the owning earlier slice where applicable.
If the completed implementation already passes, record that these are coverage
additions; do not claim a fabricated red run or intentionally break product code.

- [ ] **Step 4: Document safe PostgreSQL and Redis verification.**

Extend `tour/README.md` with the cache/live-data boundary, default TTL,
cache-disabled behavior, in-memory auth limits, and eventual-content-consistency
limits. State that Redis TLS/access restrictions require production hardening.
Never include the real connection string/password.

Document these commands:

1. Unit-only verification, no live DB required:
   `.\mvnw.cmd "-Dtest=TourContentCacheTest,TourCacheTest,TourServiceTest,TourCacheInvalidatorTest,TourContentInvalidationTest,CacheConfigTest,TourControllerTest,FixedWindowRateLimiterTest,RateLimitFilterTest,TourCacheDatabaseGuardTest" test`.
2. New guarded integration class: set the three `TOUR_CACHE_TEST_DB_*` variables
   above for disposable local database `tour_cache_test`, then run
   `.\mvnw.cmd "-Dtest=TourCacheIntegrationTests" test`.
   Verify the class ran rather than skipped. Existing regression classes lack
   that guard: separately set `SPRING_DATASOURCE_URL`, username, and password to
   those same disposable values, overriding `.env`, then run
   `.\mvnw.cmd "-Dtest=TourSearchIntegrationTests,BookingFlowIntegrationTests,BookingRefundIntegrationTests" test`.
   Stop if the dedicated database is unavailable. Do not run bare `mvn test`
   against current shared datasource defaults.
3. Real Redis smoke against a separate local Redis, with local URL supplied
   through process environment: launch backend on the dedicated demo database,
   warm the three public endpoints, inspect only `tour:tours::v2:*` keys with
   `SCAN` (not `KEYS`), and use a one-guest booking/cancel to observe 28/27/28
   while static content remains cached. Verify no cached departure payload.
   Repeat with `SPRING_CACHE_TYPE=none` and an unreachable Redis address.
   Never stop shared Redis or clear its entire database.

- [ ] **Step 5: Run verification and report its exact scope.**

Run the unit command. Run integration/smoke only with the isolated services
described above. Run `git diff --check` and inspect changed-file scope.
Report executed commands, failures/skips, and unverified cloud behavior.
Do not call unrun integration checks passing.

- [ ] **Step 6: Review final diff; commit only if separately authorized.**

## Self-review and handoff

- [x] Spec requirements map to Tasks 1–4.
- [x] Interfaces, DTO fields, keys, TTL, and caller changes are explicit.
- [x] Five Review Focus cases have named tests in the owning tasks.
- [x] No migration, new dependency, frontend change, or Redis rate limiter added.
- [x] No commit permission inferred from the plan template.
- [x] Resolve independent HY4 review before requesting execution approval.

HY4 (`opencode/hy4-flash-free#high`) re-reviewed the corrected documents and
reported no remaining required findings. Gemini later reported two verified gaps:
top-level immutable-list Redis deserialization and bracketed IPv6 host spelling.
Both were independently reproduced during review; the documents added the
`TourCardCatalog` wrapper and explicit `[::1]` acceptance case before implementation.

The user then selected HY4 subagent-driven execution in the current checkout.
All four tasks and a final fix wave completed with independent review. No commit,
push, merge, or shared-service testing was authorized or performed.
