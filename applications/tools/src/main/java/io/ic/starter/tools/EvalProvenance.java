package io.ic.starter.tools;

import io.ic.starter.catalog.DocChunk;
import io.ic.starter.catalog.DocsLoader;
import io.ic.starter.eval.EvalReport;
import io.ic.starter.eval.FixtureLoader;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Comparator;
import java.util.TreeMap;

import static io.ic.starter.search.RetrievalConfig.*;

final class EvalProvenance {
    static EvalReport.Provenance capture(DataSource source, Path corpus, Path qrels, Path queryCache) {
        var expected = new Fingerprint();
        var chunks = new DocsLoader().loadChunks(corpus);
        chunks.stream().sorted(Comparator.comparingLong(DocChunk::chunkId)).forEach(chunk -> {
            expected.add(Long.toString(chunk.chunkId()));
            expected.add(chunk.title());
            expected.add(chunk.page());
            expected.add(chunk.searchText());
        });
        String corpusContents = expected.finish();
        var actual = new Fingerprint();
        var embeddings = new Fingerprint();
        int count = 0;
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery(
                    "select chunk_id, title, page, search_text, embedding::text from chunks order by chunk_id")) {
                while (rows.next()) {
                    for (int i = 1; i <= 4; i++) {
                        String value = rows.getString(i);
                        if (value == null) {
                            throw new IllegalStateException("Chunk " + rows.getLong(1) + " has a null corpus field; rerun ingestDocs");
                        }
                        actual.add(value);
                    }
                    String vector = rows.getString(5);
                    if (vector == null) {
                        throw new IllegalStateException("Chunk " + rows.getLong(1) + " has no embedding; run backfillEmbeddings");
                    }
                    embeddings.add(rows.getString(1));
                    embeddings.add(vector);
                    count++;
                }
            }
            if (count == 0 || !corpusContents.equals(actual.finish())) {
                throw new IllegalStateException("Database corpus differs from " + corpus + "; run ingestDocs and backfillEmbeddings");
            }
            String postgres;
            try (var rows = statement.executeQuery("show server_version")) {
                rows.next();
                postgres = rows.getString(1);
            }
            var extensions = new TreeMap<String, String>();
            try (var rows = statement.executeQuery(
                    "select extname, extversion from pg_extension where extname in ('vector', 'pg_search')")) {
                while (rows.next()) extensions.put(rows.getString(1), rows.getString(2));
            }
            var indexes = new TreeMap<String, String>();
            try (var rows = statement.executeQuery(
                    "select indexname, indexdef from pg_indexes where schemaname = 'public' and tablename = 'chunks'")) {
                while (rows.next()) indexes.put(rows.getString(1), rows.getString(2));
            }
            return new EvalReport.Provenance(Instant.now().toString(), count,
                    Fingerprint.file(corpus), corpusContents, embeddings.finish(), fixtureFingerprint(),
                    Fingerprint.file(qrels), Fingerprint.file(queryCache), EMBEDDING_MODEL,
                    EMBEDDING_DIMENSIONS, postgres, extensions, indexes);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not capture eval provenance", e);
        }
    }

    private static String fixtureFingerprint() {
        try (InputStream input = FixtureLoader.class.getResourceAsStream("/fixtures/demo-query-set.tsv")) {
            if (input == null) throw new IllegalStateException("Missing query fixture");
            return Fingerprint.stream(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
