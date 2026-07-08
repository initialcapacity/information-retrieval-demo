package io.ic.starter.app;

import io.ic.starter.catalog.ProductRecord;
import io.ic.starter.catalog.ProductsGateway;
import io.ic.starter.search.Bm25Gateway;
import io.ic.starter.search.EmbeddingGateway;
import io.ic.starter.search.HybridSearchService;
import io.ic.starter.search.SearchResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the three-column comparison for a query: BM25, dense, hybrid. Runs each
 * method live against Postgres and annotates results with fixture relevance.
 */
public class SearchService {
    private static final int TOP_K = 10;
    private static final int CANDIDATE_DEPTH = 100;

    private static final List<String> HERO_QUERIES = List.of(
            "bathroom vanity knobs", "beds that have leds", "writing desk 48\"");

    private final Bm25Gateway bm25Gateway;
    private final EmbeddingGateway embeddingGateway;
    private final HybridSearchService hybridService;
    private final ProductsGateway productsGateway;
    private final QueryEmbeddingResolver embeddingResolver;
    private final FixtureIndex fixtureIndex;

    public SearchService(Bm25Gateway bm25Gateway, EmbeddingGateway embeddingGateway,
                         HybridSearchService hybridService, ProductsGateway productsGateway,
                         QueryEmbeddingResolver embeddingResolver, FixtureIndex fixtureIndex) {
        this.bm25Gateway = bm25Gateway;
        this.embeddingGateway = embeddingGateway;
        this.hybridService = hybridService;
        this.productsGateway = productsGateway;
        this.embeddingResolver = embeddingResolver;
        this.fixtureIndex = fixtureIndex;
    }

    public SearchView blank() {
        return new SearchView("", false, false, null, null, List.of(), HERO_QUERIES);
    }

    public SearchView search(String query) {
        if (query == null || query.isBlank()) {
            return blank();
        }
        Long queryId = fixtureIndex.queryId(query).orElse(null);

        List<SearchResult> bm25 = bm25Gateway.search(query, TOP_K);

        QueryEmbeddingResolver.Resolved resolved;
        try {
            resolved = embeddingResolver.resolve(query);
        } catch (RuntimeException e) {
            // BM25 still renders; dense/hybrid need an embedding.
            var columns = List.of(column("BM25", "lexical", bm25, queryId));
            return new SearchView(query, true, queryId != null, null, e.getMessage(), columns, HERO_QUERIES);
        }

        List<SearchResult> dense = embeddingGateway.search(resolved.vector(), TOP_K);
        List<SearchResult> hybrid = hybridService
                .hybrid(query, resolved.vector(), CANDIDATE_DEPTH).stream().limit(TOP_K).toList();

        var columns = List.of(
                column("BM25", "lexical", bm25, queryId),
                column("Dense", "semantic", dense, queryId),
                column("Hybrid", "RRF k=60", hybrid, queryId)
        );
        return new SearchView(query, true, queryId != null, resolved.source(), null, columns, HERO_QUERIES);
    }

    private SearchView.Column column(String method, String subtitle, List<SearchResult> results, Long queryId) {
        Set<Long> ids = new LinkedHashSet<>(results.stream().map(SearchResult::productId).toList());
        Map<Long, ProductRecord> products = productsGateway.findSummaries(ids);

        var views = new java.util.ArrayList<SearchView.Result>();
        int rank = 1;
        for (SearchResult result : results) {
            ProductRecord product = products.get(result.productId());
            String name = product != null ? product.productName() : "product " + result.productId();
            String category = product != null ? categoryOf(product) : "";
            String relevance = queryId != null ? fixtureIndex.relevance(queryId, result.productId()) : null;
            views.add(new SearchView.Result(rank++, result.productId(), name, category, relevance));
        }
        return new SearchView.Column(method, subtitle, views);
    }

    private static String categoryOf(ProductRecord product) {
        if (product.productClass() != null && !product.productClass().isBlank()) {
            return product.productClass();
        }
        return product.categoryHierarchy() == null ? "" : product.categoryHierarchy();
    }
}
