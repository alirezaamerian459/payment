# Testing and verification policy

Every phase defines its required checks. A phase is not verified by code review or test count alone: report exact commands, pass/fail totals where available, environment limits, and remaining gaps. Coverage percentage is not a substitute for behavioral evidence.

## Test layers

| Layer | Required focus |
|---|---|
| Unit | Pure domain calculations, credit reasons/policies, installment rounding, state machines, validation, and error mapping. Include boundary and invalid cases. |
| Database integration | Flyway migrations, constraints, transaction rollback, balanced immutable journals, idempotency, scoped queries, and restart behavior. Use PostgreSQL rather than an in-memory substitute for PostgreSQL-specific behavior. |
| Concurrency | Competing reservations, repayments, refunds, duplicate callbacks, and races; prove no over-allocation or duplicate posting. |
| API/authorization | Authentication, roles, branch scope, object/property authorization, request validation, error contract, and forbidden actions. Include negative tests. |
| Provider simulation | Valid/invalid signatures, server-side status inquiry, duplicate/late events, timeout, retry, refunds, and reconciliation mismatch using deterministic sandbox adapters. Never point tests at live financial endpoints. |
| Web E2E/accessibility | Customer onboarding to checkout/repayment, staff review/operations, RTL, keyboard semantics, validation and pending/error/empty states, responsive/mobile navigation, and sandbox warnings. |
| Mobile | Widget and device/integration tests, secure token storage, logout/revocation, API errors, supported OS/device behavior. Required when Flutter work begins. |
| Resilience/recovery | Worker/outbox replay, crash/restart, database backup restore, circuit breaker, and reconciliation recovery before pilot readiness. |
| Performance | Only after correctness gates and a representative workload/SLO exist; isolate all runs from real payment providers and production data. |

## Current commands and gaps

- API: from `apps/api`, run `mvn test`; Testcontainers requires Docker and runs PostgreSQL-backed integration tests.
- Web: from `apps/web`, install with `npm ci`, then run `npm run typecheck`, `npm run build`, and `npm run test:e2e` (the API/database must be available for the E2E customer flow).
- Current CI runs API packaging with `-DskipTests`, plus npm audit, web typecheck, and web build. It does not yet run API tests or Playwright. Add these as phase work after confirming stable CI service/dependency setup.
- No mobile suite, load/performance suite, provider-signature suite against an authorized provider, or restore drill exists yet.

## Quality gates

- A changed financial/auth/security path must have direct tests for success, rejection, retry/idempotency, and relevant concurrency or scope boundaries.
- Required CI checks must pass before merge when CI is configured for the phase. If a check cannot execute, mark the task incomplete and explain the blocker; do not replace it with an unsupported claim.
- Security review includes secret scanning/dependency review and authorization-negative cases. A passing unit suite alone is not a security assessment.
- Keep tests deterministic and isolated. Use synthetic fixtures, fixed clocks where appropriate, and disposable data. Do not log real personal or payment data.
- Do not invent coverage/performance targets; set targets when scope and risk justify them and record the rationale.
