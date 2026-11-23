CREATE TABLE purchases (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    purchase_date DATE NOT NULL,
    provider_id BIGINT REFERENCES providers(id),
    provider_name VARCHAR(150),
    registered_by_id BIGINT NOT NULL REFERENCES users(id),
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('CASH', 'BANK', 'TRANSFER', 'DEBIT')),
    notes VARCHAR(2000),
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount > 0)
);

CREATE TABLE purchase_items (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    purchase_id BIGINT NOT NULL REFERENCES purchases(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    product_code VARCHAR(50) NOT NULL,
    product_description VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price > 0),
    line_total NUMERIC(12,2) NOT NULL CHECK (line_total > 0)
);

CREATE TABLE purchase_idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    user_id BIGINT NOT NULL REFERENCES users(id),
    idempotency_key VARCHAR(36) NOT NULL,
    purchase_id BIGINT REFERENCES purchases(id),
    UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_purchases_date ON purchases (purchase_date DESC, id DESC);
CREATE INDEX idx_purchases_provider_date ON purchases (provider_id, purchase_date DESC);
CREATE INDEX idx_purchase_items_purchase ON purchase_items (purchase_id, id);
CREATE INDEX idx_purchase_items_product ON purchase_items (product_id, purchase_id);

INSERT INTO permissions (name) VALUES ('PURCHASE_READ'), ('PURCHASE_CREATE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r
JOIN permissions p ON p.name IN ('PURCHASE_READ', 'PURCHASE_CREATE')
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
