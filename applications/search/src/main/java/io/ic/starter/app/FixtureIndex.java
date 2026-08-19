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
 * relevance for a chunk under that query, using the committed fixture qrels
 * (Exact + Partial). All data is read from the classpath - no network, no DB.
 */
public class FixtureIndex {
    private static final String QRELS_RESOURCE = "/fixture-qrels.tsv";

    private final Map<String, Long> queryIdByText = new HashMap<>();
    private final Map<Long, Map<Long, String>> relevanceByQuery = new HashMap<>();

    public FixtureIndex() {
        for (FixtureQuery query : new FixtureLoader().load()) {
            queryIdByText.put(QueryText.normalize(query.query()), query.queryId());
        }
        // Canonical qrels: id, query_id, chunk_id, label.
        readQrels(QRELS_RESOURCE, f -> {
            long queryId = Long.parseLong(f[1].trim());
            long chunkId = Long.parseLong(f[2].trim());
            String label = f[3].trim();
            if (label.equals("Exact") || label.equals("Partial")) {
                relevanceByQuery.computeIfAbsent(queryId, _ -> new HashMap<>()).put(chunkId, label);
            }
        });
    }

    public Optional<Long> queryId(String text) {
        return Optional.ofNullable(queryIdByText.get(QueryText.normalize(text)));
    }

    /** Returns "Exact", "Partial", or null. */
    public String relevance(long queryId, long chunkId) {
        return relevanceByQuery.getOrDefault(queryId, Map.of()).get(chunkId);
    }

    private void readQrels(String resource, Consumer<String[]> onRow) {
        try (InputStream stream = FixtureIndex.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + resource + " on classpath");
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
