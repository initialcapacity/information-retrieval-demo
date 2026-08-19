package io.ic.starter.app;

import java.util.List;
import java.util.Locale;

/**
 * View model for the side-by-side search comparison.
 */
public record SearchView(
        String query,
        boolean submitted,
        boolean fixtureQuery,
        String embeddingSource,
        String errorTitle,
        String error,
        List<Column> columns,
        Timing timing,
        List<String> heroQueries,
        int pickerTotal,
        List<LeanGroup> pickerGroups
) {
    public record Column(String method, String subtitle, double millis, List<Result> results) {
        public String latency() {
            return format(millis);
        }
    }

    public record Result(int rank, long chunkId, String name, String category, String relevance) {
        public boolean relevant() {
            return relevance != null;
        }
    }

    /**
     * Server-side timings for one request, with the retrieval depths they cover so
     * the on-screen note can say what each number includes. {@code embeddingMillis}
     * is null when the query never reached the embedder.
     */
    public record Timing(Double embeddingMillis, double totalMillis, int displayK, int candidateDepth) {
        public String embedding() {
            return format(embeddingMillis);
        }

        public String total() {
            return format(totalMillis);
        }
    }

    /** A lean bucket in the labelled-query picker (keyword / semantic / mixed). */
    public record LeanGroup(String lean, String label, String desc, int count, List<PickerQuery> queries) {
    }

    public record PickerQuery(String query, String category, int exact) {
    }

    /**
     * Locale-independent, so the decimal separator stays a period on any host:
     * 0.42 ms, 4.2 ms, 412 ms.
     */
    static String format(Double millis) {
        if (millis == null) {
            return null;
        }
        if (millis < 1) {
            return String.format(Locale.ROOT, "%.2f ms", millis);
        }
        if (millis < 10) {
            return String.format(Locale.ROOT, "%.1f ms", millis);
        }
        return String.format(Locale.ROOT, "%.0f ms", millis);
    }
}
