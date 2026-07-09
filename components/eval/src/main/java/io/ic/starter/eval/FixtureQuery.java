package io.ic.starter.eval;

public record FixtureQuery(
        long queryId,
        String query,
        String queryClass,
        int nExact,
        String lean
) {
}
