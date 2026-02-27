INSERT INTO users (email, password, enabled)
VALUES (
  'admin@maidav.com',
  '$2a$10$REEMPLAZAR_POR_HASH_BCRYPT',
  true
);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.email = 'admin@maidav.com'
AND r.name = 'ADMIN';
