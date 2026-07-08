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
 * Resolves a query text to an embedding, preferring the committed cache for the
 * fixture queries (so demo runs offline) and falling back to a live embed for
 * arbitrary queries.
 */
public class QueryEmbeddingResolver {
    private static final String CACHE_RESOURCE = "/fixture-query-embeddings.tsv";

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
        // Map cached vectors (keyed by query_id) back to query text via the fixture.
        Map<Long, String> textById = new HashMap<>();
        for (FixtureQuery q : new FixtureLoader().load()) {
            textById.put(q.queryId(), q.query());
        }
        try (InputStream stream = QueryEmbeddingResolver.class.getResourceAsStream(CACHE_RESOURCE)) {
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
                long queryId = Long.parseLong(line.substring(0, tab).trim());
                String text = textById.get(queryId);
                if (text == null) {
                    continue;
                }
                String[] parts = line.substring(tab + 1).split(",");
                float[] vector = new float[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    vector[i] = Float.parseFloat(parts[i]);
                }
                cachedByText.put(normalize(text), vector);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
