ALTER TABLE credit_installments
    ADD COLUMN paid_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

UPDATE credit_installments
SET paid_amount = amount
WHERE status = 'PAID';
