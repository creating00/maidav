UPDATE company_settings
SET mora_notice_template_before_due = 'Hola {CLIENTE}, te recordamos que la cuota {CUOTA} vence en {DIAS_PARA_VENCIMIENTO} dia(s) el {FECHA_VENCIMIENTO} por {IMPORTE}. Queres que te reserve un link de pago o te llamamos para coordinar?'
WHERE mora_notice_template_before_due IS NULL
   OR mora_notice_template_before_due = ''
   OR mora_notice_template_before_due = 'Hola {CLIENTE}, te recordamos que la cuota {CUOTA} vence el {FECHA_VENCIMIENTO} por {IMPORTE}.';
