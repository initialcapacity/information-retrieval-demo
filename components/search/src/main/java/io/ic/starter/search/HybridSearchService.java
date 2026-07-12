package io.ic.starter.search;

import java.util.List;

/**
 * Runs BM25 and embedding search and fuses them with Reciprocal Rank Fusion.
 * The query embedding is supplied by the caller (embedded live or read from a
 * cache) so this service stays free of any network dependency.
 */
public class HybridSearchService {
    private final Bm25Gateway bm25Gateway;
    private final EmbeddingGateway embeddingGateway;
    private final ReciprocalRankFusion fusion;

    public HybridSearchService(Bm25Gateway bm25Gateway, EmbeddingGateway embeddingGateway) {
        this(bm25Gateway, embeddingGateway, new ReciprocalRankFusion());
    }

    public HybridSearchService(Bm25Gateway bm25Gateway, EmbeddingGateway embeddingGateway, ReciprocalRankFusion fusion) {
        this.bm25Gateway = bm25Gateway;
        this.embeddingGateway = embeddingGateway;
        this.fusion = fusion;
    }

    /**
     * Fuses BM25 and embedding results. Each method retrieves {@code candidateDepth}
     * results before fusion; the fused list is returned in full (truncate at the
     * caller's k).
     */
    public List<SearchResult> hybrid(String query, float[] queryEmbedding, int candidateDepth) {
        List<SearchResult> bm25Results = bm25Gateway.search(query, candidateDepth);
        List<SearchResult> denseResults = embeddingGateway.search(queryEmbedding, candidateDepth);
        return fusion.fuse(List.of(
                ReciprocalRankFusion.toIds(bm25Results),
                ReciprocalRankFusion.toIds(denseResults)
        ));
    }
}
