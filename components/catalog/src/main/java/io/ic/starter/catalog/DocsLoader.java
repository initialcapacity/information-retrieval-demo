package io.ic.starter.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the tab-separated docs chunks (data/pgdocs/chunks.tsv). Columns
 * (with header): chunk_id, title, page, text.
 */
public class DocsLoader {

    public List<DocChunk> loadChunks(Path chunksTsv) {
        try (BufferedReader reader = Files.newBufferedReader(chunksTsv, StandardCharsets.UTF_8)) {
            var chunks = new ArrayList<DocChunk>();
            String header = reader.readLine();
            if (header == null) {
                return chunks;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length < 4) {
                    continue;
                }
                chunks.add(new DocChunk(
                        Long.parseLong(fields[0].trim()),
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
