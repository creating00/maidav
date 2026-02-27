package com.sales.maidav.repository.settings;

import com.sales.maidav.model.settings.CompanySettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanySettingsRepository extends JpaRepository<CompanySettings, Long> {
    Optional<CompanySettings> findFirstByOrderByIdAsc();
}
