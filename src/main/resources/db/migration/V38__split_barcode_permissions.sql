INSERT INTO permissions (name) VALUES
  ('PRODUCT_BARCODE_LABEL'),
  ('PRODUCT_BARCODE_SHEET')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('PRODUCT_BARCODE_LABEL', 'PRODUCT_BARCODE_SHEET')
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
