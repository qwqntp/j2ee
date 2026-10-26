# Hành Trình — frontend

Next.js 16 app for the tour booking project. The backend lives in `../tour` and exposes the API at
`http://localhost:8080`.

## Run

```powershell
npm ci
npm run dev
```

Open `http://localhost:3000`.

```powershell
npm run lint
npm run build
npm start
```

## API configuration

Server-side reads use, in order:

1. `API_URL`
2. `NEXT_PUBLIC_API_URL`
3. `http://localhost:8080`

The backend's default CORS origin is `http://localhost:3000`.

## Pages

- `/` — editorial landing page with destinations, curated experiences, travel notes and a search
  entry point.
- `/tours` — searchable, paginated catalogue.
- `/tours/[id]` — tour detail and departure selection.
- `/payment` — UI-only checkout preview. It does not create a booking or take a payment.

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
