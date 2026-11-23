INSERT INTO permissions (name) VALUES ('FINANCE_READ') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name = 'FINANCE_READ'
WHERE r.name = 'ADMIN' ON CONFLICT DO NOTHING;

CREATE INDEX idx_finance_cash_sales_date ON sales (sale_date, id) WHERE payment_type = 'CASH' AND status = 'ACTIVE';
CREATE INDEX idx_finance_credit_payments_date ON credit_payments (paid_at, id);
