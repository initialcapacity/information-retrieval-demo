package io.ic.starter.tools;

import io.ic.starter.eval.FixtureQuery;
import io.ic.starter.eval.RankingFunction;
import io.ic.starter.search.Bm25Gateway;
import io.ic.starter.search.EmbeddingGateway;
import io.ic.starter.search.ReciprocalRankFusion;
import io.ic.starter.search.SearchResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.ic.starter.search.RetrievalConfig.CANDIDATE_DEPTH;

/** Retrieve once per query so every binarization and output scores the same rankings. */
final class FixtureRankings {
    private final Map<Long, Map<String, List<Long>>> byQuery = new LinkedHashMap<>();

    FixtureRankings(List<FixtureQuery> queries, QueryEmbeddingCache cache,
                    Bm25Gateway bm25, EmbeddingGateway embeddings) {
        for (FixtureQuery query : queries) {
            List<Long> lexical = ReciprocalRankFusion.toIds(bm25.search(query.query(), CANDIDATE_DEPTH));
            List<Long> dense = ReciprocalRankFusion.toIds(embeddings.search(cache.get(query.queryId()), CANDIDATE_DEPTH));
            List<Long> hybrid = new ReciprocalRankFusion().fuse(List.of(lexical, dense))
                    .stream().map(SearchResult::chunkId).toList();
            byQuery.put(query.queryId(), Map.of("bm25", lexical, "embeddings", dense, "hybrid", hybrid));
        }
    }

    RankingFunction method(String name) {
        return query -> byQuery.get(query.queryId()).get(name);
    }

    String fingerprint() {
        var digest = new Fingerprint();
        byQuery.forEach((id, methods) -> {
            digest.add(Long.toString(id));
            for (String name : List.of("bm25", "embeddings", "hybrid")) {
                digest.add(name);
                digest.add(methods.get(name).toString());
            }
        });
        return digest.finish();
    }
}
