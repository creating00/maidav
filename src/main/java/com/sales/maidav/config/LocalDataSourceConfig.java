package com.sales.maidav.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Configuration
@Profile("local")
public class LocalDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "maidav.local.datasource")
    public DataSource dataSource() {
        return new HikariDataSource();
    }
}
