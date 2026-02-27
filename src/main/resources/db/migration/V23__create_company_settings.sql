CREATE TABLE company_settings (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150),
    address VARCHAR(255),
    phone VARCHAR(30),
    email VARCHAR(150),
    logo_path VARCHAR(300),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);
