INSERT INTO roles (name) VALUES
  ('VENDEDOR'),
  ('COBRADOR')
ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions (name) VALUES
  ('CLIENT_READ'),
  ('CLIENT_CREATE'),
  ('CLIENT_UPDATE'),
  ('CLIENT_DELETE'),
  ('SALES_CREATE'),
  ('ORDER_CREATE'),
  ('DISCOUNT_APPLY'),
  ('SALES_DELETE'),
  ('ARREARS_READ'),
  ('PRODUCT_READ'),
  ('PERMISSION_READ'),
  ('PERMISSION_CREATE'),
  ('PERMISSION_UPDATE'),
  ('PERMISSION_DELETE'),
  ('SETTINGS_READ')
ON CONFLICT (name) DO NOTHING;

-- ADMIN -> todos los permisos
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- VENDEDOR -> permisos base
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
  'CLIENT_READ',
  'CLIENT_CREATE',
  'CLIENT_UPDATE',
  'SALES_CREATE',
  'ORDER_CREATE',
  'DISCOUNT_APPLY',
  'PRODUCT_READ'
)
WHERE r.name = 'VENDEDOR'
ON CONFLICT DO NOTHING;

-- COBRADOR -> permisos base
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
  'CLIENT_READ',
  'ARREARS_READ',
  'PRODUCT_READ'
)
WHERE r.name = 'COBRADOR'
ON CONFLICT DO NOTHING;
