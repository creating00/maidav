package com.sales.maidav.service.settings;

import com.sales.maidav.model.settings.CompanySettings;

public interface CompanySettingsService {
    CompanySettings getSettings();
    CompanySettings save(CompanySettings settings);
}
