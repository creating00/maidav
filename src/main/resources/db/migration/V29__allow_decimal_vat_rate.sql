ALTER TABLE products
    ALTER COLUMN vat_rate TYPE NUMERIC(5,2)
    USING vat_rate::numeric(5,2);
