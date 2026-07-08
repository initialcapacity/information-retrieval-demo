package io.ic.starter.eval;

import java.util.List;

/**
 * Runs a retrieval method over a query set and macro-averages P/R/F1 at k.
 */
public class EvalRunner {

    public MethodMetrics evaluate(String method, List<FixtureQuery> queries, Qrels qrels, RankingFunction ranking, int k) {
        double precisionSum = 0.0;
        double recallSum = 0.0;
        double f1Sum = 0.0;
        int counted = 0;

        for (FixtureQuery query : queries) {
            var relevant = qrels.relevantFor(query.queryId());
            if (relevant.isEmpty()) {
                continue;
            }
            List<Long> retrieved = ranking.rank(query);
            double precision = Metrics.precisionAtK(retrieved, relevant, k);
            double recall = Metrics.recallAtK(retrieved, relevant, k);
            precisionSum += precision;
            recallSum += recall;
            f1Sum += Metrics.f1(precision, recall);
            counted++;
        }

        if (counted == 0) {
            return new MethodMetrics(method, 0, 0, 0, 0);
        }
        return new MethodMetrics(method, counted, precisionSum / counted, recallSum / counted, f1Sum / counted);
    }
}
