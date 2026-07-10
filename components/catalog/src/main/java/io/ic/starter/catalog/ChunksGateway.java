package io.ic.starter.catalog;

import io.ic.starter.databasesupport.DatabaseTemplate;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ChunksGateway {

    private final DatabaseTemplate databaseTemplate;

    public ChunksGateway(DataSource dataSource) {
        this.databaseTemplate = new DatabaseTemplate(dataSource);
    }

    public long count() {
        return databaseTemplate.query("select count(*) from chunks", rs -> rs.getLong(1)).orElse(0L);
    }

    public long countWithEmbeddings() {
        return databaseTemplate
                .query("select count(*) from chunks where embedding is not null", rs -> rs.getLong(1))
                .orElse(0L);
    }

    public Optional<ChunkRecord> find(long chunkId) {
        return databaseTemplate.query(
                "select chunk_id, title, page, search_text from chunks where chunk_id = ?",
                statement -> statement.setLong(1, chunkId),
                this::mapChunk
        );
    }

    /**
     * Batch-inserts chunks, deriving search_text from title + text.
     */
    public void insertBatch(List<DocChunk> chunks) {
        databaseTemplate.inTransaction(connection -> {
            String sql = "insert into chunks (chunk_id, title, page, search_text) " +
                    "values (?, ?, ?, ?) on conflict (chunk_id) do nothing";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (DocChunk chunk : chunks) {
                    statement.setLong(1, chunk.chunkId());
                    statement.setString(2, chunk.title());
                    statement.setString(3, chunk.page());
                    statement.setString(4, chunk.searchText());
                    statement.addBatch();
                }
                statement.executeBatch();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    public Map<Long, ChunkRecord> findSummaries(Collection<Long> chunkIds) {
        var result = new HashMap<Long, ChunkRecord>();
        if (chunkIds.isEmpty()) {
            return result;
        }
        String inClause = chunkIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        databaseTemplate.queryList(
                "select chunk_id, title, page, search_text from chunks where chunk_id in (" + inClause + ")",
                rs -> {
                    var chunk = mapChunk(rs);
                    result.put(chunk.chunkId(), chunk);
                    return chunk;
                }
        );
        return result;
    }

    public List<ChunkEmbeddingInput> chunksMissingEmbeddings(int limit) {
        return databaseTemplate.queryList(
                "select chunk_id, search_text from chunks where embedding is null order by chunk_id limit ?",
                statement -> statement.setInt(1, limit),
                rs -> new ChunkEmbeddingInput(rs.getLong("chunk_id"), rs.getString("search_text"))
        );
    }

    /**
     * Writes embeddings for a batch of chunks in one transaction. Each vector is
     * a pgvector literal string, e.g. "[0.1,0.2,...]".
     */
    public void updateEmbeddings(List<EmbeddingUpdate> updates) {
        databaseTemplate.inTransaction(connection -> {
            String sql = "update chunks set embedding = ?::vector where chunk_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (EmbeddingUpdate update : updates) {
                    statement.setString(1, update.vectorLiteral());
                    statement.setLong(2, update.chunkId());
                    statement.addBatch();
                }
                statement.executeBatch();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    public void createEmbeddingIndex() {
        databaseTemplate.execute(
                "create index if not exists chunks_embedding on chunks using hnsw (embedding vector_cosine_ops)");
    }

    public record EmbeddingUpdate(long chunkId, String vectorLiteral) {
    }

    private ChunkRecord mapChunk(ResultSet rs) throws SQLException {
        return new ChunkRecord(
                rs.getLong("chunk_id"),
                rs.getString("title"),
                rs.getString("page"),
                rs.getString("search_text")
        );
    }
}
