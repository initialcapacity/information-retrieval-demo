package io.ic.starter.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ic.starter.databasesupport.DataSourceFactory;
import io.ic.starter.eval.EvalReport;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
        Path labelCsv = Path.of(args.length > 0 ? args[0] : "data/pgdocs/qrels.tsv");
        Path cacheFile = Path.of(args.length > 1 ? args[1] : "data/query-embeddings.tsv");

        // Raise HNSW ef_search well above the candidate depth: the pgvector default
        // (40) under-retrieves and was artificially depressing dense/hybrid.
        DataSource dataSource = DataSourceFactory.create(databaseUrl, 10, "set hnsw.ef_search = 400");
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
        System.out.println("Information retrieval eval  | pgdocs |  " + queries.size() + " fixture queries");
        System.out.println("relevance: Exact + Partial (default)");
        System.out.println("=".repeat(60));

        // Headline / gate config: Exact-only, k=10 (the agreed demo binarization).
        List<MethodMetrics> overall = List.of(
                runner.evaluate("bm25", queries, qrelsExact, bm25, K),
                runner.evaluate("dense", queries, qrelsExact, dense, K),
                runner.evaluate("hybrid", queries, qrelsExact, hybridRank, K)
        );
        System.out.println(ResultsTable.format("OVERALL (Exact only) [headline config]", K, overall));

        // Default Exact+Partial mode (shown for completeness; hybrid ~ dense here at k=10).
        System.out.println(ResultsTable.format("OVERALL (Exact+Partial)", K, List.of(
                runner.evaluate("bm25", queries, qrels, bm25, K),
                runner.evaluate("dense", queries, qrels, dense, K),
                runner.evaluate("hybrid", queries, qrels, hybridRank, K)
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

        // Export a real-results snapshot for the web app's eval view (offline render).
        var report = new EvalReport(K, ReciprocalRankFusion.DEFAULT_K, 400, List.of(
                buildMode("Exact only", queries, byLean, qrelsExact, bm25, dense, hybridRank, runner),
                buildMode("Exact + Partial", queries, byLean, qrels, bm25, dense, hybridRank, runner)
        ));
        Path jsonOut = Path.of("applications/search/src/main/resources/eval-results.json");
        try {
            Files.createDirectories(jsonOut.getParent());
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(jsonOut.toFile(), report);
            System.out.println("\nWrote eval snapshot -> " + jsonOut.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("Failed to write eval snapshot: " + e.getMessage());
        }
    }

    private static EvalReport.ModeReport buildMode(
            String name, List<FixtureQuery> queries, Map<String, List<FixtureQuery>> byLean,
            Qrels qrels, RankingFunction bm25, RankingFunction dense, RankingFunction hybrid, EvalRunner runner) {
        List<MethodMetrics> overall = List.of(
                runner.evaluate("bm25", queries, qrels, bm25, K),
                runner.evaluate("dense", queries, qrels, dense, K),
                runner.evaluate("hybrid", queries, qrels, hybrid, K)
        );
        var buckets = new ArrayList<EvalReport.BucketReport>();
        for (String lean : List.of("keyword", "semantic", "mixed")) {
            List<FixtureQuery> bucket = byLean.getOrDefault(lean, List.of());
            buckets.add(new EvalReport.BucketReport(lean, bucket.size(), List.of(
                    runner.evaluate("bm25", bucket, qrels, bm25, K),
                    runner.evaluate("dense", bucket, qrels, dense, K),
                    runner.evaluate("hybrid", bucket, qrels, hybrid, K)
            )));
        }
        return new EvalReport.ModeReport(name, overall, buckets);
    }
}
