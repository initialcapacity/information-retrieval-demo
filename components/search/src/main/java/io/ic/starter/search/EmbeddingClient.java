package io.ic.starter.search;

import java.util.List;

/**
 * Provider-agnostic embedding client so the OpenAI implementation is swappable.
 */
public interface EmbeddingClient {
    List<float[]> embed(List<String> texts);

    default float[] embed(String text) {
        return embed(List.of(text)).getFirst();
    }
}
