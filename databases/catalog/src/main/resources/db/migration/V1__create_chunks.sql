create extension if not exists pg_search;
create extension if not exists vector;

create table chunks (
    chunk_id    bigint primary key,
    title       text,
    page        text,
    search_text text,
    embedding   vector(1536)
);

-- BM25 index (pg_search 0.25.3). key_field is the row identity for paradedb.score().
create index chunks_bm25 on chunks
using bm25 (chunk_id, search_text)
with (key_field = 'chunk_id');
