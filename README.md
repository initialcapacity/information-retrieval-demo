# Information Retrieval Demo

A Java search demo comparing BM25 (keyword), embeddings (semantic), and their RRF hybrid over the PostgreSQL 18 manual: the retrieval component of a docs assistant, searching the documentation of the very database that serves it. Built for the DubJUG talk.

Stack: Java 26, Javalin, Postgres 18 with `pg_search` (BM25) and `pgvector` (embeddings), all in one database via the `paradedb/paradedb:pg18` image. Retrieval config: k=10, RRF k=60, `hnsw.ef_search=400`.

The documents: 1,779 section-level chunks parsed from the official `postgresql-18.1-docs.tar.gz` (PostgreSQL License), committed at `data/pgdocs/chunks.tsv`. Relevance labels are LLM judgments (UMBRELA-style, graded 0-3 and binarized) over pooled BM25 + embedding candidates for 56 hand-written developer queries; `Exact` means grade >= 2 (answers the query), `Partial` means grade 1 (related).

## Prerequisites

- Docker (running)
- JDK 26 (the Gradle wrapper is 9.5.1, which supports running on JDK 26)
- `OPENAI_API_KEY` set in `.env` (used to backfill chunk embeddings and to embed ad-hoc queries; the core demo serves from a committed cache)

## One-time setup

```bash
docker compose up -d                                 # ParadeDB (pg_search + pgvector) on host :5433
set -a && . ./.env && set +a                         # load DATABASE_URL + OPENAI_API_KEY

./scripts/verify.sh                                  # create db, migrate, build + test (offline)

./gradlew :applications:tools:ingestDocs             # load the 1,779 doc chunks (seconds)
./gradlew :applications:tools:backfillEmbeddings     # embed chunks + build HNSW (~2 min, one OpenAI pass)
```

Fixture query embeddings ship committed (`fixture-query-embeddings.tsv` and `data/query-embeddings.tsv`), so search over fixture queries and the eval run offline. `cacheQueryEmbeddings` regenerates them if the fixture changes.

## Run

```bash
./gradlew :applications:search:run
# Search view: http://localhost:8888/
# Eval view:   http://localhost:8888/eval
```

## Demo script (~5 minutes)

1. **The embeddings query.** On the Search view, click `my database keeps growing even though I delete rows`. BM25 returns PL/Perl and SSL configuration sections; embeddings return deleting-data and vacuuming sections. The query shares no vocabulary with the answer (the manual says "dead tuples" and "reclaiming storage"), so keyword search has nothing to match.
2. **The BM25 query.** Click `wal_level logical`. BM25 ranks all six relevant sections in its top 10; embeddings return nothing relevant. Exact config tokens need exact matching. The hybrid keeps the top result but only two of the six.
3. **The hybrid query.** Click `writes are slow when many clients commit at once`. BM25 finds 4 of 9 relevant sections, embeddings find a different 4 (only 2 shared), and the hybrid column shows 7 of 9 with the top four rows all relevant, including sections neither method ranked in its top 10.
4. **The numbers.** Open the Eval view. The eval scores all three methods with the same measure: BM25 0.349, embeddings 0.366, hybrid 0.403 F1 (Exact-only, k=10). The per-bucket table shows keyword queries scoring best with BM25 and semantic queries with embeddings, with the hybrid highest overall.

## Regenerate the eval snapshot

```bash
./gradlew :applications:tools:runEval    # reruns the eval and rewrites the committed eval-results.json
```

## Offline note

All three hero queries are fixture queries, so they serve from the committed embedding cache: the scripted demo is network-independent. Any *other* ad-hoc query typed live does a live OpenAI embed and needs `OPENAI_API_KEY` (without it, the UI degrades to a BM25-only column with a warning).

## Regenerating the chunks

`data/pgdocs/chunks.tsv` was produced from the official docs tarball by the chunker in `scripts/chunk_docs.py` (one chunk per page, split at h2/h3 past ~900 words, release notes excluded). Re-running it against a newer docs release changes chunk ids, which invalidates the committed qrels; re-label before swapping corpora.
