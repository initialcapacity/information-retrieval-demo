package io.ic.starter.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ic.starter.eval.EvalReport;
import io.ic.starter.eval.FixtureQuery;
import io.ic.starter.eval.MethodMetrics;
import io.ic.starter.eval.Qrels;
import io.ic.starter.search.Bm25Gateway;
import io.ic.starter.search.EmbeddingGateway;
import io.ic.starter.search.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static io.ic.starter.search.RetrievalConfig.EMBEDDING_DIMENSIONS;
import static org.junit.jupiter.api.Assertions.*;

class EvalMainTest {
    @TempDir Path directory;

    @Test
    void reusesRankingsAcrossModesBucketsAndSerializedReport() throws Exception {
        var queries = List.of(new FixtureQuery(1, "query", "", 1, "semantic"));
        var cache = new QueryEmbeddingCache(directory.resolve("cache.tsv"), queries);
        cache.put(1, new float[EMBEDDING_DIMENSIONS]);
        var lexicalCalls = new AtomicInteger();
        var denseCalls = new AtomicInteger();
        var lexical = new Bm25Gateway(null) {
            @Override public List<SearchResult> search(String query, int limit) {
                lexicalCalls.incrementAndGet();
                return List.of(new SearchResult(1, 1));
            }
        };
        var dense = new EmbeddingGateway(null) {
            @Override public List<SearchResult> search(float[] vector, int limit) {
                denseCalls.incrementAndGet();
                return List.of(new SearchResult(2, 1));
            }
        };
        var rankings = new FixtureRankings(queries, cache, lexical, dense);
        var exact = new Qrels(Map.of(1L, Set.of(1L)));
        var partial = new Qrels(Map.of(1L, Set.of(1L, 2L)));
        var report = EvalMain.buildReport(queries, exact, partial, rankings, null);
        assertEquals(1, lexicalCalls.get());
        assertEquals(1, denseCalls.get());
        assertEquals(0, report.modes().get(0).overall().get(1).recall());
        assertEquals(0.5, report.modes().get(1).overall().get(1).recall());
        for (var mode : report.modes()) {
            assertEquals(mode.overall(), mode.buckets().get(1).metrics());
        }
        Path output = directory.resolve("report.json");
        EvalMain.writeReport(output, report);
        assertEquals(report, new ObjectMapper().readValue(output.toFile(), EvalReport.class));
    }

    @Test
    void rejectsLosingOrTiedHybrid() {
        assertThrows(IllegalStateException.class, () -> EvalMain.requireHybridWin(metrics(0.5, 0.4, 0.3)));
        assertThrows(IllegalStateException.class, () -> EvalMain.requireHybridWin(metrics(0.5, 0.4, 0.5)));
        assertDoesNotThrow(() -> EvalMain.requireHybridWin(metrics(0.5, 0.4, 0.6)));
    }

    @Test
    void propagatesExportFailureAndPreservesExistingTarget() throws Exception {
        Path output = directory.resolve("existing-directory");
        Files.createDirectory(output);
        Files.writeString(output.resolve("keep"), "unchanged");
        var report = new EvalReport(10, 60, 400, 100, null, "hash", List.of());
        assertThrows(UncheckedIOException.class, () -> EvalMain.writeReport(output, report));
        assertEquals("unchanged", Files.readString(output.resolve("keep")));
        try (var files = Files.list(directory)) {
            assertEquals(List.of(output), files.toList());
        }
    }

    private static List<MethodMetrics> metrics(double bm25, double dense, double hybrid) {
        return List.of(new MethodMetrics("bm25", 1, 0, 0, bm25),
                new MethodMetrics("embeddings", 1, 0, 0, dense),
                new MethodMetrics("hybrid", 1, 0, 0, hybrid));
    }
}
