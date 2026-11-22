# Task G1 — Google Identity Services sign-in

> Date: 2026-11-21. Scope: `tour/` (backend) and `fe-tour/` (frontend), plus
> `docs/api-spec.md`. No `.env` file was opened, read, or modified; no token or secret was
> logged. Placeholder values only in documentation and `.env.example`. No commit was made.

## Outcome

- Added public `POST /api/auth/google` with body `{ "idToken": "..." }`. It returns the same
  `ApiResponse<AuthResponse>` body and `Set-Cookie` refresh-token contract as `POST /api/auth/login`
  through `AuthController.withCookie`.
- The backend verifies the Google ID token (RS256 signature, both accepted issuer spellings,
  audience equal to the configured client id, expiry against the injected `Clock`, and
  `email_verified == true`) before resolving an account. No Spring OAuth2 client, redirect flow,
  HTTP session, or client secret is used.
- Account resolution runs in one transaction: match `google_sub`; otherwise link by
  case-insensitive email; otherwise create a new `ROLE_USER` account.
- Blank `GOOGLE_CLIENT_ID` / `app.google.client-id` fails closed with `503`
  `Google sign-in is not configured`.
- Added Flyway migration `V9__google_sign_in.sql` and updated the `Account` entity so
  `ddl-auto=validate` passes.
- Added a separate `google` rate-limit bucket (10/minute/IP) and made the route public.
- `POST /api/auth/change-password` now returns `409` for a passwordless account and points to
  Forgot password. Password reset sets `password_set = true`.
- Frontend loads Google Identity Services with `next/script` (no new npm dependency), renders the
  official button on `/login` and `/register`, and hides it when
  `NEXT_PUBLIC_GOOGLE_CLIENT_ID` is blank.
- `/account/profile` shows a **Google linked** badge and replaces the change-password form with a
  **Set a password** link (Forgot password flow) when the backend reports `passwordSet: false`.
- Updated `docs/api-spec.md` with the endpoint, rate-limit table, profile flags, and
  change-password behavior.

## Design decisions

### Verifier library

Chose `org.springframework.security:spring-security-oauth2-jose` directly, using
`NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")`. This is the
smallest addition that gives Nimbus JOSE + JWT signature verification and JWKS caching; it avoids
the larger `google-api-client` / `google-http-client` stack. The decoder is created without a
network call at startup; the JWKS is fetched lazily on the first verification.

`NimbusGoogleIdTokenVerifierService` performs the checks itself so each failure has a controlled
`ApiException`:

- `iss` is read with `getClaimAsString("iss")` because Google also emits the bare
  `accounts.google.com` issuer, which is not a valid `URL` for `Jwt.getIssuer()`.
- `aud` must contain the configured client id.
- `exp` must be after `Clock.instant()`.
- `email_verified` must be boolean `true` (or the string `"true"`).
- `email` and `sub` must be non-blank.
- Any failure becomes `401 Invalid Google ID token`; a blank client id is checked first and becomes
  `503 Google sign-in is not configured`.

No email-domain restriction or allowlist was added, as required.

### Migration version

`V9__google_sign_in.sql` adds:

```sql
ALTER TABLE public.accounts ADD COLUMN IF NOT EXISTS google_sub VARCHAR(64) NULL;
ALTER TABLE public.accounts ADD COLUMN IF NOT EXISTS password_set BOOLEAN NOT NULL DEFAULT TRUE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_accounts_google_sub
    ON public.accounts (google_sub) WHERE google_sub IS NOT NULL;
```

The partial unique index allows any number of accounts without Google (`NULL`) while preventing two
accounts from claiming the same Google subject. Existing accounts keep working because
`password_set` defaults to `TRUE` both in the database and in the entity
(`Boolean passwordSet = Boolean.TRUE`). The V1–V8 migrations were not modified.

### Security case 4b — linking an existing unverified account

Decision: when a Google identity matches an existing account by email and that account has
`emailVerified != 1`, the backend:

1. replaces the password hash with a fresh random, unusable BCrypt-encoded value;
2. sets `password_set = false`;
3. revokes **all** of that account's refresh tokens through
   `RefreshTokenService.revokeAllForAccount`;
4. sets `google_sub`, `emailVerified = 1`, and `updatedAt`;
5. saves the account and issues a new token pair.

Reason: an unverified account may have been pre-registered by someone else who knows its password.
Linking Google without invalidating that password would leave an attacker with access. A verified
account keeps its existing password and simply gains `google_sub`, so normal password sign-in still
works. Disabled accounts are rejected with `403` **before** linking.

New Google accounts get a username derived from the email local part (sanitized, unique, maximum 50
characters), a random unusable password, `emailVerified = 1`, `status = 1`, and
`password_set = false`.

### Passwordless accounts

`AuthService.changePassword` checks `passwordSet` first (treating legacy `NULL` as usable) and
returns `409`:

```
This account has no password set. Use "Forgot password" to set one.
```

`register` and `resetPassword` explicitly set `password_set = true`, preserving normal behavior.
`ProfileResponse` now carries `boolean passwordSet` and `boolean googleLinked`; neither exposes the
hash or the Google subject.

## Contract

| Path | Request | Success | Failure |
|---|---|---|---|
| `POST /api/auth/google` | `{ "idToken": string }` (`@NotBlank`) | `200` `ApiResponse<AuthResponse>` plus refresh cookie | `400` validation; `401` invalid token/audience/issuer/expiry/unverified email; `403` disabled account; `429` rate limit; `503` not configured |

The frontend sends the Google credential unchanged as `idToken`; it never reads or logs the token.
After success it saves the session, publishes a flash message, and follows the existing `?next=`
logic used by the password form.

## Frontend behavior

- `components/google-signin-button.tsx` loads
  `https://accounts.google.com/gsi/client` with `next/script` (`afterInteractive`) and calls
  `google.accounts.id.initialize` / `renderButton`.
- No new npm dependency was added.
- If `NEXT_PUBLIC_GOOGLE_CLIENT_ID` is blank or whitespace, the component renders nothing, including
  the "or" divider, so the email/password form is unchanged.
- A synchronous in-flight ref prevents a duplicate credential callback (Strict Mode or a double
  click) from issuing two sign-ins.
- `withTimeout` adds a 15-second watchdog around the API call in addition to the existing 10-second
  browser fetch abort; every path clears the pending state, so the UI cannot spin forever.
- Error copy covers `401`, `403`, `429` (with `Retry-After` when numeric), `503`, `504` timeout, and
  network failure. A script load failure shows a fallback message and the email form remains usable.
- The pure helpers (`resolveGoogleClientId`, `googleSignInErrorMessage`, `withTimeout`) are in
  `lib/google-signin.ts` and unit-tested; the React component itself has no browser test.

## Tests and verification

New or updated tests:

- Backend unit: `GoogleAuthServiceTest` (8 tests, fake verifier; match/link/create, disabled,
  unverified case 4b, username collision, blank config), `NimbusGoogleIdTokenVerifierServiceTest`
  (8 tests; locally generated RSA key, wrong audience/issuer, expired token, unverified email,
  malformed token, blank client id). No test calls the real JWKS URL.
- Backend web/security: `AuthControllerTest` Google cases (body shape + refresh cookie),
  `SecurityConfigTest` public-route case, `RateLimitFilterTest` google bucket (10/minute/IP).
- Backend password/profile: `AuthServiceTest` `password_set` cases,
  `ProfileServiceTest` flags, `ProfileAndPasswordIntegrationTests` two DB-backed cases.
- Frontend: `lib/api.test.ts` (exact POST body), `lib/auth.test.ts` (session persistence),
  `lib/google-signin.test.ts` (9 tests for the pure helpers).

Commands actually run:

| Command | Result |
|---|---|
| `fe-tour: npm run lint` | Passed, 0 errors |
| `fe-tour: npx tsc --noEmit` | Passed, 0 errors |
| `fe-tour: npm test` | **18 files, 135 tests passed** |
| `fe-tour: npm run build` | Passed; all 26 routes generated |
| `tour: .\mvnw.cmd test` | **319 tests, 0 failures, 0 errors, 1 skipped; BUILD SUCCESS** (5:36 min) |

The single skipped test is the existing `TourApplicationTests` disabled case. Flyway applied V9 to
the test database during the suite and reported version `9 - google sign in`.

Tooling note: `next build` printed a security-advisory upgrade reminder for the installed Next.js
version. The second run continued and completed successfully. No Next.js upgrade was performed as
part of this task.

### Manual vs built

- **Built and automatically exercised:** all backend code and migrations; Google token verification
  against locally signed test tokens; account resolution/linking/creation; rate limiting; security
  route visibility; profile flags; passwordless change-password behavior; frontend API/auth helpers,
  lint, typecheck, and production build.
- **Manually inspected only:** the rendered Google button, profile layout, error copy, and the
  behavior of the real `accounts.google.com/gsi/client` script. The production build proves the
  code compiles and the routes exist; it does not exercise the Google iframe.
- **Not tested:** a real Google round trip. No live Google credential was obtained or sent, no real
  Google account was linked, and no browser session was created. A real round trip still needs the
  manual checklist below with a configured client id and an authorized JavaScript origin.

## Manual checklist before release

- [ ] Create a Google Cloud **Web application** OAuth client. Add the frontend origin (for local
      development `http://localhost:3000`) as an authorized JavaScript origin. No client secret is
      needed by this implementation.
- [ ] Set backend `GOOGLE_CLIENT_ID` (or `app.google.client-id`) to the same client id and restart.
- [ ] Set frontend `NEXT_PUBLIC_GOOGLE_CLIENT_ID` (local `.env.local`, deployment environment) and
      rebuild; the public env var is inlined at build time.
- [ ] Confirm `/login` and `/register` show the official Google button, and that a blank frontend
      env var hides both the button and the "or" divider.
- [ ] Sign in with a Google account that has no existing account: verify a new `ROLE_USER` account,
      `emailVerified = 1`, `password_set = false`, unique username, and a refresh cookie.
- [ ] Sign in again with the same Google account: verify it matches by `google_sub` and does not
      create a second account.
- [ ] Sign in with a Google email matching an existing **verified** password account: verify Google
      is linked, the existing password still works, and no duplicate account is created.
- [ ] Sign in with a Google email matching an existing **unverified** account: verify its old
      password no longer works, refresh tokens are revoked, `password_set = false`, and Google is
      linked.
- [ ] Try a disabled account; expect `403` and no linking.
- [ ] With the backend client id blank, call the endpoint and confirm `503`.
- [ ] Send a token with a wrong audience/issuer, an expired token, and an unverified-email token;
      confirm `401` and no account changes.
- [ ] Send more than 10 requests in a minute from one IP; confirm `429` and a usable `Retry-After`.
- [ ] On a Google-only account, open `/account/profile`: confirm the **Google linked** badge, no
      change-password form, and the **Set a password** link. Complete Forgot password and verify
      `password_set` becomes true and normal sign-in works.
- [ ] Confirm no `.env` value, ID token, access token, refresh token, password, or email content is
      written to logs or the repository.
