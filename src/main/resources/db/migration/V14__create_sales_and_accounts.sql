CREATE TABLE sales (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    payment_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    discount_amount NUMERIC(12,2) NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    sale_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_sales_client FOREIGN KEY (client_id) REFERENCES clients(id),
    CONSTRAINT fk_sales_seller FOREIGN KEY (seller_id) REFERENCES users(id)
);

CREATE TABLE sale_items (
    id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    line_total NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_sale_items_sale FOREIGN KEY (sale_id) REFERENCES sales(id),
    CONSTRAINT fk_sale_items_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE credit_accounts (
    id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL UNIQUE,
    client_id BIGINT NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    balance NUMERIC(12,2) NOT NULL,
    weeks_count INTEGER NOT NULL,
    due_day INTEGER NOT NULL,
    start_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_credit_accounts_sale FOREIGN KEY (sale_id) REFERENCES sales(id),
    CONSTRAINT fk_credit_accounts_client FOREIGN KEY (client_id) REFERENCES clients(id)
);

CREATE TABLE credit_installments (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    installment_number INTEGER NOT NULL,
    due_date DATE NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    paid_at DATE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_credit_installments_account FOREIGN KEY (account_id) REFERENCES credit_accounts(id)
);

CREATE TABLE credit_payments (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    paid_at DATE NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_credit_payments_account FOREIGN KEY (account_id) REFERENCES credit_accounts(id)
);
