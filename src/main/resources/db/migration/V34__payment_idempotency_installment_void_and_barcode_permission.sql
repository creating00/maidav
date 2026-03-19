ALTER TABLE credit_payments
    ADD COLUMN operation_token VARCHAR(120);

CREATE UNIQUE INDEX IF NOT EXISTS ux_credit_payments_account_operation_token
    ON credit_payments (account_id, operation_token)
    WHERE operation_token IS NOT NULL;

ALTER TABLE credit_installments
    ADD COLUMN voided BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN voided_at TIMESTAMP,
    ADD COLUMN voided_by VARCHAR(150),
    ADD COLUMN void_reason VARCHAR(255);

INSERT INTO permissions (name) VALUES
  ('PRODUCT_BARCODE_PRINT')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('PRODUCT_BARCODE_PRINT')
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

