package io.ic.starter.catalog;

/**
 * A product that still needs an embedding: just the id and the text to embed.
 */
public record ProductEmbeddingInput(long productId, String searchText) {
}
