# Frontend — agent guide

## What this is
Next.js 16 App Router UI (React 19, TypeScript, Tailwind CSS 4) for the tour booking API. It has editorial tour pages, a typed API client, login/register, and a checkout that creates a real booking. Backend lives in `../tour/`.

## Commands
- Install: `npm install`
- Dev: `npm run dev` (port 3000)
- Build: `npm run build`
- Start production build: `npm start`
- Lint: `npm run lint`
- Typecheck: `npx tsc --noEmit`
- Test: `npm test` (Vitest)

## Conventions that differ from defaults
- App Router only (`app/`); no `pages/` directory.
- TypeScript strict mode. Import via `@/*`, which maps to this project root.
- Tailwind 4 is loaded through the custom Turbopack rule in `next.config.ts`; do not replace it with a PostCSS config without checking the Next 16/Tailwind 4 setup.
- Prefer server components. There is no client state library, data-fetching library, or form library installed.
- Keep `next-env.d.ts` and the generated `LayoutProps<"/">` type intact; they are produced by Next.

## Gotchas / never-do
- `next.config.ts` enables experimental `cacheComponents`, `partialPrefetching`, and `agentFeedback`. Do not remove or rename these without verifying the Next 16 build.
- No Next.js proxy. The browser calls the backend directly and relies on backend CORS (`APP_CORS_ALLOWED_ORIGINS`). `lib/api.ts` sets `credentials: "include"` so the refresh cookie is sent.
- Auth tokens live in `localStorage` (`lib/auth.ts`); the refresh token is an HttpOnly cookie scoped to `/api/auth`. Access tokens last 15 minutes; `ensureSession()` checks the stored token with `/api/auth/me`, then falls back to `/api/auth/refresh`.
- Booking create has no contact/traveler fields, so the checkout appends name/email/phone to `booking_note`. Real traveler capture needs a backend request change. Checkout shows a sign-in gate until `ensureSession()` returns a session.
- Vitest is configured (`npm test`, `lib/*.test.ts`). Verify changes with `npm run lint`, `npx tsc --noEmit`, `npm test`, and `npm run build`.
- Use npm; `package-lock.json` is the only lockfile.
- Pages and components should call the backend, not query PostgreSQL directly.
- Styling uses Tailwind utility classes in `app/globals.css`; fonts are Inter + Playfair Display via `next/font/google` in `app/layout.tsx`.
- Backend base URL: `API_URL` (server), then `NEXT_PUBLIC_API_URL` (browser), then `http://localhost:8080`. Copy `.env.example` to `.env.local` to override.
- There is no demo/sample data. If the backend is unreachable, pages throw to `app/error.tsx`.

## Where things live
- Routes and pages: `app/` (`/`, `/tours`, `/tours/[id]`, `/login`, `/register`, `/payment`)
- Root layout and fonts: `app/layout.tsx`
- Global styles / Tailwind entry: `app/globals.css`
- API client and types: `lib/api.ts`, `lib/types.ts`
- Session/auth helpers: `lib/auth.ts`; forms in `components/auth-form.tsx`
- Cached tour reads: `lib/tours.ts` (`use cache`, `cacheLife`, `cacheTag`)
- Checkout: `app/payment/page.tsx`, `components/payment-form.tsx` (calls `POST /api/bookings`)
- Shared components: `components/`
- Static assets: `public/`
- Next config: `next.config.ts`
- Backend URL template: `.env.example`
- TypeScript config and `@/*` alias: `tsconfig.json`

<!-- BEGIN:nextjs-agent-rules -->

## This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

<!-- BEGIN:nextjs-agent-feedback -->

## Report Next.js feedback

The project owner enabled `experimental.agentFeedback`, authorizing this feedback pass. When enabled, `next dev` writes this block and restores it if removed. Commit `AGENTS.md` with your work to keep the tree clean.

During the current user request, retain anonymized facts as feedback candidates for meaningful or recurring Next.js issues:

- Crashes, hangs, OOMs, or unexpected slowness
- Incorrect behavior, output, errors, or diagnostics
- Missing, unclear, or conflicting documentation
- Unexpected CLI, codemod, Skill, or upgrade behavior and non-obvious workarounds

Continue the current user request without reporting immediately. Routine debugging is not feedback. Verify changes before retaining them as feedback candidates. Never retain code, logs, paths, URLs, secrets, personal information, or project-specific data.

Retain qualifying friction even if it is later fixed or worked around; record the final outcome during the reporting pass.

Before the final response, run one reporting pass. If candidates remain, from this file's directory run `npx --no-install next internal agent-feedback-instructions` once without piping or truncating its output. Follow its output or error guidance. If a network sandbox blocks it, retry with network access; if it still returns no output, continue normally.

<!-- END:nextjs-agent-feedback -->
