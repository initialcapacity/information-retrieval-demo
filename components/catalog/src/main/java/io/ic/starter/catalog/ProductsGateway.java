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

public class ProductsGateway {

    private final DatabaseTemplate databaseTemplate;

    public ProductsGateway(DataSource dataSource) {
        this.databaseTemplate = new DatabaseTemplate(dataSource);
    }

    public long count() {
        return databaseTemplate.query("select count(*) from products", rs -> rs.getLong(1)).orElse(0L);
    }

    public long countWithEmbeddings() {
        return databaseTemplate
                .query("select count(*) from products where embedding is not null", rs -> rs.getLong(1))
                .orElse(0L);
    }

    public Optional<ProductRecord> find(long productId) {
        return databaseTemplate.query(
                "select product_id, product_name, product_description, product_class, category_hierarchy, search_text " +
                        "from products where product_id = ?",
                statement -> statement.setLong(1, productId),
                this::mapProduct
        );
    }

    /**
     * Batch-inserts products, deriving search_text from name + description + features.
     */
    public void insertBatch(List<WandsProduct> products) {
        databaseTemplate.inTransaction(connection -> {
            String sql = "insert into products " +
                    "(product_id, product_name, product_description, product_features, product_class, category_hierarchy, search_text) " +
                    "values (?, ?, ?, ?, ?, ?, ?) on conflict (product_id) do nothing";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (WandsProduct product : products) {
                    statement.setLong(1, product.productId());
                    statement.setString(2, product.productName());
                    statement.setString(3, product.productDescription());
                    statement.setString(4, product.productFeatures());
                    statement.setString(5, product.productClass());
                    statement.setString(6, product.categoryHierarchy());
                    statement.setString(7, product.searchText());
                    statement.addBatch();
                }
                statement.executeBatch();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    public Map<Long, ProductRecord> findSummaries(Collection<Long> productIds) {
        var result = new HashMap<Long, ProductRecord>();
        if (productIds.isEmpty()) {
            return result;
        }
        String inClause = productIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        databaseTemplate.queryList(
                "select product_id, product_name, product_description, product_class, category_hierarchy, search_text " +
                        "from products where product_id in (" + inClause + ")",
                rs -> {
                    var product = mapProduct(rs);
                    result.put(product.productId(), product);
                    return product;
                }
        );
        return result;
    }

    public List<ProductEmbeddingInput> productsMissingEmbeddings(int limit) {
        return databaseTemplate.queryList(
                "select product_id, search_text from products where embedding is null order by product_id limit ?",
                statement -> statement.setInt(1, limit),
                rs -> new ProductEmbeddingInput(rs.getLong("product_id"), rs.getString("search_text"))
        );
    }

    /**
     * Writes embeddings for a batch of products in one transaction. Each vector is
     * a pgvector literal string, e.g. "[0.1,0.2,...]".
     */
    public void updateEmbeddings(List<EmbeddingUpdate> updates) {
        databaseTemplate.inTransaction(connection -> {
            String sql = "update products set embedding = ?::vector where product_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (EmbeddingUpdate update : updates) {
                    statement.setString(1, update.vectorLiteral());
                    statement.setLong(2, update.productId());
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
                "create index if not exists products_embedding on products using hnsw (embedding vector_cosine_ops)");
    }

    public record EmbeddingUpdate(long productId, String vectorLiteral) {
    }

    private ProductRecord mapProduct(ResultSet rs) throws SQLException {
        return new ProductRecord(
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getString("product_description"),
                rs.getString("product_class"),
                rs.getString("category_hierarchy"),
                rs.getString("search_text")
        );
    }
}
