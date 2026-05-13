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
        settings.setMoraNoticeTemplateBeforeDue(trimToNull(settings.getMoraNoticeTemplateBeforeDue()));
        settings.setMoraNoticeTemplateAfterDue(trimToNull(settings.getMoraNoticeTemplateAfterDue()));
        if (settings.getMoraNoticeTemplateBeforeDue() == null) {
            throw new IllegalArgumentException("La plantilla antes del vencimiento es obligatoria");
        }
        if (settings.getMoraNoticeTemplateAfterDue() == null) {
            throw new IllegalArgumentException("La plantilla despues del vencimiento es obligatoria");
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
        settings.setMoraNoticeTemplate(resolveLegacyTemplate(settings));
    }

    private void applyMoraNoticeDefaults(CompanySettings settings) {
        String legacyTemplate = trimToNull(settings.getMoraNoticeTemplate());
        if (settings.getMoraNoticeTemplateBeforeDue() == null || settings.getMoraNoticeTemplateBeforeDue().isBlank()) {
            settings.setMoraNoticeTemplateBeforeDue(
                    legacyTemplate != null ? legacyTemplate : DEFAULT_MORA_NOTICE_TEMPLATE_BEFORE_DUE
            );
        }
        if (settings.getMoraNoticeTemplateAfterDue() == null || settings.getMoraNoticeTemplateAfterDue().isBlank()) {
            settings.setMoraNoticeTemplateAfterDue(
                    legacyTemplate != null ? legacyTemplate : DEFAULT_MORA_NOTICE_TEMPLATE_AFTER_DUE
            );
        }
        if (settings.getMoraNoticeDays() == null) {
            settings.setMoraNoticeDays(DEFAULT_MORA_NOTICE_DAYS);
        }
        if (settings.getMoraNoticeTiming() == null) {
            settings.setMoraNoticeTiming(DEFAULT_MORA_NOTICE_TIMING);
        }
        settings.setMoraNoticeTemplate(resolveLegacyTemplate(settings));
    }

    private String resolveLegacyTemplate(CompanySettings settings) {
        if (settings == null || settings.getMoraNoticeTiming() == null) {
            return DEFAULT_MORA_NOTICE_TEMPLATE;
        }
        return settings.getMoraNoticeTiming() == com.sales.maidav.model.settings.MoraNotificationTiming.BEFORE_DUE_DATE
                ? settings.getMoraNoticeTemplateBeforeDue()
                : settings.getMoraNoticeTemplateAfterDue();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
