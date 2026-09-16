package io.ic.starter.search;

/** Shared settings for the web demo, offline eval, and smoke check. */
public final class RetrievalConfig {
    public static final int K = 10;
    public static final int CANDIDATE_DEPTH = 100;
    public static final int RRF_K = 60;
    public static final int EF_SEARCH = 400;
    public static final String CONNECTION_INIT_SQL = "set hnsw.ef_search = " + EF_SEARCH;
    public static final String EMBEDDING_MODEL = "text-embedding-3-small";
    public static final int EMBEDDING_DIMENSIONS = 1536;

    private RetrievalConfig() {}
}
