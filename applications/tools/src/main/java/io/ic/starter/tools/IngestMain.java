package io.ic.starter.tools;

import com.zaxxer.hikari.HikariDataSource;
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

        try (var dataSource = (HikariDataSource) DataSourceFactory.create(databaseUrl)) {
            var gateway = new ChunksGateway(dataSource);

            System.out.println("Loading chunks from " + chunksTsv.toAbsolutePath());
            List<DocChunk> chunks = new DocsLoader().loadChunks(chunksTsv);
            System.out.println("Parsed " + chunks.size() + " chunks; reconciling...");

            ChunksGateway.Reconciliation result = gateway.replaceAll(chunks);
            System.out.printf("Reconciled %d chunks: %d inserted or changed, %d deleted%n",
                    result.incoming(), result.insertedOrChanged(), result.deleted());
            System.out.println("Done. chunks in table: " + gateway.count());
        }
    }
}
