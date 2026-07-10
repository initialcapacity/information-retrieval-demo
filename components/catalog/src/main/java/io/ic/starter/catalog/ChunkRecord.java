package io.ic.starter.catalog;

public record ChunkRecord(
        long chunkId,
        String title,
        String page,
        String searchText
) {
}
