package com.sales.maidav;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
@EnableJpaAuditing
public class MaidavApplication {

	public static void main(String[] args) {
		normalizeDatasourceUrl();
		SpringApplication.run(MaidavApplication.class, args);
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		System.out.println(encoder.encode("admin123"));
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
