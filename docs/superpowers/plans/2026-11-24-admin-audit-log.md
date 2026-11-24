# Admin Audit Log Implementation Plan

> **For agentic workers:** Implement directly in this session as requested. Use tests first and track steps below; do not run git commands.

**Goal:** Capture best-effort admin mutations and provide a searchable admin audit page.

**Architecture:** A post-security servlet filter publishes sanitized immutable events to a nonblocking bounded queue through `AuditEventPublisher`. A worker writes the append-only PostgreSQL table, and an admin-only endpoint exposes filtered pages to the existing frontend.

**Tech Stack:** Spring Boot 4, Java 21, PostgreSQL/Flyway, Next.js, React, TypeScript, Vitest.

**Spec:** `docs/superpowers/specs/2026-11-24-admin-audit-log-design.md`

## Global Constraints

- No git commands, existing business-service changes, SecurityConfig changes, existing migration edits or added dependencies.
- Shared navigation gets one additive line; existing docs are append-only.
- Use username from principal, not email lookup; queue capacity 1000, nonblocking and best effort.
- Do not run DB-backed tests against shared or production data.

## Review Focus

- Malformed, truncated, nested and oversized bodies must never expose raw secrets.
- Queue saturation and database failures must not delay or break the original request.
- Application 400, 409 and unhandled 500 must record failure; security rejections are excluded.
- Filter inputs, date boundaries and stable pagination must behave consistently.
- Shutdown, async servlet dispatch and escaped exceptions must not duplicate events.

### Task 1: Capture and persistence

- [ ] Add failing tests for path parsing, nested sensitive-key redaction, malformed/oversized input, filter selection/status, and bounded queue overflow.
- [ ] Add V10 migration, entity, repository and independent writer.
- [ ] Add pure path and redaction helpers and `AuditEventPublisher` with bounded in-memory implementation.
- [ ] Register a post-security servlet filter; wrap only eligible requests, publish without blocking, catch failures, and avoid storing query strings.
- [ ] Run focused backend tests and compile.

### Task 2: Admin endpoint

- [ ] Test authorization, pagination, ordering and all filters, including date bounds, using isolated PostgreSQL when available.
- [ ] Add read-only controller, DTO and query service/repository with OpenAPI annotations and validated pagination.
- [ ] Run backend build and tests without exposing a shared database to writes.

### Task 3: Admin interface

- [ ] Add a failing Vitest test for the filter-to-query helper, including false booleans and escaping.
- [ ] Add typed API client and a page with filters, list states, pagination and accessible expandable details.
- [ ] Add one admin navigation entry; run frontend lint, tests, typecheck and build.

### Task 4: Documentation and verification

- [ ] Append API and progress documentation, recording filters and best-effort semantics.
- [ ] Verify builds and tests, report unavailable checks explicitly.
- [ ] Write `docs/admin/task-audit-log-result.md` with files, conflict risk, migration, endpoint, manual testing, decisions, limitations and actual verification results.
