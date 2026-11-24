# Hành Trình Admin Shell Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` for native execution. Steps use checkbox syntax. Stop after Phase A and wait for the user's go-ahead before Phase B.

**Goal:** Give all existing admin pages a persistent shared workspace without changing public routes or management behavior.

**Architecture:** Keep one Next.js root. Introduce a `(public)` route group in Phase A, preserving the existing rendering on both sides of the boundary. Replace the temporary admin wrapper with a gated application shell in Phase B.

**Tech stack:** Next.js 16 App Router with Cache Components, React 19, TypeScript, Tailwind CSS 4, Vitest.

**Spec:** [Admin shell design](../specs/2026-11-22-admin-shell-design.md).

## Global constraints

- Preserve route URLs, permissions, API contracts, and user data; follow `fe-tour/AGENTS.md`; keep admin styling out of public pages.
- Never modify Spring Boot, read or modify `.env` files or secrets, perform real payments during verification, or commit, push, or run destructive Git commands without permission.
- Do not add dependencies. Do not use `use cache` in admin pages or fetch admin data during prerendering.
- Run quality commands from `fe-tour/`. If ordinary Next.js startup would load local `.env` files, use an isolated frontend copy that excludes them for both baseline and changed verification. Do not copy credentials.

## Review focus

1. Public unknown URLs and public segment-level `notFound()` calls both show public chrome (A).
2. Direct loads of VNPay return, verification, and reset keep their response status and baseline metadata and referrer/robots policies (A).
3. The first admin screen does not repeat `/api/auth/me`, but expiry and sign-out still invalidate access (B1).
4. A drawer with one or more focusable controls wraps focus in both directions and restores focus on dismissal (B2).
5. An invalid or unknown admin descendant never renders public chrome; an invoice prints without the shell or clipping (B3).

## Phase A — Route-group move only

**Files:** `fe-tour/app/layout.tsx`, `fe-tour/app/(public)/layout.tsx`, `fe-tour/app/not-found.tsx`, the public page and fallback files listed below, `fe-tour/components/public-layout.tsx`, and `fe-tour/app/admin/layout.tsx` (temporary compatibility wrapper only).

- [x] **Capture the baseline.** Run `npm run build` and save its complete route table outside the source tree. Record statuses, title, description, robots/referrer tags and response headers for `/payment/vnpay-return`, `/verify-email`, and `/reset-password` using the production server without real tokens or query data. If unable to build a baseline, report the blocker before claiming a comparison.
- [x] **Extract unchanged public chrome.** Export `PublicLayout({ children }: { children: React.ReactNode })` from `components/public-layout.tsx`, using the existing skip link, `SiteHeader`, `<main id="main">`, and `SiteFooter`. Keep body classes, fonts, metadata, and `FlashToast` in the root. Use the extracted wrapper from both the new `(public)/layout.tsx` and the existing admin layout temporarily, so Phase A changes neither appearance.
- [x] **Move public routes without editing their page contents.** Move the root `page.tsx` and route directories `account`, `forgot-password`, `invoices`, `login`, `my-trips`, `payment`, `register`, `reset-password`, `tours`, and `verify-email` under `(public)`. Leave all `app/admin` page files in place. Compare the three sensitive page files against their original tracked blobs; do not change `next.config.ts` or security settings.
- [x] **Preserve public fallbacks.** Move `app/error.tsx` under `(public)`, preserving the existing root-level error behavior for admin until Phase B if needed. Extract the original 404 body so `(public)/not-found.tsx` renders it inside the group's existing chrome and root `app/not-found.tsx` wraps it in `PublicLayout` for unmatched URLs. Do not introduce a new URL in Phase A.
- [x] **Run all four checks.** Run `npm run lint`, `npx tsc --noEmit`, `npm test`, and `npm run build`. Compare sorted production build route URLs against baseline, reporting additions and removals separately. Smoke-check an unknown public URL for HTTP 404, its existing fallback text, and public header/footer. Compare the three metadata and response-header snapshots against baseline.
- [x] **Stop and report.** Show the route comparison, verification results, and any failures or limitations. Do not start Phase B in this pass.

**Phase A checkpoint:** The complete build route tables matched (16 public pages, 8 admin pages, 1 framework fallback; 0 additions/removals). All 16 moved page files matched their original Git blobs. Lint, typecheck, 164 Vitest tests, and the production build passed in an isolated frontend copy that excluded environment files. Seven production HTTP snapshots matched. Chrome confirmed the public 404 and the public error boundary with public chrome. Next.js issued a pre-existing security upgrade warning; both builds succeeded on retry without changing dependencies.

## Phase B — Admin shell and page styling

### B1. Share validated admin access

**Files:** `fe-tour/components/admin-gate.tsx`, `fe-tour/components/admin-{accounts,bookings,catalog,dashboard,invoices,promotions,tours}.tsx`, `fe-tour/components/invoice-view.tsx`.

- [x] Establish a browser regression check with a test-only session/API fixture: initial graph for an authorized page has one `/api/auth/me`; unauthenticated and standard accounts make no admin data requests.
- [x] Expose the validated account through React context in `AdminGate`. Give protected consumers a way to obtain the current authorized session without revalidating on initial render. Preserve refresh and denied access, including session loss while the shell stays mounted; do not retain a stale access token indefinitely. Keep public invoice mode on its existing auth path.
- [x] Verify a dashboard load, a second admin section, expired access, and sign-out against the fixture. Check public sign-in in Chrome and confirm the public invoice route and fallback still match the baseline; no live invoice was used.

### B2. Build the persistent responsive shell

**Files:** `fe-tour/app/admin/layout.tsx`, `fe-tour/components/admin/{admin-layout,admin-sidebar,admin-topbar}.tsx`, `fe-tour/lib/admin-navigation{,.test}.ts`, `fe-tour/lib/admin-drawer{,.test}.ts`.

- [x] Add failing Vitest tests for `getAdminLocation(pathname)`: `/admin` selects Dashboard, `/admin/invoices/42` selects Invoices and titles the page Invoice details, `/adminish` and `/admin/invoices-extra` do not match those sections. Test `getDrawerFocusIndex(currentIndex, length, backwards)` for empty, single, wrapped, and outside-focus cases. Run `npm test -- lib/admin-navigation.test.ts lib/admin-drawer.test.ts` and confirm failure before adding the modules.
- [x] Implement the helpers and rerun the targeted tests. Mount the shell at the admin layout boundary, replacing the temporary public wrapper. Use existing SVG conventions and auth flow. Keep sidebar width while switching routes, reset only content scroll, and update the top bar from current navigation. Use a dependency-free drawer with immediate focus placement, keyboard containment, Escape and route-driven dismissal, and focus restoration.
- [x] In the browser, check desktop collapse, tablet nav, mobile drawer including Tab/Shift+Tab and Escape, exact and nested active states, profile menu link and sign-out, and stable shell mounting across internal navigation.

### B3. Style and verify all management pages

**Files:** `fe-tour/app/admin/{error,not-found}.tsx`, an admin-only catch-all if required for unmatched URLs, admin components and shared admin UI under `fe-tour/components/admin/`, and `fe-tour/components/invoice-view.tsx` for admin mode only.

- [x] Add admin error and not-found boundaries. Verify an invalid invoice ID and an unknown admin descendant never render public chrome. If root unmatched URL handling would otherwise win, route unknown admin descendants to the admin fallback.
- [x] Apply shared presentation to the eight inventoried routes: headings, actions, filters, table regions, pagination, panels, forms, inline confirmations, loading, empty, and error states. Preserve existing handlers and provide an accessible confirmation for departure deletion. Keep all dashboard values and management data intact. Verify browser print media for the invoice without chrome.
- [x] Run `npm run lint`, `npx tsc --noEmit`, `npm test`, and `npm run build`. In the browser traverse all seven sections and an invoice detail; exercise inline create/edit, filters, pagination, and confirmations where supported. Check 320, 768, 1024, and 1440 px. Verify no whole-page horizontal overflow. Recheck public chrome and sensitive routes; distinguish fixture-backed verification from checks using a live backend.
- [x] Write [`docs/archive/task-fe1-report.md`](../../archive/task-fe1-report.md) with the route comparison, modified files, change summary, verification evidence, and remaining limitations.
