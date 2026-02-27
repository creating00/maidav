INSERT INTO permissions (name) VALUES
  ('ZONE_READ'),
  ('ZONE_CREATE'),
  ('ZONE_UPDATE'),
  ('ZONE_DELETE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
  'ZONE_READ',
  'ZONE_CREATE',
  'ZONE_UPDATE',
  'ZONE_DELETE'
)
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
