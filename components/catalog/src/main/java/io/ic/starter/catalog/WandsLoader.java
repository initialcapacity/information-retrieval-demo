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
 * Parses the tab-separated WANDS product.csv. Columns (with header):
 * product_id, product_name, product_class, category hierarchy,
 * product_description, product_features, rating_count, average_rating, review_count.
 */
public class WandsLoader {

    public List<WandsProduct> loadProducts(Path productCsv) {
        try (BufferedReader reader = Files.newBufferedReader(productCsv, StandardCharsets.UTF_8)) {
            var products = new ArrayList<WandsProduct>();
            String header = reader.readLine();
            if (header == null) {
                return products;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length < 6) {
                    continue;
                }
                products.add(new WandsProduct(
                        Long.parseLong(fields[0].trim()),
                        blankToNull(fields[1]),
                        blankToNull(fields[2]),
                        blankToNull(fields[3]),
                        blankToNull(fields[4]),
                        blankToNull(fields[5])
                ));
            }
            return products;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
