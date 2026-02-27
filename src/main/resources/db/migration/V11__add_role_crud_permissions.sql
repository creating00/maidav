INSERT INTO permissions (name) VALUES
  ('ROLE_CREATE'),
  ('ROLE_UPDATE'),
  ('ROLE_DELETE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('ROLE_CREATE', 'ROLE_UPDATE', 'ROLE_DELETE')
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
