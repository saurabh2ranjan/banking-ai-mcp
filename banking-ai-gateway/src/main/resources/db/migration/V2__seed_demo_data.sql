-- ============================================================
-- V2__seed_demo_data.sql
-- Demo data for development/staging environments only.
-- Do NOT apply this migration in production.
-- ============================================================

-- ── Demo Customers ────────────────────────────────────────────────────────────
INSERT INTO customers (
    customer_id, first_name, last_name, date_of_birth, gender,
    email, mobile, nationality, pan_number,
    id_type, id_expiry_date,
    kyc_status, kyc_verified_at,
    risk_category, onboarding_status, onboarding_completed_at,
    address_line1, city, state, postal_code, country,
    employment_type, employer_name, annual_income, income_currency,
    created_by
) VALUES
(
    'CUST-00000001', 'Alice', 'Johnson', '1990-05-15', 'FEMALE',
    'alice.johnson@demo.com', '+447700900001', 'British', 'ABCDE1234F',
    'PAN_CARD', '2030-12-31',
    'VERIFIED', NOW() - INTERVAL '10 days',
    'LOW', 'COMPLETED', NOW() - INTERVAL '9 days',
    '123 High Street', 'London', 'England', 'EC1A 1BB', 'GBR',
    'SALARIED', 'Tech Corp Ltd', 95000.00, 'GBP',
    'SYSTEM'
),
(
    'CUST-00000002', 'Bob', 'Smith', '1985-03-20', 'MALE',
    'bob.smith@demo.com', '+447700900002', 'British', 'XYZPQ5678G',
    'PAN_CARD', '2029-06-30',
    'VERIFIED', NOW() - INTERVAL '5 days',
    'LOW', 'COMPLETED', NOW() - INTERVAL '4 days',
    '45 Park Lane', 'Manchester', 'England', 'M1 1AA', 'GBR',
    'SELF_EMPLOYED', 'Smith Consulting', 120000.00, 'GBP',
    'SYSTEM'
),
(
    'CUST-00000003', 'Charlie', 'Corporate', '1978-11-08', 'MALE',
    'charlie.corp@acme.com', '+12125550001', 'American', 'LMNOP9012H',
    'PASSPORT', '2031-01-15',
    'VERIFIED', NOW() - INTERVAL '30 days',
    'MEDIUM', 'COMPLETED', NOW() - INTERVAL '29 days',
    '1 Wall Street', 'New York', 'New York', '10005', 'USA',
    'BUSINESS_OWNER', 'Acme Industries', 500000.00, 'USD',
    'SYSTEM'
),
(
    'CUST-00000004', 'Diana', 'Pending', '1995-07-22', 'FEMALE',
    'diana.pending@example.com', '+447700900004', 'British', 'RSTUV3456I',
    'PAN_CARD', '2032-03-31',
    'UNDER_REVIEW', NULL,
    'LOW', 'DOCUMENTS_SUBMITTED', NULL,
    '78 Oak Avenue', 'Birmingham', 'England', 'B1 1BB', 'GBR',
    'SALARIED', 'Finance House', 55000.00, 'GBP',
    'SYSTEM'
);

-- ── Demo Accounts ─────────────────────────────────────────────────────────────
INSERT INTO accounts (
    account_id, account_number, customer_id, display_name,
    account_type, status, balance, available_balance, hold_amount,
    currency, daily_debit_limit, single_txn_limit, minimum_balance, interest_rate,
    opened_date, created_by
) VALUES
(
    'ACC-202401-001001-5', 'GB29BANK12345600000001',
    'CUST-00000001', 'Alice Savings',
    'SAVINGS', 'ACTIVE', 85000.00, 85000.00, 0.00,
    'GBP', 10000.00, 5000.00, 500.00, 0.0350,
    CURRENT_DATE - 9, 'SYSTEM'
),
(
    'ACC-202401-002001-3', 'GB29BANK12345600000002',
    'CUST-00000001', 'Alice Current',
    'CURRENT', 'ACTIVE', 12500.00, 12500.00, 0.00,
    'GBP', 50000.00, 25000.00, 1000.00, 0.0000,
    CURRENT_DATE - 8, 'SYSTEM'
),
(
    'ACC-202401-003001-7', 'GB29BANK12345600000003',
    'CUST-00000002', 'Bob Current',
    'CURRENT', 'ACTIVE', 250000.00, 250000.00, 0.00,
    'GBP', 100000.00, 50000.00, 5000.00, 0.0000,
    CURRENT_DATE - 4, 'SYSTEM'
),
(
    'ACC-202401-004001-2', 'US29BANK12345600000004',
    'CUST-00000003', 'Acme Business Account',
    'CURRENT', 'ACTIVE', 1500000.00, 1500000.00, 0.00,
    'USD', 500000.00, 200000.00, 10000.00, 0.0000,
    CURRENT_DATE - 29, 'SYSTEM'
),
(
    'ACC-202401-005001-9', 'GB29BANK12345600000005',
    'CUST-00000002', 'Bob Fixed Deposit',
    'FIXED_DEPOSIT', 'ACTIVE', 50000.00, 50000.00, 0.00,
    'GBP', 0.00, 0.00, 0.00, 0.0650,
    CURRENT_DATE - 3, 'SYSTEM'
);
