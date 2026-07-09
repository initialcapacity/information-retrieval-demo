package io.ic.starter.search;

/**
 * Shared query-text normalization. Cache-key lookups across the apps depend on
 * this being identical everywhere, so it lives in one place.
 */
public class QueryText {
    private QueryText() {
    }

    public static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
