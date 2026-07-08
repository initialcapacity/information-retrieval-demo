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
        List<String> heroQueries
) {
    public record Column(String method, String subtitle, List<Result> results) {
    }

    public record Result(int rank, long productId, String name, String category, String relevance) {
        public boolean relevant() {
            return relevance != null;
        }
    }
}
