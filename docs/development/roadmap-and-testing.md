# Verification strategy reference

The canonical delivery sequence and phase checklists are in [planning/roadmap.md](../../planning/roadmap.md). This file preserves the earlier milestone mapping for continuity; phase IDs below are authoritative.

## Milestone mapping

| Milestone | Scope | Exit evidence |
|---|---|---|
| A. Foundation → Phase 01 | Monorepo, architecture decisions, local databases, API/web shells, CI, development conventions | Repeatable local startup, documented boundaries, required CI checks passing. |
| B. Identity and access → Phase 02 | OTP abstraction, profiles, staff, roles, branch scope, consent, identity-verification provider boundary | Auth/session lifecycle and authorization tests; audit of privileged flows. |
| C. Credit → Phase 03 | Application workflow, versioned deterministic policy, review, limits and reservations | Explainable decisions, policy tests, concurrency proof for reservations. |
| D. Financial core → Phase 03 | Ledger, four-installment plans, allocations/settlement, funding budget, circuit breaker | Balanced journal and immutability invariants; financial scenario suite. |
| E. Sandbox payments → Phase 04 | Mock provider, idempotent intents, callbacks, refunds, reconciliation/outbox recovery | Duplicate, timeout, retry, late callback, refund, and recovery scenarios pass. |
| F. Web portals → Phase 05 | Customer flows, owner/credit/finance/branch surfaces, cashier checkout | API-backed journeys, access checks, RTL/accessibility review. |
| G. Mobile → Phase 05 | Flutter Android/iOS app, secure sessions, notifications | Device/session and API integration checks on supported OS versions. |
| H. Hardening → Phase 06 | Security, concurrency, resilience, load, accessibility, restore and incident practice | Findings triaged, integrity verified after failure scenarios, runbooks exercised. |
| I. Pilot preparation → Phase 06 | Sandbox pilot controls, reconciliation, risk policy review, deployment prerequisites | Signed operational readiness and separate owner authorization before any live integration. |

## Verification strategy

The authoritative test layers and current CI gaps are in [TESTING_POLICY.md](../TESTING_POLICY.md). The summary below records the commands used by the current repository.

Implemented now: PostgreSQL-backed scenario checks for the 20 named financial cases, test-level ledger/funding/installment integrity checks, role authorization coverage, and Playwright customer-journey/RTL/accessibility/mobile checks. See [local setup](local-setup.md) for commands. The backend integration suite needs Docker/Testcontainers; browser journeys need the API and database running locally.

Not yet implemented: Flutter widget/device tests, load tests, provider signature verification against an authorized provider, secure staff authentication/MFA, and operational restore drills. Do not report those as complete.

- **Unit:** credit rules/reason codes, integer-rial installment split and deterministic rounding, payment/refund/repayment state machines.
- **Database integration:** migrations, constraints, transaction rollback, journal posting, idempotency uniqueness, branch-scoped queries.
- **Concurrency:** simultaneous reservations, repayments, refunds, duplicate callbacks, and repayment/refund races.
- **Security:** unauthenticated access, role boundaries, branch isolation, step-up/dual control, rate limits, session revocation.
- **Provider:** signed and invalid callback, status inquiry, timeout, retry, late success, duplicate delivery, and sandbox behavior.
- **Web:** RTL rendering, accessible forms, loading/empty/error/pending states, and end-to-end customer/merchant journeys.
- **Mobile:** widgets, secure storage, session revocation, API failures, and supported-device integration.
- **Resilience:** outbox/job replay, worker crash, database restart/restore, circuit breaker, reconciliation mismatch.
- **Load:** realistic, staged checkout/reservation/repayment traffic after correctness and integrity tests pass; never use real payment endpoints during development load tests.

CI foundation should compile/lint each app. As feature tests are added, make the relevant test suites required checks. Keep sandbox credentials synthetic and test fixtures free of real customer data.
