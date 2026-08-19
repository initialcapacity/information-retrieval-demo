package io.ic.starter.tools;

import io.ic.starter.eval.FixtureQuery;
import io.ic.starter.eval.QueryEmbeddingMetadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * On-disk cache of query embeddings so the eval runs offline. Format per line:
 * {@code queryId \t f0,f1,...,f1535}.
 */
public class QueryEmbeddingCache {
    private final Path file;
    private final QueryEmbeddingMetadata expectedMetadata;
    private final Map<Long, float[]> byQueryId = new LinkedHashMap<>();
    private final Map<Long, String> serializedByQueryId = new LinkedHashMap<>();

    public QueryEmbeddingCache(Path file, List<FixtureQuery> queries) {
        this(file, queries, false);
    }

    public QueryEmbeddingCache(Path file, List<FixtureQuery> queries, boolean rebuildStaleCache) {
        this.file = file;
        this.expectedMetadata = QueryEmbeddingMetadata.expected(queries);
        Path metadataFile = QueryEmbeddingMetadata.sidecar(file);
        if (Files.exists(file)) {
            if (!Files.exists(metadataFile)) {
                if (!rebuildStaleCache) {
                    throw new IllegalStateException(
                            "Query embedding cache metadata is missing; run cacheQueryEmbeddings");
                }
            } else {
                try {
                    QueryEmbeddingMetadata.load(metadataFile).requireMatch(expectedMetadata);
                    load();
                } catch (RuntimeException e) {
                    if (!rebuildStaleCache) {
                        throw e;
                    }
                }
            }
        }
        retainQueries(queries);
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                int tab = line.indexOf('\t');
                long queryId = Long.parseLong(line.substring(0, tab).trim());
                String[] parts = line.substring(tab + 1).split(",");
                float[] vector = new float[parts.length];
                if (parts.length != QueryEmbeddingMetadata.DIMENSIONS) {
                    throw new IllegalStateException(
                            "Query " + queryId + " has " + parts.length + " embedding dimensions; expected "
                                    + QueryEmbeddingMetadata.DIMENSIONS);
                }
                for (int i = 0; i < parts.length; i++) {
                    vector[i] = Float.parseFloat(parts[i]);
                }
                byQueryId.put(queryId, vector);
                serializedByQueryId.put(queryId, line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean contains(long queryId) {
        return byQueryId.containsKey(queryId);
    }

    public float[] get(long queryId) {
        float[] vector = byQueryId.get(queryId);
        if (vector == null) {
            throw new IllegalStateException("No cached embedding for query " + queryId + "; run cacheQueryEmbeddings first");
        }
        return vector;
    }

    public void put(long queryId, float[] vector) {
        if (vector == null || vector.length != QueryEmbeddingMetadata.DIMENSIONS) {
            int actual = vector == null ? 0 : vector.length;
            throw new IllegalArgumentException(
                    "Query " + queryId + " has " + actual + " embedding dimensions; expected "
                            + QueryEmbeddingMetadata.DIMENSIONS);
        }
        byQueryId.put(queryId, vector);
        serializedByQueryId.remove(queryId);
    }

    public int size() {
        return byQueryId.size();
    }

    public void retainQueries(List<FixtureQuery> queries) {
        Set<Long> queryIds = queries.stream().map(FixtureQuery::queryId).collect(Collectors.toSet());
        byQueryId.keySet().retainAll(queryIds);
        serializedByQueryId.keySet().retainAll(queryIds);
    }

    public void save() {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            List<String> lines = new ArrayList<>(byQueryId.size());
            for (var entry : byQueryId.entrySet()) {
                String serialized = serializedByQueryId.get(entry.getKey());
                if (serialized != null) {
                    lines.add(serialized);
                    continue;
                }
                var sb = new StringBuilder();
                sb.append(entry.getKey()).append('\t');
                float[] vector = entry.getValue();
                for (int i = 0; i < vector.length; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(vector[i]);
                }
                lines.add(sb.toString());
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
            expectedMetadata.write(QueryEmbeddingMetadata.sidecar(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
