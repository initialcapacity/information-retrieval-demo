package io.ic.starter.search;

import io.ic.starter.databasesupport.DatabaseTemplate;

import javax.sql.DataSource;
import java.util.List;

/**
 * Lexical retrieval via the pg_search (ParadeDB) BM25 index. Uses paradedb.match
 * so raw user queries (with quotes, SKUs, inch marks) are tokenized as terms
 * rather than parsed as query-string syntax.
 */
public class Bm25Gateway {
    private final DatabaseTemplate databaseTemplate;

    public Bm25Gateway(DataSource dataSource) {
        this.databaseTemplate = new DatabaseTemplate(dataSource);
    }

    public List<SearchResult> search(String query, int limit) {
        return databaseTemplate.queryList(
                "select chunk_id, paradedb.score(chunk_id) as score " +
                        "from chunks " +
                        "where chunk_id @@@ paradedb.match('search_text', ?) " +
                        "order by score desc, chunk_id " +
                        "limit ?",
                statement -> {
                    statement.setString(1, query);
                    statement.setInt(2, limit);
                },
                rs -> new SearchResult(rs.getLong("chunk_id"), rs.getDouble("score"))
        );
    }
}
