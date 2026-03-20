ALTER TABLE credit_installments
    ADD COLUMN restored_from_installment_id BIGINT;

ALTER TABLE credit_payments
    ADD COLUMN reversal BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reversal_of_payment_id BIGINT,
    ADD COLUMN target_installment_id BIGINT,
    ADD COLUMN reversal_reason VARCHAR(255);
