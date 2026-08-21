ALTER TABLE sales
    ADD COLUMN payment_collection_method VARCHAR(20);

ALTER TABLE credit_installments
    ADD COLUMN cash_amount NUMERIC(12,2);
