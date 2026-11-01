# Coding standards

Follow the existing formatting and language conventions; `.editorconfig` is the baseline. Keep the standards enforceable with the tools already present. Do not add a formatter/linter dependency without a phase task and rationale.

## Names and layout

- Java packages are lowercase (`ir.privatepay.<domain>`); types use `UpperCamelCase`; methods and fields use `lowerCamelCase`; constants use `UPPER_SNAKE_CASE`.
- TypeScript/React components and types use `UpperCamelCase`; functions, hooks, and values use `lowerCamelCase`; components use `.tsx` and non-UI modules use `.ts`.
- Use descriptive kebab-case for documentation and phase directories. Name tests after the behavior/subject, e.g. `CreditReservationTest` or `customer-journey.spec.ts`.
- Keep domain code inside its owning package/module. Keep shared code small and genuinely cross-domain.

## APIs, data, and errors

- Expose versioned REST endpoints under `/api/v1`; use nouns for resources, explicit action endpoints only for state transitions, validated request/response DTOs, and documented status/error shapes.
- Use stable machine-readable error codes with safe user messages; do not expose stack traces, secrets, SQL, or internal policy details.
- Database tables/columns use `snake_case`; migrations are ordered Flyway SQL files named `V<number>__<description>.sql`. Add database constraints for critical invariants.
- Use UUIDs for externally referenced IDs, UTC timestamps in persistent records, integer IRR for amounts, and explicit enums/state machines. Avoid ambiguous booleans for multi-state workflows.
- Make idempotency scope and provider event uniqueness explicit in API and database contracts.

## React and accessibility

- Keep components focused; derive display-only values at the edge and do not make the browser authoritative for prices, eligibility, limits, permissions, or payment status.
- Use semantic HTML, associated labels, keyboard-accessible controls, visible focus, accessible status/error announcements, and RTL-aware layout. Preserve the approved palette tokens.
- Handle loading, empty, error, pending, and success states for asynchronous actions. Never show a success state until the API confirms it.

## Quality checks

- Backend: run Maven tests for the API phase; use JUnit assertions that describe business outcomes and include integration tests when persistence/transactions matter.
- Web: `npm run typecheck`, `npm run build`, and Playwright E2E for affected journeys. Keep selectors tied to accessible names/roles where practical.
- Do not claim formatting/lint checks are automated until a formatter/linter is configured. CI gaps are tracked in the foundation phase.
