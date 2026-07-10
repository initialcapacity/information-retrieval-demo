package t.ic.starter.eval;

import io.ic.starter.eval.FixtureLoader;
import io.ic.starter.eval.FixtureQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FixtureLoaderTest {

    @Test
    void loadsAllFixtureQueries() {
        List<FixtureQuery> queries = new FixtureLoader().load();
        assertEquals(56, queries.size());

        Map<String, Long> byLean = queries.stream()
                .collect(Collectors.groupingBy(FixtureQuery::lean, Collectors.counting()));
        assertEquals(20L, byLean.get("semantic"));
        assertEquals(17L, byLean.get("keyword"));
        assertEquals(19L, byLean.get("mixed"));
    }

    @Test
    void parsesHeroQuery() {
        FixtureQuery spelling = new FixtureLoader().load().stream()
                .filter(q -> q.queryId() == 31)
                .findFirst()
                .orElseThrow();
        assertEquals("find rows where the text is spelled slightly wrong", spelling.query());
        assertTrue(spelling.nExact() > 0);
    }
}
