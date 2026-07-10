package io.ic.starter.tools;

import io.ic.starter.catalog.ChunksGateway;
import io.ic.starter.catalog.DocChunk;
import io.ic.starter.catalog.DocsLoader;
import io.ic.starter.databasesupport.DataSourceFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Loads the chunked PostgreSQL docs into Postgres, deriving search_text at
 * insert time (title + text).
 */
public class IngestMain {
    public static void main(String[] args) {
        String databaseUrl = System.getenv("DATABASE_URL");
        Path chunksTsv = Path.of(args.length > 0 ? args[0] : "data/pgdocs/chunks.tsv");

        var dataSource = DataSourceFactory.create(databaseUrl);
        var gateway = new ChunksGateway(dataSource);

        System.out.println("Loading chunks from " + chunksTsv.toAbsolutePath());
        List<DocChunk> chunks = new DocsLoader().loadChunks(chunksTsv);
        System.out.println("Parsed " + chunks.size() + " chunks; inserting...");

        int batchSize = 500;
        int inserted = 0;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<DocChunk> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
            gateway.insertBatch(batch);
            inserted += batch.size();
            System.out.printf("  inserted %d / %d%n", inserted, chunks.size());
        }

        System.out.println("Done. chunks in table: " + gateway.count());
    }
}
