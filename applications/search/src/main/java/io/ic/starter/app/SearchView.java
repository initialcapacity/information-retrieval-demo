package io.ic.starter.app;

import java.util.List;

/**
 * View model for the side-by-side search comparison.
 */
public record SearchView(
        String query,
        boolean submitted,
        boolean fixtureQuery,
        String embeddingSource,
        String error,
        List<Column> columns,
        List<String> heroQueries,
        int pickerTotal,
        List<LeanGroup> pickerGroups
) {
    public record Column(String method, String subtitle, List<Result> results) {
    }

    public record Result(int rank, long chunkId, String name, String category, String relevance) {
        public boolean relevant() {
            return relevance != null;
        }
    }

    /** A lean bucket in the labelled-query picker (keyword / semantic / mixed). */
    public record LeanGroup(String lean, String label, String desc, int count, List<PickerQuery> queries) {
    }

    public record PickerQuery(String query, String category, int exact) {
    }
}
