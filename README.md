# PrivatePay

PrivatePay is a Persian-first, sandbox-only foundation for a privately operated, four-installment payment platform for company-owned shops.

> **Development boundary:** this repository does not connect to banking rails, issue real credit, or process real-money payments. All example limits and integrations are for development only.

## Current status

The local sandbox now has a Persian RTL customer journey backed by a Spring Boot API and PostgreSQL: demo login, credit application and manual review, four-part checkout, repayments, refunds, risk controls, reconciliation, and an owner screen for all 20 financial scenarios. Scenario runs are recorded in PostgreSQL. No banking rails or real-money payments are connected.

The fixed demo OTP and sandbox role switch are local preview controls, not secure customer or staff authentication. The sandbox profile must never be used outside a loopback development environment. The API's default profile remains fail-closed. Flutter, real identity providers, production sessions/MFA, and real payment-provider integration remain future work and are not represented as implemented.

## Repository map

```text
AGENTS.md              AI-agent working rules
apps/
  api/                 Spring Boot modular monolith
  web/                 Persian RTL customer and operations sandbox
  mobile/              Flutter application placeholder
contracts/
  openapi/             Versioned API contract source
docs/
  PROJECT_SPECIFICATION.md
  TECHNOLOGY_STACK.md
  DEVELOPMENT_POLICY.md
  CODING_STANDARDS.md
  TESTING_POLICY.md
  SECURITY_POLICY.md
  architecture/        Architecture, data model, and ADRs
  development/         Setup, roadmap, and verification strategy
  security/            Threat model and security baseline
planning/               Roadmap and phase instructions/tasks
reports/                Verified phase reports
docker-compose.yml      Loopback-only local PostgreSQL, Redis, and API
.github/workflows/     Build and static-analysis workflow
```

Start with [the project specification](docs/PROJECT_SPECIFICATION.md), [the roadmap](planning/roadmap.md), and [agent instructions](AGENTS.md). For implementation details, see [local development](docs/development/local-setup.md), [architecture](docs/architecture/overview.md), [testing policy](docs/TESTING_POLICY.md), [security policy](docs/SECURITY_POLICY.md), and [open owner decisions](docs/open-questions.md).

## Technology baseline

The starting dependency lines are recorded in [architecture overview](docs/architecture/overview.md). Refresh patches and review security advisories before each phase; do not use development defaults in staging or production.
