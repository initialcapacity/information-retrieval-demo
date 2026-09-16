package t.ic.starter.catalog;

import io.ic.starter.catalog.DocsLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocsLoaderTest {
    @TempDir Path directory;
    private static final String HEADER = "chunk_id\ttitle\tpage\ttext\n";
    private static final String ROW = "1\tFirst\tfirst.html\tFirst section text\n";

    @Test
    void importsEveryRowIncludingChunkOne() throws Exception {
        Path file = directory.resolve("chunks.tsv");
        Files.writeString(file, HEADER + ROW + "2\tSecond\tsecond.html\tSecond section text\n");
        var chunks = new DocsLoader().loadChunks(file);
        assertEquals(List.of(1L, 2L), chunks.stream().map(chunk -> chunk.chunkId()).toList());
        assertEquals("First section text", chunks.getFirst().text());
    }

    @Test
    void rejectsMissingHeaderMalformedRowsAndDuplicateIds() throws Exception {
        Path file = directory.resolve("chunks.tsv");
        for (String invalid : List.of(ROW, "", HEADER + ROW + "2\tMissing fields\n", HEADER + ROW + ROW)) {
            Files.writeString(file, invalid);
            assertThrows(IllegalArgumentException.class, () -> new DocsLoader().loadChunks(file));
        }
    }
}
