package io.ic.starter.eval;

import java.util.List;

/**
 * Produces a ranked list of product ids (best first) for a fixture query.
 */
@FunctionalInterface
public interface RankingFunction {
    List<Long> rank(FixtureQuery query);
}
