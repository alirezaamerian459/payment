# Owner decisions to resolve before the relevant phase

Work can proceed on the assumptions below during Phase A. Resolve each item before enabling the dependent feature in a pilot.

1. **Legal and operating scope:** Which legal entity owns the service, and which counsel will validate Iranian licensing, consumer-credit, privacy, payments, and record-retention obligations?
2. **Customer/payment providers:** Which authorized provider(s) may be evaluated for OTP delivery, identity verification, payment collection, refunds, and push notifications? No provider is selected or configured yet.
3. **Installment due-date policy:** Is each subsequent due date the same calendar day one, two, and three months later, with an explicit month-end and holiday rule? The customer agreement must define this before credit issuance.
4. **Funding policy:** Who supplies the approved business funding budget and how is liquidity evidence reconciled? The initial budget remains a test setting only.
5. **Sandbox limits:** The example credit tiers and ceilings are test values. Confirm whether to retain them only for automated fixtures or adjust them before the credit engine is built.
6. **Initial business population:** Which owned shops/branches and customer eligibility groups belong in a sandbox pilot? No hard-coded business count will be introduced.
7. **Privileged authentication:** Confirm passkey/WebAuthn as the preferred Super Admin MFA, with a documented recovery process and independent approval for critical operations.
8. **Business timezone/calendar:** Confirm Asia/Tehran for business operations, UTC database timestamps, and Jalali dates for customer and staff display.
