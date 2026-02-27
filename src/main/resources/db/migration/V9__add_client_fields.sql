ALTER TABLE clients
    ADD COLUMN address VARCHAR(255),
    ADD COLUMN neighborhood VARCHAR(100),
    ADD COLUMN email VARCHAR(150),
    ADD COLUMN birth_date DATE,
    ADD COLUMN observations TEXT,
    ADD COLUMN seller_id BIGINT,
    ADD COLUMN recommended_by_id BIGINT;

ALTER TABLE clients
    ADD CONSTRAINT fk_clients_seller
    FOREIGN KEY (seller_id) REFERENCES users(id);

ALTER TABLE clients
    ADD CONSTRAINT fk_clients_recommended_by
    FOREIGN KEY (recommended_by_id) REFERENCES clients(id);
