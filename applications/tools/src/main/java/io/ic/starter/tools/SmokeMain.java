package io.ic.starter.tools;

import io.ic.starter.catalog.ChunksGateway;
import io.ic.starter.databasesupport.DataSourceFactory;
import io.ic.starter.search.Bm25Gateway;
import io.ic.starter.search.EmbeddingGateway;
import io.ic.starter.search.OpenAiEmbeddingClient;
import io.ic.starter.search.SearchResult;

import javax.sql.DataSource;
import java.util.List;

/**
 * Hero-query smoke test: prints BM25 vs embedding top results side by side so the
 * opposite failure modes are visible without the metrics. Always embeds each
 * query live via OpenAI (requires OPENAI_API_KEY); it does not read the
 * query-embedding cache. Without a key, the embeddings column is skipped.
 */
public class SmokeMain {
    private static final String[] HERO_QUERIES = {
            "my database keeps growing even though I delete rows",
            "find rows where the text is spelled slightly wrong",
            "wal_level logical"
    };

    public static void main(String[] args) {
        String databaseUrl = System.getenv("DATABASE_URL");
        String apiKey = System.getenv("OPENAI_API_KEY");

        DataSource dataSource = DataSourceFactory.create(databaseUrl, 10, "set hnsw.ef_search = 400");
        var bm25Gateway = new Bm25Gateway(dataSource);
        var embeddingGateway = new EmbeddingGateway(dataSource);
        var chunksGateway = new ChunksGateway(dataSource);
        var client = (apiKey == null || apiKey.isBlank()) ? null : new OpenAiEmbeddingClient(apiKey);

        for (String query : HERO_QUERIES) {
            System.out.println("\n" + "=".repeat(70));
            System.out.println("QUERY: " + query);
            System.out.println("=".repeat(70));

            System.out.println("\n-- BM25 (lexical) top 5 --");
            printResults(bm25Gateway.search(query, 5), chunksGateway);

            if (client != null) {
                float[] vector = client.embed(query);
                System.out.println("\n-- Embeddings (semantic) top 5 --");
                printResults(embeddingGateway.search(vector, 5), chunksGateway);
            } else {
                System.out.println("\n-- Embeddings skipped (no OPENAI_API_KEY) --");
            }
        }
    }

    private static void printResults(List<SearchResult> results, ChunksGateway chunksGateway) {
        if (results.isEmpty()) {
            System.out.println("   (no results)");
            return;
        }
        for (SearchResult result : results) {
            var chunk = chunksGateway.find(result.chunkId());
            String title = chunk.map(c -> c.title()).orElse("?");
            String page = chunk.map(c -> c.page()).orElse("?");
            System.out.printf("   [%.4f] %s  (%s)%n", result.score(), title, page);
        }
    }
}
