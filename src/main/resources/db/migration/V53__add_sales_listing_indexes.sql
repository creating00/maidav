CREATE INDEX idx_sales_sale_date_id ON sales (sale_date DESC, id DESC);

CREATE INDEX idx_sales_seller_sale_date_id ON sales (seller_id, sale_date DESC, id DESC);
