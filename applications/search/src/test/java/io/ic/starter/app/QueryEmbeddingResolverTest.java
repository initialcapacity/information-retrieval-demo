package io.ic.starter.app;

import io.ic.starter.eval.QueryEmbeddingMetadata;
import io.ic.starter.search.EmbeddingClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryEmbeddingResolverTest {
    @Test
    void resolvesFixtureQueriesWithoutCallingTheLiveClient() {
        EmbeddingClient failingClient = _ -> {
            throw new AssertionError("fixture query should use the committed cache");
        };

        var resolved = new QueryEmbeddingResolver(failingClient)
                .resolve("  WAL_LEVEL   LOGICAL ");

        assertEquals("cached", resolved.source());
        assertEquals(QueryEmbeddingMetadata.DIMENSIONS, resolved.vector().length);
    }

    @Test
    void cachesSuccessfulLiveEmbeddingsByNormalizedText() {
        var calls = new AtomicInteger();
        EmbeddingClient client = texts -> {
            calls.incrementAndGet();
            return List.of(new float[QueryEmbeddingMetadata.DIMENSIONS]);
        };
        var resolver = new QueryEmbeddingResolver(client);

        assertEquals("live", resolver.resolve("an entirely new query").source());
        assertEquals("live cache", resolver.resolve("  AN entirely   NEW query ").source());
        assertEquals(1, calls.get());
    }

    @Test
    void rejectsUnexpectedLiveEmbeddingDimensions() {
        EmbeddingClient client = _ -> List.of(new float[3]);
        var resolver = new QueryEmbeddingResolver(client);

        assertThrows(IllegalStateException.class, () -> resolver.resolve("another new query"));
    }

    @Test
    void rejectsOverlongQueriesBeforeCallingTheClient() {
        EmbeddingClient client = _ -> {
            throw new AssertionError("overlong query should not reach the client");
        };
        var resolver = new QueryEmbeddingResolver(client);

        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("x".repeat(QueryEmbeddingResolver.MAX_QUERY_CHARACTERS + 1)));
    }
}
