UPDATE company_settings
SET mora_notice_template = 'Hola {CLIENTE}, le recordamos que la cuota {CUOTA} con vencimiento el {FECHA_VENCIMIENTO} se encuentra pendiente de pago por {IMPORTE}. Productos: {PRODUCTOS}.'
WHERE mora_notice_template = 'Hola {CLIENTE}, le recordamos que la cuota {CUOTA} con vencimiento el {FECHA_VENCIMIENTO} se encuentra pendiente de pago por {IMPORTE}.';

UPDATE company_settings
SET mora_notice_template_before_due = 'Hola {CLIENTE}, te recordamos que la cuota {CUOTA} vence en {DIAS_PARA_VENCIMIENTO} dia(s) el {FECHA_VENCIMIENTO} por {IMPORTE}. Productos: {PRODUCTOS}. Queres que te reserve un link de pago o te llamamos para coordinar?'
WHERE mora_notice_template_before_due = 'Hola {CLIENTE}, te recordamos que la cuota {CUOTA} vence en {DIAS_PARA_VENCIMIENTO} dia(s) el {FECHA_VENCIMIENTO} por {IMPORTE}. Queres que te reserve un link de pago o te llamamos para coordinar?'
   OR mora_notice_template_before_due = 'Hola {CLIENTE}, te recordamos que la cuota {CUOTA} vence el {FECHA_VENCIMIENTO} por {IMPORTE}.';

UPDATE company_settings
SET mora_notice_template_after_due = 'Hola {CLIENTE}, te recordamos que la cuota {CUOTA} vencio el {FECHA_VENCIMIENTO} y sigue pendiente por {IMPORTE}. Productos: {PRODUCTOS}.'
WHERE mora_notice_template_after_due = 'Hola {CLIENTE}, te recordamos que la cuota {CUOTA} vencio el {FECHA_VENCIMIENTO} y sigue pendiente por {IMPORTE}.';
