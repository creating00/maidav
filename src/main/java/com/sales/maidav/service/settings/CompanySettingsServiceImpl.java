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
        CompanySettings settings = repository.findFirstByOrderByIdAsc().orElseGet(CompanySettings::new);
        applyMoraNoticeDefaults(settings);
        return settings;
    }

    @Override
    public CompanySettings save(CompanySettings settings) {
        normalizeAndValidate(settings);
        return repository.save(settings);
    }

    private void normalizeAndValidate(CompanySettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("La configuracion es obligatoria");
        }
        settings.setMoraNoticeTemplate(trimToNull(settings.getMoraNoticeTemplate()));
        if (settings.getMoraNoticeTemplate() == null) {
            throw new IllegalArgumentException("El mensaje plantilla del aviso de mora es obligatorio");
        }
        if (settings.getMoraNoticeDays() == null) {
            throw new IllegalArgumentException("La cantidad de dias de aviso es obligatoria");
        }
        if (settings.getMoraNoticeDays() < 0 || settings.getMoraNoticeDays() > 365) {
            throw new IllegalArgumentException("La cantidad de dias de aviso debe estar entre 0 y 365");
        }
        if (settings.getMoraNoticeTiming() == null) {
            throw new IllegalArgumentException("El tipo de aviso es obligatorio");
        }
    }

    private void applyMoraNoticeDefaults(CompanySettings settings) {
        if (settings.getMoraNoticeTemplate() == null || settings.getMoraNoticeTemplate().isBlank()) {
            settings.setMoraNoticeTemplate(DEFAULT_MORA_NOTICE_TEMPLATE);
        }
        if (settings.getMoraNoticeDays() == null) {
            settings.setMoraNoticeDays(DEFAULT_MORA_NOTICE_DAYS);
        }
        if (settings.getMoraNoticeTiming() == null) {
            settings.setMoraNoticeTiming(DEFAULT_MORA_NOTICE_TIMING);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
