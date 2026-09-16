package io.ic.starter.app;

import io.ic.starter.catalog.ChunkRecord;
import io.ic.starter.catalog.ChunksGateway;
import io.ic.starter.eval.FixtureLoader;
import io.ic.starter.eval.FixtureQuery;
import io.ic.starter.search.Bm25Gateway;
import io.ic.starter.search.EmbeddingGateway;
import io.ic.starter.search.HybridSearchService;
import io.ic.starter.search.SearchResult;
import io.ic.starter.search.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.ic.starter.eval.DemoQueries.HERO_QUERIES;
import static io.ic.starter.search.RetrievalConfig.*;

/**
 * Builds the three-column comparison for a query: BM25, dense, hybrid. Runs each
 * method live against Postgres, times each one, and annotates results with fixture
 * relevance.
 */
public class SearchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchService.class);
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
        return new SearchView("", false, false, null, null, null, List.of(), null,
                HERO_QUERIES, pickerTotal, pickerGroups);
    }

    public SearchView search(String query) {
        if (query == null || query.isBlank()) {
            return blank();
        }
        if (query.length() > QueryEmbeddingResolver.MAX_QUERY_CHARACTERS) {
            return new SearchView(query, true, false, null, "Query is too long",
                    "Queries are limited to " + QueryEmbeddingResolver.MAX_QUERY_CHARACTERS + " characters.",
                    List.of(), null, HERO_QUERIES, pickerTotal, pickerGroups);
        }
        long start = System.nanoTime();
        Long queryId = fixtureIndex.queryId(query).orElse(null);

        Timed<List<SearchResult>> bm25 = Timed.of(() -> bm25Gateway.search(query, K));

        Timed<QueryEmbeddingResolver.Resolved> resolved;
        try {
            resolved = Timed.of(() -> embeddingResolver.resolve(query));
        } catch (RuntimeException e) {
            // BM25 still renders; dense/hybrid need an embedding.
            LOGGER.warn("Embedding unavailable; returning BM25-only results", e);
            var columns = List.of(column("BM25", "lexical", bm25, queryId));
            String message = e instanceof IllegalStateException && e.getMessage() != null
                    && e.getMessage().startsWith("No cached embedding")
                    ? e.getMessage()
                    : "The embedding service could not process this query. BM25 results are still available.";
            return new SearchView(query, true, queryId != null, null, "Embeddings and hybrid unavailable",
                    message, columns,
                    timing(null, start), HERO_QUERIES, pickerTotal, pickerGroups);
        }

        float[] vector = resolved.value().vector();
        Timed<List<SearchResult>> dense = Timed.of(() -> embeddingGateway.search(vector, K));
        Timed<List<SearchResult>> hybrid = Timed.of(() -> hybridService
                .hybrid(query, vector, CANDIDATE_DEPTH).stream().limit(K).toList());

        var columns = List.of(
                column("BM25", "lexical", bm25, queryId),
                column("Embeddings", "semantic", dense, queryId),
                column("Hybrid", "RRF k=" + RRF_K, hybrid, queryId)
        );
        return new SearchView(query, true, queryId != null, resolved.value().source(), null, null, columns,
                timing(resolved.millis(), start), HERO_QUERIES, pickerTotal, pickerGroups);
    }

    /**
     * The total covers everything this method did, so it exceeds the sum of the
     * retrieval timings: it also includes the fixture lookup and loading titles for
     * the rows on screen.
     */
    private static SearchView.Timing timing(Double embeddingMillis, long startNanos) {
        double total = (System.nanoTime() - startNanos) / 1_000_000.0;
        return new SearchView.Timing(embeddingMillis, total, K, CANDIDATE_DEPTH);
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

    private SearchView.Column column(String method, String subtitle, Timed<List<SearchResult>> timed, Long queryId) {
        List<SearchResult> results = timed.value();
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
        return new SearchView.Column(method, subtitle, timed.millis(), views);
    }
}
