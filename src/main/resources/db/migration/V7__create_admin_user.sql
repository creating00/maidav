INSERT INTO users (email, password, enabled)
VALUES (
  'admin@maidav.com',
  '$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
  true
)
ON CONFLICT (email) DO NOTHING;
