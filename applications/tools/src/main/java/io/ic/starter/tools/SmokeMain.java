package io.ic.starter.tools;

import io.ic.starter.catalog.ProductsGateway;
import io.ic.starter.databasesupport.DataSourceFactory;
import io.ic.starter.search.Bm25Gateway;
import io.ic.starter.search.EmbeddingGateway;
import io.ic.starter.search.OpenAiEmbeddingClient;
import io.ic.starter.search.SearchResult;

import javax.sql.DataSource;
import java.util.List;

/**
 * Hero-query smoke test: prints BM25 vs dense top results side by side so the
 * opposite failure modes are visible without the metrics. Embeds the query live
 * (requires OPENAI_API_KEY) unless a cached vector is available.
 */
public class SmokeMain {
    private static final String[] HERO_QUERIES = {
            "bathroom vanity knobs",
            "beds that have leds",
            "writing desk 48\""
    };

    public static void main(String[] args) {
        String databaseUrl = System.getenv("DATABASE_URL");
        String apiKey = System.getenv("OPENAI_API_KEY");

        DataSource dataSource = DataSourceFactory.create(databaseUrl, 10, "set hnsw.ef_search = 400");
        var bm25Gateway = new Bm25Gateway(dataSource);
        var embeddingGateway = new EmbeddingGateway(dataSource);
        var productsGateway = new ProductsGateway(dataSource);
        var client = (apiKey == null || apiKey.isBlank()) ? null : new OpenAiEmbeddingClient(apiKey);

        for (String query : HERO_QUERIES) {
            System.out.println("\n" + "=".repeat(70));
            System.out.println("QUERY: " + query);
            System.out.println("=".repeat(70));

            System.out.println("\n-- BM25 (lexical) top 5 --");
            printResults(bm25Gateway.search(query, 5), productsGateway);

            if (client != null) {
                float[] vector = client.embed(query);
                System.out.println("\n-- Dense (semantic) top 5 --");
                printResults(embeddingGateway.search(vector, 5), productsGateway);
            } else {
                System.out.println("\n-- Dense skipped (no OPENAI_API_KEY) --");
            }
        }
    }

    private static void printResults(List<SearchResult> results, ProductsGateway productsGateway) {
        if (results.isEmpty()) {
            System.out.println("   (no results)");
            return;
        }
        for (SearchResult result : results) {
            var product = productsGateway.find(result.productId());
            String name = product.map(p -> p.productName()).orElse("?");
            String klass = product.map(p -> p.productClass()).orElse("?");
            System.out.printf("   [%.4f] %s  (%s)%n", result.score(), name, klass);
        }
    }
}
