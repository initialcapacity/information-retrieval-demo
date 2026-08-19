package io.ic.starter.tools;

import io.ic.starter.eval.FixtureQuery;
import io.ic.starter.eval.QueryEmbeddingMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryEmbeddingCacheTest {
    @TempDir
    Path directory;

    @Test
    void preservesUnchangedSerializationAndRejectsStaleFixtureText() throws Exception {
        Path cacheFile = directory.resolve("cache.tsv");
        List<FixtureQuery> original = List.of(query("original"));
        String serialized = "1\t"
                + "0.000000000,".repeat(QueryEmbeddingMetadata.DIMENSIONS - 1)
                + "0.000000000";
        Files.writeString(cacheFile, serialized + "\n");
        QueryEmbeddingMetadata.expected(original).write(QueryEmbeddingMetadata.sidecar(cacheFile));

        var cache = new QueryEmbeddingCache(cacheFile, original);
        cache.save();

        assertEquals(serialized + "\n", Files.readString(cacheFile));
        List<FixtureQuery> changed = List.of(query("changed"));
        assertThrows(IllegalStateException.class, () -> new QueryEmbeddingCache(cacheFile, changed));
        assertEquals(0, new QueryEmbeddingCache(cacheFile, changed, true).size());
    }

    private static FixtureQuery query(String text) {
        return new FixtureQuery(1L, text, "class", 1, "mixed");
    }
}
