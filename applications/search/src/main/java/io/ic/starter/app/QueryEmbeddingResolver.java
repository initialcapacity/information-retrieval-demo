package io.ic.starter.app;

import io.ic.starter.eval.FixtureLoader;
import io.ic.starter.eval.FixtureQuery;
import io.ic.starter.search.EmbeddingClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves a query text to an embedding, preferring the committed caches (fixture
 * queries and out-of-band hero queries, so the demo runs offline) and falling
 * back to a live embed for arbitrary queries.
 */
public class QueryEmbeddingResolver {
    private static final String FIXTURE_CACHE_RESOURCE = "/fixture-query-embeddings.tsv";
    private static final String HERO_CACHE_RESOURCE = "/hero-query-embeddings.tsv";

    private final Map<String, float[]> cachedByText = new HashMap<>();
    private final EmbeddingClient liveClient;

    public record Resolved(float[] vector, String source) {
    }

    public QueryEmbeddingResolver(EmbeddingClient liveClient) {
        this.liveClient = liveClient;
        loadCache();
    }

    public Resolved resolve(String queryText) {
        float[] cached = cachedByText.get(normalize(queryText));
        if (cached != null) {
            return new Resolved(cached, "cached");
        }
        if (liveClient == null) {
            throw new IllegalStateException(
                    "No cached embedding for this query and OPENAI_API_KEY is not set for a live embed");
        }
        return new Resolved(liveClient.embed(queryText), "live");
    }

    private void loadCache() {
        loadFixtureCache();
        loadHeroCache();
    }

    /** Fixture cache is keyed by query_id; map each id back to its query text. */
    private void loadFixtureCache() {
        Map<Long, String> textById = new HashMap<>();
        for (FixtureQuery q : new FixtureLoader().load()) {
            textById.put(q.queryId(), q.query());
        }
        eachCacheLine(FIXTURE_CACHE_RESOURCE, (key, vector) -> {
            String text = textById.get(Long.parseLong(key));
            if (text != null) {
                cachedByText.put(normalize(text), vector);
            }
        });
    }

    /** Hero cache is keyed by the normalized query text directly (out-of-band queries). */
    private void loadHeroCache() {
        eachCacheLine(HERO_CACHE_RESOURCE, (key, vector) -> cachedByText.put(normalize(key), vector));
    }

    private void eachCacheLine(String resource, java.util.function.BiConsumer<String, float[]> consumer) {
        try (InputStream stream = QueryEmbeddingResolver.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return;
            }
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                int tab = line.indexOf('\t');
                consumer.accept(line.substring(0, tab).trim(), parseVector(line.substring(tab + 1)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static float[] parseVector(String csv) {
        String[] parts = csv.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
