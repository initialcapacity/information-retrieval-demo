package io.ic.starter.tools;

import io.ic.starter.catalog.ProductsGateway;
import io.ic.starter.catalog.WandsLoader;
import io.ic.starter.catalog.WandsProduct;
import io.ic.starter.databasesupport.DataSourceFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Loads the WANDS product catalogue into Postgres, deriving search_text at
 * insert time (name + description + features).
 */
public class IngestMain {
    public static void main(String[] args) {
        String databaseUrl = System.getenv("DATABASE_URL");
        Path productCsv = Path.of(args.length > 0 ? args[0] : "data/wands/product.csv");

        var dataSource = DataSourceFactory.create(databaseUrl);
        var gateway = new ProductsGateway(dataSource);

        System.out.println("Loading products from " + productCsv.toAbsolutePath());
        List<WandsProduct> products = new WandsLoader().loadProducts(productCsv);
        System.out.println("Parsed " + products.size() + " products; inserting...");

        int batchSize = 2000;
        int inserted = 0;
        for (int i = 0; i < products.size(); i += batchSize) {
            List<WandsProduct> batch = products.subList(i, Math.min(i + batchSize, products.size()));
            gateway.insertBatch(batch);
            inserted += batch.size();
            System.out.printf("  inserted %d / %d%n", inserted, products.size());
        }

        System.out.println("Done. products in table: " + gateway.count());
    }
}
