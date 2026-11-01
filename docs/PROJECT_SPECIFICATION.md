# PrivatePay project specification

**Status:** Existing project; initial requirements baseline for owner review. This document describes product intent separately from the sandbox behavior currently present.

## Purpose and users

PrivatePay is intended to let customers make purchases at company-owned Iranian shops using a four-installment payment arrangement, with customer and staff workflows supported by a Persian-first service. The initial product assumption is no customer fee for the four-installment plan; exact contract language, eligibility, due dates, fees/penalties, and operating model require owner and qualified legal review before credit is issued.

Primary users are customers, shop/branch cashiers, credit reviewers, finance/reconciliation staff, customer-support staff, and privileged service administrators. Staff access must be scoped to assigned duties and branches. The initial business scope is the organization's own shops, not an open third-party merchant marketplace.

## Product workflows

### Target workflows

- Customer onboarding, consent, identity verification, and account recovery.
- Credit application, explainable automated assessment, manual review, approval, and facility management.
- Shop/branch enrollment and scoped staff access.
- Customer checkout, payment authorization, four-installment schedule, repayment, early settlement, cancellation, and refund.
- Finance ledger, funding/liquidity controls, reconciliation, exception handling, support, and audit reporting.
- Responsive Persian RTL web experience first; Android/iOS Flutter client later, using the same versioned API.

### Existing sandbox workflows

The current repository contains a synthetic demo login, demo-role selection, manual credit review, checkout and repayment/refund demonstrations, risk/reconciliation views, a 20-scenario runner, a Spring Boot API, PostgreSQL migrations, and a Next.js RTL web app. These flows are for local demonstration and automated tests. Fixed OTP and role switching are preview controls, not authentication. They do not connect to an identity provider, bank, payment service provider, or real credit source and cannot move money.

## Requirements

### Functional requirements

1. Maintain customer and staff identities, consent and verification state, shop/branch membership, role, and audit history.
2. Enforce server-side authorization and branch scope on every protected operation.
3. Store versioned credit applications and decisions, with reason codes, reviewer actions, approval limits, expiry, and auditable changes.
4. Reserve available credit atomically; prevent overspending under concurrent requests.
5. Represent orders, payment attempts, installment agreements, repayments, refunds, funding, and reconciliation with explicit state transitions and idempotency.
6. Post financial changes as balanced, immutable ledger journals; correct errors with linked reversals/adjustments.
7. Provide customer and staff experiences in Persian RTL, with accessible forms, status feedback, and clear sandbox/live environment identification.
8. Record security- and finance-relevant actions without storing credentials, OTPs, card secrets, or unnecessary personal data in logs.
9. Keep external identity, OTP, payment, refund, and notification integrations behind replaceable provider interfaces.

### Nonfunctional requirements

- **Integrity:** integer IRR amounts; database-enforced constraints; transactionally consistent reservations, schedules, allocations, and ledger postings; duplicate/retry safety.
- **Security/privacy:** least privilege, server-side validation and authorization, protected sessions, rate limits where needed, sensitive-data minimization/redaction, managed secrets, encryption in transit/at rest as applicable, and tested recovery.
- **Reliability:** idempotent external-event processing, bounded retries, observable background work, reconciliation, and documented backup/restore procedures.
- **Usability/accessibility:** Persian-first RTL, Jalali display dates where approved, keyboard support, semantic forms, and responsive mobile web.
- **Maintainability:** domain-modular monolith, explicit ownership and APIs, migrations, reproducible local environment, and automated verification.
- **Performance/scale:** no numeric SLA or traffic estimate has been provided. Establish realistic workload and latency/error objectives before load testing or selecting production capacity; do not invent benchmarks.

## Platforms, deployment, and integrations

- **Current development target:** Windows-capable developer workflow with Docker Compose; services bind to loopback. Web runs locally on port 3000 and API on 8080; PostgreSQL and Redis are internal Compose services.
- **Initial product target:** responsive Persian RTL web/PWA and a REST API. Flutter Android/iOS is planned but not implemented.
- **Production environment:** cloud/hosting, residency, regions, availability targets, and release process are undecided.
- **External services:** no OTP/SMS, identity/KYC, bank, PSP, push-notification, or production observability provider is selected or authorized.

## Scope boundary and constraints

- Current behavior is a sandbox, not a production BNPL/credit platform.
- Do not use real customer data, issue real credit, connect live financial endpoints, or deploy externally in development work.
- Business rules, provider selection, legal requirements, shop population, funding source, calendar conventions, and privileged-authentication policy remain owner decisions listed in [open questions](open-questions.md).
- Preserve the established palette: `#d97706`, `#5bc0de`, `#3a506b`, `#1c2541`, and `#0b132b`.
- Existing starter services and UI are not evidence that production controls or readiness gates are complete.

## Initial delivery scope

The existing code is a local demonstration foundation. The next proposed production-oriented implementation phase is identity, organization, and authorization design/build, after this blueprint is approved and the relevant owner decisions are resolved. The current sandbox remains separate from any live flow. The detailed boundaries and phase order are in [the roadmap](../planning/roadmap.md).

## Acceptance for this specification

- Product purpose, user groups, scope, constraints, existing behavior, nonfunctional requirements, and unresolved decisions are documented.
- Owner review confirms or changes the assumptions before a dependent feature is implemented.
