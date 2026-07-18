-- Orbit — Feature: Person↔Organisation links (§10)
-- Reference: final-features build plan §Feature 2c
--
-- A lightweight "who works where" signal linking a person entity to an
-- organization entity. Derived post-ingestion by matching a person's org_name
-- to an existing organization entity's name. IMPORTANT: this NEVER creates an
-- org entity — if no matching org exists, no link is written and the person's
-- plain org_name string stays as the (unlinked) workplace label.

CREATE TABLE entity_links (
    person_entity_id bigint      NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    org_entity_id    bigint      NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    role             text,
    source           text        NOT NULL DEFAULT 'derived',   -- 'derived' | 'confirmed'
    created_at       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (person_entity_id, org_entity_id)
);

CREATE INDEX idx_entity_links_org ON entity_links (org_entity_id);
