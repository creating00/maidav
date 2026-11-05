CREATE TABLE sale_idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(36) NOT NULL,
    sale_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_sale_idempotency_keys_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_sale_idempotency_keys_sale FOREIGN KEY (sale_id) REFERENCES sales(id),
    CONSTRAINT uq_sale_idempotency_keys_user_key UNIQUE (user_id, idempotency_key)
);
