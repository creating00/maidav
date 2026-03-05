ALTER TABLE company_settings
    ADD COLUMN calc_recargo NUMERIC(8,4),
    ADD COLUMN calc_mult_contado NUMERIC(8,4),
    ADD COLUMN calc_mult_debito NUMERIC(8,4),
    ADD COLUMN calc_dias INTEGER,
    ADD COLUMN calc_int_dia NUMERIC(8,4),
    ADD COLUMN calc_semanas INTEGER,
    ADD COLUMN calc_int_sem NUMERIC(8,4),
    ADD COLUMN calc_meses_corto INTEGER,
    ADD COLUMN calc_int_mes_corto NUMERIC(8,4),
    ADD COLUMN calc_meses_largo INTEGER,
    ADD COLUMN calc_int_mes_largo NUMERIC(8,4);
