# Review 1 Result

**Status:** Complete and merged into `phat` on 2026-10-27.

- Base: `de70aa8`
- Head: `01347b4`
- Merge: fast-forward, 11 commits
- State: local only; `origin/phat` is 13 commits behind (the docs commit, 11 feature commits, and this result file)

**Verdict:** Final whole-branch review found no Critical or Important issues. Ready to deploy once the database gates below pass.

## Commits

| Commit | Change |
|---|---|
| `aceaaf1` | Return 404 and 405 instead of 500 |
| `1e7068d` | Bootstrap the full schema with Flyway |
| `0b6cbb4` | Enforce unique account email in the database |
| `7b05252` | Filter tour departures in the database |
| `3931939` | Fetch roles with the admin account list |
| `936a78d` | Purge expired refresh tokens daily |
| `699f7f2` | Cover frontend formatting and demo data |
| `8a6bef1` | Cover demo tour description and destination search |
| `3a11371` | Remove unused backend dependencies |
| `ec639bb` | Raise footer text contrast to WCAG AA |
| `01347b4` | Optimize local editorial images with `next/image` |

## Verification

Run on the merged tree (`phat` at `01347b4`):

| Check | Result |
|---|---|
| `tour/`: `.\mvnw.cmd clean test` | 58 tests, 0 failures, 1 skipped (`TourApplicationTests.contextLoads`, needs PostgreSQL) |
| `fe-tour/`: `npm ci` + `npm test` | 15/15 passed across 2 files |
| `fe-tour/`: `npm run lint` | Passed |
| Real PostgreSQL / Flyway | Not run: no local PostgreSQL, `psql`, or Docker was available |

## Deferred minors (optional follow-ups)

- M1: 405 responses lack the `Allow` header.
- M2: Controller tests bypass the security chain, so unauthenticated unknown paths are only proven to return 404/405 with security disabled.
- M3: `docs/progress.md` still has stale counts/purge text; `docs/tech-stack.md` still lists removed dependencies.
- M4: The refresh-token purge cron has no `zone` (runs in the server default zone).
- M5: `V2__create_schema.sql` mixes unqualified and `public.` identifiers.
- M6: Departure filtering and `@EntityGraph` behavior are covered by mocks only.
- M7: Card images use `sizes="100vw"`, so the browser may fetch larger images than needed.

## Deployment gates

1. Check for case-insensitive duplicate emails on the shared database. Expect zero rows:

   ```sql
   SELECT lower(account_email), count(*)
   FROM accounts
   GROUP BY lower(account_email)
   HAVING count(*) > 1;
   ```

   `V3` aborts startup if any duplicate exists. Its `IF NOT EXISTS` check matches the index name only, so a same-named different index would be skipped.

2. Apply Flyway `V1`-`V3` to a disposable PostgreSQL first. Do not edit or rename `V1`: the shared Supabase database already has version `1`, and a checksum change blocks startup.

3. Apply the upgrade to the shared Supabase database.

4. Run the manual seed. On a fresh database, registration fails with `Default role is missing` until the seed has run.
