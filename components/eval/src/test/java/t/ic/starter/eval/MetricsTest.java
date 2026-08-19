package t.ic.starter.eval;

import io.ic.starter.eval.Metrics;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetricsTest {

    @Test
    void precisionAtK() {
        var retrieved = List.of(1L, 2L, 3L, 4L);
        var relevant = Set.of(2L, 4L, 9L);
        // top 4 retrieved, 2 hits => 2/4
        assertEquals(0.5, Metrics.precisionAtK(retrieved, relevant, 4), 1e-9);
    }

    @Test
    void precisionAtK_truncatesToK() {
        var retrieved = List.of(2L, 4L, 3L, 5L);
        var relevant = Set.of(2L, 4L);
        // top 2 are both relevant => 2/2
        assertEquals(1.0, Metrics.precisionAtK(retrieved, relevant, 2), 1e-9);
    }

    @Test
    void precisionAtK_usesKWhenFewerResultsAreReturned() {
        assertEquals(0.2, Metrics.precisionAtK(List.of(2L, 3L), Set.of(2L), 5), 1e-9);
    }

    @Test
    void recallAtK() {
        var retrieved = List.of(1L, 2L, 3L);
        var relevant = Set.of(2L, 4L, 6L, 8L);
        // 1 of 4 relevant found => 0.25
        assertEquals(0.25, Metrics.recallAtK(retrieved, relevant, 10), 1e-9);
    }

    @Test
    void recallAtK_emptyRelevant() {
        assertEquals(0.0, Metrics.recallAtK(List.of(1L), Set.of(), 10), 1e-9);
    }

    @Test
    void f1() {
        assertEquals(0.5, Metrics.f1(0.5, 0.5), 1e-9);
        assertEquals(0.0, Metrics.f1(0.0, 0.0), 1e-9);
        assertEquals(0.4, Metrics.f1(0.3, 0.6), 1e-9);
    }
}
