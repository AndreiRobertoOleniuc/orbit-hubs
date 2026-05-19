-- 08_schema.sql — BadenConnect v8 Schema
CREATE EXTENSION IF NOT EXISTS vector;

-- Entities: persons, companies, or inserate
CREATE TABLE IF NOT EXISTS entities (
    id TEXT PRIMARY KEY, -- stable slug, e.g. 'per-marc-hofer'
    type TEXT NOT NULL, -- 'person' | 'company' | 'inserat'
    name TEXT NOT NULL,
    canonical_location TEXT, -- "Baden", "Brugg", etc.
    scope TEXT NOT NULL, -- 'fhnw' | 'aargau' | 'ch'
    created_at TIMESTAMPTZ DEFAULT NOW(),
    summary TEXT -- LLM-synthesized bio for UI
);

-- Documents: the unit of retrieval (N documents per entity)
CREATE TABLE IF NOT EXISTS documents (
    id TEXT PRIMARY KEY, -- stable, e.g. 'gh-mhofer-oauth21-migration'
    entity_id TEXT REFERENCES entities(id) ON DELETE CASCADE,
    source_type TEXT NOT NULL, -- 'github_repo', 'website_page', etc.
    source_url TEXT,
    source_label TEXT, -- "GitHub repo · oauth21-migration"
    fetched_at TIMESTAMPTZ DEFAULT NOW(),
    text TEXT NOT NULL,
    embedding VECTOR(1024), -- multilingual-e5-large
    tsv TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', coalesce(text,''))) STORED,
    meta JSONB DEFAULT '{}'::jsonb
);

-- Indexes
CREATE INDEX IF NOT EXISTS documents_embedding_idx ON documents USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS documents_tsv_idx ON documents USING GIN (tsv);
CREATE INDEX IF NOT EXISTS documents_entity_id_idx ON documents (entity_id);
