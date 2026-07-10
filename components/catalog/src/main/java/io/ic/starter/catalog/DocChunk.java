package io.ic.starter.catalog;

/**
 * One section-level chunk of the PostgreSQL documentation, as parsed from the
 * corpus TSV. searchText derives as "title. text" so BM25 and embeddings index
 * the same string (and so ingest matches the corpus generator exactly).
 */
public record DocChunk(
        long chunkId,
        String title,
        String page,
        String text
) {
    public String searchText() {
        String t = title == null ? "" : title.trim();
        String body = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return normalize(body);
        }
        return normalize(t + ". " + body);
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
