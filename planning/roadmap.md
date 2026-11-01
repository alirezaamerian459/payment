# PrivatePay roadmap

**Current state:** Existing sandbox foundation; initialization blueprint and governance documents are being established. No production capability is implied. Owner approval of this blueprint and the listed decisions is the gate before implementation of new product features.

| Phase | Focus | Status / exit gate |
|---|---|---|
| 01 | Foundation, project governance, reproducible local setup, CI verification | Existing app/docs provide a baseline. Current blueprint documented; clean verification and CI test gates remain to be evidenced. |
| 02 | Identity, consent, staff access, shops/branches, secure authentication | Proposed next; confirm identity/provider, privileged MFA/recovery, shop population, and legal/privacy constraints first. |
| 03 | Credit policy, funding controls, reservations, installment agreements, ledger | Requires approved credit/funding terms and due-date/rounding rules; all money remains sandbox-only until separately authorized. |
| 04 | Checkout, payment/refund adapters, repayment allocation, reconciliation, durable jobs | Sandbox adapter first. Live providers require contracts, credentials, security review, and explicit authorization. |
| 05 | Customer/staff web workflows, support, notifications, Flutter mobile | Build against versioned contracts and server-enforced permissions; Flutter is not implemented. |
| 06 | Security, resilience, load, restore, operational and pilot readiness | No pilot/live deployment until legal, technical, security, liquidity, and operational exit evidence is signed off. |

Each phase has `instructions.md` and `tasks.md` under `planning/phases/`. Tasks remain open until acceptance criteria and required verification are evidenced. The more detailed verification matrix is in [testing policy](../docs/TESTING_POLICY.md); owner decisions are tracked in [open questions](../docs/open-questions.md).
