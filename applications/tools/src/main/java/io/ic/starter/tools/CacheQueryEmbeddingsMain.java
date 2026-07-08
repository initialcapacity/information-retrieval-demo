package io.ic.starter.tools;

import io.ic.starter.eval.FixtureLoader;
import io.ic.starter.eval.FixtureQuery;
import io.ic.starter.search.OpenAiEmbeddingClient;

import java.nio.file.Path;
import java.util.List;

/**
 * Embeds every fixture query once and writes the vectors to the query-embedding
 * cache so the eval can run offline.
 */
public class CacheQueryEmbeddingsMain {
    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required to cache query embeddings");
        }
        Path cacheFile = Path.of(args.length > 0 ? args[0] : "data/query-embeddings.tsv");

        List<FixtureQuery> queries = new FixtureLoader().load();
        var cache = new QueryEmbeddingCache(cacheFile);
        var client = new OpenAiEmbeddingClient(apiKey);

        List<FixtureQuery> missing = queries.stream().filter(q -> !cache.contains(q.queryId())).toList();
        System.out.printf("Fixture queries: %d, cached: %d, to embed: %d%n",
                queries.size(), cache.size(), missing.size());

        if (!missing.isEmpty()) {
            List<String> texts = missing.stream().map(FixtureQuery::query).toList();
            List<float[]> vectors = client.embed(texts);
            for (int i = 0; i < missing.size(); i++) {
                cache.put(missing.get(i).queryId(), vectors.get(i));
            }
            cache.save();
        }
        System.out.println("Query embeddings cached: " + cache.size() + " -> " + cacheFile.toAbsolutePath());
    }
}
