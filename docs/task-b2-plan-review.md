# Task B2 — plan review

**Verdict: close, but not implementation-ready.** The scope, constraints, and conventions are right; the gaps are in three places that will otherwise be discovered mid-implementation: (1) the tour-sort query is underspecified and one of the two suggested mechanisms cannot do it, (2) several filter/email semantics are ambiguous, (3) the change-password endpoint will break the existing test harness in a non-obvious way.

Everything below is verified against the current code, not assumed.

---

## What the plan gets right

- `TourSummaryResponse` needs no change — `TourMapper.toSummary` already tolerates missing stats, and `priceFrom` / `nextDepartureDate` come from `findStatsByTourIds`. Good.
- Price/date filtering can reuse the exact "upcoming" predicate from `TourDepartureRepository.findStatsByTourIds` (`status in ('OPEN','FULL') and startDate > :now`). Keep the strict `>`, not `>=`.
- Both new endpoint groups already fall under `anyRequest().authenticated()`: `/api/profile` is not in the admin matcher list (`/api/customers/**` is, so the plan is right to avoid that prefix), and `/api/auth/change-password` is not in the explicit `permitAll` POST list. `SecurityConfig` should need **no rule change**.
- No migration is needed. `customers.account_id` is already `UNIQUE` (V2), and `uq_accounts_email_lower` (V3) is the DB guard for the 409.
- Batch 3 email verification is real and reusable: `AuthTokenService.issue(account, EMAIL_VERIFICATION)` + `EmailService.sendEmailVerification(...)`, exactly as `AuthService.register` does.

---

## Blocking issues

### 1. `Specification` cannot produce the required sorts — pick the query shape now

`priceAsc` / `priceDesc` / `dateAsc` sort by `min(departurePrice)` / `min(departureStartDate)`. Spring Data's `Pageable` / `Sort` resolves property names against the root entity, so `Sort.by("priceFrom")` cannot work: `priceFrom` is not a `Tour` property, and Hibernate does not reliably support correlated subqueries in `ORDER BY`. A `Specification` can express the WHERE clause but not the ORDER BY, and you cannot paginate the filter and the sort in two different queries without producing a wrong page.

Commit to this shape instead (it keeps everything in SQL and avoids N+1):

- One JPQL query returning **only `t.tourId`**, with:
  - `left join TourDeparture d on d.tour = t and d.status in ('OPEN','FULL') and d.startDate > :now and <price/date predicates>`,
  - tour-level predicates (`tourStatus='OPEN'`, keyword, `destinationId` via `exists`, `minDays` / `maxDays`) in `WHERE`,
  - `group by t.tourId`,
  - `having count(d) > 0` **only when** a price/date filter is present (this is what excludes "no upcoming departure" tours for price/date filters while keeping them for other filters),
  - `order by` the aggregate, `nulls last`, plus `t.tourId asc` as a deterministic tie-breaker.
- A matching `countQuery` using `count(distinct t.tourId)` with the same joins/filters — otherwise `totalElements` is wrong.
- Then `findAllById` + reorder by the returned ID list, then the existing batch `findStatsByTourIds` + `findFirstImageUrls` calls.

Verify `NULLS LAST` support in Hibernate 6 at the start (fallback: `order by case when min(d.price) is null then 1 else 0 end, min(d.price)`). If this is uncertain, do a 20-minute spike before writing the plan's tests. Also add the tie-breaker — without it, equal prices/dates give unstable pages.

If you keep the `spec/` package idea, scope it to WHERE only and say so; as written the plan invites a dead end.

### 2. "ANY upcoming departure matches" is ambiguous

Your own example set exposes it: departure A price 100 in January, departure B price 500 in June, query `minPrice=400&startTo=2026-02-01`. Implementing each predicate as a separate `EXISTS` returns the tour; requiring one departure to satisfy all predicates does not.

**Recommendation:** one departure must satisfy **all** supplied price/date predicates (single `EXISTS` / single joined alias). This is the intuitive meaning of a filter and matches option (1) above. Write it into the plan and into a test.

Also pin down:

- `startFrom` = `startFrom.atStartOfDay()`; `startTo` = **inclusive whole day**, i.e. compare `< startTo.plusDays(1).atStartOfDay()`. A departure at 08:00 on `startTo` must match.
- `startFrom > startTo`, `minPrice > maxPrice`, `minDays > maxDays` → 400 (the plan only mentions price).
- `minDays` / `maxDays` alone must **not** hide tours with no departures.
- Non-existent `destinationId` → empty page 200, not 404 (it's a filter).

### 3. Changing `TourService.listTours` breaks existing tests — plan for it

`TourControllerTest` stubs `tourService.listTours(0, 12, null)` and `TourServiceTest` calls the same 3-arg method in three tests. A silent overload would compile but bypass the new semantics/cache key. Change the signature (e.g. `listTours(int page, int size, TourSearchCriteria criteria)`) and update both test classes as part of the task, plus the controller's parameter wiring.

### 4. `newest` is undefined

`Tour` has no `createdAt` column. Define `newest` = `tourId DESC` (identity order is insertion order) and say so. Also validate `sort` against the four exact strings, case-sensitively, returning 400 with the allowed values — don't let an unknown value fall through to the default silently.

### 5. Profile: email source of truth and no-customer behavior are undefined

`accounts.account_email` is the unique/auth field; `customers.customer_email` is a separate nullable column. The plan says "email" for both directions without saying which wins. Decide:

- **Accounts is authoritative.** `GET` returns `account.accountEmail`; `PUT` with an email updates `accounts.account_email` **and** mirrors it to `customers.customer_email`. (Invoice snapshots fall back to `account.getAccountEmail()`, so nothing depends on the customer copy — mirroring is for consistency only.)
- `email` omitted/blank in `PUT` = keep current (otherwise a profile form that omits email would null out a `NOT NULL`-ish identity field).
- `GET` with no `Customer` row → **200** with null customer fields (not 404), because the plan's own upsert flow implies the row is optional.
- `PUT` with no `Customer` row → create one with `account` set server-side; concurrent double-insert lands on the `account_id` unique constraint → existing `DataIntegrityViolationException` handler gives 409. Acceptable; note it.

Add the repository method the plan needs: `existsByAccountEmailIgnoreCaseAndAccountIdNot(String email, Long accountId)`. Trim the input. If the new email is equal ignoring case to the current one, do **not** reset verification or send a token.

### 6. Email change must reuse Batch 3 exactly

Concrete sequence (same as register): save account with `emailVerified = 0` and `accountUpdatedAt = now(clock)`, then `authTokenService.issue(account, AuthTokenService.EMAIL_VERIFICATION)` and `emailService.sendEmailVerification(newEmail, token)`. Do not add a second token type or a parallel verifier. Also decide (and document) whether to revoke refresh tokens on email change — recommend **no** (it would log the user out of the device they're using); the password is unchanged.

### 7. Change-password: pick 400, not 401

Recommend **400 Bad Request, "Current password is incorrect"**. Reason grounded in this repo: the frontend treats 401 as "session expired/invalid" (`fe-tour/lib/auth.ts` `ensureSession`, and the 401 branches in `my-trips.tsx`, `payment-form.tsx`, `invoice-view.tsx`). Returning 401 for a wrong password would look like an auth failure and risks the client clearing the session. 401 also contradicts "the request is authenticated but the payload is wrong". Document the choice in the report.

Other required detail:

- DTO `@Size(min=8, max=100)`; service checks UTF-8 bytes `<= 72` → 400 (exact register rule). `RegisterRequest` / `AuthService.register` currently do the byte check in the service; mirror it.
- "Must differ" = `passwordEncoder.matches(newPassword, account.getPassword())`, not string comparison.
- Revoke via the existing service wrapper `refreshTokenService.revokeAllForAccount(accountId)`, then `refreshTokenService.issue(account)`. **Do not** add `clearAutomatically = true` to `revokeAllByAccountId` — it would detach the `Account` you then need for the new token. Bulk update + insert in one transaction is fine.
- Reuse `AuthController.withCookie(...)` so the response is identical to login: `ApiResponse<AuthResponse>` + `Set-Cookie`.
- Document that **already-issued access tokens remain valid until their 15-minute expiry**; only refresh tokens are revoked.

### 8. Rate limit: use its own bucket

`RateLimitFilter` keys on `ip|bucket`. Reusing `"credentials"` means a user's login retries count against the password change and vice versa. Use a distinct bucket (e.g. `"change-password"`, limit 10 — the existing ternary already defaults to 10). Add the switch case and a `RateLimitFilterTest` case for the 11th request → 429 and a distinct key.

### 9. `SecurityConfigTest` will fail (NPE) if written naively

- `/api/profile` needs `ProfileController` added to the `@WebMvcTest(controllers = {...})` list **and** a `@MockitoBean ProfileService`; otherwise the request 404s, not 200.
- `/api/auth/change-password` returns via `cookieService.create(...).toString()`. `AuthCookieService` is a `@MockitoBean` and returns `null` by default → NPE → 500. Stub it with `ResponseCookie.from(...)...build()`.
- Don't add `TourController` to that list: `publicTourReadDoesNotRequireAuthentication` currently asserts 404 for `GET /api/tours` precisely because it is unmapped there.

---

## Test-plan gaps

- **Shared database.** `application-test.properties` deliberately uses the application Supabase datasource; `@SpringBootTest` tests are `@Transactional` so inserts roll back, but the DB may already contain seeded OPEN tours. Assert **relative order among uniquely-keyed fixtures** (unique keyword prefix / unique destination), never global page order, or the sort tests will be flaky.
- **Fixtures.** `TourDeparture.departureCreatedAt` and `TouristDestination.destinationName` are `NOT NULL` and are easy to forget (Hibernate `validate` won't catch it, the insert will).
- **Missing filter cases:** tour with **no departures** appears unfiltered and last in `priceAsc` / `priceDesc` / `dateAsc`; excluded by price/date filters; **included** when only `minDays` is set; one-departure-satisfies-all-prices semantics; `startTo` inclusive end-of-day; `minPrice > maxPrice` → 400; unknown `sort` → 400; page-count correctness (`totalElements`) with a destination filter.
- **Change password:** wrong current → chosen status; same-as-current → 400; 72-byte boundary; two refresh tokens before the change — both revoked after, new one works; old password login fails, new succeeds; `accountUpdatedAt` advanced (use a fixed `Clock` in the unit test).
- **Profile:** create-when-missing; update existing; duplicate email → 409 (and the DB-index race path); email unchanged-case → no `emailVerified` reset; future `dateOfBirth` → 400; `name` blank → 400; `accountId` in the body is ignored/unknown.
- **Baseline first.** Capture the current suite numbers before touching code. `task-b1-report.md` says 133 with DB; `task-b3-report.md` could not run the DB tests. The final report must state what actually executed, not just "counts".

---

## Decisions to lock in the plan

| Question | Recommendation |
|---|---|
| `newest` | `tourId DESC` (no created-at column) |
| Sort validation | exact strings, 400 on anything else |
| Price+date filter semantics | one departure satisfies all supplied predicates |
| `startTo` | inclusive full day |
| Days validation | `>=1`, `minDays <= maxDays` else 400 |
| No-departure tours | only excluded by price/date filters |
| Profile email authority | `accounts.account_email`; mirror to customer |
| Profile `PUT` email omitted | keep current |
| `GET /api/profile` without customer | 200, customer fields null |
| Email change | `emailVerified=0` + Batch 3 verification token/email; no refresh-token revocation |
| Wrong current password | 400 |
| New password rule | 8–100 chars, ≤72 UTF-8 bytes, `matches()` against hash |
| Rate-limit bucket | distinct `"change-password"`, 10/min |
| Migration | none |

---

## Files that will change (frontend untouched)

**Part 1:** `TourController`, `TourService`, `TourRepository` (new ID-page query + count query), a new `TourSearchCriteria` (or explicit params), `TourControllerTest`, `TourServiceTest`, and a new `TourSearchIntegrationTests` (or a new class alongside `BookingFlowIntegrationTests`).

**Part 2:** `controller/ProfileController`, `service/ProfileService`, `dto/request/ProfileRequest`, `dto/response/ProfileResponse`, `AccountRepository` (add `existsBy...AndAccountIdNot`), `SecurityConfigTest`.

**Part 3:** `AuthController` (new method), `AuthService.changePassword`, `dto/request/ChangePasswordRequest`, `RateLimitFilter`, `RateLimitFilterTest`, `SecurityConfigTest`, `AuthServiceTest`, plus integration coverage.

**Docs:** `docs/task-b2-report.md`.

No new dependencies, no `fe-tour/` edits, no migration.

---

## Risks worth naming in the report

1. Price/date sorts are aggregate sorts; the existing `(tour_id, status, start_date)` index cannot supply the ordering, so the DB sorts the filtered set. Fine at current catalogue size, worth a note.
2. `listTours` is `@Cacheable("tours")`; adding 8 optional parameters multiplies cache keys (10-min Redis TTL, and departure changes already evict `tours` wholesale, so correctness is fine). Consider whether filtered variants should be cached at all.
3. Integration tests depend on the shared Supabase instance; a full `.\mvnw.cmd test` may not complete, as Batch 3 showed.
4. Only refresh tokens are revoked on password change; access tokens live up to 15 minutes.
5. Concurrent first-time profile `PUT` can hit the `account_id` unique constraint → 409 rather than upsert.

---

## Suggested execution order

1. Tour filters: query shape + criteria + controller/service + unit tests (this is the risky part; do it first).
2. Cache/key + existing-test updates; run the non-DB suite.
3. Profile (service → controller → tests).
4. Change password (service → controller → rate limit → security tests).
5. Integration tests, then the full suite and the report with real counts.

---

## Open questions before implementation

1. **Departure filter semantics** — confirm "one departure must match all supplied price/date predicates" (recommended) rather than independent `EXISTS` per filter.
2. **Profile email field** — confirm "accounts is authoritative, customer mirror, omitted email keeps current" rather than making `email` required on every `PUT`.
3. **Change-password status** — confirm 400 for a wrong current password (recommended over 401).
