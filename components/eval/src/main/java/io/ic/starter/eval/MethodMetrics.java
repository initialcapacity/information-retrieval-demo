package io.ic.starter.eval;

/**
 * Macro-averaged metrics for one retrieval method over a query set.
 */
public record MethodMetrics(
        String method,
        int queryCount,
        double precision,
        double recall,
        double f1
) {
}
