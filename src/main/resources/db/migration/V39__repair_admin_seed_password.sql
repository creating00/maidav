INSERT INTO users (email, password, enabled)
VALUES (
  'admin@maidav.com',
  '$2a$10$tMIrCWi4KGwh2KhAI3OiVed.lqHnPHxXf22CbutEu/I5JzWQzmakS',
  true
)
ON CONFLICT (email) DO NOTHING;

UPDATE users
SET password = '$2a$10$tMIrCWi4KGwh2KhAI3OiVed.lqHnPHxXf22CbutEu/I5JzWQzmakS',
    enabled = true
WHERE email = 'admin@maidav.com'
  AND password IN (
    '$2a$10$REEMPLAZAR_POR_HASH_BCRYPT',
    '$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
  );

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ADMIN'
WHERE u.email = 'admin@maidav.com'
ON CONFLICT DO NOTHING;
