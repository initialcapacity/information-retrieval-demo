package io.ic.starter.eval;

import java.util.List;
import java.util.Map;

/** Serializable snapshot of one eval run, including the inputs needed to compare runs. */
public record EvalReport(
        int k,
        int rrfK,
        int efSearch,
        int candidateDepth,
        Provenance provenance,
        String rankingsSha256,
        List<ModeReport> modes
) {
    public record Provenance(
            String generatedAt,
            int chunkCount,
            String corpusSha256,
            String indexedCorpusSha256,
            String documentEmbeddingsSha256,
            String fixtureSha256,
            String qrelsSha256,
            String queryEmbeddingsSha256,
            String embeddingModel,
            int embeddingDimensions,
            String postgresVersion,
            Map<String, String> extensionVersions,
            Map<String, String> indexDefinitions
    ) {}

    public record ModeReport(String name, List<MethodMetrics> overall, List<BucketReport> buckets) {}

    public record BucketReport(String lean, int queryCount, List<MethodMetrics> metrics) {}
}
