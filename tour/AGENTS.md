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
- Controllers return `ResponseEntity<ApiResponse<T>>`; thrown `NoSuchElementException` → 404, `IllegalStateException` → 409, validation errors → 400 via `GlobalExceptionHandler`.
- `@Transactional` boundaries live in `service/`; controllers contain no business logic.
- Entities use Lombok `@Data`, `Long` IDs with `GenerationType.IDENTITY`, and field names prefixed by the table concept (`bookingCode`, `paymentAmount`).
- DTO mapping is manual via `Response.from(entity)`; MapStruct is still unused, but `mapper/` holds static mappers (`TourMapper`, `AccountMapper`).
- Keep status strings (`PENDING`, `PAID`, `OPEN`, `SUCCESS`, etc.) aligned with the SQL `CHECK` constraints.
- Concurrent seat changes go through `TourDepartureRepository.findByIdForUpdate` (pessimistic write lock).
- A tour guide cannot lead overlapping non-`CANCELLED` departures: `GuideOverlapValidator` enforces it on departure create/update and on tour `tourGuideId` changes (409); V8 adds `tour_departures`/`tours` triggers as the race-proof backstop. Validate *before* mutating the tour (JPQL queries auto-flush dirty state).

## Gotchas / never-do
- `ddl-auto=validate` means missing/renamed columns fail startup. Change schema through a new Flyway migration and update the entity together.
- Never modify an applied Flyway migration (`V1`–`V8` exist). Add `V9__*.sql` or later; keep `V1__create_refresh_tokens.sql` and `V2__create_schema.sql` names stable because the shared Supabase database already has them applied.
- Do not add a second seat-decrement path. Booking creation currently locks the departure row and increments booked slots in one transaction.
- `BookingService.create` links the booking to the authenticated `Account`, reserves seats, and persists one `Traveler` row per guest from `CreateBookingRequest.travelers` (must match `guestCount` when provided); it does not create `Traveler` rows when none are sent. Reads/updates/deletes are owner-scoped (`ROLE_ADMIN` bypasses).
- Booking lifecycle: `PENDING` → `PAID` via payments; `PAID` → `CONFIRMED` via `POST /api/bookings/{id}/confirm`; `CONFIRMED` → `COMPLETED` via `POST /api/bookings/{id}/complete`. Confirm/complete are owner-or-admin, locked with `findByIdForUpdate`, and return `409` out of state.
- Payments are counted, not flagged: a booking becomes `PAID` when the sum of `SUCCESS` payments reaches `booking_total_amount`.
- `PaymentService` deduplicates by `payment_provider_txn_id`; preserve that idempotency when touching callbacks.
- Demo payment: `POST /api/bookings/{bookingId}/payments/mock` is owner-only (admin bypass), records a `SUCCESS` `CARD` payment, pays the remaining balance, and creates the invoice. Repeated calls return the existing successful payment. It is gated by `app.payment.mock-enabled` (`PAYMENT_MOCK_ENABLED`, default `false`); tests enable it. The existing signed gateway callback is unchanged and admin-only.
- `RefundService` locks the payment row and reserves refunds against the payment total; never validate refunds by reading without the lock. `requestRemaining` reserves whatever is still refundable and is used by the departure-cancel cascade.
- Setting a departure to `CANCELLED` in `TourDepartureService.update` cascades: cancels `PENDING|PAID|CONFIRMED` bookings, clears `bookedSlots`, emails each owner, and reserves `REQUESTED` refunds for successful payments. `COMPLETED`/`CANCELLED` bookings are skipped; promotion usage is not released.
- `GET /api/admin/tours` maps with `TourMapper.toAdminDetail` (full description); create/update still return the public view. `POST /api/payments` accepts an optional `status` so an admin can record a successful cash payment in one call.
- JWT auth is implemented in `security/`: `SecurityFilterChain`, access-token filter, opaque refresh-token rotation, rate limits. Public reads are limited to `GET /api/tours/**`, auth `POST`s, Swagger and health; everything else needs a Bearer token.
- CORS is configured in `SecurityConfig` from `APP_CORS_ALLOWED_ORIGINS` (default `http://localhost:3000`) with credentials allowed. Keep the frontend origin in that list when deploying.
- DB env vars: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (optional `.env` import). Redis read caching is enabled by default (`SPRING_DATA_REDIS_URL`; set `SPRING_CACHE_TYPE=none` to disable). RabbitMQ auto-config is disabled until configured. Do not commit real credentials.
- `tour/src/main/resources/db/quanlybantour_booking_floor.sql` is a full reference schema + seed, not a migration. Never apply it on top of a Flyway database.

## Where things live
- Entry point: `src/main/java/com/j2ee/tour/TourApplication.java`
- Controllers: `src/main/java/com/j2ee/tour/controller/`
- Services: `src/main/java/com/j2ee/tour/service/`
- Repositories: `src/main/java/com/j2ee/tour/repository/`
- Entities: `src/main/java/com/j2ee/tour/model/`
- Security/auth: `src/main/java/com/j2ee/tour/security/` (`SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`, `AuthCookieService`, `RateLimitFilter`)
- Errors/envelope: `src/main/java/com/j2ee/tour/common/` (`ApiResponse`, `GlobalExceptionHandler`)
- Requests/responses: `src/main/java/com/j2ee/tour/dto/`
- Shared envelope/paging: `src/main/java/com/j2ee/tour/common/`
- Migrations: `src/main/resources/db/migration/` (`V1`–`V8`)
- Demo payment: `src/main/java/com/j2ee/tour/controller/MockPaymentController.java`, `PaymentService.payMock`
- Dev seed: `src/main/resources/db/seed/dev_seed.sql` (run manually via psql; not a Flyway migration)
- Config: `src/main/resources/application.properties`

## Scoped rules
- Testing → see `src/test/AGENTS.md`
## Rule 
never push or commit without permission