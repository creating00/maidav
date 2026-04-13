ALTER TABLE quotes
    ADD COLUMN financing_base_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;
