# Backend — agent guide

## What this is
Spring Boot 4.1.1 REST API for tour booking (Java 21, Maven wrapper). Package-by-layer under `src/main/java/com/j2ee/tour/`. PostgreSQL + Flyway; Hibernate runs with `ddl-auto=validate`. Seats are capacity-limited per `tour_departures`; bookings, payments, and refunds hang off a departure/booking.

## Commands
- Dev: `./mvnw spring-boot:run`
- Test all: `./mvnw test`
- Test one class: `./mvnw -Dtest=TourApplicationTests test`
- Compile only: `./mvnw compile`
- Package: `./mvnw package`
- Migrate: starts automatically with the app

## Conventions that differ from defaults
- Constructor injection; no `@Autowired` fields.
- DTOs are Java records with Jakarta Validation annotations.
- Controllers return `ResponseEntity<ApiResponse<T>>`; thrown `NoSuchElementException` → 404, `IllegalStateException` → 409, validation errors → 400 via `ApiExceptionHandler`.
- `@Transactional` boundaries live in `service/`; controllers contain no business logic.
- Entities use Lombok `@Data`, `Long` IDs with `GenerationType.IDENTITY`, and field names prefixed by the table concept (`bookingCode`, `paymentAmount`).
- DTO mapping is manual via `Response.from(entity)`; `mapper/` is empty and MapStruct is unused so far.
- Keep status strings (`PENDING`, `PAID`, `OPEN`, `SUCCESS`, etc.) aligned with the SQL `CHECK` constraints.
- Concurrent seat changes go through `TourDepartureRepository.findByIdForUpdate` (pessimistic write lock).

## Gotchas / never-do
- `ddl-auto=validate` means missing/renamed columns fail startup. Change schema through a new Flyway migration and update the entity together.
- Never modify `V1__create_schema.sql` after it has run anywhere. Add a new versioned migration.
- Do not add a second seat-decrement path. Booking creation currently locks the departure row and increments booked slots in one transaction.
- Payments are counted, not flagged: a booking becomes `PAID` when the sum of `SUCCESS` payments reaches `booking_total_amount`.
- `PaymentService` deduplicates by `payment_provider_txn_id`; preserve that idempotency when touching callbacks.
- `RefundService` locks the payment row and reserves refunds against the payment total; never validate refunds by reading without the lock.
- No authentication implementation yet despite security/OAuth2/JWT dependencies. Adding auth means adding a `SecurityFilterChain`; endpoints are currently protected only by Spring Security defaults.
- No CORS configuration. Add explicit allowed origins before wiring the Next.js frontend.
- DB env vars: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (optional `.env` import). Redis/RabbitMQ auto-config is disabled until those services are configured. Do not commit real credentials.
- `tour/src/main/resources/db/quanlybantour_booking_floor.sql` is a full reference schema + seed, not a migration. Never apply it on top of a Flyway database.

## Where things live
- Entry point: `src/main/java/com/j2ee/tour/TourApplication.java`
- Controllers: `src/main/java/com/j2ee/tour/controller/`
- Services: `src/main/java/com/j2ee/tour/service/`
- Repositories: `src/main/java/com/j2ee/tour/repository/`
- Entities: `src/main/java/com/j2ee/tour/model/`
- Requests/responses: `src/main/java/com/j2ee/tour/dto/`
- Shared envelope/paging: `src/main/java/com/j2ee/tour/common/`
- Migrations: `src/main/resources/db/migration/`
- Config: `src/main/resources/application.properties`

## Scoped rules
- Testing → see `src/test/AGENTS.md`
