package io.ic.starter.eval;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the curated eval fixture (demo-query-set.tsv) from the classpath.
 * Columns: query_id, query, query_class, n_exact, bm25_recall_10,
 * bm25_recall_20, frac_zero_overlap, has_spec, lean.
 */
public class FixtureLoader {
    private static final String RESOURCE = "/fixtures/demo-query-set.tsv";

    public List<FixtureQuery> load() {
        try (InputStream stream = FixtureLoader.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Fixture not found on classpath: " + RESOURCE);
            }
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            var queries = new ArrayList<FixtureQuery>();
            reader.readLine(); // header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] f = line.split("\t", -1);
                queries.add(new FixtureQuery(
                        Long.parseLong(f[0].trim()),
                        unquote(f[1]),
                        unquote(f[2]),
                        Integer.parseInt(f[3].trim()),
                        Boolean.parseBoolean(f[7].trim()),
                        f[8].trim()
                ));
            }
            return queries;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Strips CSV-style quoting: fields containing a quote are wrapped in "" with inner "" doubled. */
    static String unquote(String field) {
        if (field == null) {
            return null;
        }
        String trimmed = field.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\"\"", "\"");
        }
        return trimmed;
    }
}
