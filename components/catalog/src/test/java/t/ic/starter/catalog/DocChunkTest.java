package t.ic.starter.catalog;

import io.ic.starter.catalog.DocChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DocChunkTest {

    @Test
    void searchTextPrefixesTitle() {
        var chunk = new DocChunk(1, "19.5. Write Ahead Log", "wal.html", "WAL settings control durability");
        assertEquals("19.5. Write Ahead Log. WAL settings control durability", chunk.searchText());
    }

    @Test
    void searchTextHandlesBlankTitle() {
        var chunk = new DocChunk(1, " ", "page.html", "body only");
        assertEquals("body only", chunk.searchText());
    }

    @Test
    void searchTextCollapsesWhitespace() {
        var chunk = new DocChunk(1, " CREATE  INDEX ", "sql.html", "  builds   an index ");
        assertEquals("CREATE INDEX. builds an index", chunk.searchText());
    }
}
