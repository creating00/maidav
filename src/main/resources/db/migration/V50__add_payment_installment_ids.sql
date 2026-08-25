ALTER TABLE credit_payments
    ADD COLUMN IF NOT EXISTS installment_ids VARCHAR(500);
