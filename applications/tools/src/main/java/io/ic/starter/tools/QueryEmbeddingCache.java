package io.ic.starter.tools;

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

/**
 * On-disk cache of query embeddings so the eval runs offline. Format per line:
 * {@code queryId \t f0,f1,...,f1535}.
 */
public class QueryEmbeddingCache {
    private final Path file;
    private final Map<Long, float[]> byQueryId = new LinkedHashMap<>();

    public QueryEmbeddingCache(Path file) {
        this.file = file;
        load();
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
                for (int i = 0; i < parts.length; i++) {
                    vector[i] = Float.parseFloat(parts[i]);
                }
                byQueryId.put(queryId, vector);
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
        byQueryId.put(queryId, vector);
    }

    public int size() {
        return byQueryId.size();
    }

    public void save() {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            List<String> lines = new ArrayList<>(byQueryId.size());
            for (var entry : byQueryId.entrySet()) {
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
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
