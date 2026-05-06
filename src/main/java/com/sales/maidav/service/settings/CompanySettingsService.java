package com.sales.maidav.service.settings;

import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.model.settings.MoraNotificationTiming;

public interface CompanySettingsService {
    String DEFAULT_MORA_NOTICE_TEMPLATE =
            "Hola {CLIENTE}, le recordamos que la cuota {CUOTA} con vencimiento el {FECHA_VENCIMIENTO} se encuentra pendiente de pago por {IMPORTE}.";
    int DEFAULT_MORA_NOTICE_DAYS = 2;
    MoraNotificationTiming DEFAULT_MORA_NOTICE_TIMING = MoraNotificationTiming.AFTER_DUE_DATE;

    CompanySettings getSettings();
    CompanySettings save(CompanySettings settings);
}
