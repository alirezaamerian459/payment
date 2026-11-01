# Phase 03 — Credit and financial core

## Objective and dependencies

Implement explainable credit decisions, funding controls, credit reservations, installment agreements, and ledger integrity. Depends on Phase 02 actor/audit model and owner approval of legal/product terms, funding source, credit policy, limits, due-date/calendar rules, rounding, reversals, and delinquency treatment.

## Scope and requirements

Use versioned policy inputs and decision reason codes; preserve manual review and appeal/escalation paths as approved. Available credit must be derived from approved limit, outstanding principal, and active reservations. Persist integer IRR; define customer-facing Toman conversions explicitly. Installment schedules preserve signed terms. Post balanced journals atomically and correct via linked reversal/adjustment.

## Architecture and implementation

Keep credit, funding, repayment agreement, and ledger ownership explicit in the monolith. PostgreSQL is authoritative. Use transaction boundaries, constraints, row locking/conditional updates, immutable posted records, and stable APIs. Do not use Redis as a balance or ledger authority.

## Security, tests, and Definition of Done

Limit decision evidence and sensitive attributes; enforce reviewer duties and audit changes. Test rounding boundaries, policy versions, reason codes, limit/reservation races, journal balance/immutability, rollback, and schedule invariants against PostgreSQL. Done only after approved policy is explainable, concurrency tests prove no overspend/double posting, and reconciliation/reporting can account for each financial reference. No live credit is enabled in this phase.
