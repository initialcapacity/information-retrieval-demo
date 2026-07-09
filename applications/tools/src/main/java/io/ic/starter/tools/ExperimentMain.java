package io.ic.starter.tools;

import io.ic.starter.databasesupport.DataSourceFactory;
import io.ic.starter.eval.EvalRunner;
import io.ic.starter.eval.FixtureLoader;
import io.ic.starter.eval.FixtureQuery;
import io.ic.starter.eval.MethodMetrics;
import io.ic.starter.eval.Qrels;
import io.ic.starter.eval.QrelsLoader;
import io.ic.starter.eval.RankingFunction;
import io.ic.starter.search.Bm25Gateway;
import io.ic.starter.search.EmbeddingGateway;
import io.ic.starter.search.ReciprocalRankFusion;
import io.ic.starter.search.SearchResult;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Eval experiments to find an honest configuration where BM25 -> dense -> hybrid
 * reads cleanly: k-sweep, per-bucket breakdown, and RRF-constant sweep. Retrieval
 * is done once at depth D; every metric is derived by truncation / re-fusion.
 */
public class ExperimentMain {
    private static final int DEPTH = 200;
    private static final int[] K_VALUES = {5, 10, 20, 50, 100};
    private static final int[] RRF_VALUES = {10, 30, 60, 100};

    public static void main(String[] args) {
        String databaseUrl = System.getenv("DATABASE_URL");
        Path labelCsv = Path.of(args.length > 0 ? args[0] : "data/wands/label.csv");
        Path cacheFile = Path.of(args.length > 1 ? args[1] : "data/query-embeddings.tsv");
        Path outFile = Path.of("experiments/results.md");

        // Raise ef_search so deep candidate retrieval (depth 200) is accurate.
        DataSource dataSource = DataSourceFactory.create(databaseUrl, 10, "set hnsw.ef_search = 400");
        var bm25Gateway = new Bm25Gateway(dataSource);
        var embeddingGateway = new EmbeddingGateway(dataSource);

        List<FixtureQuery> queries = new FixtureLoader().load();
        Set<Long> queryIds = queries.stream().map(FixtureQuery::queryId).collect(Collectors.toSet());
        var qrelsAP = new QrelsLoader().load(labelCsv, queryIds, QrelsLoader.Mode.EXACT_AND_PARTIAL);
        var qrelsExact = new QrelsLoader().load(labelCsv, queryIds, QrelsLoader.Mode.EXACT_ONLY);
        var cache = new QueryEmbeddingCache(cacheFile);

        // Precompute deep ranked lists once.
        Map<Long, List<Long>> bm25Ranks = new HashMap<>();
        Map<Long, List<Long>> denseRanks = new HashMap<>();
        for (FixtureQuery q : queries) {
            bm25Ranks.put(q.queryId(), ids(bm25Gateway.search(q.query(), DEPTH)));
            denseRanks.put(q.queryId(), ids(embeddingGateway.search(cache.get(q.queryId()), DEPTH)));
        }

        RankingFunction bm25 = q -> bm25Ranks.get(q.queryId());
        RankingFunction dense = q -> denseRanks.get(q.queryId());
        BiFunction<Integer, FixtureQuery, List<Long>> hybridAt = (rrfK, q) ->
                ReciprocalRankFusion.toIds(new ReciprocalRankFusion(rrfK)
                        .fuse(List.of(bm25Ranks.get(q.queryId()), denseRanks.get(q.queryId()))));
        RankingFunction hybrid60 = q -> hybridAt.apply(60, q);

        var runner = new EvalRunner();
        var out = new StringBuilder();
        out.append("# Information retrieval eval experiments\n\n");
        out.append("- Corpus: WANDS, 42,994 products; 116 fixture queries.\n");
        out.append("- Embeddings: OpenAI text-embedding-3-small (1536), field = name+description+features.\n");
        out.append("- Retrieval depth ").append(DEPTH).append(", hnsw.ef_search=400. RRF default k=60.\n\n");

        // ---- Step 1: k-sweep ----
        out.append("## Step 1 - k-sweep (P / R / F1 by method and k)\n\n");
        for (var mode : List.of(
                Map.entry("Exact+Partial", qrelsAP),
                Map.entry("Exact only", qrelsExact))) {
            out.append("### ").append(mode.getKey()).append("\n\n");
            Map<String, RankingFunction> methods = new java.util.LinkedHashMap<>();
            methods.put("bm25", bm25);
            methods.put("dense", dense);
            methods.put("hybrid", hybrid60);
            for (String metric : List.of("precision", "recall", "f1")) {
                out.append("**").append(metric).append("@k**\n\n");
                out.append(header());
                for (var m : methods.entrySet()) {
                    out.append(String.format("| %-6s ", m.getKey()));
                    for (int k : K_VALUES) {
                        MethodMetrics mm = runner.evaluate(m.getKey(), queries, mode.getValue(), m.getValue(), k);
                        out.append(String.format("| %6.4f ", pick(mm, metric)));
                    }
                    out.append("|\n");
                }
                out.append("\n");
            }
        }

        // Determine "best k": prefer a k where dense F1 >= bm25 F1 and hybrid is max, else max hybrid margin.
        int bestK = chooseBestK(runner, queries, qrelsAP, bm25, dense, hybrid60);
        out.append("Chosen best k (Exact+Partial, dense-competitive & hybrid-max heuristic): **")
                .append(bestK).append("**\n\n");

        // ---- Step 2: per-bucket ----
        out.append("## Step 2 - per-lean-bucket (F1 and recall)\n\n");
        Map<String, List<FixtureQuery>> byLean = queries.stream().collect(Collectors.groupingBy(FixtureQuery::lean));
        for (int k : new int[]{10, bestK}) {
            for (var mode : List.of(
                    Map.entry("Exact+Partial", qrelsAP),
                    Map.entry("Exact only", qrelsExact))) {
                out.append("### k=").append(k).append(", ").append(mode.getKey()).append("\n\n");
                out.append("| bucket | n | bm25 F1 | dense F1 | hybrid F1 | bm25 R | dense R | hybrid R |\n");
                out.append("|--------|---|---------|----------|-----------|--------|---------|----------|\n");
                for (String lean : List.of("keyword", "semantic", "mixed")) {
                    List<FixtureQuery> bucket = byLean.getOrDefault(lean, List.of());
                    MethodMetrics b = runner.evaluate("bm25", bucket, mode.getValue(), bm25, k);
                    MethodMetrics d = runner.evaluate("dense", bucket, mode.getValue(), dense, k);
                    MethodMetrics h = runner.evaluate("hybrid", bucket, mode.getValue(), hybrid60, k);
                    out.append(String.format("| %-6s | %d | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f |\n",
                            lean, bucket.size(), b.f1(), d.f1(), h.f1(), b.recall(), d.recall(), h.recall()));
                }
                out.append("\n");
            }
        }

        // ---- Step 3: RRF sweep ----
        out.append("## Step 3 - RRF constant sweep (hybrid, metric k=").append(bestK).append(")\n\n");
        for (var mode : List.of(
                Map.entry("Exact+Partial", qrelsAP),
                Map.entry("Exact only", qrelsExact))) {
            out.append("### ").append(mode.getKey()).append("\n\n");
            MethodMetrics bB = runner.evaluate("bm25", queries, mode.getValue(), bm25, bestK);
            MethodMetrics dB = runner.evaluate("dense", queries, mode.getValue(), dense, bestK);
            out.append(String.format("baseline bm25 F1=%.4f, dense F1=%.4f%n%n", bB.f1(), dB.f1()));
            out.append("| rrf_k | precision | recall | f1 | beats both? |\n");
            out.append("|-------|-----------|--------|----|-------------|\n");
            for (int rrfK : RRF_VALUES) {
                RankingFunction hyb = q -> hybridAt.apply(rrfK, q);
                MethodMetrics mm = runner.evaluate("hybrid", queries, mode.getValue(), hyb, bestK);
                boolean beats = mm.f1() > bB.f1() && mm.f1() > dB.f1();
                out.append(String.format("| %5d | %9.4f | %6.4f | %.4f | %s |%n",
                        rrfK, mm.precision(), mm.recall(), mm.f1(), beats ? "yes" : "no"));
            }
            out.append("\n");
        }

        String report = out.toString();
        System.out.println(report);
        writeFile(outFile, report);
        System.out.println("Wrote " + outFile.toAbsolutePath());
    }

    private static int chooseBestK(EvalRunner runner, List<FixtureQuery> queries, Qrels qrels,
                                   RankingFunction bm25, RankingFunction dense, RankingFunction hybrid) {
        int best = 10;
        double bestScore = -1;
        boolean foundDenseCompetitive = false;
        for (int k : K_VALUES) {
            double b = runner.evaluate("bm25", queries, qrels, bm25, k).f1();
            double d = runner.evaluate("dense", queries, qrels, dense, k).f1();
            double h = runner.evaluate("hybrid", queries, qrels, hybrid, k).f1();
            double margin = h - Math.max(b, d);
            boolean denseCompetitive = d >= b;
            // Prefer configs where dense is competitive; among those, maximize hybrid margin.
            if (denseCompetitive && !foundDenseCompetitive) {
                foundDenseCompetitive = true;
                best = k;
                bestScore = margin;
            } else if (denseCompetitive == foundDenseCompetitive && margin > bestScore) {
                best = k;
                bestScore = margin;
            }
        }
        return best;
    }

    private static String header() {
        var sb = new StringBuilder("| method ");
        for (int k : K_VALUES) {
            sb.append(String.format("| k=%-4d ", k));
        }
        sb.append("|\n|--------");
        for (int ignored : K_VALUES) {
            sb.append("|--------");
        }
        sb.append("|\n");
        return sb.toString();
    }

    private static double pick(MethodMetrics mm, String metric) {
        return switch (metric) {
            case "precision" -> mm.precision();
            case "recall" -> mm.recall();
            default -> mm.f1();
        };
    }

    private static List<Long> ids(List<SearchResult> results) {
        return new ArrayList<>(results.stream().map(SearchResult::productId).toList());
    }

    private static void writeFile(Path file, String content) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
