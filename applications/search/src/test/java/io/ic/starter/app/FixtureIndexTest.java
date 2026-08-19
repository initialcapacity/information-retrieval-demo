package io.ic.starter.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FixtureIndexTest {
    @Test
    void mapsNormalizedFixtureTextAndRelevantLabels() {
        var index = new FixtureIndex();
        long queryId = index.queryId(" WRITES are slow when many clients COMMIT at once ")
                .orElseThrow();

        assertEquals(39L, queryId);
        assertEquals("Exact", index.relevance(queryId, 866L));
        assertEquals("Partial", index.relevance(queryId, 99L));
        assertNull(index.relevance(queryId, 354L));
    }
}
