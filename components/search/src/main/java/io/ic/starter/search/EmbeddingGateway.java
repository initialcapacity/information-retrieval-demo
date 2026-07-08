package io.ic.starter.search;

import io.ic.starter.databasesupport.DatabaseTemplate;

import javax.sql.DataSource;
import java.util.List;

/**
 * Dense retrieval via pgvector cosine distance over the embedding column.
 */
public class EmbeddingGateway {
    private final DatabaseTemplate databaseTemplate;

    public EmbeddingGateway(DataSource dataSource) {
        this.databaseTemplate = new DatabaseTemplate(dataSource);
    }

    public List<SearchResult> search(float[] queryEmbedding, int limit) {
        String literal = VectorLiterals.toLiteral(queryEmbedding);
        return databaseTemplate.queryList(
                "select product_id, 1 - (embedding <=> ?::vector) as similarity " +
                        "from products " +
                        "where embedding is not null " +
                        "order by embedding <=> ?::vector " +
                        "limit ?",
                statement -> {
                    statement.setString(1, literal);
                    statement.setString(2, literal);
                    statement.setInt(3, limit);
                },
                rs -> new SearchResult(rs.getLong("product_id"), rs.getDouble("similarity"))
        );
    }
}
