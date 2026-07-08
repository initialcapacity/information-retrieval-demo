create extension if not exists pg_search;
create extension if not exists vector;

create table products (
    product_id          bigint primary key,
    product_name        text,
    product_description text,
    product_features    text,
    product_class       text,
    category_hierarchy  text,
    search_text         text,
    embedding           vector(1536)
);

-- BM25 index (pg_search 0.24). key_field is the row identity for paradedb.score().
create index products_bm25 on products
using bm25 (product_id, search_text)
with (key_field = 'product_id');
