# Task Reset 1 — password recovery UI and rate limits

> Date: 2026-11-15. Scope: `fe-tour/` plus the explicitly approved minimal
> `tour/` rate-limit change. No `.env` file was opened, read, or modified; no commit.

## Outcome

- Added `/forgot-password` and `/reset-password` pages, plus a **Forgot password?** link on `/login`.
- Reset accepts the `?token=` query under `<Suspense>`, validates a new password and confirmation,
  and posts `{ token, newPassword }` to `POST /api/auth/reset-password`.
- Forgot accepts an email and posts `{ email }` to `POST /api/auth/forgot-password`. On a successful
  request, the same neutral message is shown whether or not the account exists.
- Both calls use `lib/api.ts`'s browser fetch timeout (10 seconds, surfaced as `ApiError(504)`) and
  a 15-second UI watchdog for a promise that never settles. A reset is **only** user-submitted;
  there is no mount effect for Strict Mode to replay. A synchronous in-flight ref prevents two
  simultaneous submissions. After any reset attempt, the same token cannot be submitted again from
  that page; success replaces the form with a sign-in link, and failure offers a new-link route.
- Added `forgot-password` (5/min/IP) and `reset-password` (10/min/IP) buckets in
  `RateLimitFilter`, each independent of the existing credentials and change-password buckets.
  Both return 429 and `Retry-After` when exhausted.
- The frontend reads numeric `Retry-After` seconds into `ApiError.retryAfterSeconds` and shows them
  for 429; otherwise it shows a generic wait message.

## Contract verified from backend sources

| Path | Request | Success | Failure |
|---|---|---|---|
| `POST /api/auth/forgot-password` | `{ "email": string }` (`@NotBlank`, `@Email`, max 255) | 200 envelope with `data: null`, message `If the email exists, a password reset link has been sent` | 400 validation; 429 rate limit |
| `POST /api/auth/reset-password` | `{ "token": string, "newPassword": string }` (token required/max 512; password 8–100 chars and max 72 UTF-8 bytes) | 200 envelope with `data: null`, message `Password reset` | 400 validation or invalid/expired/used token; 429 rate limit |

The frontend requests set `allowEmpty: true` for the success envelope. Validation uses JS string
length (matching Java's `@Size`/UTF-16 count) and `TextEncoder` for the independent UTF-8 byte cap;
it does not trim or alter a nonblank password before sending. Missing token shows a clear error and a
link to `/forgot-password`; 400 on reset explains that the link may be invalid, expired, or used.
Success links to `/login`. The forgot form's 429 message gives a wait time when readable.

### Read-only security check

`AuthService.resetPassword` calls `authTokenService.consume(token, PASSWORD_RESET)`, updates the
password, then calls `refreshTokenService.revokeAllForAccount(accountId)` in one transaction.
`AuthTokenService.consume` locks the token row, rejects missing/expired/previously used tokens with
400, and sets `usedAt`. `RefreshTokenRepository.revokeAllByAccountId` updates **all non-revoked
refresh tokens for the account**. Both requested guarantees are present; no backend change was needed
for either.

## Tests and verification

TDD red/green: the three new `RateLimitFilterTest` cases failed before the new bucket mappings
(requests passed instead of returning 429). The new frontend API/helper tests also failed before
their implementation. No additional test framework was installed.

| Command | Result |
|---|---|
| `fe-tour: npm run lint` | Passed, 0 errors |
| `fe-tour: npx tsc --noEmit` | Passed, 0 errors |
| `fe-tour: npm test` | **11 files, 93 tests passed** |
| `fe-tour: npm run build` | Passed; `/forgot-password` and `/reset-password` appear as static routes |
| `tour: .\mvnw.cmd "-Dtest=RateLimitFilterTest" test` | **8 tests, 0 failures, 0 errors** |
| `tour: .\mvnw.cmd test` | **274 tests, 0 failures, 0 errors, 1 skipped; BUILD SUCCESS** (3:41 min) |

New frontend tests: `lib/reset-password.test.ts` covers required/mismatched passwords, 8–100
characters, ASCII and multibyte 72-byte boundaries, missing/used tokens, 429 wait time, timeout,
server/network failures and neutral forgot error copy. `lib/api.test.ts` covers exact recovery
POST bodies, empty success responses, numeric and malformed `Retry-After`.

### Manual vs built

- **Manually inspected (read-only):** route/component patterns; backend controller/DTO/service,
  token consumption and refresh-token repository; existing CORS configuration.
- **Automatically exercised:** Vitest helper/API tests; rate-limit filter tests; frontend lint,
  typecheck and production build; full backend suite. The production build confirms both routes
  are emitted; it does not exercise the rendered form in a browser.
- **Not manually browser-tested:** layout, keyboard interaction, client form submission, real 429
  responses, or an end-to-end emailed token. No live reset token was consumed, no account password
  was changed and no browser verification is claimed. These should be checked with a controlled
  test account before release.

## Known deployment gap (reported, not changed)

The backend's CORS configuration allows cross-origin requests but does **not** expose the
`Retry-After` response header. Browsers only let JavaScript read CORS-safelisted response headers
unless `Access-Control-Expose-Headers` lists the others. Thus the frontend can display seconds for
same-origin/mocked responses, but on the normal cross-origin `localhost:3000` → `localhost:8080`
deployment it will usually show the generic 429 wait message. To show the actual seconds in a
browser, a separate approved backend change should expose `Retry-After` in `SecurityConfig` and
cover the CORS response in a test; this task kept backend edits limited to the authorized filter.

No `.env` was accessed. No commit was made.
