package io.ic.starter.catalog;

public record ProductRecord(
        long productId,
        String productName,
        String productDescription,
        String productClass,
        String categoryHierarchy,
        String searchText
) {
}
