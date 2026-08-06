package com.sales.maidav.util;

import java.text.Normalizer;
import java.util.Locale;

/** Normalizes text used in user-facing searches. */
public final class SearchTextNormalizer {

    private SearchTextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    public static boolean contains(String value, String normalizedTerm) {
        return value != null && normalize(value).contains(normalizedTerm);
    }
}
