-- ============================================================
-- V1__initial_schema.sql
-- Flyway migration — initial banking platform schema
-- Apply in production instead of JPA ddl-auto
-- ============================================================

-- ── Extensions ────────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Customers ─────────────────────────────────────────────────────────────────
CREATE TABLE customers (
    customer_id         VARCHAR(20)     PRIMARY KEY,
    first_name          VARCHAR(50)     NOT NULL,
    last_name           VARCHAR(50)     NOT NULL,
    date_of_birth       DATE            NOT NULL,
    gender              VARCHAR(20)     NOT NULL,
    email               VARCHAR(100)    NOT NULL UNIQUE,
    mobile              VARCHAR(15)     NOT NULL UNIQUE,
    nationality         VARCHAR(50),
    pan_number          VARCHAR(10)     UNIQUE,
    passport_number     VARCHAR(20),
    national_id         VARCHAR(30),
    id_type             VARCHAR(20),
    id_expiry_date      DATE,
    kyc_status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    kyc_verified_at     TIMESTAMP,
    kyc_rejection_reason VARCHAR(500),
    risk_category       VARCHAR(20)     NOT NULL DEFAULT 'LOW',
    onboarding_status   VARCHAR(30)     NOT NULL DEFAULT 'INITIATED',
    onboarding_completed_at TIMESTAMP,
    address_line1       VARCHAR(200),
    address_line2       VARCHAR(200),
    city                VARCHAR(100),
    state               VARCHAR(100),
    postal_code         VARCHAR(20),
    country             CHAR(3),
    employment_type     VARCHAR(30),
    employer_name       VARCHAR(100),
    annual_income       NUMERIC(19,2),
    income_currency     CHAR(3),
    -- Audit
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    version             BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_customer_email           ON customers(email);
CREATE INDEX idx_customer_mobile          ON customers(mobile);
CREATE INDEX idx_customer_pan             ON customers(pan_number);
CREATE INDEX idx_customer_status          ON customers(onboarding_status);
CREATE INDEX idx_customer_kyc_status      ON customers(kyc_status);

-- ── Customer Documents ────────────────────────────────────────────────────────
CREATE TABLE customer_documents (
    document_id         UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id         VARCHAR(20)     NOT NULL REFERENCES customers(customer_id),
    document_type       VARCHAR(30)     NOT NULL,
    document_number     VARCHAR(50),
    document_url        VARCHAR(500),
    expiry_date         DATE,
    verification_status VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    rejection_reason    VARCHAR(300),
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    version             BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_doc_customer ON customer_documents(customer_id);

-- ── Accounts ──────────────────────────────────────────────────────────────────
CREATE TABLE accounts (
    account_id              VARCHAR(30)     PRIMARY KEY,
    account_number          VARCHAR(34)     NOT NULL UNIQUE,
    customer_id             VARCHAR(20)     NOT NULL,
    display_name            VARCHAR(100),
    account_type            VARCHAR(20)     NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    balance                 NUMERIC(19,2)   NOT NULL DEFAULT 0.00,
    available_balance       NUMERIC(19,2)   NOT NULL DEFAULT 0.00,
    hold_amount             NUMERIC(19,2)   NOT NULL DEFAULT 0.00,
    currency                CHAR(3)         NOT NULL,
    daily_debit_limit       NUMERIC(19,2),
    single_txn_limit        NUMERIC(19,2),
    minimum_balance         NUMERIC(19,2)   NOT NULL DEFAULT 0.00,
    interest_rate           NUMERIC(5,4)    NOT NULL DEFAULT 0.0000,
    opened_date             DATE,
    maturity_date           DATE,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100),
    version                 BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_account_customer    ON accounts(customer_id);
CREATE INDEX idx_account_status      ON accounts(status);
CREATE INDEX idx_account_type_status ON accounts(account_type, status);

-- ── Payments ──────────────────────────────────────────────────────────────────
CREATE TABLE payments (
    payment_id              VARCHAR(36)     PRIMARY KEY,
    reference_number        VARCHAR(50)     NOT NULL UNIQUE,
    customer_id             VARCHAR(20)     NOT NULL,
    source_account_id       VARCHAR(30)     NOT NULL,
    destination_account_id  VARCHAR(30)     NOT NULL,
    amount                  NUMERIC(19,2)   NOT NULL,
    currency                CHAR(3)         NOT NULL,
    payment_type            VARCHAR(20)     NOT NULL,
    status                  VARCHAR(30)     NOT NULL DEFAULT 'INITIATED',
    description             VARCHAR(500),
    initiated_at            TIMESTAMP       NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMP,
    failure_reason          VARCHAR(500),
    fraud_score             NUMERIC(4,3),
    fraud_risk_level        VARCHAR(10),
    ip_address              VARCHAR(45),
    device_fingerprint      VARCHAR(200),
    reversal_payment_id     VARCHAR(36),
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100),
    version                 BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_payment_source       ON payments(source_account_id);
CREATE INDEX idx_payment_dest         ON payments(destination_account_id);
CREATE INDEX idx_payment_status       ON payments(status);
CREATE INDEX idx_payment_customer     ON payments(customer_id);
CREATE INDEX idx_payment_initiated_at ON payments(initiated_at DESC);
CREATE INDEX idx_payment_reference    ON payments(reference_number);

-- ── Updated_at auto-update trigger ───────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_customers_updated_at    BEFORE UPDATE ON customers    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_accounts_updated_at     BEFORE UPDATE ON accounts     FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_payments_updated_at     BEFORE UPDATE ON payments     FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_documents_updated_at    BEFORE UPDATE ON customer_documents FOR EACH ROW EXECUTE FUNCTION update_updated_at();
