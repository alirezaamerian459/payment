# Phase 01 — Project foundation

## Objective and status

Establish an accurate, maintainable project blueprint and a reproducible, verified local development baseline for the existing PrivatePay sandbox. Existing code is retained; this phase does not add customer-facing business features. **Status: documentation blueprint prepared; runtime/CI verification still needs evidence.**

## Scope, dependencies, and requirements

- Depends on the existing repository, local Docker Compose configuration, API/web code, and the known PrivatePay product intent.
- In scope: project requirements, stack rationale, architecture references, development/coding/security/testing rules, agent guide, roadmap and phase checklists, CI quality-gate plan, and accurate status reporting.
- Out of scope: real identity/payment providers, live credit, production deployment, new business behavior, or destructive data reset.

## Architecture and expected artifacts

Preserve the API-backed modular-monolith direction, PostgreSQL authority, versioned REST boundary, Persian RTL web app, and sandbox-only provider boundary. Keep Compose local and loopback-only. Artifacts include root `AGENTS.md`, project/stack/policy docs, phase plan, CI follow-up tasks, and `reports/00-initialization.md`.

## Implementation and security considerations

Document current behavior separately from target design. Do not treat sandbox auth as secure auth or Redis presence as proof it is required. Keep credentials and real customer data out of docs/tests. Review all file status before any commit.

## Verification and acceptance

Acceptance requires an understandable project specification, current-vs-target architecture and stack rationale, concise agent instructions, enforceable development/security/testing policies, a dependency-ordered roadmap, truthful task statuses, and actual local/CI check evidence before declaring the runtime foundation verified. Definition of Done: all documentation links resolve, no implementation behavior changes are included, `git diff --check` passes, and unresolved checks/decisions are recorded. This documentation task did not execute app test suites.
