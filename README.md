# DubJUG Search Demo

A Java search demo comparing BM25 (lexical), dense embeddings (semantic), and their RRF hybrid over the [WANDS](https://github.com/wayfair/WANDS) product catalogue. Built for the DubJUG talk.

Stack: Java 26, Javalin, Postgres 18 with `pg_search` (BM25) and `pgvector` (dense), all in one database via the `paradedb/paradedb:pg18` image. Retrieval config: k=10, RRF k=60, `hnsw.ef_search=400`.

## Prerequisites

- Docker (running)
- JDK 26 (the Gradle wrapper is 9.5.1, which supports running on JDK 26)
- `OPENAI_API_KEY` set in `.env` (used to backfill product embeddings and to embed ad-hoc queries; the core demo serves from a committed cache)

## One-time setup

```bash
docker compose up -d                                 # ParadeDB (pg_search + pgvector) on host :5433
set -a && . ./.env && set +a                         # load DATABASE_URL + OPENAI_API_KEY

./scripts/verify.sh                                  # create db, migrate, build + test (offline)

./gradlew :applications:tools:ingestWands            # load ~43K WANDS products
./gradlew :applications:tools:backfillEmbeddings     # embed products + build HNSW (~20 min, one OpenAI pass)
./gradlew :applications:tools:cacheQueryEmbeddings   # cache the 116 fixture query vectors (offline search + eval)
./gradlew :applications:tools:cacheHeroQueries       # cache out-of-band hero queries (e.g. bathroom vanity knobs)
```

## Run

```bash
./gradlew :applications:search:run
# Search view: http://localhost:8888/
# Eval view:   http://localhost:8888/eval
```

## Demo script (~5 minutes)

1. **The clash.** On the Search view, click the `bathroom vanity knobs` hero query. BM25 returns bathroom vanity *sets* (the furniture); dense recovers the actual knobs. Keyword search lands in the wrong category, semantics rescues it. (Out-of-band query, so it does one live embed. See the offline note below.)
2. **Keyword wins.** Click `writing desk 48"`. BM25 nails the exact 48-inch desks; dense blurs across similar desks (all PARTIAL). Exact tokens and dimensions favour lexical.
3. **Semantics wins.** Click `beds that have leds`. Dense returns on-topic beds (LEDs are a feature, not in the title); BM25 drifts to a mirror and a sconce.
4. **The numbers.** Open the Eval view. The F-score climbs BM25 -> dense -> hybrid (Exact-only, k=10), and the per-bucket table shows each method winning its own turf: keyword -> BM25, semantic -> dense, mixed -> hybrid.

## Regenerate the eval snapshot

```bash
./gradlew :applications:tools:runEval    # reruns the eval and rewrites the committed eval-results.json
```

## Offline note

With both cache steps run, the entire demo is network-independent. The in-band hero queries (`beds that have leds`, `writing desk 48"`) come from the fixture cache; the out-of-band `bathroom vanity knobs` comes from the hero-query cache. Any *other* ad-hoc query typed live will still do a live OpenAI embed and needs `OPENAI_API_KEY`. To add more offline hero queries, append to `HERO_QUERIES` in `CacheHeroQueriesMain` and re-run `:applications:tools:cacheHeroQueries`.
