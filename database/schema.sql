-- OrtusPro Bank Statement Converter — Database Schema

CREATE TABLE IF NOT EXISTS statements (
    id              SERIAL PRIMARY KEY,
    bank            VARCHAR(50) NOT NULL,
    account_number  VARCHAR(50),
    account_name    VARCHAR(200),
    opening_balance DECIMAL(14,2),
    closing_balance DECIMAL(14,2),
    statement_from  DATE,
    statement_to    DATE,
    uploaded_at     TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS transactions (
    id              SERIAL PRIMARY KEY,
    statement_id    INTEGER REFERENCES statements(id) ON DELETE CASCADE,
    txn_date        DATE NOT NULL,
    description     TEXT NOT NULL,
    debit           DECIMAL(14,2),
    credit          DECIMAL(14,2),
    balance         DECIMAL(14,2),
    is_duplicate    BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_transactions_statement ON transactions(statement_id);
CREATE INDEX idx_transactions_date      ON transactions(txn_date);
