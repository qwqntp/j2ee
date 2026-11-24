# Hành Trình — frontend

Next.js 16 App Router UI (React 19, TypeScript, Tailwind CSS 4) for the tour booking project. The
backend lives in `../tour` and exposes the API at `http://localhost:8080`.

## Run

```powershell
npm ci
copy .env.example .env.local
npm run dev
```

Open `http://localhost:3000`.

```powershell
npm run lint
npx tsc --noEmit
npm test
npm run build
npm start
```

## API configuration

Copy `.env.example` to `.env.local`. Server-side reads use, in order:

1. `API_URL`
2. `NEXT_PUBLIC_API_URL`
3. `http://localhost:8080`

The browser uses `NEXT_PUBLIC_API_URL`. `NEXT_PUBLIC_*` values are inlined at build time. The
backend's default CORS origin is `http://localhost:3000`.

`NEXT_PUBLIC_GOOGLE_CLIENT_ID` is optional; when blank, the Google sign-in button is hidden.

## Pages

Customer and public routes:

- `/` — editorial landing page with destinations, curated experiences, travel notes and a search
  entry point.
- `/tours` — searchable, paginated catalogue with destination, price, date and duration filters and
  sorting.
- `/tours/[id]` — tour detail, departures, availability and ratings.
- `/login`, `/register`, `/forgot-password`, `/reset-password`, `/verify-email` — authentication,
  password recovery and email verification.
- `/payment` — real checkout. It requires a session, collects one traveler per seat plus an optional
  promotion code, and calls `POST /api/bookings`; the backend returns the authoritative
  subtotal/discount/total. The booking is created as `PENDING`, and success navigates to
  `/account/bookings/{id}`. The VNPay Sandbox option is shown only in non-development builds
  (`next dev` disables it so signed return parameters do not reach the dev logger); reserve-only
  checkout is always available.
- `/payment/vnpay-return` — VNPay browser-return page; it verifies the signed return and shows the
  recorded attempt status.
- `/account/profile` — profile editing and password change.
- `/account/bookings`, `/account/bookings/[id]` — booking list and detail with cancel (only while
  `PENDING`), confirm and complete actions, totals, travelers and promotion.
- `/my-trips`, `/my-trips/[id]` — aliases for the booking list and detail.
- `/invoices/[bookingId]` — printable invoice for the signed-in owner (`GET /api/invoices/me`).

Admin routes (`ROLE_ADMIN`, gated client-side by `components/admin-gate.tsx`; the backend also
enforces the role):

- `/admin` — dashboard with backend statistics.
- `/admin/bookings` — filters, detail, record-cash payment, confirm/complete and pending-cancel.
- `/admin/accounts` — account and role management.
- `/admin/promotions` — promotion list and inline create/edit/delete.
- `/admin/invoices`, `/admin/invoices/[bookingId]` — invoice list and printable detail.
- `/admin/catalog` — accommodations, guides, vehicles, destinations and tour images.
- `/admin/tours` — tours and departures, including the guide-overlap guard.

## Design

The interface uses an editorial travel aesthetic: forest, ivory, terracotta and sage, Playfair
Display for headlines, Inter for body and UI, rectangular cards and restrained borders.

- Editorial copy and destination/story content live in `lib/editorial.ts`.
- Local photographs live in `public/images`; attribution is in `public/images/CREDITS.md`.
- Live tour images come from the API. If one is missing or fails to load, `lib/tour-images.ts`
  matches the tour name to a local destination photo.
- Editorial photography is local, so the homepage has no remote image dependency.

## Caching and demo data

- `cacheComponents` and `partialPrefetching` are enabled.
- Public tour reads use `use cache`, `cacheLife("minutes")` and cache tags.
- Data components are wrapped in `Suspense` and call `connection()` so the static shell can
  prerender while live data streams in.
- Sample tours are selected outside the cached functions. If the API is unavailable, the UI shows a
  "Showing sample journeys" notice instead of caching demo data as live inventory.

The full API contract is in `../docs/api-spec.md`.
