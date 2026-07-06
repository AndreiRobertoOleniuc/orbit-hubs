-- Orbit — Tags, entity_tags & entity summary
-- Reference: orbit-discovery-engine-reference.md §9 (Tags, skills & subscriptions)
--
-- Tags are first-class: they carry the Kompetenzen display, the distribution
-- match, and the subscription feed. Tags are AI-derived during ingestion
-- (origin='derived') and editable by the user (origin='manual'). Manual
-- edits are sticky — they survive re-harvests.
--
-- The entity summary is a rich, LLM-generated competence description that
-- aggregates ALL sources of an entity. It is re-generated after each new
-- ingestion, getting richer as more sources are added.

-- tags: controlled vocabulary (slug + label). New tags minted on demand,
-- normalized to a slug so subscriptions and distribution join cleanly.
CREATE TABLE tags (
  id          bigint primary key generated always as identity,
  slug        text not null unique,
  label       text not null,
  created_at  timestamptz not null default now()
);

-- entity_tags: an entity's Kompetenzen (skills).
-- origin='derived' → AI-derived from sources during ingestion (editable, replaced on re-harvest)
-- origin='manual'  → user-added/confirmed (sticky, never overwritten by re-harvest)
CREATE TABLE entity_tags (
  entity_id   bigint not null references entities(id) on delete cascade,
  tag_id      bigint not null references tags(id) on delete cascade,
  origin      text not null default 'derived',
  created_at  timestamptz not null default now(),
  primary key (entity_id, tag_id)
);
CREATE INDEX ON entity_tags (tag_id);
CREATE INDEX ON entity_tags (entity_id);

-- entities.summary: rich LLM-generated competence description (3-5 sentences).
-- Re-generated after each ingestion. Separate from headline (short one-liner).
ALTER TABLE entities ADD COLUMN summary text;