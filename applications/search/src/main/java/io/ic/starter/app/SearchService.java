package io.ic.starter.app;

import io.ic.starter.catalog.ChunkRecord;
import io.ic.starter.catalog.ChunksGateway;
import io.ic.starter.eval.FixtureLoader;
import io.ic.starter.eval.FixtureQuery;
import io.ic.starter.search.Bm25Gateway;
import io.ic.starter.search.EmbeddingGateway;
import io.ic.starter.search.HybridSearchService;
import io.ic.starter.search.SearchResult;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the three-column comparison for a query: BM25, dense, hybrid. Runs each
 * method live against Postgres and annotates results with fixture relevance.
 */
public class SearchService {
    private static final int DISPLAY_K = 10;     // results shown per column on stage, aligned with the eval k
    private static final int CANDIDATE_DEPTH = 100;

    private static final List<String> HERO_QUERIES = List.of(
            "my database keeps growing even though I delete rows",
            "wal_level logical",
            "writes are slow when many clients commit at once");

    private final Bm25Gateway bm25Gateway;
    private final EmbeddingGateway embeddingGateway;
    private final HybridSearchService hybridService;
    private final ChunksGateway chunksGateway;
    private final QueryEmbeddingResolver embeddingResolver;
    private final FixtureIndex fixtureIndex;
    private final List<SearchView.LeanGroup> pickerGroups;
    private final int pickerTotal;

    public SearchService(Bm25Gateway bm25Gateway, EmbeddingGateway embeddingGateway,
                         HybridSearchService hybridService, ChunksGateway chunksGateway,
                         QueryEmbeddingResolver embeddingResolver, FixtureIndex fixtureIndex) {
        this.bm25Gateway = bm25Gateway;
        this.embeddingGateway = embeddingGateway;
        this.hybridService = hybridService;
        this.chunksGateway = chunksGateway;
        this.embeddingResolver = embeddingResolver;
        this.fixtureIndex = fixtureIndex;
        this.pickerGroups = buildPickerGroups();
        this.pickerTotal = pickerGroups.stream().mapToInt(SearchView.LeanGroup::count).sum();
    }

    public SearchView blank() {
        return new SearchView("", false, false, null, null, List.of(), HERO_QUERIES, pickerTotal, pickerGroups);
    }

    public SearchView search(String query) {
        if (query == null || query.isBlank()) {
            return blank();
        }
        Long queryId = fixtureIndex.queryId(query).orElse(null);

        List<SearchResult> bm25 = bm25Gateway.search(query, DISPLAY_K);

        QueryEmbeddingResolver.Resolved resolved;
        try {
            resolved = embeddingResolver.resolve(query);
        } catch (RuntimeException e) {
            // BM25 still renders; dense/hybrid need an embedding.
            var columns = List.of(column("BM25", "lexical", bm25, queryId));
            return new SearchView(query, true, queryId != null, null, e.getMessage(), columns, HERO_QUERIES,
                    pickerTotal, pickerGroups);
        }

        List<SearchResult> dense = embeddingGateway.search(resolved.vector(), DISPLAY_K);
        List<SearchResult> hybrid = hybridService
                .hybrid(query, resolved.vector(), CANDIDATE_DEPTH).stream().limit(DISPLAY_K).toList();

        var columns = List.of(
                column("BM25", "lexical", bm25, queryId),
                column("Embeddings", "semantic", dense, queryId),
                column("Hybrid", "RRF k=60", hybrid, queryId)
        );
        return new SearchView(query, true, queryId != null, resolved.source(), null, columns, HERO_QUERIES,
                pickerTotal, pickerGroups);
    }

    /**
     * Groups the labelled eval fixture by lean bucket for the query picker
     * (display-only; does not touch the fixture, qrels, or eval). Rows are sorted
     * by Exact count descending, matching the mockup.
     */
    private static List<SearchView.LeanGroup> buildPickerGroups() {
        record Meta(String label, String desc) {
        }
        var meta = Map.of(
                "keyword", new Meta("Keyword", "exact tokens: config names, error codes"),
                "semantic", new Meta("Semantic", "paraphrased, no shared vocabulary"),
                "mixed", new Meta("Mixed", "exact tokens and paraphrase together")
        );
        Map<String, List<FixtureQuery>> byLean = new FixtureLoader().load().stream()
                .collect(Collectors.groupingBy(FixtureQuery::lean));

        var groups = new java.util.ArrayList<SearchView.LeanGroup>();
        for (String lean : List.of("keyword", "semantic", "mixed")) {
            List<FixtureQuery> bucket = byLean.getOrDefault(lean, List.of());
            List<SearchView.PickerQuery> queries = bucket.stream()
                    .sorted(Comparator.comparingInt(FixtureQuery::nExact).reversed()
                            .thenComparing(FixtureQuery::query))
                    .map(q -> new SearchView.PickerQuery(q.query(), category(q.queryClass()), q.nExact()))
                    .toList();
            Meta m = meta.get(lean);
            groups.add(new SearchView.LeanGroup(lean, m.label(), m.desc(), queries.size(), queries));
        }
        return groups;
    }

    private static String category(String queryClass) {
        return (queryClass == null || queryClass.isBlank()) ? "—" : queryClass;
    }

    private SearchView.Column column(String method, String subtitle, List<SearchResult> results, Long queryId) {
        Set<Long> ids = new LinkedHashSet<>(results.stream().map(SearchResult::chunkId).toList());
        Map<Long, ChunkRecord> chunks = chunksGateway.findSummaries(ids);

        var views = new java.util.ArrayList<SearchView.Result>();
        int rank = 1;
        for (SearchResult result : results) {
            ChunkRecord chunk = chunks.get(result.chunkId());
            String name = chunk != null ? chunk.title() : "chunk " + result.chunkId();
            String category = chunk != null ? chunk.page() : "";
            String relevance = queryId != null ? fixtureIndex.relevance(queryId, result.chunkId()) : null;
            views.add(new SearchView.Result(rank++, result.chunkId(), name, category, relevance));
        }
        return new SearchView.Column(method, subtitle, views);
    }
}
