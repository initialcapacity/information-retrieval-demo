package io.ic.starter.app;

import io.ic.starter.eval.FixtureLoader;
import io.ic.starter.eval.FixtureQuery;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
            queryIdByText.put(normalize(query.query()), query.queryId());
        }
        loadQrels();
        loadHeroQrels();
    }

    public Optional<Long> queryId(String text) {
        return Optional.ofNullable(queryIdByText.get(normalize(text)));
    }

    /** Returns "Exact", "Partial", or null. */
    public String relevance(long queryId, long productId) {
        return relevanceByQuery.getOrDefault(queryId, Map.of()).get(productId);
    }

    private void loadQrels() {
        try (InputStream stream = FixtureIndex.class.getResourceAsStream(QRELS_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + QRELS_RESOURCE + " on classpath");
            }
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            reader.readLine(); // header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] f = line.split("\t", -1);
                long queryId = Long.parseLong(f[0].trim());
                long productId = Long.parseLong(f[1].trim());
                relevanceByQuery.computeIfAbsent(queryId, _ -> new HashMap<>()).put(productId, f[2].trim());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Hero qrels carry the query text (they are not in the fixture), so each row
     * registers both the text->id mapping and the product relevance.
     */
    private void loadHeroQrels() {
        try (InputStream stream = FixtureIndex.class.getResourceAsStream(HERO_QRELS_RESOURCE)) {
            if (stream == null) {
                return; // hero qrels are optional
            }
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            reader.readLine(); // header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] f = line.split("\t", -1);
                long queryId = Long.parseLong(f[0].trim());
                String query = f[1];
                long productId = Long.parseLong(f[2].trim());
                queryIdByText.put(normalize(query), queryId);
                relevanceByQuery.computeIfAbsent(queryId, _ -> new HashMap<>()).put(productId, f[3].trim());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
