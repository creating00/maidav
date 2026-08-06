package com.sales.maidav.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTextNormalizerTest {

    @Test
    void normalizesAccentsAndCaseForSearches() {
        String term = SearchTextNormalizer.normalize("  CAFE ");

        assertThat(term).isEqualTo("cafe");
        assertThat(SearchTextNormalizer.contains("Café molido", term)).isTrue();
        assertThat(SearchTextNormalizer.contains("CAFÉ MOLIDO", term)).isTrue();
    }
}
