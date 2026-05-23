package com.sales.maidav.service.settings;

import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.model.settings.MoraNotificationTiming;

public interface CompanySettingsService {
    String DEFAULT_MORA_NOTICE_TEMPLATE =
            "Hola {CLIENTE}, le recordamos que la cuota {CUOTA} con vencimiento el {FECHA_VENCIMIENTO} se encuentra pendiente de pago por {IMPORTE}. Productos: {PRODUCTOS}.";
    String DEFAULT_MORA_NOTICE_TEMPLATE_BEFORE_DUE =
            "Hola {CLIENTE}, te recordamos que la cuota {CUOTA} vence en {DIAS_PARA_VENCIMIENTO} dia(s) el {FECHA_VENCIMIENTO} por {IMPORTE}. Productos: {PRODUCTOS}. Queres que te reserve un link de pago o te llamamos para coordinar?";
    String DEFAULT_MORA_NOTICE_TEMPLATE_AFTER_DUE =
            "Hola {CLIENTE}, te recordamos que la cuota {CUOTA} vencio el {FECHA_VENCIMIENTO} y sigue pendiente por {IMPORTE}. Productos: {PRODUCTOS}.";
    int DEFAULT_MORA_NOTICE_DAYS = 2;
    MoraNotificationTiming DEFAULT_MORA_NOTICE_TIMING = MoraNotificationTiming.AFTER_DUE_DATE;

    CompanySettings getSettings();
    CompanySettings save(CompanySettings settings);
}
