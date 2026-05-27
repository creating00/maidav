package com.sales.maidav;

import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class MaidavApplication {

	private static final String ARGENTINA_TIME_ZONE = "America/Argentina/Buenos_Aires";

	public static void main(String[] args) {
		normalizeJvmTimeZone();
		normalizeDatasourceUrl();
		SpringApplication.run(MaidavApplication.class, args);
	}

	private static void normalizeJvmTimeZone() {
		System.setProperty("user.timezone", ARGENTINA_TIME_ZONE);
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(ARGENTINA_TIME_ZONE)));
	}

	private static void normalizeDatasourceUrl() {
		String rawUrl = System.getenv("SPRING_DATASOURCE_URL");
		if (rawUrl == null || rawUrl.isBlank()) {
			return;
		}
		if (rawUrl.startsWith("jdbc:")) {
			return;
		}
		if (rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://")) {
			String jdbcUrl = rawUrl
					.replaceFirst("^postgresql://", "jdbc:postgresql://")
					.replaceFirst("^postgres://", "jdbc:postgresql://");
			System.setProperty("SPRING_DATASOURCE_URL", jdbcUrl);
		}
	}

}
