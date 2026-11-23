CREATE TABLE expenses (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    expense_date DATE NOT NULL,
    category VARCHAR(30) NOT NULL CHECK (category IN ('RENT', 'SERVICES', 'SALARIES', 'FUEL', 'REPAIRS', 'TAXES', 'ADMINISTRATIVE', 'OTHER')),
    description VARCHAR(1000) NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('CASH', 'BANK', 'TRANSFER', 'DEBIT')),
    registered_by_id BIGINT NOT NULL REFERENCES users(id),
    receipt_name VARCHAR(200),
    receipt_content_type VARCHAR(30),
    receipt_size INTEGER CHECK (receipt_size > 0 AND receipt_size <= 5242880)
);

-- Keep binary data out of history queries. Access is only through the authorized download route.
CREATE TABLE expense_receipts (
    expense_id BIGINT PRIMARY KEY REFERENCES expenses(id),
    content BYTEA NOT NULL CHECK (octet_length(content) > 0 AND octet_length(content) <= 5242880)
);

CREATE TABLE expense_idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    user_id BIGINT NOT NULL REFERENCES users(id),
    idempotency_key VARCHAR(36) NOT NULL,
    expense_id BIGINT REFERENCES expenses(id),
    UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_expenses_date ON expenses (expense_date DESC, id DESC);
CREATE INDEX idx_expenses_category_date ON expenses (category, expense_date DESC, id DESC);
CREATE INDEX idx_expenses_amount ON expenses (amount);

INSERT INTO permissions (name) VALUES ('EXPENSE_READ'), ('EXPENSE_CREATE') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r
JOIN permissions p ON p.name IN ('EXPENSE_READ', 'EXPENSE_CREATE')
WHERE r.name = 'ADMIN' ON CONFLICT DO NOTHING;
