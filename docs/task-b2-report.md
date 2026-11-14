# Task B2 — Public tour search, profile, change password

## Summary

Batch 2 adds public catalogue filtering/sorting, a self-service profile API, and in-session
password change. All work is inside `tour/`; `fe-tour/` was not touched, no migration was added,
and nothing was committed.

- **Tour search:** `GET /api/tours` accepts `destinationId`, `minPrice`, `maxPrice`, `startFrom`,
  `startTo`, `minDays`, `maxDays`, and `sort`. One upcoming `OPEN`/`FULL` departure must satisfy
  every supplied price/date predicate. Aggregate sorts (`priceAsc`, `priceDesc`, `dateAsc`) order by
  the same definition as the card stats, with `NULLS LAST` and `tourId ASC` as the tie-breaker.
- **Profile:** `GET /api/profile` returns the account plus its customer row (or null customer
  fields); `PUT /api/profile` upserts the customer row and treats `accounts.account_email` as the
  authoritative email.
- **Change password:** `POST /api/auth/change-password` verifies the current password, enforces the
  register password rules, revokes every refresh token, and returns a new access/refresh pair
  through the same cookie flow as login.

**Baseline before any change:** `183 tests, 0 failures, 1 error, 1 skipped`.
The error was pre-existing and unrelated: `BookingFlowIntegrationTests.unpaidBookingCannotBeRated`
hit a duplicate `bookings_booking_code_key` from a leftover shared-database row
(`BK-20261114-e1e3`).

**Final result:** `232 tests, 0 failures, 0 errors, 1 skipped, BUILD SUCCESS`.
The pre-existing booking duplicate-code error did **not** recur in this run (it depends on a
leftover shared-database row); B2 does not touch that path. The single skip is the intentionally
disabled `TourApplicationTests.contextLoads`.

---

## 1. Public tour search

### Query shape

`TourSearchRepository` / `TourSearchRepositoryImpl` is a Spring Data fragment; using a fragment is
deliberate because the price/date sorts order by an aggregate over departures, which a JPA
`Specification`/`Sort` cannot express.

- Filter data query returns **only tour IDs**, paged:
  - `where t.tourStatus = 'OPEN'`
  - optional keyword (`lower(t.tourName) like lower(concat('%', :keyword, '%'))`),
  - optional `destinationId` via one `exists` over `t.destinations`,
  - optional `minDays` / `maxDays` on `t.tourDurationDays`,
  - when any price/date filter is present, one `exists` over `TourDeparture` requiring the **same**
    departure to be `OPEN`/`FULL`, start after `now` (strict `>`), and satisfy all supplied
    `minPrice`, `maxPrice`, `startFrom`, `startTo` bounds.
- The count query is a plain `count(t.tourId)` with the same `where` and no join/group by.
- For `priceAsc` / `priceDesc` / `dateAsc`, the data query adds a left join on upcoming
  `OPEN`/`FULL` departures, groups by tour, and orders by
  `min(...) asc|desc nulls last, t.tourId asc`. The sort key therefore matches the
  `priceFrom` / `nextDepartureDate` computed by `findStatsByTourIds`.
- After the ID page, the service calls `findAllById`, reorders by the returned ID list (JPA does not
  preserve `IN` order), then performs the existing batch stats + first-image lookups.

`minDays` / `maxDays` alone do not join departures, so tours without future departures still appear.
An unknown `destinationId` produces an empty page with `200`, not `404`.

### Parameters and validation

| Parameter | Rule |
|---|---|
| `page` | `>= 0`, default `0` |
| `size` | `1..50`, default `12` |
| `keyword` | `<= 100` chars; trimmed; `%`/`_` sanitized as before |
| `destinationId` | any `Long`; unknown → empty page |
| `minPrice` / `maxPrice` | `>= 0`; both present and `minPrice > maxPrice` → `400` |
| `startFrom` / `startTo` | ISO dates; `startFrom > startTo` → `400` |
| `minDays` / `maxDays` | `>= 1`; both present and `minDays > maxDays` → `400` |
| `sort` | exactly `newest`, `priceAsc`, `priceDesc`, `dateAsc` (case-sensitive); anything else → `400` listing the allowed values |

Date semantics:

- `startFrom` is the start of that calendar day.
- `startTo` is an inclusive whole day: the query compares `< startTo.plusDays(1).atStartOfDay()`.

`sort` semantics:

- `newest` = `t.tourId DESC` (the schema has no tour created-at column; identity order is insertion
  order).
- no `sort` = `t.tourId ASC`.
- `priceAsc` / `priceDesc` = minimum upcoming departure price.
- `dateAsc` = earliest upcoming departure start.

### Caching decision

`listTours` keeps `@Cacheable(cacheNames = "tours")` but now only for the plain catalogue query:
`condition = "#criteria.isUnfiltered()"`. Filtered and explicitly sorted variants are **not**
cached, because filter combinations are client-controlled and would grow Redis keys without bound.
Departure mutations already evict the `tours` cache wholesale, so correctness is unchanged.

### Signature change

`TourService.listTours(int, int, String keyword)` became
`listTours(int, int, TourSearchCriteria)`. `TourControllerTest` and `TourServiceTest` were updated
for the new contract; the old overload was removed rather than left as a trap.

---

## 2. Profile API

### Endpoints

`GET /api/profile` (authenticated)

- Returns `customerId`, `username`, `email`, `emailVerified`, `name`, `phone`, `address`,
  `dateOfBirth`.
- `email` always comes from `accounts.account_email`.
- With no customer row, the customer fields are `null` and the response is `200`.

`PUT /api/profile` (authenticated)

Request:

```json
{
  "name": "Nguyen Van A",
  "email": "new@example.com",
  "phone": "0901234567",
  "address": "Da Nang",
  "dateOfBirth": "1990-01-02"
}
```

- `name` is required and must not be blank.
- `email` is optional; omitted/blank keeps the current account email.
- Input strings are trimmed.
- `dateOfBirth` must not be in the future.
- Unknown body fields (for example `accountId`) are ignored by Jackson.
- If no customer row exists, one is created with `account` set server-side.
- `customers.customer_email` mirrors `accounts.account_email`.

Email change:

- Compare case-insensitively with the current email.
- Different email: check `existsByAccountEmailIgnoreCaseAndAccountIdNot(...)`; duplicate → `409`,
  then set `emailVerified = 0`, `accountUpdatedAt = now(clock)`, and reuse the Batch 3 flow exactly:
  `AuthTokenService.issue(account, EMAIL_VERIFICATION)` +
  `EmailService.sendEmailVerification(...)`.
- Same email ignoring case: no verification reset, no new token.
- Refresh tokens are deliberately **not** revoked on email change; the password is unchanged and
  logging the user out of the current device would be surprising.

`AccountRepository` gained
`existsByAccountEmailIgnoreCaseAndAccountIdNot(String, Long)`. No migration was needed:
`customers.account_id` is already `UNIQUE` and `uq_accounts_email_lower` (V3) guards the email.
A concurrent first-time `PUT` can still hit the unique constraint, which the existing
`DataIntegrityViolationException` handler returns as `409`.

---

## 3. Change password

`POST /api/auth/change-password` (authenticated)

Request:

```json
{ "currentPassword": "old-password", "newPassword": "new-password" }
```

Rules:

- Wrong current password → **`400` `"Current password is incorrect"`**. This is intentionally not
  `401`: the frontend treats `401` as an expired session and could clear the session.
- New password is `8..100` characters (`@Size`) and at most **72 UTF-8 bytes**, matching register.
- New password must not match the current hash (`passwordEncoder.matches(...)`), not a string compare.
- On success: update the hash and `accountUpdatedAt`, revoke all refresh tokens through
  `RefreshTokenService.revokeAllForAccount(...)` (no `clearAutomatically = true` was added), issue a
  new refresh token, and return it through `AuthController.withCookie(...)` so the response body and
  `Set-Cookie` header match login.
- Already-issued **access tokens remain valid until their normal 15-minute expiry**; only refresh
  tokens are revoked.
- `RateLimitFilter` uses its own `"change-password"` bucket (10 requests/minute, keyed
  `ip|change-password`), so login/register retries do not consume this budget.

---

## Database

No migration. Reused:

- `customers.account_id UNIQUE` (V2) for the customer upsert,
- `uq_accounts_email_lower` (V3) for case-insensitive email uniqueness,
- existing `refresh_tokens` and `auth_tokens` tables.

---

## Files changed

### Main code

- `tour/src/main/java/com/j2ee/tour/dto/request/TourSearchCriteria.java` — filter/sort contract,
  `parseSort`, `hasDepartureFilter`, `isUnfiltered`.
- `tour/src/main/java/com/j2ee/tour/repository/TourSearchRepository.java` — repository fragment.
- `tour/src/main/java/com/j2ee/tour/repository/TourSearchRepositoryImpl.java` — dynamic JPQL
  `WHERE`, whitelisted `ORDER BY`, count query.
- `tour/src/main/java/com/j2ee/tour/repository/TourRepository.java` — extends the search fragment.
- `tour/src/main/java/com/j2ee/tour/service/TourService.java` — new `listTours` signature,
  normalization/validation, ID-page reordering, filtered-cache condition.
- `tour/src/main/java/com/j2ee/tour/controller/TourController.java` — public filter parameters and
  sort parsing.
- `tour/src/main/java/com/j2ee/tour/dto/request/ProfileRequest.java` — profile input DTO.
- `tour/src/main/java/com/j2ee/tour/dto/response/ProfileResponse.java` — profile output DTO.
- `tour/src/main/java/com/j2ee/tour/service/ProfileService.java` — GET/upsert, email authority,
  verification reset + Batch 3 token/email.
- `tour/src/main/java/com/j2ee/tour/controller/ProfileController.java` — `/api/profile`.
- `tour/src/main/java/com/j2ee/tour/repository/AccountRepository.java` — new existence check.
- `tour/src/main/java/com/j2ee/tour/dto/request/ChangePasswordRequest.java` — change-password DTO.
- `tour/src/main/java/com/j2ee/tour/service/AuthService.java` — `changePassword`.
- `tour/src/main/java/com/j2ee/tour/controller/AuthController.java` — change-password endpoint and
  cookie reuse.
- `tour/src/main/java/com/j2ee/tour/security/RateLimitFilter.java` — `"change-password"` bucket.

`SecurityConfig` needed no rule change: `/api/profile` and `/api/auth/change-password` already fall
under `anyRequest().authenticated()`.

### Tests / docs

- `tour/src/test/java/com/j2ee/tour/TourSearchIntegrationTests.java` — 12 DB-backed search tests.
- `tour/src/test/java/com/j2ee/tour/service/TourServiceTest.java` — new signature + validation tests.
- `tour/src/test/java/com/j2ee/tour/controller/TourControllerTest.java` — filter/sort/validation.
- `tour/src/test/java/com/j2ee/tour/service/ProfileServiceTest.java` — 10 unit tests.
- `tour/src/test/java/com/j2ee/tour/ProfileAndPasswordIntegrationTests.java` — 7 DB-backed tests.
- `tour/src/test/java/com/j2ee/tour/security/SecurityConfigTest.java` — `ProfileController`,
  `@MockitoBean ProfileService`, real cookie stubbing, profile/password security cases.
- `tour/src/test/java/com/j2ee/tour/service/AuthServiceTest.java` — 6 change-password tests.
- `tour/src/test/java/com/j2ee/tour/security/RateLimitFilterTest.java` — change-password bucket test.
- `docs/task-b2-plan-review.md` — pre-implementation review (already present).
- `docs/task-b2-report.md` — this report.

---

## Test results

Baseline (before changes):

```text
Tests run: 183, Failures: 0, Errors: 1, Skipped: 1
```

The error was pre-existing: `BookingFlowIntegrationTests.unpaidBookingCannotBeRated` →
duplicate `bookings_booking_code_key` from `BK-20261114-e1e3`, a leftover row in the shared
Supabase database.

What actually executed:

- **Non-DB tests:** controller and service unit tests plus `@WebMvcTest` security tests ran normally.
- **DB-backed tests:** `TourSearchIntegrationTests`, `ProfileAndPasswordIntegrationTests`,
  `BookingFlowIntegrationTests`, and `CrudIntegrationTests` ran against the shared Supabase
  datasource from `tour/.env`; class-level `@Transactional` rolled fixtures back.
- **Skipped:** `TourApplicationTests.contextLoads` remains intentionally skipped.
- One integration run hit a transient Supabase pooler reset (`SQLSTATE 08006`) during Flyway
  startup; rerunning the same class passed. That is infrastructure flakiness, not a code failure.

Focused runs:

```text
TourSearchIntegrationTests              12/12 pass
ProfileAndPasswordIntegrationTests       7/7  pass
ProfileServiceTest                      10/10 pass
AuthServiceTest                         21/21 pass
SecurityConfigTest                      32/32 pass
RateLimitFilterTest                      5/5  pass
TourServiceTest                         10/10 pass
TourControllerTest                      14/14 pass
```

Final full-suite result (`.\mvnw.cmd test`, 2026-11-14):

```text
Tests run: 232, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

Breakdown vs. the 183-test baseline: `TourControllerTest` 9 → 14, `TourServiceTest` 7 → 10,
`SecurityConfigTest` 27 → 32, `AuthServiceTest` 15 → 21, `RateLimitFilterTest` 4 → 5, plus the new
`TourSearchIntegrationTests` (12), `ProfileServiceTest` (10), and
`ProfileAndPasswordIntegrationTests` (7).

The earlier baseline error (`BookingFlowIntegrationTests.unpaidBookingCannotBeRated`, duplicate
`bookings_booking_code_key`) did not recur in the final run. It is a data-dependent flake caused by
leftover rows in the shared database, not by B2; the booking code and test were not modified.

---

## Risks / notes

1. Aggregate sorts cannot use the existing `(tour_id, status, start_date)` index for ordering; the
   database sorts the filtered set. Fine at current catalogue size; revisit if the catalogue grows.
2. Only the plain catalogue query is cached; filtered variants intentionally bypass Redis.
3. On email change, refresh tokens survive by design; access tokens from before a password change
   remain valid up to 15 minutes because they are stateless JWTs.
4. Concurrent first-time profile `PUT`s can race on `customers.account_id`; the loser gets `409`.
5. The baseline booking duplicate-code error is a shared-database flake; it did not occur in the
   final run and is not caused by B2.

---

## Manual verification

1. `GET /api/tours?minPrice=4000000&maxPrice=6000000&startFrom=2026-12-01&startTo=2026-12-31&sort=priceAsc`
   returns only tours with one departure satisfying all bounds, cheapest displayed price first.
2. `GET /api/tours?sort=cheapest` returns `400` with the allowed sort values.
3. `GET /api/tours?minPrice=900&maxPrice=100` returns `400`.
4. `GET /api/tours?destinationId=999999999` returns `200` with an empty page.
5. `GET /api/profile` with a token and no customer row returns `200` with null customer fields.
6. `PUT /api/profile` creates the customer row; changing `email` sets `emailVerified = 0` and sends
   a verification link, while changing only the case sends nothing.
7. `POST /api/auth/change-password` with a wrong current password returns `400`, not `401`.
8. A successful password change sets a new `refresh_token` cookie; the old refresh token cannot be
   used, and the old password no longer logs in.
