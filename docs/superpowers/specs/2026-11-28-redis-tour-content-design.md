# Redis tour content with live availability

Date: 2026-11-28
Status: Implemented and reviewed; current Supabase/Redis checks passed with explicit one-time user authorization.
Scope: `tour/` backend only.
Plan: `docs/superpowers/plans/2026-11-28-redis-tour-content.md`.

## Confirmed intent

Use the existing Redis Cloud connection for stable public catalog and tour-detail
content in a single-instance demo. Read seats and departure availability from
PostgreSQL on every request. Preserve API responses and database booking locks.
Keep auth rate limiting in JVM memory.

The user approved the reviewed documents and HY4 subagent implementation on
`feature/redis`, choosing the current checkout. No commit or push is authorized.

## Workflow

- [x] Inspect current cache, read queries, mutations, callers, and tests.
- [x] Confirm single-instance scope and exclusions with the user.
- [x] Write spec and companion implementation plan.
- [x] Complete HY4 and Gemini document reviews; resolve findings in drafts.
- [x] Obtain approval of the written documents and an execution method.
- [x] Implement, run safe unit verification, and complete independent reviews.
- [x] Run user-authorized database integration and real Redis smoke checks.

## Current behavior and defects

- `TourService.listTours` caches an entire unfiltered `PageResponse`, including
  `priceFrom`, `nextDepartureDate`, `seatsAvailable`, and `departureCount`.
- `getTour` caches nested departure responses, including booked slots and seats.
- `getDepartures` caches the entire departure list.
- Booking and departure mutations clear the entire `tours` cache. This couples
  content-cache effectiveness to booking traffic, but does not handle time
  passing without a write.
- `AccommodationService`, `TourGuideService`, `TourImageService`,
  `TouristDestinationService`, and `TravelVehicleService` can change displayed
  content without invalidating it.
- `CacheConfig` already provides Redis serialization, a configurable TTL
  (default `PT10M`), prefix `tour:`, and fail-open cache error handling.
- `FixedWindowRateLimiter` uses `ConcurrentHashMap`, not Redis.

## Alternatives and decision

1. Keep full-response caching with a shorter TTL: smallest edit, but knowingly
   serves stale availability. Rejected.
2. Cache only stable content and compose live departure data: selected. Some
   PostgreSQL reads remain intentional; booking correctness stays independent
   of Redis.
3. Split the public API and change the frontend to fetch availability separately:
   unnecessary API/UI changes for this demo. Rejected.

## Global constraints

- Java 21 and Spring Boot 4.1.1; use existing Spring Cache and Redis dependencies.
- No new Maven/npm dependencies, schema migrations, or frontend changes.
- Preserve public JSON shapes, validation, search semantics, and admin behavior.
- PostgreSQL remains the seat authority; preserve existing pessimistic booking locks.
- Keep `FixedWindowRateLimiter` and `RateLimitFilter` behavior unchanged.
- Keep `SPRING_CACHE_TYPE=none` supported and Redis failures fail-open for reads.
- Keep default TTL `PT10M`, configurable through `SPRING_CACHE_TTL`, and prefix `tour:`.
- Never commit credentials or use shared/production databases for tests.
- Do not commit or push without explicit permission.

## Data boundary

| Data | Source on a warm read |
| --- | --- |
| Tour name, public description, itinerary, images, duration | Redis content |
| Accommodation/guide labels and currently exposed relation IDs/names | Redis content |
| Catalog membership, ordering, totals, page metadata, public visibility | PostgreSQL |
| Catalog price/date/seats/departure count | PostgreSQL aggregate query |
| All departure fields and upcoming-departure membership | PostgreSQL |
| Admin reads, destination options, bookings, payments, refunds, auth | Existing uncached paths |

Keeping all departure data live is simpler and safer than stripping only
`seatsAvailable`: `bookedSlots`, capacity, `OPEN`/`FULL`/`CANCELLED`, and time
boundaries also determine availability.

### Cache payloads and ownership

Add one read-only service, `TourContentCache`, called through Spring's bean
proxy from `TourService`. Do not self-invoke cacheable methods.

- `TourCardCatalog getCatalogContent(List<Long> tourIds, boolean cacheable)`
  loads tours and first images in existing batch queries, preserving input ID
  order. Preserve the first image per tour from the image-ID-ordered query:
  merge duplicate tour IDs with `(first, second) -> first`.
  Cache only when `cacheable` is true and IDs are nonempty. Callers unwrap
  `TourCardCatalog.items()`; never cache a bare `List`.
- `TourCardCatalog` is a public nested record inside `TourContentCache`:
  `TourCardCatalog(List<TourCardContent> items)`. The wrapper is the cached
  value. The configured serializer fails to deserialize a top-level
  `List.of(...)` or `Stream.toList()` value, but round-trips a record that
  contains such a list. Keep `items()` immutable and add no extra file.
  Neither payload holds seat counts, booked slots, departures, or live stats.
- `TourResponse getDetailContent(Long tourId)` maps an OPEN tour with an empty
  `departures` list. Materialize all collections inside its read transaction.
  Never cache an entity, a Hibernate proxy, or a response containing departures.
- `TourCardContent` is an internal record at
  `dto/cache/TourCardContent.java` with exactly
  `(Long id, String name, String description, String status, String imageUrl)`.
  Preserve the existing first-description-segment and 160-character truncation.
- Extend `TourMapper` to map that record to `TourSummaryResponse` using fresh
  stats and to copy `TourResponse` with fresh departure responses. Preserve all
  20 fields, including relation IDs and duration. Do not mutate cached values.

Reuse cache region `tours`. Explicit keys:

- Catalog: `v2:catalog:` followed by the ordered Java `List<Long>.toString()`.
- Detail: `v2:detail:` followed by the numeric tour ID.

The existing prefix makes a default detail key `tour:tours::v2:detail:1`.
Versioned keys do not read old full-response entries. Old entries expire at
their existing TTL; never run `FLUSHDB` or `FLUSHALL`.

Catalog caching is restricted to normalized `criteria.isUnfiltered()`. No
search text, arbitrary filters, sorting options, or page metadata enter Redis.
The ordered IDs come from the current database page; no per-tour N+1 content
loader is introduced. Filtered/sorted requests use the same batch loader with
`cacheable=false`, bypassing both cache lookup and population.

### Public read flows

`listTours(page, size, criteria)`:

1. Normalize and validate before consulting any cache.
2. Run `searchTourIds` every time, with one request-level `now` value.
3. Return the existing empty-page response immediately when there are no IDs.
4. Load static cards through `TourContentCache`; cache only the plain query.
5. Run `findStatsByTourIds` once for that ID set, using the same `now`.
6. Assemble new summaries and page metadata in database ID order.

This preserves existing behavior: an OPEN tour with no upcoming departure still
appears in an unfiltered catalog with null price/date and zero seats/count.
Price/date filters still require one matching departure, not several different
departures satisfying separate predicates.

`getTour(id)`:

1. Check current visibility with new repository method
   `boolean existsByTourIdAndTourStatus(Long tourId, String tourStatus)`.
2. Return the existing 404 (`"Tour not found"`) when missing or not OPEN,
   even if Redis contains a warm detail.
3. Load static detail and query future OPEN/FULL departures using current time.
4. Return a new response containing those fresh departures.

`getDepartures(id)`:

- Perform the same live visibility check.
- Always query future OPEN/FULL departures; never cache this endpoint.

Do not cache the three composed public responses. Keep admin read mapping and
controller signatures unchanged. Capture current time per request, not in a
cache key or cached payload.

## Writes and invalidation

Add `TourCacheInvalidator.invalidateAfterCommit(): void` using `CacheManager`
and Spring transaction synchronization, following the existing
`AfterCommitMailDispatcher` pattern:

- Inside a transaction, clear only region `tours` after a successful commit.
- On rollback, leave the cache untouched.
- Without transaction synchronization, clear immediately for plain callers/tests.
- Missing/no-op cache is harmless. Catch cache-clear runtime errors and log a
  warning without secrets; an already committed DB operation must not become
  an HTTP failure because Redis is down.

Use one broad region clear for this small demo, not reverse indexes or a cache
dependency graph. Call the invalidator after successful create/update/delete
logic in these services:

| Service | Reason |
| --- | --- |
| `TourService` | Main content, visibility, image sync, relation assignments |
| `TourImageService` | First image and detail gallery, including image reassignment |
| `AccommodationService` | Name, rating, type |
| `TourGuideService` | Name and phone |
| `TouristDestinationService` | Destination names |
| `TravelVehicleService` | Vehicle types |

Remove legacy `@CacheEvict` from `TourService` and replace with the callback.
Remove cache-eviction annotations/imports from `BookingService` and
`TourDepartureService`: neither changes cached content. Keep every transaction,
validation, lock, seat update, refund cascade, and mail callback intact.
Promotion values are not cached; only `promotionId` is exposed. Changes to tour
assignment already invalidate through `TourService`.

### Consistency contract

Availability is a fresh database observation, not a seat reservation or a
promise that another transaction cannot book immediately afterward.

Successful application content writes schedule invalidation after commit.
Stable content remains eventually consistent: a concurrent older read may
repopulate after invalidation, Redis clear may fail, or a direct SQL edit may
bypass the application. Existing TTL bounds each stale entry's lifetime, not
an absolute wall-clock guarantee from the write. No distributed locks, local
cache fallback, or generation protocol is needed for this demo.

Visibility and departure queries still run against PostgreSQL despite these
stable-content limits. A concurrent visibility change after a visibility query
is an ordinary read/write race, not strict serializable API behavior.

## Error handling and operations

- Preserve `CacheConfig` fail-open behavior for cache get/put/serialization
  failures; only Redis errors fall back, not PostgreSQL errors.
- Never fall back to an old departure response when a live DB query fails.
- `SPRING_CACHE_TYPE=none` executes the same loaders uncached.
- Keep existing timeouts; do not tune cloud timeouts without measurements.
- Cache access is server-side only. No credentials in browser code, docs,
  logs, or tracked configuration.
- Redis Cloud currently has TLS and CIDR restrictions disabled. This design
  does not change paid plans or cloud settings. Treat that configuration as
  demo-only; secure transport/access before production.

## Acceptance and verification

1. Warm detail reuses static content while seats change from 28 to 27 to 28
   across reservation/cancellation reads without clearing content.
2. Warm catalog reuses batched static cards; aggregate stats and metadata are
   fetched per request, including changed page membership and ordering.
3. Warm detail and departures return 404 once the DB reports the tour nonpublic.
4. Moving the clock past departure start removes it and refreshes catalog stats,
   without requiring any write or cache clear.
5. Empty pages skip content/stats queries; no-upcoming tours retain current defaults.
6. Filtered/sorted requests do not read or populate content cache; invalid
   ranges still return 400. Existing query semantics remain unchanged.
7. Cache records round-trip through the actual configured serializer, including
   the `TourCardCatalog` wrapper with immutable items and a null image, with no
   seat counts, booked slots, or departure objects stored.
8. Related-content writes clear after commit; rollback does not clear; Redis
   clear failure does not undo a committed mutation.
9. Redis get/put failure and cache-disabled mode return correct live availability.
10. Existing auth rate-limit and booking/payment/refund regression tests remain
    unchanged in meaning and pass in their appropriate environments.

Use JUnit 5, Mockito, AssertJ, and Spring test facilities already installed.
Proxy-based unit tests with an isolated in-memory cache prove cache boundaries;
serializer tests exercise the configured Redis serializer without a server.
Serializer tests must use the exact cached `TourCardCatalog` value and also pin
the known top-level immutable-list failures, so an in-memory cache cannot hide
the provider issue.
Plain Spring test contexts importing `CacheConfig` must explicitly provide
`CacheProperties`; Boot auto-configuration is not present in `@SpringJUnitConfig`.
Dedicated PostgreSQL integration tests prove booking effects; do not claim
mocked repositories prove seat locking. A separate local Redis smoke test proves
the real provider. Never use the configured shared Supabase DB for testing.

The new integration class is opt-in through `TOUR_CACHE_TEST_DB_URL`, and a
test-only context initializer must validate loopback host, database name
`tour_cache_test`, and explicit test credentials before context refresh.
Accepted Java URI host spellings after stripping `jdbc:` are `localhost`,
`127.0.0.1`, and `[::1]` (bracketed IPv6 loopback). It overrides datasource
properties before Flyway or any connection initializes.
Use a distinct `@Primary` mutable test Clock to override the production Clock.
Existing integration classes retain their current configuration; running them
still requires explicit dedicated-database overrides. Do not run the full suite
against shared datasource defaults.

## Non-goals

No Redis rate limiting, sessions, booking locks, payment state, destination-option
cache, arbitrary search cache, response schema changes, frontend caching changes,
metrics platform, dependency upgrades, or production Redis provisioning.

## Document review

HY4 (`opencode/hy4-flash-free#high`, highest available effort) reviewed both
documents using review skills. Four findings were addressed: explicit test
`CacheProperties`, pre-refresh dedicated-database guard, first-image merge, and
test Clock selection. That review missed the top-level immutable-list serializer
gap. Gemini reported it and the bracketed IPv6 host spelling; HY4 independently
reproduced both (project Java 21, spring-data-redis 4.1.1). Top-level
`List.of(card)` and `Stream.toList()` fail deserialization, `ArrayList`
round-trips, and a wrapper record containing an immutable list round-trips.
Both documents use `TourCardCatalog` and accept `[::1]`. The user subsequently
approved implementation. Each of four implementation tasks passed independent
review. Final review identified six fixes, all addressed and accepted by scoped
re-review: secret-safe invalidator warnings, decoded-item immutability,
pre-read cache-survival assertions, robust catalog fixture selection,
test-configuration isolation, and complete README unit commands.

## Implementation verification

- All 36 explicitly selected unit-test classes pass: 374 tests, no failures,
  errors, or skips. Final source and untracked-file whitespace checks pass.
- The guarded integration class remains unchanged. With explicit subsequent
  user permission to use the current Supabase database, a temporary adapter ran
  all four original scenario methods with their real setup/cleanup: all passed.
  It did not run the dedicated-database initializer against a shared URL.
- The existing booking/search/refund integration suites passed 41 tests against
  current Supabase with migrations disabled and rollback-scoped fixtures.
- Real Redis smoke passed: configured serializer and TTL, isolated key prefix,
  live 28/27/28 seats without content eviction, committed-content invalidation,
  reload, and rollback retention. Cache-disabled and unreachable-Redis checks
  also passed with real PostgreSQL.
- Raw deletion was observed after 58–135 ms in the final smoke run. The
  temporary probe therefore used a five-second bounded condition assertion,
  consistent with the existing eventual-content contract, not an immediate
  deletion guarantee. No production-code change was needed.
- This was a one-time user-authorized exception to the default dedicated-test
  restriction. Application connection configuration and permanent test guards
  remain unchanged. Created fixture rows were deleted by owned IDs and their
  absence verified; Redis cleanup targeted only the isolated namespace.
- No dependency, migration, frontend, auth-rate-limit, or production connection
  configuration changes. No commit, staging, push, or merge.
- Long-duration TTL expiry, repeated-run/load behavior, and production Redis
  security hardening were not tested. Follow `tour/README.md` for the default
  safe verification procedure against dedicated services.
