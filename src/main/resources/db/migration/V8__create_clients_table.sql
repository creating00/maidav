CREATE TABLE clients (
    id BIGSERIAL PRIMARY KEY,
    national_id VARCHAR(20) NOT NULL UNIQUE, -- cédula
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(30),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);
