# Phase 04 — Checkout, payments, and reconciliation

## Objective and dependencies

Complete purchase, payment-attempt, repayment allocation, refund, and reconciliation lifecycles. Depends on Phases 02–03, approved commercial/provider agreements, provider sandbox access, callback/security specifications, refund policy, and operations ownership.

## Scope and requirements

Start with deterministic sandbox adapters. Use idempotency keys with explicit actor/operation scope, provider event uniqueness, server-side status inquiry, bounded retries, and explicit timeout/late-success states. Never finalize a financial state from an unverified callback alone. Persist provider references and link each financial event to order, ledger journal, and reconciliation record.

## Architecture and implementation

Use provider ports and adapter implementations; durable outbox/job processing for retries and notifications where required. Keep network calls outside database transactions where possible, with explicit reservation/release behavior and recovery paths. Real endpoints remain disabled unless separately approved.

## Security, tests, and Definition of Done

Threat-model callback spoofing/replay, duplicate refunds, amount tampering, and provider outage. Test valid/invalid signatures, inquiry mismatch, duplicate/late callbacks, timeouts/retries, refund limits, repayment/refund races, job replay, and reconciliation discrepancies. Done when all sandbox transitions are idempotent and recoverable, ledger entries reconcile, and the integration remains explicitly sandbox-labelled.
