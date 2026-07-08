# Eval experiments: findings and recommendation

## TL;DR

The muddy story ("dense below BM25, hybrid barely ahead") was **not** a real ranking
result. It was an HNSW retrieval artifact: pgvector's default `hnsw.ef_search = 40`
under-retrieves, which depressed dense and hybrid even at k=10. Raising
`hnsw.ef_search` to ~200-400 (a standard, honest pgvector tuning knob, applied
equally to dense and hybrid) makes the climb hold, and dense becomes competitive
with or better than BM25 at every k. **No query curation was needed** (step 5 not
used). No re-embed is warranted (step 4 negative).

## Recommended demo configuration

**Exact-only binarization, k = 10, RRF k = 60, `hnsw.ef_search = 400`.**

Overall macro F1 (116 fixture queries):

| method | precision | recall | F1 |
|--------|-----------|--------|----|
| bm25   | 0.4210 | 0.2461 | 0.2827 |
| dense  | 0.4431 | 0.2501 | 0.2927 |
| **hybrid** | **0.4716** | **0.2744** | **0.3163** |

Clean monotonic climb, and hybrid is clearly best (+11.9% F1 over BM25, +8.1% over
dense) rather than the earlier ~2% edge.

Per-lean at this config tells the textbook story - each method wins its intended
bucket, hybrid wins the mixed bucket:

| bucket | n | bm25 F1 | dense F1 | hybrid F1 | winner |
|--------|---|---------|----------|-----------|--------|
| keyword | 19 | **0.6315** | 0.4649 | 0.6025 | BM25 |
| semantic | 55 | 0.0907 | **0.1812** | 0.1623 | dense |
| mixed  | 42 | 0.3765 | 0.3607 | **0.3955** | hybrid |

### Alternatives
- **Exact+Partial, k=10**: also a clean climb (0.1274 -> 0.1439 -> 0.1440) but
  hybrid's edge over dense is negligible; use if you want the default binarization.
- **k=20** (either binarization): cleaner hybrid-over-dense separation than k=10;
  Exact-only k=20 is 0.3398 -> 0.3633 -> 0.3860. Slightly less intuitive than
  "top 10 results" for a general audience.
- Absolute F1 rises through k=50 then falls (precision decays); k=10-20 is the
  legible zone. k=100 maximizes the raw hybrid-minus-baseline margin but recall
  over 20-40+ relevant products is hard to narrate on a slide.

## Step 1 - k-sweep (summary, F1)

Exact+Partial F1 - dense and hybrid beat BM25 at every k:

| method | k=5 | k=10 | k=20 | k=50 | k=100 |
|--------|-----|------|------|------|-------|
| bm25   | 0.0718 | 0.1274 | 0.2170 | 0.3558 | 0.4222 |
| dense  | 0.0792 | 0.1439 | 0.2438 | 0.3946 | 0.4526 |
| hybrid | 0.0806 | 0.1442 | 0.2473 | 0.3996 | 0.4623 |

Exact-only F1:

| method | k=5 | k=10 | k=20 | k=50 | k=100 |
|--------|-----|------|------|------|-------|
| bm25   | 0.2096 | 0.2827 | 0.3398 | 0.3092 | 0.2323 |
| dense  | 0.1995 | 0.2927 | 0.3633 | 0.3385 | 0.2368 |
| hybrid | 0.2311 | 0.3188 | 0.3860 | 0.3477 | 0.2442 |

(Full precision/recall/F1 tables for both binarizations in `results.md`.)

## Step 3 - RRF constant sweep

Fusion is robust to the RRF constant: over rrf_k in {10, 30, 60, 100} the hybrid F1
moves by <0.006 and beats both baselines at every value. Weighting does **not**
sharpen the win materially. Keep the standard rrf_k = 60. (Full table in
`results.md`.)

## Step 4 - field-length dense-quality check

Shortening the embedding field does **not** help; on Exact-only it hurts. On a
30-query sample (8 keyword / 12 semantic / 10 mixed), recall@10 over each query's
candidate pool:

| embedding field | recall@10 (Exact+Partial) | recall@10 (Exact only) |
|-----------------|---------------------------|------------------------|
| name+desc+features (current) | 0.0554 | **0.2423** |
| name only | 0.0580 | 0.1867 |
| name+description | 0.0552 | 0.1836 |

The current full field is best or tied. A full re-embed would be cheap
(name-only ~$0.01, name+description ~$0.09) but is **not worth it** since it does
not improve retrieval. Keep name+description+features. (Detail in `field-length.md`.)

## What changed in code

- `DataSourceFactory` gained a `connectionInitSql` overload.
- `EvalMain` and `SmokeMain` now open their pool with `set hnsw.ef_search = 400`.
  The future web app (StarterSetup) should do the same so live search matches the
  eval.
- `ExperimentMain` (k-sweep, per-bucket, RRF sweep) and
  `FieldLengthExperimentMain` (step 4) added under `applications/tools`, run via
  `./gradlew :applications:tools:runExperiments` and `:fieldExperiment`.
