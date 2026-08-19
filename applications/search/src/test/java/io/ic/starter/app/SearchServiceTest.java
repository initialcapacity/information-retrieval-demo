package io.ic.starter.app;

import io.ic.starter.catalog.ChunkRecord;
import io.ic.starter.catalog.ChunksGateway;
import io.ic.starter.search.Bm25Gateway;
import io.ic.starter.search.EmbeddingClient;
import io.ic.starter.search.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchServiceTest {
    @Test
    void rejectsOverlongQueriesWithoutRunningRetrieval() {
        var service = new SearchService(null, null, null, null, null, new FixtureIndex());

        SearchView view = service.search("x".repeat(QueryEmbeddingResolver.MAX_QUERY_CHARACTERS + 1));

        assertEquals("Query is too long", view.errorTitle());
        assertTrue(view.columns().isEmpty());
    }

    @Test
    void returnsBm25WithASafeMessageWhenLiveEmbeddingFails() {
        var bm25 = new Bm25Gateway(null) {
            @Override
            public List<SearchResult> search(String query, int limit) {
                return List.of(new SearchResult(1L, 0.5));
            }
        };
        var chunks = new ChunksGateway(null) {
            @Override
            public Map<Long, ChunkRecord> findSummaries(Collection<Long> chunkIds) {
                return Map.of(1L, new ChunkRecord(1L, "Title", "page.html", "text"));
            }
        };
        EmbeddingClient client = _ -> {
            throw new RuntimeException("provider response that must stay private");
        };
        var service = new SearchService(
                bm25, null, null, chunks, new QueryEmbeddingResolver(client), new FixtureIndex());

        SearchView view = service.search("a brand new ad hoc query");

        assertEquals(1, view.columns().size());
        assertEquals("BM25", view.columns().getFirst().method());
        assertTrue(view.error().contains("could not process"));
        assertFalse(view.error().contains("provider response"));
    }
}
