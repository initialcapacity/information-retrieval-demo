package io.ic.starter.eval;

import java.util.Map;
import java.util.Set;

/**
 * Relevance judgments per query. {@code relevant} maps query_id to the set of
 * chunk_ids treated as relevant under the chosen binarization.
 */
public record Qrels(Map<Long, Set<Long>> relevant) {
    public Set<Long> relevantFor(long queryId) {
        return relevant.getOrDefault(queryId, Set.of());
    }
}
