# Admin management screens — result

## Progress

- [ ] Passengers per departure: backend
- [ ] Passengers per departure: frontend (+ print + CSV)
- [ ] Payments: backend
- [ ] Payments: frontend (+ reconcile action)
- [ ] Customers: backend
- [ ] Customers: frontend
- [ ] Docs: append the new endpoints to docs/api-spec.md and an entry to docs/progress.md
- [ ] Full verification: backend build + tests, frontend lint + tests + build

## Current status

## Design and implementation plan

- Follow existing admin UI, API envelopes and zero-based pagination; use new focused admin read
  controllers/services/DTOs and repositories where existing APIs lack filters or joined data.
- Passenger slice: load the departure and non-cancelled travelers without N+1 queries; show all
  requested fields and provide browser printing and safely escaped client-side CSV.
- Payments slice: filter and paginate newest-first; add server-side summary only if cheap.
  Reconciliation must reuse verified VNPay processing, authorize admins, reject non-pending
  attempts, preserve customer ownership, and avoid changes to existing method bodies. If that
  cannot be done safely, omit the button and document why.
- Customer slice: paginated name/email/phone search and profile with linked booking history;
  use existing edits only when demonstrably safe, without new destructive behavior.
- Add focused backend and Vitest tests before behavior; avoid shared databases. Then append API
  and progress docs, run safe backend tests/build and frontend lint/tests/build, and report results.
- No git commands, migrations, dependencies, SecurityConfig, pom.xml, or unrelated business-service
  changes. Shared files receive minimal additive edits. Document each touched existing file.

## Reconciliation decision

The user approved a new admin-only reconciliation endpoint and, only if unavoidable, an additive
method in the existing VNPay service. Existing methods and customer authorization remain unchanged.
