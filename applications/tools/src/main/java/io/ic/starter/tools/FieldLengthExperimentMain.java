package io.ic.starter.tools;

import io.ic.starter.databasesupport.DataSourceFactory;
import io.ic.starter.databasesupport.DatabaseTemplate;
import io.ic.starter.eval.FixtureLoader;
import io.ic.starter.eval.FixtureQuery;
import io.ic.starter.eval.QrelsLoader;
import io.ic.starter.eval.Qrels;
import io.ic.starter.search.Bm25Gateway;
import io.ic.starter.search.EmbeddingGateway;
import io.ic.starter.search.OpenAiEmbeddingClient;
import io.ic.starter.search.SearchResult;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cheap dense-quality check: does a shorter embedding field make dense retrieval
 * more competitive? On a ~30-query sample we build a per-query candidate pool
 * (relevant + BM25 top-50 + dense top-50), re-embed the pool's products with
 * name-only and name+description text, and compare recall@10 against the full
 * name+description+features embedding already in the column. Same pool per
 * variant, so it is a fair relative comparison.
 */
public class FieldLengthExperimentMain {
    private static final int POOL_DEPTH = 50;
    private static final int K = 10;

    public static void main(String[] args) {
        String databaseUrl = System.getenv("DATABASE_URL");
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY required for the field-length experiment");
        }
        Path labelCsv = Path.of(args.length > 0 ? args[0] : "data/wands/label.csv");
        Path cacheFile = Path.of(args.length > 1 ? args[1] : "data/query-embeddings.tsv");
        Path outFile = Path.of("experiments/field-length.md");

        DataSource dataSource = DataSourceFactory.create(databaseUrl, 10, "set hnsw.ef_search = 200");
        var template = new DatabaseTemplate(dataSource);
        var bm25Gateway = new Bm25Gateway(dataSource);
        var embeddingGateway = new EmbeddingGateway(dataSource);
        var client = new OpenAiEmbeddingClient(apiKey);
        var cache = new QueryEmbeddingCache(cacheFile);

        // Sample ~30 queries with a bucket mix.
        List<FixtureQuery> all = new FixtureLoader().load();
        Map<String, List<FixtureQuery>> byLean = all.stream()
                .sorted((a, b) -> Long.compare(a.queryId(), b.queryId()))
                .collect(Collectors.groupingBy(FixtureQuery::lean));
        List<FixtureQuery> sample = new ArrayList<>();
        sample.addAll(take(byLean.get("keyword"), 8));
        sample.addAll(take(byLean.get("semantic"), 12));
        sample.addAll(take(byLean.get("mixed"), 10));

        Set<Long> sampleIds = sample.stream().map(FixtureQuery::queryId).collect(Collectors.toSet());
        Qrels qrelsAP = new QrelsLoader().load(labelCsv, sampleIds, QrelsLoader.Mode.EXACT_AND_PARTIAL);
        Qrels qrelsExact = new QrelsLoader().load(labelCsv, sampleIds, QrelsLoader.Mode.EXACT_ONLY);

        // Build per-query candidate pools and the global set of pool product ids.
        Map<Long, Set<Long>> poolByQuery = new HashMap<>();
        Set<Long> allPoolIds = new LinkedHashSet<>();
        for (FixtureQuery q : sample) {
            Set<Long> pool = new LinkedHashSet<>(qrelsAP.relevantFor(q.queryId()));
            bm25Gateway.search(q.query(), POOL_DEPTH).forEach(r -> pool.add(r.productId()));
            embeddingGateway.search(cache.get(q.queryId()), POOL_DEPTH).forEach(r -> pool.add(r.productId()));
            poolByQuery.put(q.queryId(), pool);
            allPoolIds.addAll(pool);
        }
        System.out.printf("Sample: %d queries, %d unique candidate products%n", sample.size(), allPoolIds.size());

        // Fetch product text + existing full embedding for the pool.
        Map<Long, String> names = new HashMap<>();
        Map<Long, String> descriptions = new HashMap<>();
        Map<Long, float[]> fullEmbedding = new HashMap<>();
        List<Long> poolList = new ArrayList<>(allPoolIds);
        for (int i = 0; i < poolList.size(); i += 1000) {
            List<Long> chunk = poolList.subList(i, Math.min(i + 1000, poolList.size()));
            String inClause = chunk.stream().map(String::valueOf).collect(Collectors.joining(","));
            template.queryList(
                    "select product_id, coalesce(product_name,'') as n, coalesce(product_description,'') as d, embedding::text as e " +
                            "from products where product_id in (" + inClause + ")",
                    rs -> {
                        long id = rs.getLong("product_id");
                        names.put(id, rs.getString("n"));
                        descriptions.put(id, rs.getString("d"));
                        fullEmbedding.put(id, parseVector(rs.getString("e")));
                        return id;
                    });
        }

        // Re-embed the pool with name-only and name+description.
        Map<Long, float[]> nameOnlyEmbedding = embedPool(client, poolList, id -> names.get(id));
        Map<Long, float[]> nameDescEmbedding = embedPool(client, poolList,
                id -> (names.get(id) + " " + descriptions.get(id)).trim());

        // Evaluate recall@10 per variant over each query's pool.
        double[] apRecall = new double[3];   // full, nameOnly, nameDesc
        double[] exactRecall = new double[3];
        int countedAP = 0;
        int countedExact = 0;
        for (FixtureQuery q : sample) {
            float[] queryVec = cache.get(q.queryId());
            List<Long> pool = new ArrayList<>(poolByQuery.get(q.queryId()));

            Set<Long> relevantAP = qrelsAP.relevantFor(q.queryId());
            Set<Long> relevantExact = qrelsExact.relevantFor(q.queryId());

            List<Map<Long, float[]>> variants = List.of(fullEmbedding, nameOnlyEmbedding, nameDescEmbedding);
            if (!relevantAP.isEmpty()) {
                countedAP++;
                for (int v = 0; v < 3; v++) {
                    apRecall[v] += recallAtK(rankPool(pool, queryVec, variants.get(v)), relevantAP);
                }
            }
            if (!relevantExact.isEmpty()) {
                countedExact++;
                for (int v = 0; v < 3; v++) {
                    exactRecall[v] += recallAtK(rankPool(pool, queryVec, variants.get(v)), relevantExact);
                }
            }
        }

        // Cost estimate for a full 43K re-embed of each field.
        long products = template.query("select count(*) from products", rs -> rs.getLong(1)).orElse(0L);
        double avgName = template.query("select avg(length(coalesce(product_name,''))) from products", rs -> rs.getDouble(1)).orElse(0.0);
        double avgNameDesc = template.query(
                "select avg(length(coalesce(product_name,'')) + length(coalesce(product_description,''))) from products",
                rs -> rs.getDouble(1)).orElse(0.0);
        double avgSearch = template.query("select avg(length(coalesce(search_text,''))) from products", rs -> rs.getDouble(1)).orElse(0.0);

        var out = new StringBuilder();
        out.append("# Step 4 - field-length dense-quality check\n\n");
        out.append(String.format("Sample: %d queries (8 keyword, 12 semantic, 10 mixed), %d unique candidate products.%n",
                sample.size(), allPoolIds.size()));
        out.append("Recall@").append(K).append(" over each query's candidate pool (relevant + BM25 top-50 + dense top-50), same pool per variant.\n\n");
        out.append("| embedding field | mean recall@10 (Exact+Partial) | mean recall@10 (Exact only) |\n");
        out.append("|-----------------|-------------------------------|-----------------------------|\n");
        out.append(String.format("| name+desc+features (current) | %.4f | %.4f |%n", apRecall[0] / countedAP, exactRecall[0] / countedExact));
        out.append(String.format("| name only | %.4f | %.4f |%n", apRecall[1] / countedAP, exactRecall[1] / countedExact));
        out.append(String.format("| name+description | %.4f | %.4f |%n", apRecall[2] / countedAP, exactRecall[2] / countedExact));
        out.append("\n");
        out.append("### Full re-embed cost estimate (text-embedding-3-small @ $0.02 / 1M tokens, ~4 chars/token)\n\n");
        out.append(String.format("- products: %d; avg chars name=%.0f, name+desc=%.0f, search_text(current)=%.0f%n",
                products, avgName, avgNameDesc, avgSearch));
        out.append(String.format("- name-only full re-embed: ~%.2fM tokens, ~$%.3f%n",
                products * avgName / 4 / 1e6, products * avgName / 4 / 1e6 * 0.02));
        out.append(String.format("- name+description full re-embed: ~%.2fM tokens, ~$%.3f%n",
                products * avgNameDesc / 4 / 1e6, products * avgNameDesc / 4 / 1e6 * 0.02));

        String report = out.toString();
        System.out.println(report);
        writeFile(outFile, report);
        System.out.println("Wrote " + outFile.toAbsolutePath());
    }

    private static List<FixtureQuery> take(List<FixtureQuery> list, int n) {
        if (list == null) {
            return List.of();
        }
        return list.subList(0, Math.min(n, list.size()));
    }

    private static Map<Long, float[]> embedPool(OpenAiEmbeddingClient client, List<Long> ids,
                                                java.util.function.Function<Long, String> text) {
        Map<Long, float[]> result = new HashMap<>();
        int batch = 128;
        for (int i = 0; i < ids.size(); i += batch) {
            List<Long> chunk = ids.subList(i, Math.min(i + batch, ids.size()));
            List<String> texts = chunk.stream().map(text).map(t -> t.isBlank() ? " " : t).toList();
            List<float[]> vectors = client.embed(texts);
            for (int j = 0; j < chunk.size(); j++) {
                result.put(chunk.get(j), vectors.get(j));
            }
        }
        return result;
    }

    private static List<Long> rankPool(List<Long> pool, float[] queryVec, Map<Long, float[]> embeddings) {
        return pool.stream()
                .sorted((a, b) -> Double.compare(cosine(queryVec, embeddings.get(b)), cosine(queryVec, embeddings.get(a))))
                .limit(K)
                .toList();
    }

    private static double recallAtK(List<Long> topK, Set<Long> relevant) {
        if (relevant.isEmpty()) {
            return 0.0;
        }
        long hits = topK.stream().filter(relevant::contains).count();
        return (double) hits / relevant.size();
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null) {
            return -1;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-12);
    }

    private static float[] parseVector(String literal) {
        String trimmed = literal.substring(1, literal.length() - 1);
        String[] parts = trimmed.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }

    private static void writeFile(Path file, String content) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
