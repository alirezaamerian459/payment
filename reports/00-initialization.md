# Report 00 — Project initialization blueprint

## Phase

Phase 01 — Project foundation; documentation blueprint prepared for owner review.

## Summary

Inspected the existing PrivatePay repository and documented its sandbox-versus-target boundary, known requirements, existing stack, architecture references, policies for development/coding/testing/security, concise agent instructions, and six proposed implementation phases.

## Files created or updated

- Added root `AGENTS.md` and project, architecture-index, stack, development, coding, testing, and security documents under `docs/`.
- Added `planning/roadmap.md` and `instructions.md`/`tasks.md` for phases 01–06.
- Updated the README repository map and documentation entry points.

## Decisions and observations

- Preserve the existing Persian RTL web sandbox, Spring Boot API, PostgreSQL, local Compose, and modular-monolith direction.
- Keep real identity/payment/credit/deployment integrations outside current scope.
- The API CI job currently invokes Maven with `-DskipTests`; browser E2E is not in CI. Both remain open foundation tasks.
- Redis is configured but no current business usage is documented; resolve before expanding its role.
- Owner decisions remain in `docs/open-questions.md`.

## Verification

- Repository/manifests/docs and CI configuration were inspected.
- `git diff --check` and application tests were **not run in this documentation-only initialization task**. The existing repo's test instructions and CI gaps are recorded in the testing policy and Phase 01 task list.

## Limitations and next steps

- This report does not claim runtime, test, security, or production readiness.
- Review/approve the project assumptions and resolve owner decisions relevant to Phase 02.
- Re-run API, web, E2E, and CI checks; complete the Phase 01 verification tasks and then update this report with actual results.
- Begin feature implementation only after the blueprint is approved.
