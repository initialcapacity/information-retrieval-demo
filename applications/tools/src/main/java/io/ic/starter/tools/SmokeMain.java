package io.ic.starter.tools;

import io.ic.starter.catalog.ChunksGateway;
import io.ic.starter.databasesupport.DataSourceFactory;
import io.ic.starter.eval.DemoQueries;
import io.ic.starter.eval.FixtureLoader;
import io.ic.starter.eval.FixtureQuery;
import io.ic.starter.eval.QrelsLoader;
import io.ic.starter.search.Bm25Gateway;
import io.ic.starter.search.EmbeddingGateway;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static io.ic.starter.search.RetrievalConfig.*;

/** Offline rehearsal of all three hero queries, with the same cutoff and fusion as the app. */
public class SmokeMain {
    public static void main(String[] args) {
        var fixture = new FixtureLoader().load();
        var byText = fixture.stream().collect(Collectors.toMap(FixtureQuery::query, query -> query));
        List<FixtureQuery> heroes = DemoQueries.HERO_QUERIES.stream().map(text -> {
            var query = byText.get(text);
            if (query == null) throw new IllegalStateException("Hero query is missing from the fixture: " + text);
            return query;
        }).toList();
        var cache = new QueryEmbeddingCache(
                Path.of("applications/search/src/main/resources/fixture-query-embeddings.tsv"), fixture);
        var qrels = new QrelsLoader().load(Path.of("data/pgdocs/qrels.tsv"),
                heroes.stream().map(FixtureQuery::queryId).collect(Collectors.toSet()), QrelsLoader.Mode.EXACT_ONLY);

        try (var source = DataSourceFactory.create(System.getenv("DATABASE_URL"), 10, CONNECTION_INIT_SQL)) {
            var chunks = new ChunksGateway(source);
            if (chunks.count() == 0 || chunks.countWithEmbeddings() != chunks.count()) {
                throw new IllegalStateException("Smoke check needs a fully embedded corpus; run ingestDocs and backfillEmbeddings");
            }
            var rankings = new FixtureRankings(heroes, cache, new Bm25Gateway(source), new EmbeddingGateway(source));
            for (int i = 0; i < heroes.size(); i++) {
                var query = heroes.get(i);
                System.out.println("\nQUERY: " + query.query() + " (cached embedding)");
                int[] hits = new int[3];
                int methodIndex = 0;
                for (String method : List.of("bm25", "embeddings", "hybrid")) {
                    var ids = rankings.method(method).rank(query).stream().limit(K).toList();
                    var summaries = chunks.findSummaries(ids);
                    var relevant = qrels.relevantFor(query.queryId());
                    hits[methodIndex++] = (int) ids.stream().filter(relevant::contains).count();
                    System.out.printf("-- %s top %d: %d/%d Exact --%n", method, K, hits[methodIndex - 1], relevant.size());
                    int rank = 1;
                    for (long id : ids) {
                        var chunk = summaries.get(id);
                        if (chunk == null) throw new IllegalStateException("Missing retrieved chunk " + id);
                        System.out.printf("  %2d. %s%s (%s)%n", rank++, relevant.contains(id) ? "[Exact] " : "",
                                chunk.title(), chunk.page());
                    }
                }
                boolean matchesScript = switch (i) {
                    case 0 -> hits[1] > hits[0];
                    case 1 -> hits[0] > hits[1];
                    default -> hits[2] > Math.max(hits[0], hits[1]);
                };
                if (!matchesScript) {
                    throw new IllegalStateException("Hero query no longer demonstrates the scripted comparison: " + query.query());
                }
            }
            System.out.println("\nPASS: all three hero comparisons hold at k=" + K + ".");
        }
    }
}
