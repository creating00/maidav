ALTER TABLE credit_payments
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20);

UPDATE credit_payments
SET payment_method = 'BANK'
WHERE payment_method IS NULL;
