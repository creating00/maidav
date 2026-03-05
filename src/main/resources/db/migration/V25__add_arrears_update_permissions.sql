INSERT INTO permissions (name) VALUES
  ('ARREARS_UPDATE_PAYMENT'),
  ('ARREARS_UPDATE_DUE_DATE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
  'ARREARS_UPDATE_PAYMENT',
  'ARREARS_UPDATE_DUE_DATE'
)
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
