INSERT INTO roles (name) VALUES
  ('ADMIN'),
  ('USER');

INSERT INTO permissions (name) VALUES
  ('USER_READ'),
  ('USER_CREATE'),
  ('USER_UPDATE'),
  ('USER_DELETE'),
  ('ROLE_READ'),
  ('ROLE_ASSIGN');

-- ADMIN → todos los permisos
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN';

-- USER → permisos básicos
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('USER_READ')
WHERE r.name = 'USER';
