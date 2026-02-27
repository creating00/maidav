ALTER TABLE sales
    ADD COLUMN first_due_date DATE;

ALTER TABLE credit_accounts
    ADD COLUMN payment_frequency VARCHAR(20),
    ADD COLUMN due_days VARCHAR(50);

ALTER TABLE credit_accounts
    ALTER COLUMN due_day DROP NOT NULL;
