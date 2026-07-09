package io.ic.starter.tools;

import io.ic.starter.search.OpenAiEmbeddingClient;
import io.ic.starter.search.QueryText;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Embeds the out-of-band hero queries (not in the eval fixture) once and writes
 * them to a text-keyed cache so the live demo resolves them offline. Add more
 * hero queries to {@link #HERO_QUERIES} as needed.
 */
public class CacheHeroQueriesMain {
    private static final List<String> HERO_QUERIES = List.of(
            "bathroom vanity knobs"
    );

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required to cache hero queries");
        }
        Path outFile = Path.of(args.length > 0 ? args[0]
                : "applications/search/src/main/resources/hero-query-embeddings.tsv");

        var client = new OpenAiEmbeddingClient(apiKey);
        List<float[]> vectors = client.embed(HERO_QUERIES);

        var lines = new ArrayList<String>(HERO_QUERIES.size());
        for (int i = 0; i < HERO_QUERIES.size(); i++) {
            lines.add(QueryText.normalize(HERO_QUERIES.get(i)) + "\t" + toCsv(vectors.get(i)));
        }
        try {
            Files.createDirectories(outFile.getParent());
            Files.write(outFile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println("Hero queries cached: " + HERO_QUERIES.size() + " -> " + outFile.toAbsolutePath());
    }

    private static String toCsv(float[] vector) {
        var sb = new StringBuilder(vector.length * 12);
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.toString();
    }
}
