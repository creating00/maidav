ALTER TABLE credit_payments
    ADD COLUMN impact_amount NUMERIC(12,2);

UPDATE credit_payments
SET impact_amount = amount
WHERE impact_amount IS NULL;

ALTER TABLE credit_payments
    ALTER COLUMN impact_amount SET NOT NULL;
