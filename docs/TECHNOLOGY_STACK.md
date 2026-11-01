# Technology stack and trade-offs

This records the stack already present and the initial recommendation. It does not imply a dependency is already used in business logic or approved for production. Recheck supported releases and advisories when a phase starts.

| Concern | Current/recommended choice | Reason and trade-off |
|---|---|---|
| Backend | Java 21, Spring Boot 4.1.x, Spring Security, Spring Modulith | Strong validation, transaction and security ecosystem; modular monolith keeps financial invariants in one deployable/database transaction boundary. Modulith conventions must be kept proportionate. |
| API | Versioned REST under `/api/v1`, OpenAPI | Broad web/mobile/provider interoperability and inspectable contracts; contract files are not yet complete. Avoid GraphQL/gRPC until a concrete need exists. |
| Persistence | PostgreSQL 18.x, JPA for ordinary aggregates, explicit SQL/locking for contended financial paths; Flyway migrations | Relational constraints and ACID transactions suit ledger, schedules, and reservations. Schema and transaction behavior require domain-phase review. |
| Cache/rate limits | Redis 8.x is included in local Compose and the API dependency set | Useful for distributed throttling or ephemeral state, but it is not the financial source of truth. No current business use has been established; retain only if a specific phase justifies it, otherwise simplify startup/dependencies. |
| Web | Node.js 22 LTS, Next.js 16.x, React 19.x, TypeScript 5.x | Existing app supports server-rendered routing and Persian RTL; keep API authoritative for business rules and access control. |
| Mobile | Flutter/Dart, planned only | One Android/iOS codebase and platform-secure storage; Flutter source and device tests do not yet exist. Start only in the approved mobile phase. |
| Authentication | Spring Security boundary exists; sandbox role headers are demo-only | Production choice is open. Evaluate a maintained OIDC identity provider and phishing-resistant staff MFA/passkeys, recovery, and revocation before implementation. No vendor is selected. |
| Tests | JUnit 5/Spring test support, Testcontainers PostgreSQL, Playwright, axe-core | Supports domain/database checks and realistic customer flows. Existing CI does not run API tests and does not run Playwright; close these gaps in Phase 01. |
| Local runtime | Docker Compose v2; PostgreSQL and Redis containers; API container; Next.js dev server | Simple reproducible local environment. Services should stay loopback/private in development. No Kubernetes or microservices are justified yet. |
| CI | GitHub Actions | Current workflow builds the API and web, runs npm audit/typecheck/web build. API tests are skipped; E2E and dedicated backend security checks are absent. |
| Migrations | Flyway SQL migrations | Explicit reviewable database evolution; production expand/contract and rollback/recovery rules remain to be defined. |
| Observability | Spring Actuator health endpoints are present; structured logs/metrics/traces not yet specified | Add only the signals and retention needed by operations; redact sensitive values and do not expose management endpoints publicly. |

## Alternatives and decisions

- Keep a modular monolith and one PostgreSQL authority until measured scale, independent team ownership, or isolation needs justify extraction. Do not introduce microservices, Kubernetes, or a message broker by default.
- Use a durable outbox and a worker in the API deployment when asynchronous provider events/notifications are implemented; do not add a separate queue before delivery/replay requirements justify it.
- Avoid adding frontend state-management packages until observed client complexity requires them; the current app can use React state and server/API boundaries.
- Provider, hosting, secret manager, and monitoring products are intentionally undecided pending owner requirements and legal/provider approval.

See [architecture](architecture/overview.md), [ADR 0001](architecture/decisions/0001-modular-monolith.md), and [owner decisions](open-questions.md).
