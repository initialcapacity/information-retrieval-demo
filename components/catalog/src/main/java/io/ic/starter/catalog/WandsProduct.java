package io.ic.starter.catalog;

/**
 * A single row from the WANDS product.csv, before search_text is derived.
 */
public record WandsProduct(
        long productId,
        String productName,
        String productClass,
        String categoryHierarchy,
        String productDescription,
        String productFeatures
) {
    /**
     * Document text used by BOTH the BM25 index and the embeddings, so the
     * comparison is fair: name + description + features.
     */
    public String searchText() {
        return String.join(" ",
                nullToEmpty(productName),
                nullToEmpty(productDescription),
                nullToEmpty(productFeatures)
        ).trim().replaceAll("\\s+", " ");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
