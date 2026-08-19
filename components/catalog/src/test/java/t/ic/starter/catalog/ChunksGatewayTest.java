package t.ic.starter.catalog;

import io.ic.starter.catalog.ChunksGateway;
import io.ic.starter.catalog.DocChunk;
import io.ic.starter.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunksGatewayTest {
    private final TestDatabase testDatabase = TestDatabase.create();
    private final ChunksGateway gateway = new ChunksGateway(testDatabase.dataSource());

    @BeforeEach
    void setUp() {
        testDatabase.template().clear();
    }

    @AfterEach
    void tearDown() {
        testDatabase.close();
    }

    @Test
    void replacementUpdatesChangedRowsInvalidatesEmbeddingsAndDeletesMissingRows() {
        List<DocChunk> initial = List.of(
                new DocChunk(1L, "First", "first.html", "old text"),
                new DocChunk(2L, "Second", "second.html", "second text"));
        gateway.replaceAll(initial);
        testDatabase.template().execute(
                "update chunks set embedding = (" +
                        "'[' || array_to_string(array_fill(0.0, array[1536]), ',') || ']'" +
                        ")::vector");

        ChunksGateway.Reconciliation unchanged = gateway.replaceAll(initial);
        assertEquals(0, unchanged.insertedOrChanged());
        assertTrue(hasEmbedding(1L));

        ChunksGateway.Reconciliation changed = gateway.replaceAll(List.of(
                new DocChunk(1L, "First updated", "first.html", "new text"),
                new DocChunk(3L, "Third", "third.html", "third text")));

        assertEquals(2, changed.insertedOrChanged());
        assertEquals(1, changed.deleted());
        assertEquals(2L, gateway.count());
        assertFalse(hasEmbedding(1L));
        assertTrue(gateway.find(2L).isEmpty());
        assertTrue(gateway.find(3L).isPresent());
    }

    private boolean hasEmbedding(long chunkId) {
        return testDatabase.template()
                .query("select embedding is not null from chunks where chunk_id = ?",
                        statement -> statement.setLong(1, chunkId),
                        result -> result.getBoolean(1))
                .orElse(false);
    }
}
