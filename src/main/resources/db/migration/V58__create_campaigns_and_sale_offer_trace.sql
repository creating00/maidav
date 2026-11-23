CREATE TABLE campaigns (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(2000),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL CHECK (end_date >= start_date),
    item_count INTEGER NOT NULL CHECK (item_count BETWEEN 1 AND 100),
    registered_by_id BIGINT NOT NULL REFERENCES users(id),
    cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    cancelled_at TIMESTAMP,
    cancelled_by_id BIGINT REFERENCES users(id)
);
CREATE TABLE campaign_items (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    campaign_id BIGINT NOT NULL REFERENCES campaigns(id),
    product_id BIGINT REFERENCES products(id) ON DELETE SET NULL,
    product_code VARCHAR(50) NOT NULL,
    product_description VARCHAR(200) NOT NULL,
    image_path VARCHAR(300),
    normal_price NUMERIC(12,2) NOT NULL CHECK (normal_price > 0),
    offer_price NUMERIC(12,2) NOT NULL CHECK (offer_price > 0 AND offer_price < normal_price),
    discount_mode VARCHAR(20) NOT NULL CHECK (discount_mode IN ('FIXED_PRICE', 'PERCENTAGE')),
    discount_value NUMERIC(12,2) NOT NULL CHECK (discount_value > 0),
    UNIQUE (campaign_id, product_id)
);
CREATE TABLE campaign_idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    user_id BIGINT NOT NULL REFERENCES users(id),
    idempotency_key VARCHAR(36) NOT NULL,
    campaign_id BIGINT REFERENCES campaigns(id),
    UNIQUE (user_id, idempotency_key)
);
CREATE INDEX idx_campaigns_dates ON campaigns (start_date, end_date, id) WHERE cancelled = FALSE;
CREATE INDEX idx_campaign_items_campaign ON campaign_items (campaign_id, id);
ALTER TABLE sale_items
    ADD COLUMN campaign_item_id BIGINT REFERENCES campaign_items(id),
    ADD COLUMN campaign_name VARCHAR(150),
    ADD COLUMN campaign_normal_price NUMERIC(12,2);
INSERT INTO permissions (name) VALUES ('CAMPAIGN_READ'), ('CAMPAIGN_CREATE'), ('CAMPAIGN_UPDATE') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name = 'CAMPAIGN_READ' ON CONFLICT DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN ('CAMPAIGN_CREATE', 'CAMPAIGN_UPDATE')
WHERE r.name = 'ADMIN' ON CONFLICT DO NOTHING;
