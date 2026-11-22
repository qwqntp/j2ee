# Hành Trình shared admin shell — V1

## Objective

Give administrators a compact workspace that stays in place as they move between management pages. Keep the public site and all existing admin operations working at the same URLs.

The design follows the written brief: no reference screenshot was available during discovery. Create and edit flows stay embedded where they exist today. The dashboard currently presents KPIs and data tables, not charts; this change restyles those views without inventing metrics.

## Routing and access

- Keep `fe-tour/app/layout.tsx` as the single root for document markup, fonts, global styles, metadata, and the existing flash toast.
- Move public page entries and their existing header, footer, skip link, and main wrapper under `fe-tour/app/(public)/`. The route group does not appear in URLs. Preserve public links, metadata, and visible navigation.
- Move the public error boundary under `(public)` so it renders inside public chrome. Keep the root unmatched-URL 404 wrapped in that same chrome and reuse the existing 404 content inside the public group for segment-level not-found responses. In Phase B, handle admin errors and unknown admin descendants inside `app/admin` so they never fall through to public chrome.
- Preserve the `/payment/vnpay-return`, `/verify-email`, and `/reset-password` page source and effective metadata and security policies. Check the VNPay return page's `no-referrer` and `noindex, nofollow` in the same form emitted before the move; do not relocate or change existing response-header rules.
- Mount the admin shell once at `fe-tour/app/admin/layout.tsx`. Its main landmark contains the active page; the sidebar and top bar stay mounted during client-side admin navigation. Provide admin-specific fallback presentation where needed, rather than rendering public chrome for admin errors.
- Reuse `AdminGate`, the existing `ROLE_ADMIN` check, and `signOut()`. Share the validated account through React context so rendering the shell and its initial page does not request `/api/auth/me` twice. Preserve session refresh and backend authorization. Loading, signed-out, and non-admin states must not render protected content or management controls.
- Do not use `use cache` in admin pages or fetch admin data during prerendering. A prerendered shell or access-check placeholder may contain no admin data; protected requests remain client-side after access validation.
- A URL-based chrome toggle would couple public and admin layouts. Multiple root layouts would isolate them but cause full document loads when crossing between them. A route group below one root avoids both trade-offs.

### Route inventory

| Route | Coverage |
| --- | --- |
| `/admin` | Dashboard and backend-provided statistics |
| `/admin/bookings` | Booking filters, details, payments, pagination, and status actions |
| `/admin/accounts` | Account search, details, permissions, and confirmations |
| `/admin/promotions` | Promotion list, inline create/edit, and deletion |
| `/admin/invoices` | Invoice filters, pagination, and detail links |
| `/admin/invoices/[bookingId]` | Invoice detail, return link, and printing |
| `/admin/catalog` | Resource navigation, lists, and inline create/edit/delete |
| `/admin/tours` | Tour and departure lists, forms, and status changes |

## Shell and visual design

- Build focused `AdminLayout`, `AdminSidebar`, and `AdminTopbar` components. Define section labels, icons, and active-route matching in one place. Match `/admin` exactly and keep a section highlighted on its descendants. The invoice detail top bar identifies the page and links back to Invoices.
- Use the existing Inter font for the workspace and Playfair for the Hành Trình wordmark. Reuse forest deep (`#173a33`), forest (`#214e45`), ivory (`#f7f4ec`), charcoal (`#26332d`), and line (`#d8d2c4`), with white panels and accessible state colors. Use subtle borders, compact spacing, and restrained motion instead of a marketing hero or decorative effects.
- Keep navigation visible on desktop and tablet with an expand/collapse control and an independently scrollable menu. Keep the selected width during admin navigation. On mobile, use a labeled drawer that supports Escape, manual focus containment and restoration (or an inert background), and closing on route selection. Do not add a dependency.
- Include the active page title, username, and administrator role in the compact top bar. Preserve access to the existing profile and account destinations. Both sidebar and top bar use the same sign-out action.
- Keep one scrollable content area below the top bar. Reset its scroll on route changes without resetting the shell's state. Keep the skip link and keyboard focus usable with keyboard-only navigation.

## Page presentation

- Use admin-scoped Tailwind styles and small reusable components for headings, panels, primary and secondary actions, search and filters, table wrappers, pagination, forms, and loading, empty, and error messages. Use named components such as `<AdminTableRegion label="Invoices">` where a shared accessible behavior is needed.
- Keep table semantics and contain wide tables in labeled, keyboard-scrollable regions. Forms and action groups wrap on small screens. Do not hide actions to make tables fit.
- Keep existing validation, confirmation wording and safeguards, request handlers, page sizes, and API payloads. Use consistent presentation for inline confirmations and an accessible dialog where the existing departure cancellation confirmation needs custom styling.
- Keep KPI labels, values, formatting, generated time, booking status counts, top tours, and capacity risks tied to existing dashboard data. No placeholders that look like live figures.
- Scope the visual changes for shared invoice content to admin mode where possible. When printing an invoice, remove shell chrome and scroll constraints so the full document prints.

## Verification

**Phase A — route-group move only.** Capture the production build route list before editing. Move the public pages and their chrome without changing page behavior or visual design, including the existing admin appearance until Phase B. From `fe-tour/`, run `npm run lint`, `npx tsc --noEmit`, `npm test`, and `npm run build`. Compare the before/after build route lists: every existing public and admin URL must remain. Verify a 404 outside `/admin` still shows public chrome and compare the three sensitive pages' metadata and security policies. Report the comparison and stop before Phase B.

**Phase B — admin experience.** Implement and verify the shell, visual changes, admin fallbacks, and shared access context. Repeat the four quality commands. Add Vitest coverage for navigation metadata, route matching, and the drawer's pure focus logic where possible. Check the nested invoice route, false prefix matches, and that opening the shell and initial page does not duplicate account validation.

In a browser, navigate through all seven top-level sections and the nested invoice link. Confirm the shell remains mounted, active highlighting and titles update, the content scroll resets, and no public navigation, header, or footer appears. Check the embedded create/edit forms, confirmations, filters, pagination, invoice print layout, and access states. Check desktop, tablet, and mobile at approximately 1440, 1024, 768, and 320 pixels, including drawer keyboard behavior and table overflow. Spot-check public navigation, account and login links, checkout, and the VNPay return route without initiating a real payment. If live admin access is unavailable, distinguish fixture-backed checks from live checks in the final report.

## Boundaries and deliverables

- **Always:** Preserve route URLs, permissions, API contracts, and user data; follow `fe-tour/AGENTS.md`; keep admin styling out of public pages.
- **Ask first:** Adding dependencies or changing authentication or payment behavior.
- **Never:** Modify Spring Boot, read or modify `.env` files or secrets, perform real payments during verification, or commit, push, or run destructive Git commands without permission.
- **Out of scope:** New management modules, standalone create/edit routes, new metrics, and public-site redesign.
- **Deliverable:** Record implemented changes, covered routes, test results, and verification limitations in `docs/task-fe1-report.md` after the work is finished.
