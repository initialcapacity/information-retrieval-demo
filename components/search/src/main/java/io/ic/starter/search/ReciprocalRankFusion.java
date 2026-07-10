package io.ic.starter.search;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion. Combines ranked lists on rank alone (no score
 * normalization). Default k = 60.
 */
public class ReciprocalRankFusion {
    public static final int DEFAULT_K = 60;

    private final int k;

    public ReciprocalRankFusion() {
        this(DEFAULT_K);
    }

    public ReciprocalRankFusion(int k) {
        this.k = k;
    }

    /**
     * Fuses ranked lists of chunk ids (best first) into a single ranked list.
     */
    public List<SearchResult> fuse(List<List<Long>> rankedLists) {
        Map<Long, Double> scores = new HashMap<>();
        for (List<Long> list : rankedLists) {
            for (int i = 0; i < list.size(); i++) {
                int rank = i + 1;
                scores.merge(list.get(i), 1.0 / (k + rank), Double::sum);
            }
        }
        return scores.entrySet().stream()
                .map(entry -> new SearchResult(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed()
                        .thenComparingLong(SearchResult::chunkId))
                .toList();
    }

    public static List<Long> toIds(List<SearchResult> results) {
        return results.stream().map(SearchResult::chunkId).toList();
    }
}
