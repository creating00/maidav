CREATE TABLE quotes (
  id BIGSERIAL PRIMARY KEY,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP,
  seller_id BIGINT NOT NULL,
  quote_number VARCHAR(20) UNIQUE,
  price_mode VARCHAR(20) NOT NULL,
  item_count INTEGER NOT NULL,
  product_summary VARCHAR(255) NOT NULL,
  pricing_base_amount NUMERIC(12, 2) NOT NULL,
  cash_amount NUMERIC(12, 2) NOT NULL,
  debit_amount NUMERIC(12, 2) NOT NULL,
  total_amount NUMERIC(12, 2) NOT NULL,
  CONSTRAINT fk_quotes_seller FOREIGN KEY (seller_id) REFERENCES users(id)
);

CREATE TABLE quote_items (
  id BIGSERIAL PRIMARY KEY,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP,
  quote_id BIGINT NOT NULL,
  product_id BIGINT,
  display_order INTEGER NOT NULL,
  product_code VARCHAR(50) NOT NULL,
  product_description VARCHAR(200) NOT NULL,
  product_image_path VARCHAR(300),
  quantity INTEGER NOT NULL,
  unit_price NUMERIC(12, 2) NOT NULL,
  line_total NUMERIC(12, 2) NOT NULL,
  CONSTRAINT fk_quote_items_quote FOREIGN KEY (quote_id) REFERENCES quotes(id) ON DELETE CASCADE,
  CONSTRAINT fk_quote_items_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL
);

CREATE TABLE quote_plan_options (
  id BIGSERIAL PRIMARY KEY,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP,
  quote_id BIGINT NOT NULL,
  display_order INTEGER NOT NULL,
  plan_type VARCHAR(30) NOT NULL,
  title VARCHAR(120) NOT NULL,
  promo_text VARCHAR(160),
  installment_count INTEGER NOT NULL,
  fee_amount NUMERIC(12, 2) NOT NULL,
  cash_fee_amount NUMERIC(12, 2),
  CONSTRAINT fk_quote_plans_quote FOREIGN KEY (quote_id) REFERENCES quotes(id) ON DELETE CASCADE
);

CREATE INDEX idx_quotes_seller_created_at ON quotes (seller_id, created_at DESC, id DESC);
CREATE INDEX idx_quote_items_quote_order ON quote_items (quote_id, display_order, id);
CREATE INDEX idx_quote_plan_options_quote_order ON quote_plan_options (quote_id, display_order, id);

INSERT INTO permissions (name) VALUES
  ('QUOTE_READ'),
  ('QUOTE_CREATE'),
  ('QUOTE_DELETE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('QUOTE_READ', 'QUOTE_CREATE', 'QUOTE_DELETE')
WHERE r.name IN ('ADMIN', 'VENDEDOR')
ON CONFLICT DO NOTHING;
