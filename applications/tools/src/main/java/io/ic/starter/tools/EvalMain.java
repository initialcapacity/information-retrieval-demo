package io.ic.starter.tools;

import io.ic.starter.databasesupport.DataSourceFactory;
import io.ic.starter.eval.EvalRunner;
import io.ic.starter.eval.FixtureLoader;
import io.ic.starter.eval.FixtureQuery;
import io.ic.starter.eval.Metrics;
import io.ic.starter.eval.MethodMetrics;
import io.ic.starter.eval.Qrels;
import io.ic.starter.eval.QrelsLoader;
import io.ic.starter.eval.RankingFunction;
import io.ic.starter.eval.ResultsTable;
import io.ic.starter.search.Bm25Gateway;
import io.ic.starter.search.EmbeddingGateway;
import io.ic.starter.search.HybridSearchService;
import io.ic.starter.search.ReciprocalRankFusion;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs the full eval: BM25, dense, and hybrid over the fixture, macro-averaged
 * P/R/F1 at k, with per-lean breakdowns and a validation gate.
 */
public class EvalMain {
    private static final int K = 10;
    private static final int CANDIDATE_DEPTH = 100;

    public static void main(String[] args) {
        String databaseUrl = System.getenv("DATABASE_URL");
        Path labelCsv = Path.of(args.length > 0 ? args[0] : "data/wands/label.csv");
        Path cacheFile = Path.of(args.length > 1 ? args[1] : "data/query-embeddings.tsv");

        DataSource dataSource = DataSourceFactory.create(databaseUrl);
        var bm25Gateway = new Bm25Gateway(dataSource);
        var embeddingGateway = new EmbeddingGateway(dataSource);
        var hybrid = new HybridSearchService(bm25Gateway, embeddingGateway);

        List<FixtureQuery> queries = new FixtureLoader().load();
        Set<Long> queryIds = queries.stream().map(FixtureQuery::queryId).collect(Collectors.toSet());

        var qrels = new QrelsLoader().load(labelCsv, queryIds, QrelsLoader.Mode.EXACT_AND_PARTIAL);
        var qrelsExact = new QrelsLoader().load(labelCsv, queryIds, QrelsLoader.Mode.EXACT_ONLY);
        var cache = new QueryEmbeddingCache(cacheFile);

        RankingFunction bm25 = q -> ReciprocalRankFusion.toIds(bm25Gateway.search(q.query(), CANDIDATE_DEPTH));
        RankingFunction dense = q -> ReciprocalRankFusion.toIds(embeddingGateway.search(cache.get(q.queryId()), CANDIDATE_DEPTH));
        RankingFunction hybridRank = q -> ReciprocalRankFusion.toIds(hybrid.hybrid(q.query(), cache.get(q.queryId()), CANDIDATE_DEPTH));

        var runner = new EvalRunner();

        System.out.println("=".repeat(60));
        System.out.println("DubJUG search eval  |  WANDS  |  " + queries.size() + " fixture queries");
        System.out.println("relevance: Exact + Partial (default)");
        System.out.println("=".repeat(60));

        List<MethodMetrics> overall = List.of(
                runner.evaluate("bm25", queries, qrels, bm25, K),
                runner.evaluate("dense", queries, qrels, dense, K),
                runner.evaluate("hybrid", queries, qrels, hybridRank, K)
        );
        System.out.println(ResultsTable.format("OVERALL (Exact+Partial)", K, overall));

        // Strict Exact-only mode.
        System.out.println(ResultsTable.format("OVERALL (Exact only)", K, List.of(
                runner.evaluate("bm25", queries, qrelsExact, bm25, K),
                runner.evaluate("dense", queries, qrelsExact, dense, K),
                runner.evaluate("hybrid", queries, qrelsExact, hybridRank, K)
        )));

        // Per-lean breakdowns.
        Map<String, List<FixtureQuery>> byLean = queries.stream()
                .collect(Collectors.groupingBy(FixtureQuery::lean));
        for (String lean : List.of("semantic", "keyword", "mixed")) {
            List<FixtureQuery> bucket = byLean.getOrDefault(lean, List.of());
            System.out.println(ResultsTable.format("LEAN=" + lean, K, List.of(
                    runner.evaluate("bm25", bucket, qrels, bm25, K),
                    runner.evaluate("dense", bucket, qrels, dense, K),
                    runner.evaluate("hybrid", bucket, qrels, hybridRank, K)
            )));
        }

        // Validation gate.
        double bm25F1 = overall.get(0).f1();
        double denseF1 = overall.get(1).f1();
        double hybridF1 = overall.get(2).f1();
        System.out.println("-".repeat(60));
        boolean climbs = hybridF1 > bm25F1 && hybridF1 > denseF1;
        System.out.printf("VALIDATION GATE: hybrid F1=%.4f  bm25 F1=%.4f  dense F1=%.4f%n", hybridF1, bm25F1, denseF1);
        System.out.println(climbs
                ? "PASS: hybrid F1 exceeds both single-method baselines."
                : "FAIL: hybrid does not beat both baselines (see diagnosis above).");

        // Semantic-bucket validation: does dense actually recover BM25's failures?
        System.out.println("-".repeat(60));
        System.out.println("SEMANTIC-BUCKET VALIDATION (per-query recall@" + K + ")");
        List<FixtureQuery> semantic = byLean.getOrDefault("semantic", List.of());
        int denseWins = 0;
        int hardForBoth = 0;
        var hardQueries = new java.util.ArrayList<String>();
        for (FixtureQuery q : semantic) {
            Set<Long> relevant = qrels.relevantFor(q.queryId());
            if (relevant.isEmpty()) {
                continue;
            }
            double bm25Recall = Metrics.recallAtK(bm25.rank(q), relevant, K);
            double denseRecall = Metrics.recallAtK(dense.rank(q), relevant, K);
            if (denseRecall > bm25Recall) {
                denseWins++;
            }
            if (bm25Recall == 0.0 && denseRecall == 0.0) {
                hardForBoth++;
                hardQueries.add(q.queryId() + " \"" + q.query() + "\"");
            }
        }
        System.out.printf("semantic queries: %d | dense recall > bm25 recall on %d | hard for both (recall 0): %d%n",
                semantic.size(), denseWins, hardForBoth);
        if (!hardQueries.isEmpty()) {
            System.out.println("  hard-for-both queries:");
            hardQueries.forEach(h -> System.out.println("    - " + h));
        }
    }
}
