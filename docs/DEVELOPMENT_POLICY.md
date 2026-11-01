# Development policy

This is the project-level engineering policy. It applies to human and AI contributors and is subordinate to system/security rules and explicit user instructions. Phase-specific acceptance criteria may add stricter requirements.

## Code and architecture

- Inspect the existing implementation and local instructions before editing; preserve unrelated work and use the established stack/patterns.
- Keep changes small, domain-owned, typed, and testable. Put business rules in the API, not in web/mobile clients. Avoid duplicate rule implementations and arbitrary cross-domain repository access.
- Use validated request objects and explicit state transitions. Keep financial operations transactional and use database constraints/locking where correctness depends on concurrency.
- Use integer IRR for persisted money; never use binary floating point for monetary calculations. Format Toman and Jalali dates only at the presentation boundary.
- Avoid new dependencies or infrastructure without a documented need and trade-off. Do not introduce microservices, brokers, or Kubernetes speculatively.
- Record material architecture changes in `docs/architecture/decisions/` and update the specification, contract, phase tasks, and docs they affect.

## Security and data

- Never hardcode or commit credentials, keys, OTPs for non-demo flows, production data, or sensitive logs. Keep `.env` local; use managed secrets in deployed environments.
- Validate untrusted input at the API boundary. Authorize every operation server-side, derive staff scope from the authenticated principal, and fail closed on missing identity or policy.
- Treat callbacks, retries, browser-supplied roles/IDs, and client state as untrusted. Use idempotency, provider verification, audit records, and explicit transition rules for financial state.
- Keep the sandbox loopback-only and synthetic. Do not enable real money movement, real credit, or external services without explicit authorization and required legal, provider, and operational approvals.
- Redact identity, session, payment, and credential data from logs, traces, reports, and test output.

## Development behavior and documentation

- Keep each task within its approved scope. Do not silently overwrite user changes, perform destructive data operations, deploy, publish, push, or modify external resources without authorization.
- For every significant feature, follow this sequence: confirm requirements/acceptance; identify affected modules, data, and interfaces; implement; test; review correctness/security/side effects; update docs/tasks; then report evidence and limitations. Use only the steps technically relevant to the feature, but do not skip required verification.
- Update authoritative docs when behavior/interfaces/architecture change. Do not duplicate requirements when a link to the source of truth is clearer.
- Track work in the active phase. Mark a task complete only when its acceptance criteria and required checks are evidenced.
- After a significant phase, write a concise numbered report with files, decisions, exact checks/results, limitations, and next work. Never infer a passing check from code inspection.

## Definition of done

A feature is done only when approved requirements and acceptance criteria are satisfied, the approved architecture and coding standards are followed, required tests/checks pass, security implications are reviewed, no known critical defect is left unaddressed, relevant docs and phase tasks are current, and limitations are reported. If required verification is unavailable, the work remains unverified/incomplete.

## Quality and dependencies

- Add regression and edge-case coverage for meaningful behavior, especially money, identity, authorization, concurrency, retries, and data integrity.
- Run the checks required by the active phase; report commands and actual outcomes. Distinguish test results from coverage and leave unavailable checks explicitly incomplete.
- Keep lockfiles and review dependency licenses/advisories on updates. Do not suppress a security finding without an explained, tracked exception.
- Profile before performance optimization. Define workload and SLOs before claiming capacity or adding performance infrastructure.

## Version control

- Use focused, meaningful commits; preserve separate concerns. Do not commit generated build/test output or secrets.
- Use `codex/<short-task>` for agent work and `feature/<short-task>` or `fix/<short-task>` for human work when branches are used. Prefer commit subjects of `<type>: <imperative summary>` (for example, `docs: define API error contract`). Review staged paths and `git diff --check` before committing.
- When a remote/PR workflow is configured, require peer review and passing required CI checks before merging to a protected `main`; do not assume protection exists until it is configured. Use semantic version tags for intentional releases once the release/versioning policy is approved; do not tag unreviewed development snapshots.
- Do not push, create remote resources, publish releases, or alter deployment environments without explicit authorization.
