package io.ic.starter.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ic.starter.databasesupport.DataSourceFactory;
import io.ic.starter.eval.*;
import io.ic.starter.search.Bm25Gateway;
import io.ic.starter.search.EmbeddingGateway;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.ic.starter.search.RetrievalConfig.*;

/** Offline evaluation. Both console and JSON render the same captured rankings and report. */
public class EvalMain {
    public static void main(String[] args) {
        Path qrelsFile = Path.of(args.length > 0 ? args[0] : "data/pgdocs/qrels.tsv");
        Path cacheFile = Path.of(args.length > 1 ? args[1]
                : "applications/search/src/main/resources/fixture-query-embeddings.tsv");
        Path output = Path.of(args.length > 2 ? args[2]
                : "applications/search/src/main/resources/eval-results.json");
        Path corpus = Path.of(args.length > 3 ? args[3] : "data/pgdocs/chunks.tsv");

        try (var source = DataSourceFactory.create(System.getenv("DATABASE_URL"), 10, CONNECTION_INIT_SQL)) {
            List<FixtureQuery> queries = new FixtureLoader().load();
            Set<Long> ids = queries.stream().map(FixtureQuery::queryId).collect(Collectors.toSet());
            var exact = new QrelsLoader().load(qrelsFile, ids, QrelsLoader.Mode.EXACT_ONLY);
            var partial = new QrelsLoader().load(qrelsFile, ids, QrelsLoader.Mode.EXACT_AND_PARTIAL);
            var cache = new QueryEmbeddingCache(cacheFile, queries);
            var provenance = EvalProvenance.capture(source, corpus, qrelsFile, cacheFile);
            var rankings = new FixtureRankings(queries, cache, new Bm25Gateway(source), new EmbeddingGateway(source));
            var report = buildReport(queries, exact, partial, rankings, provenance);
            printReport(report);
            printSemanticValidation(queries, exact, rankings);

            // Enforce the scripted demo's claim. An experiment where hybrid loses is
            // useful, but must not silently replace the presentation's snapshot.
            requireHybridWin(report.modes().getFirst().overall());
            writeReport(output, report);
            System.out.println("Wrote eval snapshot -> " + output.toAbsolutePath());
        }
    }

    static EvalReport buildReport(List<FixtureQuery> queries, Qrels exact, Qrels partial,
                                  FixtureRankings rankings, EvalReport.Provenance provenance) {
        var byLean = queries.stream().collect(Collectors.groupingBy(FixtureQuery::lean));
        return new EvalReport(K, RRF_K, EF_SEARCH, CANDIDATE_DEPTH, provenance, rankings.fingerprint(), List.of(
                buildMode("Exact only", queries, byLean, exact, rankings),
                buildMode("Exact + Partial", queries, byLean, partial, rankings)));
    }

    static void printReport(EvalReport report) {
        System.out.println("Information retrieval eval | pgdocs | headline relevance: Exact only");
        for (var mode : report.modes()) {
            System.out.println(ResultsTable.format("OVERALL (" + mode.name() + ")", report.k(), mode.overall()));
            for (var bucket : mode.buckets()) {
                System.out.println(ResultsTable.format("LEAN=" + bucket.lean() + " (" + mode.name() + ")",
                        report.k(), bucket.metrics()));
            }
        }
    }

    static void requireHybridWin(List<MethodMetrics> metrics) {
        Map<String, Double> f1 = metrics.stream().collect(Collectors.toMap(MethodMetrics::method, MethodMetrics::f1));
        if (!(f1.get("hybrid") > f1.get("bm25") && f1.get("hybrid") > f1.get("embeddings"))) {
            throw new IllegalStateException("Validation failed: Exact-only hybrid F1 must exceed both baselines; snapshot unchanged");
        }
        System.out.println("PASS: Exact-only hybrid F1 exceeds both single-method baselines.");
    }

    static void writeReport(Path output, EvalReport report) {
        Path temporary = null;
        try {
            Path target = output.toAbsolutePath();
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".eval-", ".json");
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), report);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write eval snapshot: " + output, e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException e) { throw new UncheckedIOException(e); }
            }
        }
    }

    private static EvalReport.ModeReport buildMode(String name, List<FixtureQuery> queries,
            Map<String, List<FixtureQuery>> byLean, Qrels qrels, FixtureRankings rankings) {
        var buckets = new ArrayList<EvalReport.BucketReport>();
        for (String lean : List.of("keyword", "semantic", "mixed")) {
            var bucket = byLean.getOrDefault(lean, List.of());
            buckets.add(new EvalReport.BucketReport(lean, bucket.size(), evaluate(bucket, qrels, rankings)));
        }
        return new EvalReport.ModeReport(name, evaluate(queries, qrels, rankings), buckets);
    }

    private static List<MethodMetrics> evaluate(List<FixtureQuery> queries, Qrels qrels, FixtureRankings rankings) {
        var runner = new EvalRunner();
        return List.of("bm25", "embeddings", "hybrid").stream()
                .map(name -> runner.evaluate(name, queries, qrels, rankings.method(name), K)).toList();
    }

    private static void printSemanticValidation(List<FixtureQuery> queries, Qrels exact, FixtureRankings rankings) {
        int denseWins = 0;
        var hard = new ArrayList<String>();
        for (var query : queries.stream().filter(q -> q.lean().equals("semantic")).toList()) {
            var relevant = exact.relevantFor(query.queryId());
            if (relevant.isEmpty()) continue;
            double bm25 = Metrics.recallAtK(rankings.method("bm25").rank(query), relevant, K);
            double dense = Metrics.recallAtK(rankings.method("embeddings").rank(query), relevant, K);
            if (dense > bm25) denseWins++;
            if (bm25 == 0 && dense == 0) hard.add(query.query());
        }
        System.out.printf("SEMANTIC (Exact only): embeddings improve recall@%d on %d queries; %d have zero recall for both%n",
                K, denseWins, hard.size());
        hard.forEach(query -> System.out.println("  " + query));
    }
}
