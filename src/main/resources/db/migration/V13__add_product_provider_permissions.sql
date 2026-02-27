INSERT INTO permissions (name) VALUES
  ('PRODUCT_READ'),
  ('PRODUCT_CREATE'),
  ('PRODUCT_UPDATE'),
  ('PRODUCT_DELETE'),
  ('PROVIDER_READ'),
  ('PROVIDER_CREATE'),
  ('PROVIDER_UPDATE'),
  ('PROVIDER_DELETE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
  'PRODUCT_READ',
  'PRODUCT_CREATE',
  'PRODUCT_UPDATE',
  'PRODUCT_DELETE',
  'PROVIDER_READ',
  'PROVIDER_CREATE',
  'PROVIDER_UPDATE',
  'PROVIDER_DELETE'
)
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('PRODUCT_READ', 'PROVIDER_READ')
WHERE r.name = 'VENDEDOR'
ON CONFLICT DO NOTHING;
