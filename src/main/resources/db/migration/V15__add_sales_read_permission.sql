INSERT INTO permissions (name) VALUES
  ('SALES_READ')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('SALES_READ')
WHERE r.name IN ('ADMIN', 'VENDEDOR', 'COBRADOR')
ON CONFLICT DO NOTHING;
