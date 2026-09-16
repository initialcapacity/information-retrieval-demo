package io.ic.starter.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

/**
 * Parses the tab-separated docs chunks (data/pgdocs/chunks.tsv). Columns
 * (with header): chunk_id, title, page, text.
 */
public class DocsLoader {

    public List<DocChunk> loadChunks(Path chunksTsv) {
        try (BufferedReader reader = Files.newBufferedReader(chunksTsv, StandardCharsets.UTF_8)) {
            var chunks = new ArrayList<DocChunk>();
            String header = reader.readLine();
            if (!"chunk_id\ttitle\tpage\ttext".equals(header)) {
                throw new IllegalArgumentException("Expected chunks TSV header in " + chunksTsv);
            }
            var ids = new HashSet<Long>();
            int lineNumber = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isEmpty()) {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length != 4) {
                    throw new IllegalArgumentException("Expected four fields at " + chunksTsv + ":" + lineNumber);
                }
                long id = Long.parseLong(fields[0].trim());
                if (!ids.add(id)) {
                    throw new IllegalArgumentException("Duplicate chunk id " + id + " at " + chunksTsv + ":" + lineNumber);
                }
                chunks.add(new DocChunk(
                        id,
                        fields[1].trim(),
                        fields[2].trim(),
                        fields[3].trim()
                ));
            }
            return chunks;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
