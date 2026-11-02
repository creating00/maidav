CREATE TABLE credit_payment_allocations (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES credit_accounts(id),
    payment_id BIGINT NOT NULL REFERENCES credit_payments(id),
    installment_id BIGINT NOT NULL REFERENCES credit_installments(id),
    collected_amount NUMERIC(12,2) NOT NULL,
    impact_amount NUMERIC(12,2) NOT NULL,
    verified BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT ux_credit_payment_allocations_payment_installment UNIQUE (payment_id, installment_id)
);

CREATE INDEX ix_credit_payment_allocations_account ON credit_payment_allocations(account_id);
CREATE INDEX ix_credit_payment_allocations_installment ON credit_payment_allocations(installment_id);

-- No backfill: los importes por cuota anteriores no se almacenaron de forma verificable.
-- BANK permanece como valor legado ambiguo entre débito y transferencia.
