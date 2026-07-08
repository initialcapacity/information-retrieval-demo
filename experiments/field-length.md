# Step 4 - field-length dense-quality check

Sample: 30 queries (8 keyword, 12 semantic, 10 mixed), 12121 unique candidate products.
Recall@10 over each query's candidate pool (relevant + BM25 top-50 + dense top-50), same pool per variant.

| embedding field | mean recall@10 (Exact+Partial) | mean recall@10 (Exact only) |
|-----------------|-------------------------------|-----------------------------|
| name+desc+features (current) | 0.0554 | 0.2423 |
| name only | 0.0580 | 0.1867 |
| name+description | 0.0552 | 0.1836 |

### Full re-embed cost estimate (text-embedding-3-small @ $0.02 / 1M tokens, ~4 chars/token)

- products: 42994; avg chars name=40, name+desc=435, search_text(current)=2002
- name-only full re-embed: ~0.43M tokens, ~$0.009
- name+description full re-embed: ~4.67M tokens, ~$0.093
