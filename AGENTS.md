# PrivatePay agent instructions

Before changing code:

1. Read [the project specification](docs/PROJECT_SPECIFICATION.md), [the current roadmap and phase tasks](planning/roadmap.md), and the applicable [development policy](docs/DEVELOPMENT_POLICY.md), [coding standards](docs/CODING_STANDARDS.md), [testing policy](docs/TESTING_POLICY.md), and [security policy](docs/SECURITY_POLICY.md).
2. Read [the architecture overview](docs/architecture/overview.md), relevant data-model/ADR material, and the nearest app-level `AGENTS.md`. The web app has Next.js-generated instructions that must be followed for web changes.
3. Confirm the active phase and its acceptance criteria. Implement only the user's authorized scope; ask before changing an approved architectural boundary or enabling a real external integration.
4. Inspect affected files and preserve unrelated edits. Keep diffs focused. Do not expose, copy, or commit secrets, real personal data, or production records.
5. Add or update tests for changed behavior and run the checks required by the phase. Report exact commands and observed results; if a check cannot run, state why and leave verification incomplete.
6. Update phase tasks, relevant documentation, and a numbered report only with verified status. Record decisions in `docs/architecture/decisions/`.

The sandbox is synthetic and loopback-only. Never enable real credit, banking, payment, identity, or notification integrations or deploy externally without explicit authorization and the required owner/legal/provider approvals. Higher-priority system, security, and user instructions take precedence over this file.
