package io.ic.starter.eval;

import java.util.List;

/**
 * Serializable snapshot of an eval run, for offline rendering in the web app.
 */
public record EvalReport(
        int k,
        int rrfK,
        int efSearch,
        List<ModeReport> modes
) {
    public record ModeReport(
            String name,
            List<MethodMetrics> overall,
            List<BucketReport> buckets
    ) {
    }

    public record BucketReport(
            String lean,
            int queryCount,
            List<MethodMetrics> metrics
    ) {
    }
}
