# Phase 02 — Identity and organization

## Objective and dependencies

Replace sandbox-only identity assumptions with an approved identity, consent, customer profile, staff, shop, and branch foundation. Depends on Phase 01 exit, owner/legal decisions about identity, data use/retention, provider selection, shop population, and privileged MFA/recovery.

## Scope and requirements

Define customer onboarding/account recovery, identity-verification status, staff invitations/membership, roles and branch scopes, consent evidence, session/device lifecycle, audit records, and server-side authorization. Select an identity provider only after owner approval. No live OTP/KYC provider or broad customer rollout is included without explicit authorization.

## Architecture and implementation

Own identity/access and organization modules in the Spring Boot monolith with PostgreSQL migrations and versioned REST contracts. Expose stable API DTOs; derive actor and branch scope from the authenticated principal. The web client never controls role claims. Expected areas: API identity/organization packages, migrations, OpenAPI contracts, web session integration, and focused tests.

## Security and verification

Threat-model account takeover, OTP abuse, privilege escalation, self-approval, IDOR, and cross-branch access. Require expiry/replay/rate-limit tests, session revocation/recovery tests, positive and negative object/function/property authorization tests, and audit evidence. Definition of Done: approved auth flows are implemented, branch isolation is demonstrated by API tests, sensitive data is minimized/redacted, documentation and tasks match behavior, and no sandbox shortcut is available in the selected non-sandbox profile.
