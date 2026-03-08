CREATE TABLE product_price_adjustments (
    id BIGSERIAL PRIMARY KEY,
    adjustment_type VARCHAR(20) NOT NULL,
    percentage NUMERIC(8,4) NOT NULL,
    factor_applied NUMERIC(16,8) NOT NULL,
    provider_id BIGINT,
    products_affected INTEGER NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    undone BOOLEAN NOT NULL DEFAULT FALSE,
    undone_at TIMESTAMP,
    undone_by VARCHAR(120),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_price_adjustments_provider FOREIGN KEY (provider_id) REFERENCES providers(id)
);

CREATE TABLE product_price_adjustment_items (
    id BIGSERIAL PRIMARY KEY,
    adjustment_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    CONSTRAINT fk_adjustment_items_adjustment FOREIGN KEY (adjustment_id) REFERENCES product_price_adjustments(id),
    CONSTRAINT fk_adjustment_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT uq_adjustment_items UNIQUE (adjustment_id, product_id)
);
