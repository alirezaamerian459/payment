# Data model overview

PostgreSQL is authoritative. The diagram shows the first major relationships, not a complete schema. Detailed constraints and migrations are introduced with the owning domain phase.

```mermaid
erDiagram
  USER ||--o| CUSTOMER_PROFILE : has
  USER ||--o{ STAFF_MEMBERSHIP : assigned
  SHOP ||--o{ BRANCH : contains
  BRANCH ||--o{ STAFF_MEMBERSHIP : scopes
  CUSTOMER_PROFILE ||--o{ CREDIT_APPLICATION : submits
  CREDIT_APPLICATION ||--o{ CREDIT_DECISION : records
  CUSTOMER_PROFILE ||--o| CREDIT_FACILITY : receives
  CREDIT_FACILITY ||--o{ CREDIT_RESERVATION : reserves
  CUSTOMER_PROFILE ||--o{ ORDER : places
  ORDER ||--o| PAYMENT_INTENT : initiates
  PAYMENT_INTENT ||--o{ PAYMENT_ATTEMPT : records
  ORDER ||--o| INSTALLMENT_PLAN : finances
  INSTALLMENT_PLAN ||--|{ INSTALLMENT : schedules
  INSTALLMENT ||--o{ REPAYMENT_ALLOCATION : settles
  REFUND ||--o{ LEDGER_JOURNAL : posts
  PAYMENT_ATTEMPT ||--o{ LEDGER_JOURNAL : posts
  REPAYMENT_ALLOCATION ||--o{ LEDGER_JOURNAL : posts
  LEDGER_JOURNAL ||--|{ LEDGER_LINE : contains
  USER ||--o{ AUDIT_EVENT : acts
```

## Core invariants

- Every monetary column is integer IRR, nonnegative where appropriate, and bounded by explicit checks.
- A posted journal has at least two lines and debits equal credits. Enforce posting in one database transaction; posted journals and lines are immutable. Correct them with linked reversals or adjustments.
- Credit availability is approved limit minus outstanding principal and live reservations, under a row lock or an atomic conditional update. It is never a wallet balance.
- An idempotency key is unique within a documented actor/operation scope. External callback IDs and provider references also have unique constraints.
- A reservation is created, consumed, expired, or released exactly once. State transitions use conditional updates and record an audit event.
- Installment schedules preserve their original agreement terms. Changes create explicit adjustments, not silent edits.
- Each staff membership carries a branch scope; every branch-owned query checks that scope on the server.
- Financial event references link a payment, order, refund, or repayment to its ledger posting and reconciliation record.

Use UUID identifiers for externally referenced records. Store timestamps in UTC; render Iranian-local time and Jalali dates at the presentation boundary. Keep customer-facing Toman formatting separate from the IRR ledger amount.
