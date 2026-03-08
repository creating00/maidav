ALTER TABLE product_price_adjustments
    ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'ALL';
