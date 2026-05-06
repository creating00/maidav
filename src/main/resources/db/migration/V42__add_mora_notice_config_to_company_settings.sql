ALTER TABLE company_settings
    ADD COLUMN mora_notice_template VARCHAR(2000),
    ADD COLUMN mora_notice_days INTEGER,
    ADD COLUMN mora_notice_timing VARCHAR(30);
