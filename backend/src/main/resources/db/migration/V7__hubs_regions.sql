-- Orbit — Geo & scope: Hub → Region → CH
-- Reference: orbit-discovery-engine-reference.md §5, §14 (3.2 Geo & scope)
--
-- regions form a tree (CH at the root); hubs are affiliations (an institution,
-- incubator, or city scene) that belong to one region; hub_memberships is the
-- entity↔hub join that backs the hard scope filter. entities.home_region_id
-- anchors hub-less profiles to a region so region-scoped search still finds them.

-- regions: the region tree. CH is the root (parent_region_id = null).
CREATE TABLE regions (
  id                bigint primary key generated always as identity,
  slug              text not null unique,
  name              text not null,
  parent_region_id  bigint references regions(id),   -- null = root (CH)
  latitude          double precision,
  longitude         double precision,
  created_at        timestamptz not null default now()
);
CREATE INDEX ON regions (parent_region_id);

-- hubs: affiliations, each belonging to exactly one region.
CREATE TABLE hubs (
  id          bigint primary key generated always as identity,
  slug        text not null unique,
  name        text not null,
  hub_type    text,                                  -- 'university' | 'incubator' | 'scene' | …
  region_id   bigint not null references regions(id),
  latitude    double precision,
  longitude   double precision,
  created_at  timestamptz not null default now()
);
CREATE INDEX ON hubs (region_id);

-- hub_memberships: entity↔hub (many-to-many). source='derived' (from ingestion)
-- until the user confirms it in the profile (source='confirmed').
CREATE TABLE hub_memberships (
  entity_id   bigint not null references entities(id) on delete cascade,
  hub_id      bigint not null references hubs(id) on delete cascade,
  affiliation text,                                  -- "FHNW-E-Mail" | "Arbeitgeber" | …
  source      text not null default 'derived',       -- 'derived' | 'confirmed'
  joined_at   timestamptz not null default now(),
  primary key (entity_id, hub_id)
);
CREATE INDEX ON hub_memberships (hub_id);

-- entities.home_region_id: region anchor (derived from location during ingestion).
ALTER TABLE entities ADD COLUMN home_region_id bigint REFERENCES regions(id);
CREATE INDEX ON entities (home_region_id);

-- ── Seed: the Hub → Region → CH tree for the Baden/Aargau demo scope ──
INSERT INTO regions (slug, name, parent_region_id) VALUES ('ch', 'Schweiz', null);
INSERT INTO regions (slug, name, parent_region_id)
  SELECT 'aargau', 'Aargau', id FROM regions WHERE slug = 'ch';
INSERT INTO regions (slug, name, parent_region_id)
  SELECT 'region-baden', 'Region Baden', id FROM regions WHERE slug = 'aargau';

INSERT INTO hubs (slug, name, hub_type, region_id)
  SELECT 'fhnw', 'FHNW', 'university', id FROM regions WHERE slug = 'region-baden';
INSERT INTO hubs (slug, name, hub_type, region_id)
  SELECT 'powerplant', 'Baden 4.0 / PowerPlant', 'incubator', id FROM regions WHERE slug = 'region-baden';
