# Task Fix 2 — verification page stuck on "Verifying…"

> Date: 2026-11-14
> Scope: `fe-tour/` only. No backend code, no `.env` file read, no commit.
> Status: fixed; lint, typecheck, tests and production build pass. Browser behaviour checked
> against the running dev server with mocked API responses.

## Root cause

`components/verify-email.tsx` used two guards that fought each other under React Strict Mode:

```tsx
const started = useRef(false);

useEffect(() => {
  if (started.current) return;   // run-once guard
  started.current = true;
  let active = true;

  async function verify() {
    setState({ kind: "verifying" });
    await verifyEmail(token);
    if (active) setState({ kind: "success" });  // result discarded
  }

  void verify();
  return () => {
    active = false;              // Strict Mode cleanup runs immediately
  };
}, [token]);
```

In development React Strict Mode mounts, cleans up, then mounts again:

1. Effect run 1 sets `started.current = true`, sets `active = true`, and starts the single-use
   `POST /api/auth/verify-email`.
2. The Strict Mode cleanup sets `active = false`.
3. Effect run 2 sees `started.current === true` and returns early. It does **not** start a new
   request and does **not** re-attach a cleanup.
4. The first request succeeds, but its handler checks `if (active)` — false — and throws the
   success away.

Nothing is left to move the state out of `verifying`, so the page shows "Verifying your email…"
forever. The account is verified in the database, which is exactly what the evidence showed.

A second request (another tab/reload) gets `400 Invalid or expired token` because the first call
consumed the token, but the old code could not recover from that either: the page had no
successful result to fall back to and no retry path.

`app/verify-email/page.tsx` was already correct: it wraps `<VerifyEmail />` in `<Suspense>`, so
`useSearchParams()` is valid, and the token is read with `params.get("token")?.trim()`. No change
was needed there.

## Fix

`components/verify-email.tsx`:

- **In-flight request ref.** `requestRef` stores the promise. Strict Mode's second effect reuses the
  same request instead of sending a second call for a single-use token.
- **No result-discarding cleanup.** The effect no longer returns a cleanup that flips an `active`
  flag. The promise's handlers always apply the result to state.
- **Single-use success guard.** `succeededRef` is set before the success state is applied. Once
  true, no new request is started, and a late `400` is treated as success
  (`succeededRef.current && isConsumedToken(error)`), satisfying "400 after success still shows
  success".
- **Terminal states only.** Initial state is `verifying` when a token exists, otherwise the
  missing-token error. `lib/api.ts` already aborts browser requests after 10s and turns that into a
  `504 ApiError`; a 15s watchdog (`VERIFY_TIMEOUT_MS`) additionally guarantees the page leaves
  `verifying` even if a promise never settles.
- **Retry.** Network, 429, 5xx and 504 failures render a visible **Try again** button that calls the
  same request starter. A 400 is not retryable because the token is already dead.
- **Clear expired/reused message.** A genuine 400 says the link is invalid, expired, or already
  used, tells the user to check whether the email already shows as verified, and links to
  `/account/profile`.
- **Session refresh on success.** After a successful verify, `ensureSession()` refreshes the cached
  account from `/api/auth/me` (falling back to the refresh cookie), so `emailVerified` updates in the
  stored session/header without a reload. The success card already links to `/account/profile` and
  `/tours`.

New pure helper `lib/verify-email.ts`:

- `describeVerifyError(error)` → `{ message, retryable, checkProfile }`
  - `400` → invalid/expired/reused copy, not retryable, profile check
  - `504` → timeout copy, retryable
  - `429` / `5xx` → retryable
  - network/unknown → "Could not reach the booking service…", retryable
  - other `ApiError` → API message, not retryable, profile check
- `isConsumedToken(error)` → true only for `ApiError` status `400`
- `MISSING_TOKEN_FAILURE`, `TIMEOUT_FAILURE`, `VERIFY_TIMEOUT_MS`

## Tests added

`lib/verify-email.test.ts` (Vitest, no new dependency):

- `isConsumedToken` accepts a 400 and rejects 500/network errors
- 400 maps to the profile-check message and is not retryable
- 504/429/500/503 are retryable
- network failure is retryable with the reachability message
- other client errors keep the API message
- missing-token copy is clear

## Verification

Commands run from `fe-tour/` (PowerShell blocks the `.ps1` shims, so `.cmd` entrypoints were used):

| Command | Result |
|---|---|
| `npm.cmd run lint` | clean |
| `npx.cmd tsc --noEmit` | clean |
| `npm.cmd test` | **10 files / 83 tests passed** |
| `npm.cmd run build` | **BUILD SUCCESS**, `/verify-email` prerendered |

### Browser verification (actually exercised)

The Next dev server (`localhost:3000`) and backend (`localhost:8080`) were already running. A real
Chrome session was driven against the dev server while `POST /api/auth/verify-email` was mocked at
the browser layer, so no real single-use token was consumed:

1. **Success under Strict Mode** — first call `200`, any later call `400`.
   - Result: **1 request**, page showed **"You are all set."**, profile link present.
   - This is the regression case: the first successful call is no longer discarded.
2. **Genuine 400 on a fresh page** — always `400 Invalid or expired token`.
   - Result: page showed **"We could not verify this link."** with the
     invalid/expired/already-used explanation, an **Open your profile** link, and **no retry**
     button.
3. **Network error then retry** — first call aborted, second `200`.
   - Result: page showed **"Could not reach the booking service…"** with a **Try again** button;
     clicking it produced **"You are all set."** (2 requests total).

### Only built / reasoned, not live-tested

- No real email link with an unused token was opened end to end. Doing so would consume a real
  single-use token and mutate the account, so the successful backend response was simulated in the
  browser. The real `400` path was also simulated rather than taken from a consumed token.
- The "400 after success in the same page session" branch is defensive: the fix prevents the second
  request after success, so the branch could not be forced from the UI. It is covered by
  `isConsumedToken` tests and by code review; the browser run confirms only one request is sent.
- `ensureSession()`'s account refresh is exercised indirectly (it runs on the success screen), but
  the logged-in header/profile update was not re-checked in a logged-in browser session.

No `.env` file was read or modified. The backend was not changed. No commit was made.
