package com.sales.maidav.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.TimeZone;

@Configuration
public class ArgentinaTimeZoneConfig {

    public static final String ARGENTINA_TIME_ZONE = "America/Argentina/Buenos_Aires";
    public static final ZoneId ARGENTINA_ZONE_ID = ZoneId.of(ARGENTINA_TIME_ZONE);

    @PostConstruct
    void configureDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ARGENTINA_ZONE_ID));
    }

    @Bean(name = "auditingDateTimeProvider")
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now(ARGENTINA_ZONE_ID));
    }
}
