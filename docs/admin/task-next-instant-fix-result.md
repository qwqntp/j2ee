# Next.js instant-navigation investigation — result

## Progress

- [x] Findings written before any code changes
- [ ] Fix applied (blocked: affected segment not identified)
- [x] Lint/tests/build run (baseline only; there is no fix to verify)
- [x] Manual-check instructions written

## Baseline findings

- `fe-tour/package.json` specifies `next: "^16.4.5"`; installed Next.js is **16.4.5**.
- `fe-tour/next.config.ts` enables `cacheComponents` and `partialPrefetching`. It does not configure `instantInsights.validationLevel`.
- The reported text is “Next.js could not validate that a segment in your UI has instant navigation”. This was supplied by the user; it was **not observed** in the baseline build or the development log inspected.
- `npm.cmd run build` passed, exit 0, with no such error. The installed `instant` documentation says the default validation level is development-only; this build cannot prove the insight absent in development.
- An existing dev server was already running on port 3000; a separate `npm.cmd run dev -- --port 3107` refused to start because of that existing server. It was not stopped.
- Direct HTTP requests to `/admin`, `/admin/accounts`, `/admin/bookings`, `/admin/catalog`, `/admin/customers`, `/admin/customers/1`, `/admin/departures/1/passengers`, `/admin/invoices`, `/admin/invoices/1`, `/admin/payments`, `/admin/promotions`, `/admin/tours`, and `/admin/nonexistent` returned HTTP 200. No matching insight appeared in the inspected development log; `/admin/bookings` HTML did not contain the message. HTTP requests do not exercise browser-side development validation or authenticated navigation.
- Browser verification is blocked: the browser relay reported `paired-offline`. No affected route or dropped segment has been confirmed, and it is not established whether the user's report is dev-only or occurs in both environments.

## Root-cause hypothesis and fix decision

`app/admin/layout.tsx` places its page subtree inside `AdminGate`. On the initial server render, `AdminGate` shows “Checking admin access…” rather than its children, making it a plausible dropped-segment cause, **not a confirmed diagnosis**. The existing Suspense boundary is inside the gate. No change has been made because the diagnostic's exact segment was not observed and it would be unsafe to guess.

The official [unrendered-segment documentation](https://nextjs.org/docs/messages/instant-unrendered-segment) says an opt-out belongs on the dropped page or layout itself; opting out a parent layout does not generally exempt child pages. The [installed `instant` reference](https://nextjs.org/docs/app/api-reference/file-conventions/route-segment-config/instant) says `instant = false` does not prevent descendants from being validated. Reworking the gate to render protected children before validation risks altering access behavior. An app-wide `manual-warning` setting would reduce automatic validation across unrelated routes and is not justified without identifying the failure.

## Manual check / information needed

1. Start the backend and run `npm run dev` in `fe-tour` (or use the existing server). Open the Next.js DevTools overlay and browser console.
2. Visit `/admin`, `/admin/accounts`, `/admin/bookings`, `/admin/catalog`, `/admin/customers`, `/admin/invoices`, `/admin/payments`, `/admin/promotions`, and `/admin/tours`; also open actual customer, departure passenger, and invoice detail pages from their lists. Refresh each and navigate between admin pages using their links.
3. Check while signed out, as a non-admin, and as an administrator. Confirm protected content remains hidden from non-admins.
4. If the insight appears, copy its **complete text, named route and segment/file, and stack** and identify whether it followed a refresh or a client navigation. Share those details so the specific dropped segment can be fixed and verified. Also report any occurrences on non-admin routes.

## Files changed and verification

- `docs/admin/task-next-instant-fix-result.md`: this result report only; no shared frontend source files changed.
- Authentication, admin pages, config and backend were not edited.
- Baseline `npm.cmd run lint`: passed, exit 0.
- Baseline `npm.cmd test`: passed, 26 files and 201 tests, exit 0. These are the existing suite; they do not establish browser-level admin authorization behavior.
- Baseline `npx.cmd tsc --noEmit`: passed, exit 0.
- Baseline `npm.cmd run build`: passed, exit 0. No post-fix verification exists because no fix was applied.
- Dev browser validation and authenticated/unauthenticated browser behavior: not verified. The browser relay needs to reconnect.
