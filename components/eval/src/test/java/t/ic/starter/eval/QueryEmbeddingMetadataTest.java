package t.ic.starter.eval;

import io.ic.starter.eval.FixtureQuery;
import io.ic.starter.eval.QueryEmbeddingMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class QueryEmbeddingMetadataTest {
    @Test
    void fixtureTextChangesInvalidateTheFingerprint() {
        var original = List.of(new FixtureQuery(1L, "original query", "class", 1, "mixed"));
        var changed = List.of(new FixtureQuery(1L, "changed query", "class", 1, "mixed"));

        assertNotEquals(
                QueryEmbeddingMetadata.expected(original).fixtureSha256(),
                QueryEmbeddingMetadata.expected(changed).fixtureSha256());
    }
}
