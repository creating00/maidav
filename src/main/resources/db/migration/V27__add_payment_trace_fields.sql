ALTER TABLE credit_payments
    ADD COLUMN IF NOT EXISTS registered_by VARCHAR(150),
    ADD COLUMN IF NOT EXISTS allocation_summary VARCHAR(255);
