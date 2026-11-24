# Departure performance investigation

## Checklist
- [x] Analysis findings written before application changes
- [x] N+1 fixes
- [x] Pagination/limits assessed
- [x] Migration with indexes assessed
- [x] Config tweak assessed
- [x] Tests
- [x] Full verification (requested non-DB build/tests; database verification remains open)

## Baseline investigation (static analysis; not measured)

No request trace or isolated PostgreSQL was supplied, so the actual cause of the reported 30-second timeout is **not proven**. The following are demonstrable query-amplification risks, not measured timing results. Counts exclude authentication, Redis, database-internal trigger statements and may vary with Hibernate and the first-level cache.

* Admin Tours & departures (`components/admin-tours.tsx:95`) calls `GET /api/admin/tours?page=0&size=100`, not the departure list. It consumes only the first page. `TourService.list` pages tours, then `TourMapper.toAdminDetail` initializes departures, images, destinations and vehicles, plus accommodation and guide names, for each tour. The four collections have `@BatchSize(25)` in `Tour`, so ideally approximately 2 + 4*ceil(N/25) + A + G SQL statements for N tours, A distinct non-null accommodations and G distinct non-null guides; up to 218 for 100 tours with distinct accommodation and guide, plus any provider-specific eager loads. Promotion ID accesses may not initialize a proxy. All departures of each returned tour are embedded, unbounded. The actual count requires statistics. This is a credible N+1 on the two to-one associations, not four unbatched collection N+1s.
* `/api/tour-departures` is a Spring Pageable page; `TourDeparture.tour` is default EAGER and can require one secondary select per distinct tour, even though the response needs only its ID. Estimated 1 content + optional count + up to D distinct tour selects, with D <= page size. `listDepartures` defaults to 100 but has no current component caller found. DTO uses stored `departureBookedSlots` and subtracts it from capacity; no per-departure booking count, traveler query or existence query in reads.
* Public `/api/tours/{id}/departures`: on a cold cache one tour lookup plus one unbounded upcoming-departure query, ordered by start date; the tour is already in the persistence context, so no per-row tour load expected. Warm Redis hit: zero service SQL. The endpoint is an array; silently limiting it would change behavior.
* Public `/api/tours` uses a page of IDs and a count, followed by batch tour lookup, one grouped departure stats query and one image query: approximately 5 statements for a nonempty cold page; 2 for empty. Summary mapping reads scalar tour fields only. Price/date filtering uses a correlated EXISTS; price/date sorting uses a join and aggregate MIN/grouping; both affect query work even though statement counts are constant. Controller size is 1–50. Filtered/sorted variants bypass Redis; unfiltered catalogue and public tour/detail departures use `tours` cache. Writes to tours, departures and booking service evict `tours`; Redis TTL is 10 minutes by default. Cache failures can bypass cache; see `CacheConfig`.
* Public detail additionally maps images, destinations, vehicles, accommodation and guide for one tour; not a per-departure N+1. No booking/traveler traversal in these DTOs.
* Write paths: departure update/delete uses pessimistic `findByIdForUpdate`; guide validation does one overlap count per departure write, plus a guide lookup only for a conflict. Tour guide reassignment loops all live departures and performs an overlap count per row. V8 runs database trigger overlap checks on departure insert/update, including seat updates, and loops on tour guide change. Cancellation intentionally locks bookings one at a time and queries payments/refunds, then dispatches email after commit; it is not a read path and must retain locking semantics. Trigger SQL is not included in Hibernate statement counts.
* Hikari max 5, min idle 1, connection timeout 30 seconds; `open-in-view=false`; no global default batch fetch size, but explicit 25 on tour collections. Read-only `TourService` and departure list transactions include mapping, so connections can remain occupied during all secondary queries. Pool exhaustion versus slow SQL is unmeasured; do not change pool or OSIV.

## Index inventory and query predicates

Reviewed V1–V9, V2 schema and `quanlybantour_booking_floor.sql` (the latter is seed-only and declares no indexes). V2 has departure PK, `(tour_id)` and `(departure_status, departure_start_date)`; V7 adds `(tour_id, departure_status, departure_start_date)`. V2 has booking `(departure_id)`, `(account_id)`, `(booking_status)` and traveler `(booking_id)`; V7 adds booking `(account_id, booking_created_at DESC, booking_id DESC)`, payment `(booking_id, payment_status)`, image `(tour_id, image_id)` and tour `(tour_status, tour_id)`. V2 includes image `(tour_id)`, tour status and join-table PKs `(tour_id,destination_id)` and `(tour_id,vehicle_id)`; related entity IDs are primary keys. V2 payment `(booking_id)` and refund `(booking_id)`; V7 refund `(payment_id, refund_status)`. No tour guide ID index declared on tours; no departure end-date or price index declared. No vehicle ID on departure; it is linked through `tour_vehicles`.

Upcoming/stats use tour ID, status, start date; catalogue additionally filters price and orders aggregated price/start; overlap joins tour guide ID to tour ID and tests non-cancelled status and both interval bounds; delete/cascade looks up booking departure ID. Existing V7 index is aligned with upcoming reads. Actual plans and selectivity are unknown: no index use or timing can be asserted without EXPLAIN on an isolated representative database. Next available migration is V10.

## Smallest change and remaining decisions

Added to-one-only entity graphs on the two paged repository methods. The departure page fetches its tour in the page query; admin tours fetch accommodation and guide in the page query. No collection fetch join is used, preserving database pagination and the existing batched collections. Estimated departure page after: 1 content + optional count; estimated admin page after: 1 content + optional count + roughly 4*ceil(N/25) collection queries (about 18 for N=100), provider behavior permitting. These are estimates, not verified SQL counts.

Public departure array and nested admin departures remain unbounded: truncating either would drop records without changing the envelope, and adding a page envelope would violate the contract. Admin paging currently accepts Spring's configured/default resolver limits and the UI requests 100, but only displays its first page. A UI pagination change requires coordination with the developer working there. Catalogue already limits page size to 50. No new index migration: the key upcoming index exists, and missing guide index has not been shown by a query plan to cause the read timeout; an unmeasured index adds write overhead. V10 is available if representative EXPLAIN later justifies one. No config or cache changes.

## Verification and limitations

`tour\mvnw.cmd '-Dtest=!*IntegrationTests,!TourApplicationTests' package`: **BUILD SUCCESS**, 323 tests, 0 failures/errors/skips. The new fetch-plan annotation tests were run first and failed before the changes, then passed. They verify declared fetch plans, not actual Hibernate SQL or JSON serialization; existing `TourServiceTest` and `TourControllerTest` cover portions of the existing mapping and public API. No real database was connected, no EXPLAIN or migration was run, and neither the generated SQL, unchanged JSON for the two admin pages, nor runtime improvement has been verified against PostgreSQL. In particular the timeout is not yet demonstrated fixed. No frontend was changed, so frontend checks were not run.

Cache clear failures are logged and suppressed, so existing cached entries can remain stale until TTL if eviction fails; this change does not alter caching.

## Manual comparison on your isolated local database

1. Use the same representative local dataset and request parameters before and after these changes; do not use a shared database. Temporarily add to `tour/src/main/resources/application.properties`:
   ```properties
   logging.level.org.hibernate.SQL=DEBUG
   spring.jpa.properties.hibernate.generate_statistics=true
   ```
2. Start the backend against your isolated database. Open the admin **Tours & departures** page or make an authenticated `GET /api/admin/tours?page=0&size=100`. In browser Network record duration and status, and inspect SQL logs and Hibernate session statistics for prepared statement count. Repeat with the same data and comparable warm-up, before and after the code changes; capture several requests. For the service portion with 100 tours and distinct accommodation/guide, the static estimate is roughly **218 before vs 18 after** (2 base + 16 batched collection + up to 200 to-one, versus 2 + 16); fewer if relations are shared, absent, batch fetching differs, or count SQL is omitted. Authentication adds SQL. Verify actual counts rather than treating the estimates as guarantees.
3. Also make authenticated `GET /api/tour-departures?page=0&size=100`: estimate **1–2 + D before vs 1–2 after**, D being distinct tours not already managed. Compare returned JSON and page totals. Public `GET /api/tours/{id}/departures` cold-cache estimate is 2 before and after; filtered `GET /api/tours?sort=priceAsc` estimate is about 5 before and after for nonempty pages. Use the same cache state for each comparison; admin pages are not cached. Elapsed time cannot be predicted without measurement; desired result is below the frontend's 30-second timeout, not a guaranteed time.
4. If it remains slow, save representative SQL and run `EXPLAIN (ANALYZE, BUFFERS)` only on your isolated copy, inspect pool waits and trigger/write timings separately, then decide whether V10 indexes or coordinated UI pagination are warranted. Remove the diagnostic properties afterward; SQL/statistics logging can be noisy.

## Files changed

* `tour/src/main/java/com/j2ee/tour/repository/TourRepository.java` — shared repository, paged fetch graph.
* `tour/src/main/java/com/j2ee/tour/repository/TourDepartureRepository.java` — shared repository, paged fetch graph; locking query untouched.
* `tour/src/test/java/com/j2ee/tour/repository/DepartureReadFetchPlanTest.java` — new fetch-plan checks.
* `docs/admin/task-departure-perf-result.md` — this report.

No migration was created or applied; there is no new migration SQL to apply. No shared service or frontend file named in the request was edited.
