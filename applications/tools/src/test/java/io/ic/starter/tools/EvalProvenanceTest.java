package io.ic.starter.tools;

import io.ic.starter.catalog.ChunksGateway;
import io.ic.starter.catalog.DocsLoader;
import io.ic.starter.testsupport.TestDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EvalProvenanceTest {
    @TempDir Path directory;

    @Test
    void fingerprintsActualVectorsAndRejectsIncompleteOrChangedCorpora() throws Exception {
        Path corpus = directory.resolve("chunks.tsv");
        Files.writeString(corpus, "chunk_id\ttitle\tpage\ttext\n1\tTitle\tpage.html\tSome text\n");
        Path labels = directory.resolve("qrels.tsv");
        Files.writeString(labels, "labels");
        Path cache = directory.resolve("cache.tsv");
        Files.writeString(cache, "cache");
        try (var db = TestDatabase.create()) {
            db.template().clear();
            var chunks = new ChunksGateway(db.dataSource());
            chunks.replaceAll(new DocsLoader().loadChunks(corpus));
            assertThrows(IllegalStateException.class,
                    () -> EvalProvenance.capture(db.dataSource(), corpus, labels, cache));
            db.template().execute("update chunks set embedding = ('[' || array_to_string(array_fill(0.1, array[1536]), ',') || ']')::vector");
            var original = EvalProvenance.capture(db.dataSource(), corpus, labels, cache);
            assertEquals(1, original.chunkCount());
            assertTrue(original.extensionVersions().containsKey("vector"));
            db.template().execute("update chunks set embedding = ('[' || array_to_string(array_fill(0.2, array[1536]), ',') || ']')::vector");
            var changed = EvalProvenance.capture(db.dataSource(), corpus, labels, cache);
            assertEquals(original.indexedCorpusSha256(), changed.indexedCorpusSha256());
            assertNotEquals(original.documentEmbeddingsSha256(), changed.documentEmbeddingsSha256());
            db.template().execute("update chunks set search_text = 'different corpus'");
            assertThrows(IllegalStateException.class,
                    () -> EvalProvenance.capture(db.dataSource(), corpus, labels, cache));
        }
    }
}
