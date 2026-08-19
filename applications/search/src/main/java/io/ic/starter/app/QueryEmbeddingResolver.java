package io.ic.starter.app;

import io.ic.starter.eval.FixtureLoader;
import io.ic.starter.eval.FixtureQuery;
import io.ic.starter.eval.QueryEmbeddingMetadata;
import io.ic.starter.search.EmbeddingClient;
import io.ic.starter.search.QueryText;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a query text to an embedding, preferring the committed fixture cache
 * so the scripted demo runs offline and falling back to a live embed for arbitrary
 * queries.
 */
public class QueryEmbeddingResolver {
    public static final int MAX_QUERY_CHARACTERS = 500;
    private static final int MAX_LIVE_CACHE_ENTRIES = 100;
    private static final String FIXTURE_CACHE_RESOURCE = "/fixture-query-embeddings.tsv";
    private static final String FIXTURE_CACHE_METADATA_RESOURCE = "/fixture-query-embeddings.tsv.properties";

    private final Map<String, float[]> cachedByText = new HashMap<>();
    private final Map<String, float[]> liveByText;
    private final EmbeddingClient liveClient;

    public record Resolved(float[] vector, String source) {
    }

    public QueryEmbeddingResolver(EmbeddingClient liveClient) {
        this.liveClient = liveClient;
        this.liveByText = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                return size() > MAX_LIVE_CACHE_ENTRIES;
            }
        });
        loadCache();
    }

    public Resolved resolve(String queryText) {
        if (queryText != null && queryText.length() > MAX_QUERY_CHARACTERS) {
            throw new IllegalArgumentException(
                    "Queries are limited to " + MAX_QUERY_CHARACTERS + " characters");
        }
        String normalized = QueryText.normalize(queryText);
        float[] cached = cachedByText.get(normalized);
        if (cached != null) {
            return new Resolved(cached, "cached");
        }
        float[] liveCached = liveByText.get(normalized);
        if (liveCached != null) {
            return new Resolved(liveCached, "live cache");
        }
        if (liveClient == null) {
            throw new IllegalStateException(
                    "No cached embedding for this query and OPENAI_API_KEY is not set for a live embed");
        }
        float[] vector = liveClient.embed(queryText);
        requireDimensions(vector);
        liveByText.put(normalized, vector);
        return new Resolved(vector, "live");
    }

    private void loadCache() {
        loadFixtureCache();
    }

    /** Fixture cache is keyed by query_id; map each id back to its query text. */
    private void loadFixtureCache() {
        List<FixtureQuery> queries = new FixtureLoader().load();
        validateMetadata(queries);
        Map<Long, String> textById = new HashMap<>();
        for (FixtureQuery q : queries) {
            textById.put(q.queryId(), q.query());
        }
        eachCacheLine(FIXTURE_CACHE_RESOURCE, (key, vector) -> {
            String text = textById.get(Long.parseLong(key));
            if (text != null) {
                cachedByText.put(QueryText.normalize(text), vector);
            }
        });
        if (cachedByText.size() != queries.size()) {
            throw new IllegalStateException(
                    "Query embedding cache contains " + cachedByText.size() + " fixture queries; expected "
                            + queries.size());
        }
    }

    private void validateMetadata(List<FixtureQuery> queries) {
        try (InputStream stream = QueryEmbeddingResolver.class.getResourceAsStream(FIXTURE_CACHE_METADATA_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Missing query embedding cache metadata: " + FIXTURE_CACHE_METADATA_RESOURCE);
            }
            QueryEmbeddingMetadata.load(stream).requireMatch(QueryEmbeddingMetadata.expected(queries));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
                if (tab < 1) {
                    throw new IllegalStateException("Malformed query embedding cache line in " + resource);
                }
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
        requireDimensions(vector);
        return vector;
    }

    private static void requireDimensions(float[] vector) {
        if (vector == null || vector.length != QueryEmbeddingMetadata.DIMENSIONS) {
            int actual = vector == null ? 0 : vector.length;
            throw new IllegalStateException(
                    "Expected " + QueryEmbeddingMetadata.DIMENSIONS + " embedding dimensions, got " + actual);
        }
    }
}
