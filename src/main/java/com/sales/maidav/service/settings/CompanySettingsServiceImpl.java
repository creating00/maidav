package com.sales.maidav.service.settings;

import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.repository.settings.CompanySettingsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanySettingsServiceImpl implements CompanySettingsService {

    private final CompanySettingsRepository repository;

    @Override
    public CompanySettings getSettings() {
        return repository.findFirstByOrderByIdAsc().orElseGet(CompanySettings::new);
    }

    @Override
    public CompanySettings save(CompanySettings settings) {
        return repository.save(settings);
    }
}
