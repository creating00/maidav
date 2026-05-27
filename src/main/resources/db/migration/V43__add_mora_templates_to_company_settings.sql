ALTER TABLE company_settings
    ADD COLUMN IF NOT EXISTS mora_notice_template_before_due VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS mora_notice_template_after_due VARCHAR(2000);

UPDATE company_settings
SET mora_notice_template_before_due = COALESCE(
        mora_notice_template_before_due,
        mora_notice_template,
        'Hola {CLIENTE}, te recordamos que la cuota {CUOTA} vence el {FECHA_VENCIMIENTO} por {IMPORTE}.'
    ),
    mora_notice_template_after_due = COALESCE(
        mora_notice_template_after_due,
        mora_notice_template,
        'Hola {CLIENTE}, te recordamos que la cuota {CUOTA} vencio el {FECHA_VENCIMIENTO} y sigue pendiente por {IMPORTE}.'
    );
