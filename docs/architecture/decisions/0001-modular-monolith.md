# ADR 0001: Start as a domain-modular monolith

- **Status:** Accepted for the development foundation
- **Date:** 2026-10-31

## Context

PrivatePay needs transactionally safe credit reservations, order finalization, installment creation, and double-entry posting. The repository is new, operating scale is unknown, and no separate engineering teams or independently scaled services have been established.

## Decision

Use one Spring Boot deployable with explicit business modules and one authoritative PostgreSQL database. Keep payment, identity, and notification providers behind interfaces. Use a durable PostgreSQL-backed outbox/job model before adding a separate broker. Use Redis only for disposable cache, throttling, or coordination that can safely be reconstructed.

## Consequences

- Core financial transitions can use local ACID transactions.
- Module boundaries need code review and automated architecture checks to prevent coupling.
- A module can be extracted later behind its interface if measured operational needs justify it.
- Database schema changes need a deliberate owner and backward-compatible rollout plan.
- High availability and load capacity still need environment-specific validation before any pilot.
