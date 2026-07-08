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
        assertEquals(116, queries.size());

        Map<String, Long> byLean = queries.stream()
                .collect(Collectors.groupingBy(FixtureQuery::lean, Collectors.counting()));
        assertEquals(55L, byLean.get("semantic"));
        assertEquals(19L, byLean.get("keyword"));
        assertEquals(42L, byLean.get("mixed"));
    }

    @Test
    void parsesHeroQuery() {
        FixtureQuery beds = new FixtureLoader().load().stream()
                .filter(q -> q.queryId() == 14)
                .findFirst()
                .orElseThrow();
        assertEquals("beds that have leds", beds.query());
        assertTrue(beds.nExact() > 0);
    }
}
