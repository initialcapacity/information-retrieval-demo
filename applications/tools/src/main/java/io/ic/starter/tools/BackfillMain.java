package io.ic.starter.tools;

import io.ic.starter.catalog.ChunkEmbeddingInput;
import io.ic.starter.catalog.ChunksGateway;
import io.ic.starter.databasesupport.DataSourceFactory;
import io.ic.starter.search.OpenAiEmbeddingClient;
import io.ic.starter.search.VectorLiterals;

import java.util.ArrayList;
import java.util.List;

/**
 * One-shot backfill: embeds every chunk's search_text with OpenAI
 * text-embedding-3-small and writes the vectors into the embedding column.
 * Resumable: only rows with a null embedding are processed. Builds the HNSW
 * index once the column is fully populated.
 */
public class BackfillMain {
    private static final int BATCH_SIZE = 128;

    public static void main(String[] args) {
        String databaseUrl = System.getenv("DATABASE_URL");
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required for the embedding backfill");
        }

        var dataSource = DataSourceFactory.create(databaseUrl);
        var gateway = new ChunksGateway(dataSource);
        var client = new OpenAiEmbeddingClient(apiKey);

        long total = gateway.count();
        long alreadyDone = gateway.countWithEmbeddings();
        System.out.printf("Chunks: %d, already embedded: %d%n", total, alreadyDone);

        long processed = 0;
        long startTime = System.currentTimeMillis();
        while (true) {
            List<ChunkEmbeddingInput> batch = gateway.chunksMissingEmbeddings(BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            List<String> texts = batch.stream().map(ChunkEmbeddingInput::searchText).toList();
            List<float[]> vectors = client.embed(texts);

            var updates = new ArrayList<ChunksGateway.EmbeddingUpdate>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                updates.add(new ChunksGateway.EmbeddingUpdate(
                        batch.get(i).chunkId(),
                        VectorLiterals.toLiteral(vectors.get(i))
                ));
            }
            gateway.updateEmbeddings(updates);

            processed += batch.size();
            double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
            System.out.printf("  embedded %d (%.1f/s), remaining ~%d%n",
                    alreadyDone + processed, processed / Math.max(elapsed, 0.001),
                    total - alreadyDone - processed);
        }

        System.out.println("Embeddings complete: " + gateway.countWithEmbeddings() + " / " + total);
        System.out.println("Building HNSW index (this can take a moment)...");
        gateway.createEmbeddingIndex();
        System.out.println("Done.");
    }
}
