# Documentation, setup files and CI — result

> Date: 2026-11-24
> Scope: `docs/`, root `README.md`, `tour/README.md`, `fe-tour/README.md`, the two `.env.example`
> files and `.github/workflows/ci.yml`.
> No Java/TypeScript source, tests, migrations, `pom.xml`, `package.json` or
> `application.properties` were changed. No git commands were run and no `.env` file was opened.

## Checklist

- [x] 1. Doc classification table
- [x] 2. Archive moves + link fixes
- [x] 3. Root README
- [x] 4. `tour/README.md`
- [x] 5. `fe-tour/README.md` fix
- [x] 6. The two `.env.example` files
- [x] 7. CI workflow
- [ ] 8. Final check that every command and path in the READMEs exists

## 1. Classification

Legend: **KEEP** = leave in place; **UPDATE** = edit in place; **ARCHIVE** = moved (never deleted)
to `docs/archive/`.

| # | File | Decision | Reason | Moved to |
|---|---|---|---|---|
| 1 | `DATABASE_FIX_PLAN.md` | KEEP | One-off plan, but referenced by the protected `docs/admin/task-requirements-audit-result.md`; kept in place per the link rule. | — |
| 2 | `prices-on-request.md` | KEEP | Still-useful explanation of the `priceFrom` / `nextDepartureDate` null behaviour. | — |
| 3 | `docs/api-spec.md` | KEEP (do not edit) | Protected: owned by another branch. | — |
| 4 | `docs/progress.md` | KEEP (do not edit) | Protected and stale (claims no admin UI / no VNPay); archive after the other branches merge. | — |
| 5 | `docs/feature-list.md` | UPDATE | Current feature inventory; only the "Cập nhật" date line was changed. | — |
| 6 | `docs/feature-audit.md` | KEEP (do not edit) | Known stale (claims no admin UI / no VNPay); must stay for now; archive after the other branches merge. | — |
| 7 | `docs/admin-gap-checklist.md` | KEEP (do not edit) | Protected. | — |
| 8 | `docs/missing-features.md` | KEEP | Substantive backend admin-coverage audit that is still consistent with the code. | — |
| 9 | `docs/tech-stack.md` | KEEP | Useful stack overview, though it lists some unused technologies (see "Review"). | — |
| 10 | `docs/review_1.md` | ARCHIVE | One-off implementation plan from October. | `docs/archive/review_1.md` |
| 11 | `docs/review_1_result.md` | ARCHIVE | One-off result report from October. | `docs/archive/review_1_result.md` |
| 12 | `docs/fe-audit.md` | ARCHIVE | One-off frontend audit dated 2026-11-14. | `docs/archive/fe-audit.md` |
| 13 | `docs/fetch.md` | ARCHIVE | One-off read-path performance audit dated 2026-11-22. | `docs/archive/fetch.md` |
| 14 | `docs/skills.md` | ARCHIVE | Snapshot of the local OpenCode skill list; not project documentation. | `docs/archive/skills.md` |
| 15 | `docs/test_result.md` | ARCHIVE | One-off test-run log dated 2026-11-05 (BUILD FAILURE). | `docs/archive/test_result.md` |
| 16 | `docs/task-a-security-report.md` | ARCHIVE | One-off task report. | `docs/archive/task-a-security-report.md` |
| 17 | `docs/task-b1-report.md` | ARCHIVE | One-off task report. | `docs/archive/task-b1-report.md` |
| 18 | `docs/task-b1-fix-report.md` | ARCHIVE | One-off task report. | `docs/archive/task-b1-fix-report.md` |
| 19 | `docs/task-b2-report.md` | ARCHIVE | One-off task report. | `docs/archive/task-b2-report.md` |
| 20 | `docs/task-b2-plan-review.md` | ARCHIVE | One-off plan review. | `docs/archive/task-b2-plan-review.md` |
| 21 | `docs/task-b3-report.md` | KEEP | `docs/progress.md` (protected) links to it; kept in place per the link rule. | — |
| 22 | `docs/task-f1-report.md` | ARCHIVE | One-off task report; internal links updated after the move. | `docs/archive/task-f1-report.md` |
| 23 | `docs/task-fe1-report.md` | ARCHIVE | One-off task report; links in `docs/superpowers/` updated. | `docs/archive/task-fe1-report.md` |
| 24 | `docs/task-fix-1-report.md` | KEEP | Referenced by the protected `docs/admin/task-requirements-audit-result.md`; kept in place. | — |
| 25 | `docs/task-fix-2-report.md` | ARCHIVE | One-off task report. | `docs/archive/task-fix-2-report.md` |
| 26 | `docs/task-fix-be1-report.md` | ARCHIVE | One-off task report; internal link to `docs/skills.md` updated. | `docs/archive/task-fix-be1-report.md` |
| 27 | `docs/task-g1-report.md` | ARCHIVE | One-off task report. | `docs/archive/task-g1-report.md` |
| 28 | `docs/task-g2-report.md` | ARCHIVE | One-off task report; links in `docs/superpowers/` updated. | `docs/archive/task-g2-report.md` |
| 29 | `docs/task-g2-fix-report.md` | ARCHIVE | One-off task report. | `docs/archive/task-g2-fix-report.md` |
| 30 | `docs/task-mail-report.md` | ARCHIVE | One-off task report. | `docs/archive/task-mail-report.md` |
| 31 | `docs/task-reset-1-report.md` | ARCHIVE | One-off task report. | `docs/archive/task-reset-1-report.md` |
| 32 | `docs/admin/task-requirements-audit-result.md` | KEEP (do not edit) | Protected. | — |
| 33 | `docs/admin/task-admin-screens-result.md` | KEEP (do not edit) | Protected. | — |
| 34 | `docs/superpowers/plans/2026-11-17-admin-operations.md` | KEEP | Historical plan kept in its conventional `docs/superpowers/` location. | — |
| 35 | `docs/superpowers/plans/2026-11-21-vnpay-sandbox.md` | KEEP | Historical plan; task-report links updated. | — |
| 36 | `docs/superpowers/plans/2026-11-22-admin-data-fetching.md` | KEEP | Historical plan. | — |
| 37 | `docs/superpowers/plans/2026-11-22-admin-shell.md` | KEEP | Historical plan; task-report link updated. | — |
| 38 | `docs/superpowers/specs/2026-11-21-vnpay-sandbox-design.md` | KEEP | Historical design; task-report link updated. | — |
| 39 | `docs/superpowers/specs/2026-11-22-admin-data-fetching-design.md` | KEEP | Historical design. | — |
| 40 | `docs/superpowers/specs/2026-11-22-admin-shell-design.md` | KEEP | Historical design; task-report link updated. | — |
| 41 | `fe-tour/README.md` | UPDATE | Removed the stale "UI-only checkout" claim and replaced the four-page list with the real routes. | — |
| 42 | `fe-tour/AGENTS.md` | KEEP (do not edit) | Protected agent guide. | — |
| 43 | `fe-tour/public/images/CREDITS.md` | KEEP | Image attribution referenced by the README. | — |
| 44 | `tour/AGENTS.md` | KEEP (do not edit) | Protected agent guide. | — |
| 45 | `tour/src/test/AGENTS.md` | KEEP (do not edit) | Protected agent guide. | — |

New files: `README.md`, `tour/README.md`, `tour/.env.example`, `fe-tour/.env.example`,
`docs/archive/README.md`, `.github/workflows/ci.yml`.

## 2. Files created / modified / moved

### Created
- `README.md`
- `tour/README.md`
- `tour/.env.example`
- `fe-tour/.env.example`
- `docs/archive/README.md`
- `.github/workflows/ci.yml`
- this result file

### Modified
- `docs/feature-list.md` — date line only.
- `fe-tour/README.md` — stale checkout claim + real route list.
- `docs/superpowers/plans/2026-11-21-vnpay-sandbox.md` — report links.
- `docs/superpowers/plans/2026-11-22-admin-shell.md` — report link.
- `docs/superpowers/specs/2026-11-21-vnpay-sandbox-design.md` — report link.
- `docs/superpowers/specs/2026-11-22-admin-shell-design.md` — report link.
- moved reports with broken `docs/...` links: `fe-audit.md`, `task-f1-report.md`,
  `task-fix-be1-report.md`, `task-b2-report.md`, `task-b2-plan-review.md`, plus self-references
  in the moved reports.

### Moved to `docs/archive/`
`review_1.md`, `review_1_result.md`, `fe-audit.md`, `fetch.md`, `skills.md`, `test_result.md`,
`task-a-security-report.md`, `task-b1-report.md`, `task-b1-fix-report.md`, `task-b2-report.md`,
`task-b2-plan-review.md`, `task-f1-report.md`, `task-fe1-report.md`, `task-fix-2-report.md`,
`task-fix-be1-report.md`, `task-g1-report.md`, `task-g2-report.md`, `task-g2-fix-report.md`,
`task-mail-report.md`, `task-reset-1-report.md`.

## 3. Deliberately left untouched

- `docs/api-spec.md` — owned by another branch.
- `docs/progress.md` — owned by another branch, and known stale ("no admin UI", "no VNPay").
  **Archive after the other branches merge.**
- `docs/feature-audit.md` — same stale claims. **Archive after the other branches merge.**
- `docs/admin-gap-checklist.md`, `docs/admin/task-requirements-audit-result.md`,
  `docs/admin/task-admin-screens-result.md` — protected by the task, even where stale.
- All `AGENTS.md` files — protected.
- `docs/task-b3-report.md` — linked from `docs/progress.md`; cannot move until that file may be
  edited.
- `docs/task-fix-1-report.md` and `DATABASE_FIX_PLAN.md` — referenced by the protected
  `docs/admin/task-requirements-audit-result.md`; kept in place.
- `docs/superpowers/plans/` and `docs/superpowers/specs/` — historical plans/specs kept in their
  conventional location.
- `docs/tech-stack.md`, `docs/missing-features.md`, `prices-on-request.md` — still useful; see
  "Review".
- `docs/feature-list.md` section 9 — not rewritten, only the date line was touched.

## 4. Review / uncertain

- **Flyway does not seed roles.** `dev_seed.sql` assumes `account_roles` already contains
  `ROLE_ADMIN`, `ROLE_USER` and `ROLE_STAFF` (it inserts a `demo_staff` account). The migrations
  create the table but do not insert rows. The READMEs document a one-time role insert before
  running the seed. I did not change the seed or migrations.
- **Role model reality:** only `ROLE_USER` (default on registration/Google sign-in) and
  `ROLE_ADMIN` (admin console + `/api/admin/**` and the admin-only route matrix) are enforced.
  `ROLE_STAFF` is a seeded label with no authorization behaviour.
- **VNPay in development:** `fe-tour/lib/vnpay.ts` disables the VNPay option when
  `NODE_ENV === "development"` (to keep signed return parameters out of the dev logger). The
  README says so instead of calling checkout a "preview".
- **`docs/tech-stack.md`** lists MapStruct, RabbitMQ, WebSocket and Cloudinary, which the audit
  found documented but not implemented. Left as-is; the new READMEs describe the stack that is
  actually used.
- **No project Node version pin.** There is no `.nvmrc` and no `engines` entry. Next.js 16
  requires Node `>=20.9.0` (from `node_modules/next/package.json`); CI uses Node 22 LTS.
- **No `typecheck` npm script.** `package.json` only has `dev`, `build`, `start`, `lint`, `test`.
  CI therefore does not run a separate typecheck; `next build` type-checks.

## 5. Could not verify

- GitHub Actions itself was not run (no local runner). YAML syntax and the commands were checked
  locally as far as this machine allows.
- No live PostgreSQL, Redis, SMTP, VNPay Sandbox or Google sign-in flow was exercised.
- The local JDK is 26, while CI (and `pom.xml`) target Java 21, so a local backend test run is not
  identical to CI.
