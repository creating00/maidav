CREATE TABLE providers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(150),
    address VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    product_code VARCHAR(50) NOT NULL,
    barcode VARCHAR(50),
    description VARCHAR(200) NOT NULL,
    cost NUMERIC(12,2) NOT NULL,
    price_wholesale_net NUMERIC(12,2) NOT NULL,
    price_retail_net NUMERIC(12,2) NOT NULL,
    vat_rate INTEGER NOT NULL,
    price_wholesale NUMERIC(12,2) NOT NULL,
    price_retail NUMERIC(12,2) NOT NULL,
    stock_available INTEGER NOT NULL,
    stock_min INTEGER NOT NULL,
    stock_max INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_products_provider FOREIGN KEY (provider_id) REFERENCES providers(id),
    CONSTRAINT uq_products_provider_code UNIQUE (provider_id, product_code)
);
