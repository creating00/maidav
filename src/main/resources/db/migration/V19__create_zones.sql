CREATE TABLE zones (
    id BIGSERIAL PRIMARY KEY,
    address VARCHAR(255) NOT NULL,
    number VARCHAR(20),
    map_link VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

ALTER TABLE clients
    ADD COLUMN zone_id BIGINT;

ALTER TABLE clients
    ADD CONSTRAINT fk_clients_zone
    FOREIGN KEY (zone_id) REFERENCES zones(id);
