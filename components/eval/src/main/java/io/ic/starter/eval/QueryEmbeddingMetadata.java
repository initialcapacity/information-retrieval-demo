package io.ic.starter.eval;

import io.ic.starter.search.RetrievalConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

/**
 * Identifies the model and exact fixture text used to produce a query-embedding
 * cache. A matching query id is insufficient: changing its text must invalidate
 * the old vector.
 */
public record QueryEmbeddingMetadata(String model, int dimensions, String fixtureSha256) {
    public static final String MODEL = RetrievalConfig.EMBEDDING_MODEL;
    public static final int DIMENSIONS = RetrievalConfig.EMBEDDING_DIMENSIONS;

    public static QueryEmbeddingMetadata expected(List<FixtureQuery> queries) {
        return new QueryEmbeddingMetadata(MODEL, DIMENSIONS, fingerprint(queries));
    }

    public static QueryEmbeddingMetadata load(InputStream stream) {
        var properties = new Properties();
        try {
            properties.load(stream);
            return new QueryEmbeddingMetadata(
                    require(properties, "model"),
                    Integer.parseInt(require(properties, "dimensions")),
                    require(properties, "fixtureSha256"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static QueryEmbeddingMetadata load(Path path) {
        try (InputStream stream = Files.newInputStream(path)) {
            return load(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void write(Path path) {
        String content = "model=" + model + "\n"
                + "dimensions=" + dimensions + "\n"
                + "fixtureSha256=" + fixtureSha256 + "\n";
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void requireMatch(QueryEmbeddingMetadata expected) {
        if (!equals(expected)) {
            throw new IllegalStateException(
                    "Query embedding cache metadata is stale; run cacheQueryEmbeddings");
        }
    }

    public static Path sidecar(Path cacheFile) {
        return cacheFile.resolveSibling(cacheFile.getFileName() + ".properties");
    }

    private static String fingerprint(List<FixtureQuery> queries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            queries.stream()
                    .sorted(Comparator.comparingLong(FixtureQuery::queryId))
                    .map(query -> query.queryId() + "\t" + query.query() + "\n")
                    .forEach(row -> digest.update(row.getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String require(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing query embedding metadata property: " + key);
        }
        return value.trim();
    }
}
