# Initial security and threat model

This is a foundation, not a completed security assessment. Use OWASP ASVS as a requirements baseline and validate the applicable Iranian legal, privacy, payment, and credit obligations with qualified counsel before a live pilot.

## Assets

- Customer identity and contact data, consent, and verification evidence.
- Authentication credentials, OTPs, session and device records.
- Credit policies, application evidence, decisions, limits, and reservations.
- Orders, payment state, repayment allocations, refunds, ledger journals, and funding limits.
- Provider credentials, webhook secrets, encryption keys, audit events, and backups.

## Main threats and controls

| Threat | Baseline control |
|---|---|
| OTP abuse, SIM swap, account takeover | OTP expiry, single use, hashed storage, per-account/device/IP throttles, risk signals, secure recovery, session/device revocation. Never log OTP values. |
| Privilege escalation or self-approval | Server-side role and attribute checks, no self-service Super Admin elevation, least privilege, step-up authentication, and dual control for critical policy/funding changes. |
| Cross-branch data access | Derive scope from authenticated staff membership on every request; never trust client-supplied branch IDs alone; test negative authorization paths in later phases. |
| Double spend or race conditions | Database transactions, row locking or compare-and-set updates, unique idempotency constraints, and concurrency testing before enabling checkout. |
| Forged or replayed payment callbacks | Verify callback authenticity and provider status server-side, deduplicate event identifiers, and use explicit state machines. No financial posting from a message alone. |
| Ledger tampering or inconsistent postings | Append-only posted journals, balanced posting transaction, reversal-only correction, restricted DB roles, independent audit trail, reconciliation, and backup restore drills. |
| Sensitive data leakage | Data minimization, encryption in transit/at rest, restricted document storage with retention rules, redacted structured logs, secret manager in deployed environments. |
| Service abuse / denial of service | Rate limits, bounded request sizes, timeouts, circuit breakers, provider isolation, health/queue alerts, and emergency financing circuit breaker. |
| Dependency or supply-chain compromise | Lockfiles, automated dependency/advisory scanning, minimal runtime images, signed CI artifacts, and reviewed upgrade cadence. |

## Security gates

- Privileged staff use phishing-resistant MFA (passkeys/WebAuthn where supported) with recovery and device revocation.
- Customer and staff sessions use rotation, revocation, secure cookies or platform keystore storage, and short-lived access credentials.
- Production secrets and encryption keys never live in source control, client bundles, logs, or database dumps without encryption.
- Collect no raw card PIN/CVV. Prefer an approved hosted/tokenized provider flow if and when separately authorized.
- Critical admin actions record actor, before/after values where safe, reason, request ID, time, and second reviewer when policy requires it.
- Run security, privacy, and operational reviews before a controlled pilot. No production deployment is part of this foundation.
