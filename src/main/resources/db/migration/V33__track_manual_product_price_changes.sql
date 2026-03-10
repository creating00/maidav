ALTER TABLE product_price_adjustments
    ALTER COLUMN percentage DROP NOT NULL;

ALTER TABLE product_price_adjustments
    ALTER COLUMN factor_applied DROP NOT NULL;

ALTER TABLE product_price_adjustment_items
    ADD COLUMN previous_cost NUMERIC(12,2),
    ADD COLUMN new_cost NUMERIC(12,2),
    ADD COLUMN previous_price_wholesale_net NUMERIC(12,2),
    ADD COLUMN new_price_wholesale_net NUMERIC(12,2),
    ADD COLUMN previous_price_retail_net NUMERIC(12,2),
    ADD COLUMN new_price_retail_net NUMERIC(12,2);
