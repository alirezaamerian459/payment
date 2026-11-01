CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE customers (
    id UUID PRIMARY KEY,
    mobile VARCHAR(16) NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE shops (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE branches (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL REFERENCES shops(id),
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE credit_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customers(id),
    state VARCHAR(32) NOT NULL CHECK (state IN ('SUBMITTED', 'MANUAL_REVIEW', 'MORE_INFO', 'APPROVED', 'DECLINED')),
    requested_limit_irr BIGINT NOT NULL CHECK (requested_limit_irr > 0),
    policy_version VARCHAR(40) NOT NULL,
    reason_codes TEXT[] NOT NULL DEFAULT '{}',
    reviewer_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE credit_accounts (
    customer_id UUID PRIMARY KEY REFERENCES customers(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    tier VARCHAR(24) NOT NULL,
    limit_irr BIGINT NOT NULL CHECK (limit_irr >= 0),
    outstanding_irr BIGINT NOT NULL DEFAULT 0 CHECK (outstanding_irr >= 0),
    reserved_irr BIGINT NOT NULL DEFAULT 0 CHECK (reserved_irr >= 0),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (outstanding_irr + reserved_irr <= limit_irr OR status = 'SUSPENDED')
);

CREATE TABLE funding_control (
    singleton_id SMALLINT PRIMARY KEY CHECK (singleton_id = 1),
    budget_irr BIGINT NOT NULL CHECK (budget_irr >= 0),
    committed_irr BIGINT NOT NULL DEFAULT 0 CHECK (committed_irr >= 0),
    reserved_irr BIGINT NOT NULL DEFAULT 0 CHECK (reserved_irr >= 0),
    new_financing_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (committed_irr + reserved_irr <= budget_irr OR NOT new_financing_enabled)
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    amount_irr BIGINT NOT NULL CHECK (amount_irr > 0),
    description VARCHAR(240) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('PAYMENT_PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'REFUNDED', 'PARTIALLY_REFUNDED')),
    idempotency_key VARCHAR(96) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_intents (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    amount_irr BIGINT NOT NULL CHECK (amount_irr > 0),
    first_installment_irr BIGINT NOT NULL CHECK (first_installment_irr > 0),
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT')),
    provider_reference VARCHAR(96) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intent_id UUID NOT NULL REFERENCES payment_intents(id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    result VARCHAR(24) NOT NULL CHECK (result IN ('SUCCESS', 'FAILURE', 'TIMEOUT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (intent_id, attempt_number)
);

CREATE TABLE installment_plans (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    original_amount_irr BIGINT NOT NULL CHECK (original_amount_irr > 0),
    first_payment_irr BIGINT NOT NULL CHECK (first_payment_irr > 0),
    outstanding_irr BIGINT NOT NULL CHECK (outstanding_irr >= 0),
    policy_version VARCHAR(40) NOT NULL,
    agreement_reference VARCHAR(96) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('ACTIVE', 'SETTLED', 'ADJUSTED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE installments (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES installment_plans(id),
    sequence_number SMALLINT NOT NULL CHECK (sequence_number BETWEEN 1 AND 4),
    due_date DATE NOT NULL,
    original_amount_irr BIGINT NOT NULL CHECK (original_amount_irr > 0),
    adjusted_amount_irr BIGINT NOT NULL CHECK (adjusted_amount_irr >= 0),
    paid_irr BIGINT NOT NULL DEFAULT 0 CHECK (paid_irr >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DUE', 'PARTIAL', 'PAID', 'OVERDUE', 'ADJUSTED')),
    UNIQUE (plan_id, sequence_number),
    CHECK (paid_irr <= adjusted_amount_irr)
);
CREATE INDEX installments_due_idx ON installments (due_date, status);

CREATE TABLE repayments (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    amount_irr BIGINT NOT NULL CHECK (amount_irr > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT')),
    idempotency_key VARCHAR(96) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE repayment_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repayment_id UUID NOT NULL REFERENCES repayments(id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    result VARCHAR(24) NOT NULL CHECK (result IN ('SUCCESS', 'FAILURE', 'TIMEOUT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (repayment_id, attempt_number)
);

CREATE TABLE repayment_allocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repayment_id UUID NOT NULL REFERENCES repayments(id),
    installment_id UUID NOT NULL REFERENCES installments(id),
    amount_irr BIGINT NOT NULL CHECK (amount_irr > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refund_requests (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    amount_irr BIGINT NOT NULL CHECK (amount_irr > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED', 'APPROVED', 'DECLINED', 'PENDING_PROVIDER', 'COMPLETED')),
    reason TEXT NOT NULL,
    idempotency_key VARCHAR(96) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ledger_accounts (
    code VARCHAR(48) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    normal_side VARCHAR(8) NOT NULL CHECK (normal_side IN ('DEBIT', 'CREDIT'))
);

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    event_reference VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(240) NOT NULL,
    posted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reversal_of UUID REFERENCES journal_entries(id)
);

CREATE TABLE journal_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_id UUID NOT NULL REFERENCES journal_entries(id),
    account_code VARCHAR(48) NOT NULL REFERENCES ledger_accounts(code),
    debit_irr BIGINT NOT NULL DEFAULT 0 CHECK (debit_irr >= 0),
    credit_irr BIGINT NOT NULL DEFAULT 0 CHECK (credit_irr >= 0),
    CHECK ((debit_irr > 0 AND credit_irr = 0) OR (credit_irr > 0 AND debit_irr = 0))
);
CREATE INDEX journal_lines_account_idx ON journal_lines (account_code, journal_id);

CREATE TABLE provider_events (
    provider_event_id VARCHAR(120) PRIMARY KEY,
    intent_id UUID NOT NULL REFERENCES payment_intents(id),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reconciliation_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_reference VARCHAR(120) NOT NULL,
    expected_irr BIGINT NOT NULL,
    observed_irr BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('MATCHED', 'MISMATCH')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE durable_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type VARCHAR(60) NOT NULL,
    payload JSONB NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('READY', 'RUNNING', 'COMPLETED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor VARCHAR(120) NOT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id VARCHAR(120) NOT NULL,
    reason TEXT,
    metadata JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_events_entity_idx ON audit_events (entity_type, entity_id, occurred_at DESC);

CREATE TABLE sandbox_scenario_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scenario_key VARCHAR(48) NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(12) NOT NULL CHECK (status IN ('PASS', 'FAIL')),
    detail TEXT NOT NULL,
    duration_ms BIGINT NOT NULL CHECK (duration_ms >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX sandbox_scenario_runs_created_idx ON sandbox_scenario_runs (created_at DESC);

INSERT INTO shops (id, name) VALUES ('22222222-2222-2222-2222-222222222222', 'فروشگاه آزمایشی');
INSERT INTO branches (id, shop_id, name) VALUES ('33333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222', 'شعبه مرکزی');
INSERT INTO customers (id, mobile, display_name) VALUES ('11111111-1111-1111-1111-111111111111', '00000000000', 'کاربر آزمایشی');
INSERT INTO credit_accounts (customer_id, branch_id, tier, limit_irr) VALUES ('11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', 'Gold', 200000000);
INSERT INTO funding_control (singleton_id, budget_irr) VALUES (1, 4000000000);
INSERT INTO credit_applications (id, customer_id, state, requested_limit_irr, policy_version, reason_codes)
VALUES ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', 'APPROVED', 200000000, 'sandbox-1', ARRAY['SANDBOX_PROFILE_APPROVED']);
INSERT INTO ledger_accounts (code, name, normal_side) VALUES
    ('CASH', 'Sandbox cash', 'DEBIT'),
    ('CUSTOMER_RECEIVABLE', 'Customer receivable', 'DEBIT'),
    ('MERCHANT_PAYABLE', 'Merchant payable', 'CREDIT'),
    ('CUSTOMER_REFUNDS', 'Sandbox customer refunds', 'DEBIT');

CREATE FUNCTION assert_journal_balanced() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    target_journal UUID;
    debit_total BIGINT;
    credit_total BIGINT;
    line_count BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'journal_entries' THEN
        target_journal := COALESCE(NEW.id, OLD.id);
    ELSE
        target_journal := COALESCE(NEW.journal_id, OLD.journal_id);
    END IF;
    SELECT COALESCE(sum(debit_irr), 0), COALESCE(sum(credit_irr), 0), count(*)
      INTO debit_total, credit_total, line_count
      FROM journal_lines WHERE journal_id = target_journal;
    IF line_count < 2 OR debit_total <> credit_total THEN
        RAISE EXCEPTION 'Journal % is not balanced (debit %, credit %, lines %)', target_journal, debit_total, credit_total, line_count;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER journal_entry_must_balance
AFTER INSERT ON journal_entries
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION assert_journal_balanced();
CREATE CONSTRAINT TRIGGER journal_lines_must_balance
AFTER INSERT OR UPDATE OR DELETE ON journal_lines
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION assert_journal_balanced();

CREATE FUNCTION prevent_posted_ledger_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Posted ledger rows are immutable; use a reversal or adjustment';
END;
$$;
CREATE TRIGGER journal_entries_immutable BEFORE UPDATE OR DELETE ON journal_entries
FOR EACH ROW EXECUTE FUNCTION prevent_posted_ledger_mutation();
CREATE TRIGGER journal_lines_immutable BEFORE UPDATE OR DELETE ON journal_lines
FOR EACH ROW EXECUTE FUNCTION prevent_posted_ledger_mutation();

CREATE TRIGGER audit_events_immutable BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW EXECUTE FUNCTION prevent_posted_ledger_mutation();
