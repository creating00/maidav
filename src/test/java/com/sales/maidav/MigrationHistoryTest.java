package com.sales.maidav;

import org.flywaydb.core.api.Location;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.flywaydb.core.internal.resource.classpath.ClassPathResource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationHistoryTest {
    @Test
    void purchasesMigrationRetainsTheChecksumAlreadyAppliedInTesting() {
        ClassPathResource migration = new ClassPathResource(new Location("classpath:db/migration"),
                "db/migration/V55__create_purchases_module.sql", getClass().getClassLoader(), StandardCharsets.UTF_8);

        assertThat(ChecksumCalculator.calculate(migration)).isEqualTo(323830310);
        assertThat(getClass().getClassLoader().getResource("db/migration/V59__add_pinned_quote_templates.sql"))
                .isNotNull();
    }

    @Test
    void buildContainsExactlyTheSourceMigrationsWithUniqueVersions() throws Exception {
        List<String> source = migrations(Path.of("src/main/resources/db/migration"));
        List<String> compiled = migrations(Path.of("target/classes/db/migration"));
        assertThat(compiled).as("Run mvn clean test if migrations from another branch remain in target")
                .containsExactlyInAnyOrderElementsOf(source);
        assertThat(compiled.stream().map(name -> name.substring(0, name.indexOf("__"))).toList())
                .doesNotHaveDuplicates();
    }

    private List<String> migrations(Path directory) throws Exception {
        try (var paths = Files.list(directory)) {
            return paths.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("V") && name.endsWith(".sql")).toList();
        }
    }
}
