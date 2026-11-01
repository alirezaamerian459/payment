# Security policy

This is a development baseline, not a certification or completed security review. The [threat model](security/threat-model.md) records current assets and threats. Validate applicable Iranian financial, consumer-credit, privacy, identity, and record-retention obligations with qualified counsel before any pilot.

## Required controls

1. **Authentication and sessions:** replace demo OTP/role selection with a selected, maintained identity/session design before real users. OTPs must expire, be single-use, stored safely, throttled, and never logged. Support rotation, revocation, recovery, and device/session review. Use phishing-resistant MFA for privileged staff where supported.
2. **Authorization:** deny by default; check function, object, property, and branch scope on the server for every request. Do not trust roles, amounts, branch IDs, or actor IDs from the client. Separate duties and require step-up/dual approval for high-risk actions where policy requires.
3. **Input and API safety:** validate type, size, format, ranges, and state transitions at the boundary. Bound payloads/timeouts, rate-limit abuse-prone endpoints, and return safe error messages.
4. **Secrets and configuration:** commit no real secrets. Use developer-only values in local examples, a managed secret facility in deployed environments, least-privileged service/database accounts, rotation, and documented incident response.
5. **Data protection:** minimize collection and retention; encrypt transport and sensitive stored data where appropriate; restrict document/file access; redact logs, traces, analytics, exports, and test reports. Never collect raw PIN/CVV.
6. **Financial integrity:** use integer IRR, atomic reservations/allocations, idempotent operations, balanced append-only posted journals, reversal/adjustment corrections, verified provider status, reconciliation, and immutable audit evidence.
7. **Database and deployment:** private network paths, restricted database roles, reviewed migrations, backups with protected keys, tested restore, patched minimal images, and management endpoints isolated from public access.
8. **Dependencies and supply chain:** retain lockfiles, review advisories/licensing, scan dependencies and source in CI, use protected CI permissions, and preserve provenance for release artifacts where supported.
9. **Logging and monitoring:** structured operational events with request/correlation IDs and redacted fields; alert on auth abuse, failed reconciliation, ledger/funding invariants, retries, and service health. Never expose debug/actuator details publicly.
10. **Response and recovery:** name incident owners, escalation and credential rotation steps, evidence preservation, backup recovery objectives, and customer/provider communication responsibilities before a pilot.

## Current boundary and release gate

The sandbox profile permits requests and relies on demo-only controls; it is not production authentication. The API container is mapped to loopback by Compose and uses synthetic data. Do not bind it to a public or shared network, reuse its OTP/password, deploy the sandbox profile, or configure a real provider. Before pilot, require threat review, legal/provider approvals, secure auth and access tests, dependency/security checks, operational runbooks, and a restore/reconciliation exercise. Production use remains disabled until the owner explicitly authorizes the relevant integration/deployment task.
