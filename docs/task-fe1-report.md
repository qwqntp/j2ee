# FE1 — Shared Hành Trình admin workspace

All eight existing admin routes now use one persistent Next.js layout. The sidebar and top bar stay mounted while the management content changes. No public header or footer appears in the admin workspace.

## Phase A — Public routing boundary

Moved the 16 public pages beneath `fe-tour/app/(public)/` without changing their files or URLs. The shared root keeps the document, fonts, metadata, and flash toasts. Public chrome and the public error boundary live with the public routes; the root unmatched-URL 404 still renders that chrome.

| Production build route entries | Before | After Phase A | After Phase B |
| --- | ---: | ---: | ---: |
| Existing public pages | 16 | 16 | 16 |
| Existing admin pages | 8 | 8 | 8 |
| Framework not-found route | 1 | 1 | 1 |
| Admin-only catch-all fallback | 0 | 0 | 1 |
| **Total** | **25** | **25** | **26** |

The complete Phase A route tables matched, including their rendering classifications. Phase B added only `/admin/[...missing]`; it removed or renamed no existing route. All 16 moved page files still match their original Git blobs.

Production responses for `/payment/vnpay-return`, `/verify-email`, and `/reset-password` retained the baseline HTTP status, page metadata, and response-header policies. VNPay still emits `no-referrer` and `noindex, nofollow` as HTML metadata; the baseline Next.js response did not emit separate HTTP headers for those policies. An unknown public URL still returns HTTP 404 with the public header and footer. The public browser error boundary also rendered with public chrome.

## Phase B — Shared admin workspace

- Added a collapsible, independently scrollable sidebar, route-aware top bar, account menu, and scrollable content area. The mobile drawer contains keyboard focus, closes on Escape or navigation, and restores focus.
- Applied shared headings, forms, actions, table regions, filter and pagination styling, confirmations, and feedback states to Dashboard, Bookings & payments, Accounts, Promotions, Invoices, Catalog, and Tours & departures. The nested invoice detail and inline create/edit flows use the same workspace. Print media hides the workspace chrome and preserves the invoice body, header, and footer.
- Reused the `ROLE_ADMIN` gate and logout flow. The validated account is shared through React context; protected data loads client-side, not during prerendering. No admin page uses `use cache`.
- Kept existing dashboard values, endpoint contracts, and management and payment handlers. No Spring Boot, dependency, `.env`, or payment-integration changes were made.

## Modified files

| Area | Files |
| --- | --- |
| Routing and public chrome | `fe-tour/app/layout.tsx`, `fe-tour/app/not-found.tsx`, routes and fallbacks under `fe-tour/app/(public)/`, `fe-tour/components/public-layout.tsx` |
| Admin entry and fallbacks | `fe-tour/app/admin/layout.tsx`, `admin.css`, `error.tsx`, `not-found.tsx`, `[...missing]/page.tsx`, `invoices/[bookingId]/page.tsx` |
| Shell and shared UI | `fe-tour/components/admin/`: layout, sidebar, top bar, icons, shared page UI, and departure confirmation |
| Management pages and access | `fe-tour/components/admin-{accounts,bookings,catalog,dashboard,gate,invoices,promotions,tours}.tsx`, `fe-tour/components/invoice-view.tsx` |
| Utilities and tests | `fe-tour/lib/admin-{drawer,navigation,session}.ts` and corresponding Vitest files |
| Documentation | `fe-tour/AGENTS.md`, design spec, two-phase implementation plan, and this report |

## Verification

| Check | Phase A | Phase B |
| --- | --- | --- |
| `npm run lint` | Pass | Pass |
| `npx tsc --noEmit` | Pass | Pass |
| `npm test` | 164 tests passed | 175 tests passed |
| `npm run build` | Pass after advisory retry | Pass after advisory retry |

Commands ran from an isolated frontend copy without `.env` files. Chrome and local fixture APIs verified:

- Navigation through all seven sidebar sections and `/admin/invoices/42`, including active navigation, current title, and persistence of the collapsed state.
- The 320, 768, 1024, and 1440 px layouts. At 320 px, the booking table scrolls within its labeled region rather than widening the workspace. The drawer was tested with Tab/Shift+Tab, Escape, focus restoration, and route selection.
- Dashboard data from the fixture, invoice search/empty state and pagination, opening inline promotion create/edit forms, and opening and dismissing the departure deletion dialog. Browser print media confirmed that invoice content remains visible while the shell is hidden.
- One `/api/auth/me` request on a direct dashboard load with a stored session, token refresh after expiry, sign-out, and access denial for a non-admin account.
- Invalid invoice IDs and unknown admin descendants display the admin fallback inside the shell for an authorized admin. Under the app's current Next.js streaming setup, the two tested admin not-found responses carried HTTP 200; an unmatched public URL still returned HTTP 404.

No live payment, live CRUD write, or production account was used. The existing Next.js 16.4.3 installation emitted a security advisory. Both production builds passed after retry; upgrade Next.js separately before deployment. No commits or pushes were made.

## Follow-up — Visual refinement

Used `FE-DACN` as a read-only reference for dashboard composition, management panels, form grouping, and confirmation dialogs. The Hành Trình implementation keeps its own branding, terminology, data, and all eight routes.

- The dashboard now has four primary KPIs, a secondary performance summary, two booking-count visualizations, and the existing top-tour and capacity tables. The backend does not expose historical revenue or growth figures, so there is no trend chart or period selector.
- Management pages group real filters, scrollable tables, actions, counts, and pagination in shared panels. Promotions, tours, departures, and catalog records retain their embedded forms. Departure, promotion, and catalog deletion use the same accessible confirmation dialog.
- Initial dashboard, list, invoice detail, and asynchronous image-form loads show layout-matched skeletons. Refetches retain existing rows and show a progress indicator. Skeleton animation is disabled under reduced motion.

**Verification:** Lint, TypeScript, all 176 Vitest tests, and the production build passed. Chrome fixture checks covered all eight routes, sidebar states, widths from 320 to 1440 px, search, pagination, inline forms, dialog focus and deletion against the fixture, a simulated API error and retry, and invoice print styles. Five public response snapshots retained their baseline status, metadata, policies, and chrome. The Next.js security advisory remains; the build passed after retry without a dependency change. No live account, data write, or payment was used.

## Follow-up — Management dialogs

Accounts now shows Edit and Delete side by side in Actions. Add user and Edit open dialogs; role, status, email verification, and temporary-password controls are inside Edit. Add and Delete use the existing admin account endpoints, with the last-active-admin guard preserved. Promotions, Catalog, Tours, and Departures use the same form dialog for their existing add and edit actions and retain confirmation dialogs for existing delete actions.

Lint, TypeScript, all 178 Vitest tests, and the production build passed. Chrome checks verified account creation, editing, temporary-password placement, and deletion against a local fixture. The other management dialogs opened and dismissed correctly, and a long form fit a 320 px viewport with keyboard focus restored on close. No live writes were performed.
