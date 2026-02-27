ALTER TABLE users
    ADD COLUMN first_name VARCHAR(100),
    ADD COLUMN last_name VARCHAR(100),
    ADD COLUMN phone VARCHAR(30),
    ADD COLUMN address VARCHAR(255),
    ADD COLUMN birth_date DATE,
    ADD COLUMN photo_path VARCHAR(255);
