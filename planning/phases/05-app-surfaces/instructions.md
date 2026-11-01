# Phase 05 — Customer and staff product surfaces

## Objective and dependencies

Deliver approved web, mobile, cashier, credit, finance, support, and owner workflows against stable API contracts. Depends on approved UX/business requirements and completed server-side access contracts for each included flow. The current web sandbox is a demonstration and is not a production-ready portal.

## Scope and requirements

Support Persian RTL customer application, credit status/terms, purchase/repayment/history/support; staff tasks limited to assigned role and branch; accessible responsive layouts and clear payment states. Add Flutter Android/iOS only when the mobile scope is approved, reusing API contracts and platform-secure session storage. Notifications must be consent-aware, redacted, retryable, and provider-abstracted.

## Architecture and implementation

Next.js remains the initial web client, Flutter the planned mobile client, and the API the source of truth. Keep UI components focused and preserve the requested palette. Avoid financial calculations or authorization decisions in clients. Expected files include routes/screens, typed API clients, accessibility assets/tests, and Flutter source only in its planned work.

## Security, tests, and Definition of Done

Test RTL, keyboard/screen-reader semantics, validation, empty/error/pending/success states, responsive layouts, session expiry/logout, role and branch restrictions, and end-to-end approved journeys. Mobile acceptance includes secure storage and device/session revocation. Done when each UI flow is backed by an authorized API and no demo-only identity or fabricated transaction state is presented as real.
