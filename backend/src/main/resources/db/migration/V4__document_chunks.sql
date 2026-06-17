-- Orbit v2 schema — V4: split document text + embedding into document_chunks
--
-- A document is the citable unit (source/citation metadata); a chunk is the retrieval unit
-- (text + vector + FTS). Moving the embedding/FTS onto a child table means re-embedding one
-- document never rewrites another. No backfill: there are no existing document/chunk rows.

-- The embedded slices hybrid retrieval scores. Vector + FTS live here, per chunk.
CREATE TABLE document_chunks (
    document_id text NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index int  NOT NULL,
    text        text NOT NULL,
    embedding   vector(1024),
    tsv         tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(text, ''))) STORED,
    PRIMARY KEY (document_id, chunk_index)
);

-- ANN + FTS indexes on the chunk (same hnsw type / vector_cosine_ops opclass as requests.embedding).
CREATE INDEX idx_document_chunks_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_document_chunks_tsv       ON document_chunks USING GIN (tsv);

-- documents now holds citation/source metadata only — drop the moved text/vector/FTS columns.
DROP INDEX IF EXISTS idx_documents_embedding;
DROP INDEX IF EXISTS idx_documents_tsv;
ALTER TABLE documents DROP COLUMN tsv;
ALTER TABLE documents DROP COLUMN embedding;
ALTER TABLE documents DROP COLUMN text;
