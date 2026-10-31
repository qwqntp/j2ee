# Frontend — agent guide

## What this is
Next.js 16 App Router UI (React 19, TypeScript, Tailwind CSS 4) for the tour booking API. Currently the default create-next-app scaffold: no feature pages, API client, or auth flow yet. Backend lives in `../tour/`.

## Commands
- Install: `npm install`
- Dev: `npm run dev` (port 3000)
- Build: `npm run build`
- Start production build: `npm start`
- Lint: `npm run lint`
- Typecheck: `npx tsc --noEmit`

## Conventions that differ from defaults
- App Router only (`app/`); no `pages/` directory.
- TypeScript strict mode. Import via `@/*`, which maps to this project root.
- Tailwind 4 is loaded through the custom Turbopack rule in `next.config.ts`; do not replace it with a PostCSS config without checking the Next 16/Tailwind 4 setup.
- Prefer server components. There is no client state library, data-fetching library, or form library installed.
- Keep `next-env.d.ts` and the generated `LayoutProps<"/">` type intact; they are produced by Next.

## Gotchas / never-do
- `next.config.ts` enables experimental `cacheComponents`, `partialPrefetching`, and `agentFeedback`. Do not remove or rename these without verifying the Next 16 build.
- No API base URL, proxy, or CORS setup exists. Backend defaults to `http://localhost:8080`; cross-origin calls need backend CORS or a Next rewrite.
- No test runner is configured. Verify changes with `npm run lint`, `npx tsc --noEmit`, and `npm run build`.
- Use npm; `package-lock.json` is the only lockfile.
- Pages and components should call the backend, not query PostgreSQL directly.
- Styling uses Tailwind utility classes in `app/globals.css` plus the default Geist fonts in `app/layout.tsx`.

## Where things live
- Routes and pages: `app/`
- Root layout and fonts: `app/layout.tsx`
- Global styles / Tailwind entry: `app/globals.css`
- Static assets: `public/`
- Next config: `next.config.ts`
- TypeScript config and `@/*` alias: `tsconfig.json`
