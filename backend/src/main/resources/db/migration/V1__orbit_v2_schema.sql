-- Orbit v2 production schema
-- Flyway migration V1

CREATE EXTENSION IF NOT EXISTS vector;

-- Users (nullable self-claimed entity; FK added after entities exists)
CREATE TABLE users (
    id            text PRIMARY KEY,
    email         text UNIQUE NOT NULL,
    display_name  text,
    entity_id     text,
    created_at    timestamptz NOT NULL DEFAULT now()
);

-- Matchable entities (person or company)
CREATE TABLE entities (
    id                  text PRIMARY KEY,
    type                text NOT NULL,
    name                text NOT NULL,
    headline            text,
    summary             text,                         -- intentionally NOT embedded
    location            text,
    location_lat        double precision,
    location_lng        double precision,
    scope               text NOT NULL DEFAULT 'ch',
    presence_mode       text,
    verified            boolean NOT NULL DEFAULT false,
    handelsregister_uid text,
    owner_user_id       text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

-- Mutual FKs between users and entities (added after both tables exist)
ALTER TABLE users
    ADD CONSTRAINT fk_users_entity_id
    FOREIGN KEY (entity_id) REFERENCES entities(id);

ALTER TABLE entities
    ADD CONSTRAINT fk_entities_owner_user_id
    FOREIGN KEY (owner_user_id) REFERENCES users(id);

-- Evidence documents retrieved and cited for an entity
CREATE TABLE documents (
    id           text PRIMARY KEY,
    entity_id    text NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    source_type  text,
    source_url   text,
    source_label text,
    text         text NOT NULL,
    embedding    vector(1024),
    tsv          tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(text,''))) STORED,
    text_hash    text,
    meta         jsonb,
    fetched_at   timestamptz
);

-- Availability sub-record (1:1 with entity, nullable by absence)
CREATE TABLE availability (
    entity_id       text PRIMARY KEY REFERENCES entities(id) ON DELETE CASCADE,
    is_open         boolean NOT NULL DEFAULT false,
    available_from  date,
    capacity_note   text,
    preferred_modes text[],
    updated_at      timestamptz NOT NULL DEFAULT now()
);

-- Searchable demand-side requests
CREATE TABLE requests (
    id              text PRIMARY KEY,
    title           text NOT NULL CHECK (char_length(title) >= 6),
    description     text NOT NULL,
    owner_entity_id text REFERENCES entities(id),
    originator_type text,
    scope           text NOT NULL DEFAULT 'ch',
    presence_mode   text,
    status          text NOT NULL DEFAULT 'OPEN'
                      CHECK (status IN ('OPEN', 'DISTRIBUTED', 'CONTACTED', 'CLOSED')),
    embedding       vector(1024),
    tsv             tsvector GENERATED ALWAYS AS (
                        to_tsvector('simple',
                            coalesce(title,'') || ' ' || coalesce(description,'')
                        )
                    ) STORED,
    valid_until     date,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

-- Request lifecycle audit log
CREATE TABLE request_transitions (
    id            bigserial PRIMARY KEY,
    request_id    text NOT NULL REFERENCES requests(id) ON DELETE CASCADE,
    from_status   text,
    to_status     text,
    actor_user_id text NULL REFERENCES users(id),
    note          text,
    created_at    timestamptz NOT NULL DEFAULT now()
);

-- Who was notified about a request and why
CREATE TABLE request_distributions (
    id           bigserial PRIMARY KEY,
    request_id   text NOT NULL REFERENCES requests(id) ON DELETE CASCADE,
    entity_id    text NOT NULL REFERENCES entities(id),
    reason       text,
    matched_tags text[],
    created_at   timestamptz NOT NULL DEFAULT now()
);

-- One-time first contact handoffs
CREATE TABLE contact_handoffs (
    id              bigserial PRIMARY KEY,
    request_id      text NULL REFERENCES requests(id),
    from_user_id    text REFERENCES users(id),
    to_entity_id    text REFERENCES entities(id),
    message         text,
    delivered_email text,
    created_at      timestamptz NOT NULL DEFAULT now()
);

-- Tag vocabulary
CREATE TABLE tags (
    slug  text PRIMARY KEY,
    label text
);

-- Tag assignments and subscriptions
CREATE TABLE entity_tags (
    entity_id text REFERENCES entities(id) ON DELETE CASCADE,
    tag_slug  text REFERENCES tags(slug),
    PRIMARY KEY (entity_id, tag_slug)
);

CREATE TABLE request_tags (
    request_id text REFERENCES requests(id) ON DELETE CASCADE,
    tag_slug   text REFERENCES tags(slug),
    PRIMARY KEY (request_id, tag_slug)
);

CREATE TABLE tag_subscriptions (
    user_id  text REFERENCES users(id) ON DELETE CASCADE,
    tag_slug text REFERENCES tags(slug),
    PRIMARY KEY (user_id, tag_slug)
);

-- External source connections synced for an entity
CREATE TABLE source_connections (
    id             bigserial PRIMARY KEY,
    entity_id      text REFERENCES entities(id) ON DELETE CASCADE,
    source_type    text,
    source_ref     text,
    sync_status    text,
    last_synced_at timestamptz
);

-- Indexes
CREATE INDEX idx_documents_embedding ON documents USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_requests_embedding   ON requests   USING hnsw (embedding vector_cosine_ops);

CREATE INDEX idx_documents_tsv ON documents USING GIN (tsv);
CREATE INDEX idx_requests_tsv  ON requests   USING GIN (tsv);

CREATE INDEX idx_documents_entity_id ON documents(entity_id);
CREATE INDEX idx_requests_status     ON requests(status);
CREATE INDEX idx_requests_scope      ON requests(scope);
CREATE INDEX idx_entity_tags_tag_slug  ON entity_tags(tag_slug);
CREATE INDEX idx_request_tags_tag_slug ON request_tags(tag_slug);
