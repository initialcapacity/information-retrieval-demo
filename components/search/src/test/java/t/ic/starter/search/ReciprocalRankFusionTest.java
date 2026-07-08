package t.ic.starter.search;

import io.ic.starter.search.ReciprocalRankFusion;
import io.ic.starter.search.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReciprocalRankFusionTest {

    @Test
    void fusesRanksAcrossLists() {
        var rrf = new ReciprocalRankFusion(60);
        // id 2 appears near the top of both lists, so it should win the fusion.
        var fused = rrf.fuse(List.of(
                List.of(1L, 2L, 3L),
                List.of(2L, 4L, 1L)
        ));
        assertEquals(2L, fused.getFirst().productId());
    }

    @Test
    void itemInBothListsBeatsItemInOne() {
        var rrf = new ReciprocalRankFusion(60);
        // id 5 is rank 1 in one list only; id 1 is rank 2 and 3 across both.
        var fused = rrf.fuse(List.of(
                List.of(5L, 1L),
                List.of(9L, 1L)
        ));
        assertEquals(1L, fused.getFirst().productId());
    }

    @Test
    void scoreUsesKConstant() {
        var rrf = new ReciprocalRankFusion(60);
        var fused = rrf.fuse(List.of(List.of(7L)));
        assertEquals(1L, fused.size());
        assertEquals(1.0 / 61.0, fused.getFirst().score(), 1e-9);
    }

    @Test
    void toIdsPreservesOrder() {
        var ids = ReciprocalRankFusion.toIds(List.of(
                new SearchResult(3L, 0.9),
                new SearchResult(1L, 0.5)
        ));
        assertEquals(List.of(3L, 1L), ids);
        assertTrue(ids instanceof List);
    }
}
