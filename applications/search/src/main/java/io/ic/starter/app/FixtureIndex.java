package io.ic.starter.app;

import io.ic.starter.eval.FixtureLoader;
import io.ic.starter.eval.FixtureQuery;
import io.ic.starter.search.QueryText;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Maps a typed query back to a fixture query (by normalized text) and answers
 * relevance for a product under that query, using the committed fixture qrels
 * (Exact + Partial). All data is read from the classpath - no network, no DB.
 *
 * Also loads hero qrels: relevance labels for out-of-band demo queries (e.g.
 * "bathroom vanity knobs") that are deliberately excluded from the eval fixture
 * but should still show Exact/Partial badges in the UI. This does not affect the
 * eval, which reads only the fixture.
 */
public class FixtureIndex {
    private static final String QRELS_RESOURCE = "/fixture-qrels.tsv";
    private static final String HERO_QRELS_RESOURCE = "/hero-qrels.tsv";

    private final Map<String, Long> queryIdByText = new HashMap<>();
    private final Map<Long, Map<Long, String>> relevanceByQuery = new HashMap<>();

    public FixtureIndex() {
        for (FixtureQuery query : new FixtureLoader().load()) {
            queryIdByText.put(QueryText.normalize(query.query()), query.queryId());
        }
        // Fixture qrels (required): query_id, product_id, label.
        readQrels(QRELS_RESOURCE, true, f -> {
            long queryId = Long.parseLong(f[0].trim());
            long productId = Long.parseLong(f[1].trim());
            relevanceByQuery.computeIfAbsent(queryId, _ -> new HashMap<>()).put(productId, f[2].trim());
        });
        // Hero qrels (optional) carry the query text, so each row also registers
        // the text->id mapping: query_id, query, product_id, label.
        readQrels(HERO_QRELS_RESOURCE, false, f -> {
            long queryId = Long.parseLong(f[0].trim());
            long productId = Long.parseLong(f[2].trim());
            queryIdByText.put(QueryText.normalize(f[1]), queryId);
            relevanceByQuery.computeIfAbsent(queryId, _ -> new HashMap<>()).put(productId, f[3].trim());
        });
    }

    public Optional<Long> queryId(String text) {
        return Optional.ofNullable(queryIdByText.get(QueryText.normalize(text)));
    }

    /** Returns "Exact", "Partial", or null. */
    public String relevance(long queryId, long productId) {
        return relevanceByQuery.getOrDefault(queryId, Map.of()).get(productId);
    }

    private void readQrels(String resource, boolean required, Consumer<String[]> onRow) {
        try (InputStream stream = FixtureIndex.class.getResourceAsStream(resource)) {
            if (stream == null) {
                if (required) {
                    throw new IllegalStateException("Missing " + resource + " on classpath");
                }
                return;
            }
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            reader.readLine(); // header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                onRow.accept(line.split("\t", -1));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
